/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import AVFoundation
import Accelerate
import CoreAudio
import Foundation
import os

private let log = Logger(subsystem: "org.pytorch.executorch.ExecuWhisper", category: "AudioRecorder")

private final class NativeCaptureWriter: @unchecked Sendable {
    private let lock = NSLock()
    private var audioFile: AVAudioFile?
    private var captureURL: URL?

    func append(_ buffer: AVAudioPCMBuffer) throws {
        lock.lock()
        defer { lock.unlock() }

        if audioFile == nil {
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("execuwhisper_capture_\(UUID().uuidString).wav")
            let file = try AVAudioFile(
                forWriting: url,
                settings: buffer.format.settings,
                commonFormat: buffer.format.commonFormat,
                interleaved: buffer.format.isInterleaved
            )
            audioFile = file
            captureURL = url
        }

        try audioFile?.write(from: buffer)
    }

    func finish() -> URL? {
        lock.lock()
        defer { lock.unlock() }
        audioFile = nil
        return captureURL
    }
}

actor AudioRecorder {
    struct CoreAudioDeviceRecord: Equatable, Sendable {
        let id: AudioDeviceID
        let uid: String
        let name: String
        let inputChannelCount: UInt32
    }

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

    private let modelSampleRate: Double = 16_000
    private static let postStopTailTrimDurationMs: Double = 256
    private var engine: AVAudioEngine?
    private var writer = NativeCaptureWriter()
    private var selectedDeviceUID: String?
    private var levelHandler: (@Sendable (Float) -> Void)?
    private var configurationObserver: NSObjectProtocol?
    private var isRecoveringConfiguration = false

    func startRecording(
        selectedMicrophoneID: String? = nil,
        levelHandler: @Sendable @escaping (Float) -> Void
    ) throws {
        if engine != nil {
            stopCaptureOnly()
        }

        writer = NativeCaptureWriter()

        let availableDevices = Self.availableInputDevices()
        guard let resolvedDevice = Self.resolvePreferredMicrophone(
            selectedMicrophoneID: selectedMicrophoneID,
            availableDevices: availableDevices
        ) else {
            throw RunnerError.microphoneNotAvailable
        }

        selectedDeviceUID = resolvedDevice.device.id
        self.levelHandler = levelHandler

        let audioEngine = AVAudioEngine()
        let inputNode = audioEngine.inputNode

        guard let deviceID = Self.audioDeviceID(forUID: resolvedDevice.device.id) else {
            log.error("Could not resolve Core Audio input device for uid=\(resolvedDevice.device.id, privacy: .public)")
            throw RunnerError.microphoneNotAvailable
        }
        try Self.bindEngineInput(inputNode: inputNode, to: deviceID)

        // Pass nil format so AVAudioEngine uses the bus's actual hardware format after
        // we bound the AU to the chosen device. Caching outputFormat(forBus:) here used
        // to capture a stale 48 kHz / 2-channel format from whatever device the engine
        // momentarily latched onto before our bind, which then made `installTap` fail
        // with "Format mismatch" + "config change pending!" on Macs whose mic runs at a
        // different rate (e.g. 24 kHz). The buffer delivered to the tap carries its
        // native format; ImportedAudioDecoder downstream resamples to 16 kHz mono.
        installTap(on: inputNode, levelHandler: levelHandler)

        do {
            try audioEngine.start()
        } catch {
            inputNode.removeTap(onBus: 0)
            throw error
        }
        self.engine = audioEngine
        observeConfigurationChanges(for: audioEngine)

        let runtimeFormat = inputNode.inputFormat(forBus: 0)
        log.info("Audio recording engine bound: device=\(resolvedDevice.device.name, privacy: .public) sampleRate=\(runtimeFormat.sampleRate) channelCount=\(runtimeFormat.channelCount)")

        if resolvedDevice.usedFallback {
            log.info("Selected microphone unavailable; falling back to system default '\(resolvedDevice.device.name, privacy: .public)'")
        }
        log.info("Audio recording started with microphone '\(resolvedDevice.device.name, privacy: .public)'")
    }

    func stopRecording() throws -> Data {
        stopCaptureOnly()

        guard let captureURL = writer.finish() else {
            throw RunnerError.transcriptionFailed(description: "No audio was captured.")
        }
        defer { try? FileManager.default.removeItem(at: captureURL) }

        let decoded = try ImportedAudioDecoder().decodeAudioFile(at: captureURL)
        guard !decoded.pcmData.isEmpty else {
            throw RunnerError.transcriptionFailed(description: "No audio was captured.")
        }

        let trimmedPCM = Self.trimTrailingPCM(
            decoded.pcmData,
            sampleRate: modelSampleRate,
            trimDurationMs: Self.postStopTailTrimDurationMs
        )
        log.info("Captured \(trimmedPCM.count) bytes of 16kHz float32 PCM")
        return trimmedPCM
    }

    func cancelRecording() {
        stopCaptureOnly()
        if let url = writer.finish() {
            try? FileManager.default.removeItem(at: url)
        }
    }

    // MARK: - Device enumeration

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

    // MARK: - Utilities

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

    // MARK: - Private

    private static func discoveredAudioCaptureDevices() -> [AVCaptureDevice] {
        AVCaptureDevice.DiscoverySession(
            deviceTypes: [.microphone],
            mediaType: .audio,
            position: .unspecified
        ).devices
    }

    private func stopCaptureOnly() {
        if let configurationObserver {
            NotificationCenter.default.removeObserver(configurationObserver)
            self.configurationObserver = nil
        }
        if let engine {
            engine.inputNode.removeTap(onBus: 0)
            if engine.isRunning {
                engine.stop()
            }
            engine.reset()
        }
        engine = nil
        selectedDeviceUID = nil
        levelHandler = nil
        isRecoveringConfiguration = false
        log.info("Audio recording stopped")
    }

    private static func bindEngineInput(inputNode: AVAudioInputNode, to deviceID: AudioDeviceID) throws {
        guard let audioUnit = inputNode.audioUnit else {
            log.error("Input node has no audio unit; cannot bind to device id=\(deviceID)")
            throw RunnerError.microphoneNotAvailable
        }
        var mutableID = deviceID
        let status = AudioUnitSetProperty(
            audioUnit,
            kAudioOutputUnitProperty_CurrentDevice,
            kAudioUnitScope_Global,
            0,
            &mutableID,
            UInt32(MemoryLayout<AudioDeviceID>.size)
        )
        if status != noErr {
            log.error("Failed to bind input AU to device id=\(deviceID) status=\(status)")
            throw RunnerError.microphoneNotAvailable
        }
    }

    private func observeConfigurationChanges(for engine: AVAudioEngine) {
        configurationObserver = NotificationCenter.default.addObserver(
            forName: .AVAudioEngineConfigurationChange,
            object: engine,
            queue: nil
        ) { [weak self] _ in
            Task {
                await self?.recoverFromConfigurationChange()
            }
        }
    }

    private func recoverFromConfigurationChange() async {
        guard !isRecoveringConfiguration,
              let engine,
              let selectedDeviceUID,
              let levelHandler
        else {
            return
        }

        isRecoveringConfiguration = true
        defer { isRecoveringConfiguration = false }

        do {
            let inputNode = engine.inputNode
            inputNode.removeTap(onBus: 0)
            if engine.isRunning {
                engine.stop()
            }
            guard let deviceID = Self.audioDeviceID(forUID: selectedDeviceUID) else {
                log.error("Audio config change recovery failed: device uid unavailable uid=\(selectedDeviceUID, privacy: .public)")
                return
            }
            try Self.bindEngineInput(inputNode: inputNode, to: deviceID)
            installTap(on: inputNode, levelHandler: levelHandler)
            try engine.start()
            let runtimeFormat = inputNode.inputFormat(forBus: 0)
            log.info("Audio recording config change recovered: uid=\(selectedDeviceUID, privacy: .public) sampleRate=\(runtimeFormat.sampleRate) channelCount=\(runtimeFormat.channelCount)")
        } catch {
            log.error("Audio config change recovery failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    private func installTap(
        on inputNode: AVAudioInputNode,
        levelHandler: @Sendable @escaping (Float) -> Void
    ) {
        let captureWriter = writer
        inputNode.installTap(onBus: 0, bufferSize: 4096, format: nil) { buffer, _ in
            guard buffer.frameLength > 0 else { return }

            if let channelData = buffer.floatChannelData {
                var rms: Float = 0
                vDSP_rmsqv(channelData[0], 1, &rms, vDSP_Length(buffer.frameLength))
                levelHandler(rms)
            }

            do {
                try captureWriter.append(buffer)
            } catch {
                log.error("Failed to write capture buffer: \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    static func selectInputDeviceID(forUID uid: String, from records: [CoreAudioDeviceRecord]) -> AudioDeviceID? {
        records.first { $0.uid == uid && $0.inputChannelCount > 0 }?.id
    }

    static func audioDeviceID(forUID uid: String) -> AudioDeviceID? {
        selectInputDeviceID(forUID: uid, from: coreAudioDeviceRecords())
    }

    static func coreAudioDeviceRecords() -> [CoreAudioDeviceRecord] {
        var size: UInt32 = 0
        var address = AudioObjectPropertyAddress(
            mSelector: kAudioHardwarePropertyDevices,
            mScope: kAudioObjectPropertyScopeGlobal,
            mElement: kAudioObjectPropertyElementMain
        )
        guard AudioObjectGetPropertyDataSize(
            AudioObjectID(kAudioObjectSystemObject),
            &address,
            0, nil,
            &size
        ) == noErr, size > 0 else { return [] }

        let count = Int(size) / MemoryLayout<AudioDeviceID>.size
        var deviceIDs = [AudioDeviceID](repeating: 0, count: count)
        guard AudioObjectGetPropertyData(
            AudioObjectID(kAudioObjectSystemObject),
            &address,
            0, nil,
            &size,
            &deviceIDs
        ) == noErr else { return [] }

        return deviceIDs.compactMap { deviceID in
            guard let uid = stringProperty(
                deviceID: deviceID,
                selector: kAudioDevicePropertyDeviceUID
            ) else {
                return nil
            }
            let name = stringProperty(
                deviceID: deviceID,
                selector: kAudioObjectPropertyName
            ) ?? uid
            let inputChannels = inputChannelCount(for: deviceID)
            return CoreAudioDeviceRecord(
                id: deviceID,
                uid: uid,
                name: name,
                inputChannelCount: inputChannels
            )
        }
    }

    private static func stringProperty(deviceID: AudioDeviceID, selector: AudioObjectPropertySelector) -> String? {
        var address = AudioObjectPropertyAddress(
            mSelector: selector,
            mScope: kAudioObjectPropertyScopeGlobal,
            mElement: kAudioObjectPropertyElementMain
        )
        var value: CFString = "" as CFString
        var size = UInt32(MemoryLayout<CFString>.size)
        guard AudioObjectGetPropertyData(deviceID, &address, 0, nil, &size, &value) == noErr else {
            return nil
        }
        return value as String
    }

    private static func inputChannelCount(for deviceID: AudioDeviceID) -> UInt32 {
        var streamAddress = AudioObjectPropertyAddress(
            mSelector: kAudioDevicePropertyStreamConfiguration,
            mScope: kAudioObjectPropertyScopeInput,
            mElement: kAudioObjectPropertyElementMain
        )
        var streamSize: UInt32 = 0
        guard AudioObjectGetPropertyDataSize(deviceID, &streamAddress, 0, nil, &streamSize) == noErr,
              streamSize > 0
        else {
            return 0
        }

        let bufferListPointer = UnsafeMutablePointer<AudioBufferList>.allocate(capacity: Int(streamSize))
        defer { bufferListPointer.deallocate() }
        guard AudioObjectGetPropertyData(deviceID, &streamAddress, 0, nil, &streamSize, bufferListPointer) == noErr else {
            return 0
        }
        let bufferList = UnsafeMutableAudioBufferListPointer(bufferListPointer)
        return bufferList.reduce(UInt32(0)) { $0 + $1.mNumberChannels }
    }
}
