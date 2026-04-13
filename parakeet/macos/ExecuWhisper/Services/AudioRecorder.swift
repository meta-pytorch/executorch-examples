/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import AVFoundation
import Accelerate
import AudioToolbox
import CoreMedia
import Foundation
import os

private let log = Logger(subsystem: "org.pytorch.executorch.ExecuWhisper", category: "AudioRecorder")

private final class PCMBufferAccumulator: @unchecked Sendable {
    private let lock = NSLock()
    private var data = Data()

    func append(_ newData: Data) {
        lock.lock()
        data.append(newData)
        lock.unlock()
    }

    func takeData() -> Data {
        lock.lock()
        defer { lock.unlock() }
        return data
    }
}

private final class AudioCaptureOutputDelegate: NSObject, AVCaptureAudioDataOutputSampleBufferDelegate {
    private let accumulator: PCMBufferAccumulator
    private let levelHandler: @Sendable (Float) -> Void
    private var hasLoggedFormat = false

    init(
        accumulator: PCMBufferAccumulator,
        levelHandler: @escaping @Sendable (Float) -> Void
    ) {
        self.accumulator = accumulator
        self.levelHandler = levelHandler
    }

    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        if !hasLoggedFormat,
           let formatDescription = CMSampleBufferGetFormatDescription(sampleBuffer),
           let asbd = CMAudioFormatDescriptionGetStreamBasicDescription(formatDescription)?.pointee {
            log.info("Hardware audio format: \(asbd.mSampleRate)Hz, \(asbd.mChannelsPerFrame) channels")
            hasLoggedFormat = true
        }

        var blockBuffer: CMBlockBuffer?
        var audioBufferList = AudioBufferList(
            mNumberBuffers: 1,
            mBuffers: AudioBuffer(mNumberChannels: 1, mDataByteSize: 0, mData: nil)
        )

        let status = withUnsafeMutablePointer(to: &audioBufferList) { bufferListPointer in
            bufferListPointer.withMemoryRebound(to: AudioBufferList.self, capacity: 1) { reboundPointer in
                CMSampleBufferGetAudioBufferListWithRetainedBlockBuffer(
                    sampleBuffer,
                    bufferListSizeNeededOut: nil,
                    bufferListOut: reboundPointer,
                    bufferListSize: MemoryLayout<AudioBufferList>.size,
                    blockBufferAllocator: nil,
                    blockBufferMemoryAllocator: nil,
                    flags: UInt32(kCMSampleBufferFlag_AudioBufferList_Assure16ByteAlignment),
                    blockBufferOut: &blockBuffer
                )
            }
        }

        guard status == noErr else {
            log.error("Failed to read captured audio buffer list with OSStatus \(status)")
            return
        }

        let audioBuffer = audioBufferList.mBuffers
        guard let dataPointer = audioBuffer.mData, audioBuffer.mDataByteSize > 0 else {
            return
        }
        guard audioBuffer.mDataByteSize % UInt32(MemoryLayout<Float>.size) == 0 else {
            log.error("Captured audio buffer size \(audioBuffer.mDataByteSize) was not float32 aligned")
            return
        }

        let sampleCount = Int(audioBuffer.mDataByteSize) / MemoryLayout<Float>.size
        let samples = dataPointer.assumingMemoryBound(to: Float.self)

        var rms: Float = 0
        vDSP_rmsqv(samples, 1, &rms, vDSP_Length(sampleCount))
        levelHandler(rms)

        let data = Data(bytes: samples, count: Int(audioBuffer.mDataByteSize))
        accumulator.append(data)
    }
}

