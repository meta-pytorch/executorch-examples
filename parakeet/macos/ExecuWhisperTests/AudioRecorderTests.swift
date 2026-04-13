/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

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

    private func makePCMData(sampleCount: Int) -> Data {
        var samples = (0..<sampleCount).map { Float($0) }
        return Data(bytes: &samples, count: samples.count * MemoryLayout<Float>.size)
    }
}
