/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import AVFoundation
import Foundation

struct HealthCheck: Sendable {
    struct Result: Sendable {
        var runnerAvailable: Bool
        var modelAvailable: Bool
        var tokenizerAvailable: Bool
        var micPermission: MicPermission

        var resourcesReady: Bool {
            runnerAvailable && modelAvailable && tokenizerAvailable
        }

        var allGood: Bool {
            resourcesReady && micPermission == .authorized
        }

        var missingFiles: [String] {
            var missing: [String] = []
            if !runnerAvailable { missing.append("parakeet_helper") }
            if !modelAvailable { missing.append("model.pte") }
            if !tokenizerAvailable { missing.append("tokenizer.model") }
            return missing
        }

        var modelAssetsMissing: Bool {
            !modelAvailable || !tokenizerAvailable
        }

        var shouldOfferModelDownload: Bool {
            runnerAvailable && modelAssetsMissing
        }

        var setupStatusMessage: String {
            if !runnerAvailable { return "Helper setup required" }
            if modelAssetsMissing { return "Model download required" }
            return "Ready"
        }
    }

    enum MicPermission: Sendable {
        case authorized
        case denied
        case notDetermined
    }

    static func run(
        runnerPath: String,
        modelPath: String,
        tokenizerPath: String
    ) async -> Result {
        let fm = FileManager.default
        let micPerm = await microphonePermission()

        return Result(
            runnerAvailable: fm.isExecutableFile(atPath: runnerPath),
            modelAvailable: fileExistsAndHasData(atPath: modelPath),
            tokenizerAvailable: fileExistsAndHasData(atPath: tokenizerPath),
            micPermission: micPerm
        )
    }

    static func requestMicrophoneAccess() async -> Bool {
        await AVCaptureDevice.requestAccess(for: .audio)
    }

    static func liveMicPermission() async -> MicPermission {
        await microphonePermission()
    }

    private static func microphonePermission() async -> MicPermission {
        switch AVCaptureDevice.authorizationStatus(for: .audio) {
        case .authorized:
            return .authorized
        case .denied, .restricted:
            return .denied
        case .notDetermined:
            return .notDetermined
        @unknown default:
            return .notDetermined
        }
    }

    private static func fileExistsAndHasData(atPath path: String) -> Bool {
        guard FileManager.default.fileExists(atPath: path) else { return false }
        let attributes = try? FileManager.default.attributesOfItem(atPath: path)
        let size = attributes?[.size] as? NSNumber
        return size?.int64Value ?? 0 > 0
    }
}