actor AudioRecorder {
    struct InputDevice: Identifiable, Equatable, Sendable {
        let id: String
        let name: String
        let isDefault: Bool

        var displayName: String {
            isDefault ? "\(name) (System Default)" : name
        }
    }

    struct ResolvedInputDevice: Equatable, Sendable {
        let device: InputDevice
        let usedFallback: Bool
    }

    private let sampleRate: Double = 16_000
    private static let postStopTailTrimDurationMs: Double = 256
    private var captureSession: AVCaptureSession?
    private var captureOutputDelegate: AudioCaptureOutputDelegate?
    private var captureQueue: DispatchQueue?
    private var accumulator = PCMBufferAccumulator()

    func startRecording(
        selectedMicrophoneID: String? = nil,
        levelHandler: @Sendable @escaping (Float) -> Void
    ) throws {
        if captureSession != nil {
            stopCaptureOnly()
        }

        accumulator = PCMBufferAccumulator()

        let availableDevices = Self.availableInputDevices()
        guard let resolvedDevice = Self.resolvePreferredMicrophone(
            selectedMicrophoneID: selectedMicrophoneID,
            availableDevices: availableDevices
        ) else {
            throw RunnerError.microphoneNotAvailable
        }

        guard let captureDevice = Self.discoveredAudioCaptureDevices().first(where: {
            $0.uniqueID == resolvedDevice.device.id
        }) else {
            throw RunnerError.microphoneNotAvailable
        }

        let session = AVCaptureSession()
        let input = try AVCaptureDeviceInput(device: captureDevice)
        let output = AVCaptureAudioDataOutput()
        output.audioSettings = [
            AVFormatIDKey: Int(kAudioFormatLinearPCM),
            AVSampleRateKey: sampleRate,
            AVNumberOfChannelsKey: 1,
            AVLinearPCMBitDepthKey: 32,
            AVLinearPCMIsFloatKey: true,
            AVLinearPCMIsBigEndianKey: false,
            AVLinearPCMIsNonInterleaved: false,
        ]

        guard session.canAddInput(input) else {
            throw RunnerError.launchFailed(
                description: "Unable to use microphone '\(resolvedDevice.device.name)' as an audio input."
            )
        }
        guard session.canAddOutput(output) else {
            throw RunnerError.launchFailed(
                description: "Unable to capture audio from microphone '\(resolvedDevice.device.name)'."
            )
        }

        let captureQueue = DispatchQueue(label: "org.pytorch.executorch.ExecuWhisper.AudioRecorder")
        let delegate = AudioCaptureOutputDelegate(
            accumulator: accumulator,
            levelHandler: levelHandler
        )
        output.setSampleBufferDelegate(delegate, queue: captureQueue)

        session.beginConfiguration()
        session.addInput(input)
        session.addOutput(output)
        session.commitConfiguration()
        session.startRunning()

        self.captureSession = session
        self.captureOutputDelegate = delegate
        self.captureQueue = captureQueue
        if resolvedDevice.usedFallback {
            log.info("Selected microphone unavailable; falling back to system default '\(resolvedDevice.device.name, privacy: .public)'")
        }
        log.info("Audio recording started with microphone '\(resolvedDevice.device.name, privacy: .public)'")
    }

    func stopRecording() throws -> Data {
        stopCaptureOnly()

        let pcmData = accumulator.takeData()
        guard !pcmData.isEmpty else {
            throw RunnerError.transcriptionFailed(description: "No audio was captured.")
        }

        let trimmedPCM = Self.trimTrailingPCM(
            pcmData,
            sampleRate: sampleRate,
            trimDurationMs: Self.postStopTailTrimDurationMs
        )
        let trimmedBytes = pcmData.count - trimmedPCM.count
        log.info("Captured \(trimmedPCM.count) bytes of float32 PCM audio after trimming \(trimmedBytes) tail bytes")
        return trimmedPCM
    }

    func cancelRecording() {
        stopCaptureOnly()
        accumulator = PCMBufferAccumulator()
    }

    static func availableInputDevices() -> [InputDevice] {
        let defaultDeviceID = AVCaptureDevice.default(for: .audio)?.uniqueID
        var seenIDs: Set<String> = []

        return discoveredAudioCaptureDevices()
            .compactMap { device in
                guard seenIDs.insert(device.uniqueID).inserted else { return nil }
                return InputDevice(
                    id: device.uniqueID,
                    name: device.localizedName,
                    isDefault: device.uniqueID == defaultDeviceID
                )
            }
            .sorted { lhs, rhs in
                if lhs.isDefault != rhs.isDefault {
                    return lhs.isDefault && !rhs.isDefault
                }
                return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
            }
    }

    static func resolvePreferredMicrophone(
        selectedMicrophoneID: String?,
        availableDevices: [InputDevice]
    ) -> ResolvedInputDevice? {
        guard !availableDevices.isEmpty else { return nil }

        let normalizedSelection = selectedMicrophoneID?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let hasExplicitSelection = normalizedSelection.map { !$0.isEmpty } ?? false

        if let normalizedSelection, !normalizedSelection.isEmpty,
           let exactMatch = availableDevices.first(where: { $0.id == normalizedSelection }) {
            return ResolvedInputDevice(device: exactMatch, usedFallback: false)
        }

        let fallbackDevice = availableDevices.first(where: \.isDefault) ?? availableDevices[0]
        return ResolvedInputDevice(device: fallbackDevice, usedFallback: hasExplicitSelection)
    }

    private static func discoveredAudioCaptureDevices() -> [AVCaptureDevice] {
        AVCaptureDevice.DiscoverySession(
            deviceTypes: [.microphone],
            mediaType: .audio,
            position: .unspecified
        ).devices
    }

    private func stopCaptureOnly() {
        captureSession?.stopRunning()
        captureSession = nil
        captureOutputDelegate = nil
        captureQueue = nil
        log.info("Audio recording stopped")
    }

    static func trimTrailingPCM(
        _ pcmData: Data,
        sampleRate: Double,
        trimDurationMs: Double
    ) -> Data {
        guard trimDurationMs > 0 else { return pcmData }

        let bytesPerSample = MemoryLayout<Float>.size
        let trimSampleCount = Int((sampleRate * trimDurationMs) / 1000.0)
        let trimByteCount = trimSampleCount * bytesPerSample
        guard trimByteCount > 0, pcmData.count > trimByteCount + bytesPerSample else {
            return pcmData
        }

        return Data(pcmData.prefix(pcmData.count - trimByteCount))
    }
}
