/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import AVFoundation
import CoreAudio
import Foundation
import Testing

struct AudioRecorderTests {
    @Test
    func resolvePreferredMicrophoneUsesExactSavedDeviceWhenAvailable() {
        let available = [
            AudioRecorder.InputDevice(id: "default", name: "MacBook Microphone", isDefault: true),
            AudioRecorder.InputDevice(id: "usb", name: "USB Audio Device", isDefault: false),
        ]

        let resolved = AudioRecorder.resolvePreferredMicrophone(
            selectedMicrophoneID: "usb",
            availableDevices: available
        )

        #expect(resolved?.device.id == "usb")
        #expect(resolved?.usedFallback == false)
    }

    @Test
    func resolvePreferredMicrophoneFallsBackToDefaultWhenSavedDeviceIsMissing() {
        let available = [
            AudioRecorder.InputDevice(id: "default", name: "MacBook Microphone", isDefault: true),
            AudioRecorder.InputDevice(id: "usb", name: "USB Audio Device", isDefault: false),
        ]

        let resolved = AudioRecorder.resolvePreferredMicrophone(
            selectedMicrophoneID: "missing",
            availableDevices: available
        )

        #expect(resolved?.device.id == "default")
        #expect(resolved?.usedFallback == true)
    }

    @Test
    func resolvePreferredMicrophoneReturnsNilWhenNoDevicesAreAvailable() {
        let resolved = AudioRecorder.resolvePreferredMicrophone(
            selectedMicrophoneID: "missing",
            availableDevices: []
        )

        #expect(resolved == nil)
    }

    @Test
    func selectInputDeviceIDUsesExactUIDAndRequiresInputChannels() {
        let records = [
            AudioRecorder.CoreAudioDeviceRecord(
                id: AudioDeviceID(100),
                uid: "same-name-output",
                name: "AirPods Pro",
                inputChannelCount: 0
            ),
            AudioRecorder.CoreAudioDeviceRecord(
                id: AudioDeviceID(101),
                uid: "airpods-left",
                name: "AirPods Pro",
                inputChannelCount: 1
            ),
            AudioRecorder.CoreAudioDeviceRecord(
                id: AudioDeviceID(102),
                uid: "airpods-right",
                name: "AirPods Pro",
                inputChannelCount: 1
            ),
        ]

        #expect(AudioRecorder.selectInputDeviceID(forUID: "airpods-right", from: records) == AudioDeviceID(102))
        #expect(AudioRecorder.selectInputDeviceID(forUID: "same-name-output", from: records) == nil)
        #expect(AudioRecorder.selectInputDeviceID(forUID: "missing", from: records) == nil)
    }

    @Test
    func trimTrailingPCMRemovesConfiguredTailFromLongCapture() {
        let pcmData = makePCMData(sampleCount: 16_000)

        let trimmed = AudioRecorder.trimTrailingPCM(
            pcmData,
            sampleRate: 16_000,
            trimDurationMs: 256
        )

        let expectedTrimmedSamples = 16_000 - 4_096
        #expect(trimmed.count == expectedTrimmedSamples * MemoryLayout<Float>.size)
    }

    @Test
    func trimTrailingPCMPreservesShortCapture() {
        let pcmData = makePCMData(sampleCount: 2_000)

        let trimmed = AudioRecorder.trimTrailingPCM(
            pcmData,
            sampleRate: 16_000,
            trimDurationMs: 256
        )

        #expect(trimmed == pcmData)
    }

    @Test
    func nativeCaptureWriterCreatesReadableWAVFile() throws {
        let format = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: 44_100,
            channels: 2,
            interleaved: false
        )!
        let frameCount: AVAudioFrameCount = 4_410
        let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frameCount)!
        buffer.frameLength = frameCount
        for channel in 0..<Int(format.channelCount) {
            let samples = buffer.floatChannelData![channel]
            for frame in 0..<Int(frameCount) {
                samples[frame] = Float(frame) / Float(frameCount)
            }
        }

        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let fileURL = tempDir.appendingPathComponent("native_test.wav")
        do {
            let outputFile = try AVAudioFile(
                forWriting: fileURL,
                settings: format.settings,
                commonFormat: format.commonFormat,
                interleaved: format.isInterleaved
            )
            try outputFile.write(from: buffer)
        }

        let readBack = try AVAudioFile(forReading: fileURL)
        #expect(readBack.processingFormat.sampleRate == 44_100)
        #expect(readBack.processingFormat.channelCount == 2)
        #expect(readBack.length == Int64(frameCount))
    }

    private func makePCMData(sampleCount: Int) -> Data {
        var samples = (0..<sampleCount).map { Float($0) }
        return Data(bytes: &samples, count: samples.count * MemoryLayout<Float>.size)
    }
}
