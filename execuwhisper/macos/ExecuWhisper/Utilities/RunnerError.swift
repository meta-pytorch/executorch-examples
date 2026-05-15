/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

enum RunnerError: Error, Sendable {
    case binaryNotFound(path: String)
    case modelMissing(file: String)
    case runtimeLibraryMissing(path: String)
    case microphonePermissionDenied
    case accessibilityPermissionDenied
    case microphoneNotAvailable
    case invalidRunnerOutput(stdout: String)
    case downloadFailed(file: String, description: String)
    case hotKeyRegistrationFailed(description: String)
    case runnerCrashed(exitCode: Int32, stderr: String)
    case transcriptionFailed(description: String)
    case exportFailed(description: String)
    case launchFailed(description: String)
    case dictationNotActive
}

extension RunnerError: LocalizedError {
    var errorDescription: String? {
        switch self {
        case .binaryNotFound(let path):
            return "Runner binary not found at \(path)"
        case .modelMissing(let file):
            return "Required model file is missing: \(file)"
        case .runtimeLibraryMissing(let path):
            return "Required runtime library is missing at \(path)"
        case .microphonePermissionDenied:
            return "Microphone access denied. Enable it in System Settings -> Privacy & Security -> Microphone, then relaunch ExecuWhisper."
        case .accessibilityPermissionDenied:
            return "Accessibility access is required to auto-paste dictated text. Enable ExecuWhisper Paste Helper or ExecuWhisper in System Settings -> Privacy & Security -> Accessibility."
        case .microphoneNotAvailable:
            return "No audio input is available. Connect or enable a microphone and try again."
        case .invalidRunnerOutput(let stdout):
            return "Parakeet runner finished without returning a transcript.\n\n\(stdout)"
        case .downloadFailed(let file, let description):
            return "Failed to download \(file): \(description)"
        case .hotKeyRegistrationFailed(let description):
            return "Global hotkey registration failed: \(description)"
        case .runnerCrashed(let exitCode, let stderr):
            return "Parakeet runner exited with code \(exitCode).\n\n\(stderr)"
        case .transcriptionFailed(let description):
            return "Transcription failed: \(description)"
        case .exportFailed(let description):
            return "Export failed: \(description)"
        case .launchFailed(let description):
            return "Failed to launch the runner: \(description)"
        case .dictationNotActive:
            return nil
        }
    }
}

extension RunnerError {
    var isStickyUserActionError: Bool {
        switch self {
        case .accessibilityPermissionDenied,
             .microphonePermissionDenied,
             .binaryNotFound,
             .modelMissing,
             .runtimeLibraryMissing:
            return true
        default:
            return false
        }
    }
}
