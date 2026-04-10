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
        case speechSegment(samples: [Float])
        case silenceDetected
        case stopped
        case error(String)
    }

    private var process: Process?
    private var stdinPipe: Pipe?
    private var engine: AVAudioEngine?
    private var recentSamples: [Float] = []
    private var eventHandler: (@Sendable (Event) -> Void)?

    private var totalSamplesWritten: Int = 0
    private var peakRms: Float = 0
    private var silenceCheckFired = false
    private static let silenceCheckSamples = 16_000 * 2
    private static let silenceRmsThreshold: Float = 1e-6

    private var hangoverFramesMax = 0
    private static let frameDurationMs = 32

    private var inSpeech = false
    private var hangoverFramesRemaining = 0
    private var speechSamples: [Float] = []
    private var preRollSamples: [Float] = []
    private var speechFrameCount = 0
    private static let minSpeechFrames = 3
    private static let preRollBufferSize = 16_000 / 2  // 0.5s at 16kHz

    func start(
        runnerPath: String,
        modelPath: String,
        threshold: Float,
        hangoverMs: Int,
        eventHandler: @escaping @Sendable (Event) -> Void
    ) async throws {
        await stop()

        self.eventHandler = eventHandler
        recentSamples = []
        hangoverFramesMax = max(0, hangoverMs / Self.frameDurationMs)
        totalSamplesWritten = 0
        peakRms = 0
        silenceCheckFired = false
        inSpeech = false
        hangoverFramesRemaining = 0
        speechSamples = []
        preRollSamples = []
        speechFrameCount = 0

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
                await self.bufferSamples(samples)
                try? await self.write(samples: samples, to: handle)
            }
        }

        try engine.start()
        self.engine = engine
    }

    private func bufferSamples(_ samples: [Float]) {
        if inSpeech {
            speechSamples.append(contentsOf: samples)
        } else {
            preRollSamples.append(contentsOf: samples)
            if preRollSamples.count > Self.preRollBufferSize {
                preRollSamples.removeFirst(preRollSamples.count - Self.preRollBufferSize)
            }
        }
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
            speechFrameCount += 1
            hangoverFramesRemaining = hangoverFramesMax

            if !inSpeech && speechFrameCount >= Self.minSpeechFrames {
                inSpeech = true
                speechSamples = preRollSamples
                vadLog.info("Speech segment started")
            }
        } else if inSpeech {
            if hangoverFramesRemaining > 0 {
                hangoverFramesRemaining -= 1
            } else {
                let segment = speechSamples
                inSpeech = false
                speechSamples = []
                speechFrameCount = 0
                preRollSamples = []

                vadLog.info("Speech segment ended (\(segment.count) samples, \(String(format: "%.2f", Double(segment.count) / 16000))s)")
                emit(.speechSegment(samples: segment))
            }
        } else {
            speechFrameCount = 0
        }
    }

    private func emit(_ event: Event) {
        eventHandler?(event)
    }
}
