/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import AVFoundation
import Foundation
import os

private let vadLog = Logger(subsystem: "org.pytorch.executorch.VoxtralRealtime", category: "VadService")

actor VadService {
    enum Event: Sendable {
        case ready
        case speechDetected(preRollSamples: [Float])
        case silenceDetected
        case stopped
        case error(String)
    }

    private var process: Process?
    private var stdinPipe: Pipe?
    private var engine: AVAudioEngine?
    private var recentSamples: [Float] = []
    private var byteBuffer = Data()
    private var consecutiveSpeechFrames = 0
    private var armed = true
    private var eventHandler: (@Sendable (Event) -> Void)?

    private var totalSamplesWritten: Int = 0
    private var peakRms: Float = 0
    private var silenceCheckFired = false
    private static let silenceCheckSamples = 16_000 * 2  // 2s at 16kHz
    private static let silenceRmsThreshold: Float = 1e-6

    private var hangoverFramesRemaining = 0
    private var hangoverFramesMax = 0
    private static let frameDurationMs = 32

    func start(
        runnerPath: String,
        modelPath: String,
        threshold: Float,
        hangoverMs: Int,
        eventHandler: @escaping @Sendable (Event) -> Void
    ) async throws {
        await stop()

        self.eventHandler = eventHandler
        armed = true
        recentSamples = []
        byteBuffer = Data()
        consecutiveSpeechFrames = 0
        hangoverFramesRemaining = 0
        hangoverFramesMax = max(0, hangoverMs / Self.frameDurationMs)
        totalSamplesWritten = 0
        peakRms = 0
        silenceCheckFired = false

        let stdoutPipe = Pipe()
        let stdinPipe = Pipe()
        self.stdinPipe = stdinPipe

        let process = Process()
        process.executableURL = URL(fileURLWithPath: runnerPath)
        process.arguments = [
            "--model_path", modelPath
        ]
        process.standardInput = stdinPipe
        process.standardOutput = stdoutPipe
        process.standardError = Pipe()
        self.process = process

        process.terminationHandler = { [weak self] _ in
            Task {
                await self?.emit(.stopped)
            }
        }

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let handle = stdoutPipe.fileHandleForReading
            while true {
                let data = handle.availableData
                if data.isEmpty { break }
                guard let text = String(data: data, encoding: .utf8) else { continue }
                for line in text.split(whereSeparator: \.isNewline) {
                    Task {
                        await self?.handleOutputLine(String(line), threshold: threshold)
                    }
                }
            }
        }

        try process.run()
        try await startAudioCapture()
        vadLog.info("Silero VAD started")
    }

    func stop() async {
        engine?.inputNode.removeTap(onBus: 0)
        engine?.stop()
        engine = nil

        stdinPipe?.fileHandleForWriting.closeFile()
        stdinPipe = nil

        if let process, process.isRunning {
            process.terminate()
        }
        process = nil
    }

    private func startAudioCapture() async throws {
        guard let handle = stdinPipe?.fileHandleForWriting else {
            throw RunnerError.launchFailed(description: "VAD stdin not available")
        }

        let engine = AVAudioEngine()
        let inputNode = engine.inputNode
        let hwFormat = inputNode.outputFormat(forBus: 0)
        guard hwFormat.sampleRate > 0, hwFormat.channelCount > 0 else {
            throw RunnerError.microphoneNotAvailable
        }

        let targetFormat = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: 16_000,
            channels: 1,
            interleaved: false
        )!
        guard let converter = AVAudioConverter(from: hwFormat, to: targetFormat) else {
            throw RunnerError.launchFailed(description: "Cannot create VAD audio converter")
        }

        let sampleRateRatio = 16_000.0 / hwFormat.sampleRate
        inputNode.installTap(onBus: 0, bufferSize: 4096, format: hwFormat) { [weak self] buffer, _ in
            guard let self else { return }
            let capacity = AVAudioFrameCount(Double(buffer.frameLength) * sampleRateRatio) + 1
            guard let converted = AVAudioPCMBuffer(pcmFormat: targetFormat, frameCapacity: capacity) else {
                return
            }

            var consumed = false
            var error: NSError?
            converter.convert(to: converted, error: &error) { _, outStatus in
                if !consumed {
                    consumed = true
                    outStatus.pointee = .haveData
                    return buffer
                }
                outStatus.pointee = .noDataNow
                return nil
            }

            guard error == nil,
                  converted.frameLength > 0,
                  let channelData = converted.floatChannelData
            else { return }

            let frameCount = Int(converted.frameLength)
            let samples = Array(UnsafeBufferPointer(start: channelData[0], count: frameCount))
            Task {
                await self.appendRecent(samples)
                try? await self.write(samples: samples, to: handle)
            }
        }

        try engine.start()
        self.engine = engine
    }

    private func write(samples: [Float], to handle: FileHandle) throws {
        guard !samples.isEmpty else { return }
        let data = samples.withUnsafeBufferPointer { Data(buffer: $0) }
        try handle.write(contentsOf: data)

        let sumSq = samples.reduce(Float(0)) { $0 + $1 * $1 }
        let rms = (sumSq / Float(samples.count)).squareRoot()
        peakRms = max(peakRms, rms)
        totalSamplesWritten += samples.count

        if !silenceCheckFired && totalSamplesWritten >= Self.silenceCheckSamples {
            silenceCheckFired = true
            if peakRms < Self.silenceRmsThreshold {
                vadLog.warning("Microphone producing silence after \(self.totalSamplesWritten) samples (peak RMS: \(self.peakRms))")
                emit(.silenceDetected)
            }
        }
    }

    private func appendRecent(_ samples: [Float]) {
        recentSamples.append(contentsOf: samples)
        let maxSamples = 16_000 * 2
        if recentSamples.count > maxSamples {
            recentSamples.removeFirst(recentSamples.count - maxSamples)
        }
    }

    private func handleOutputLine(_ line: String, threshold: Float) {
        if line == "READY" {
            emit(.ready)
            return
        }

        let parts = line.split(separator: " ")
        guard parts.count == 3, parts[0] == "PROB", let probability = Float(parts[2]) else {
            return
        }

        if probability >= threshold {
            consecutiveSpeechFrames += 1
            hangoverFramesRemaining = hangoverFramesMax
        } else if hangoverFramesRemaining > 0 {
            hangoverFramesRemaining -= 1
        } else {
            consecutiveSpeechFrames = 0
        }

        if armed && consecutiveSpeechFrames >= 2 {
            armed = false
            emit(.speechDetected(preRollSamples: recentSamples))
        }
    }

    private func emit(_ event: Event) {
        eventHandler?(event)
    }
}
