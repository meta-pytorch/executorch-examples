/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import ApplicationServices
import AppKit
import Carbon.HIToolbox
import Foundation
import os

private let pasteLog = Logger(subsystem: "org.pytorch.executorch.ExecuWhisper", category: "PasteController")

enum PasteController {
    enum PasteResult: Equatable {
        case pastedWithAppPermission
        case pastedWithStableHelper
        case accessibilityRequired
        case failed(String)
    }

    private static let helperName = "execuwhisper_paste_helper"
    private static let helperAppName = "ExecuWhisper Paste Helper.app"
    private static let helperVersion = "3"
    private static let accessibilityRequiredExitCode: Int32 = 2
    static let helperIdentifier = "org.pytorch.executorch.ExecuWhisper.PasteHelper"

    static var stableHelperBundleURL: URL {
        let directory = PersistencePaths.appSupportDirectory
            .appendingPathComponent("PasteHelper", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory.appendingPathComponent(helperAppName, isDirectory: true)
    }

    static var stableHelperExecutableURL: URL {
        stableHelperBundleURL
            .appendingPathComponent("Contents", isDirectory: true)
            .appendingPathComponent("MacOS", isDirectory: true)
            .appendingPathComponent(helperName)
    }

    static func checkAccessibility(prompt: Bool = false) -> Bool {
        if appAccessibilityTrusted(prompt: prompt) {
            return true
        }
        return stableHelperIsTrusted(prompt: prompt)
    }

    static func promptForAccessibilityAccess() {
        if appAccessibilityTrusted(prompt: false) {
            return
        }
        do {
            let helperURLs = try installStableHelperIfNeeded()
            launchAccessRequestHelper(helperURLs.executableURL)
        } catch {
            pasteLog.error("Could not install paste helper for Accessibility request: \(error.localizedDescription, privacy: .public)")
        }
        openAccessibilitySettings()
    }

    static func paste(targetPID: pid_t?) -> PasteResult {
        if appAccessibilityTrusted(prompt: false) {
            return postPasteShortcut(targetPID: targetPID)
                ? .pastedWithAppPermission
                : .failed("Could not create paste keyboard event.")
        }

        switch runStableHelper(arguments: pasteArguments(targetPID: targetPID, prompt: true)) {
        case .success:
            return .pastedWithStableHelper
        case .accessibilityRequired:
            return .accessibilityRequired
        case .failed(let message):
            return .failed(message)
        }
    }

    private static func appAccessibilityTrusted(prompt: Bool) -> Bool {
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue(): prompt] as CFDictionary
        return AXIsProcessTrustedWithOptions(options)
    }

    private static func stableHelperIsTrusted(prompt: Bool) -> Bool {
        switch runStableHelper(arguments: prompt ? ["--check", "--prompt"] : ["--check"]) {
        case .success:
            return true
        case .accessibilityRequired, .failed:
            return false
        }
    }

    private static func postPasteShortcut(targetPID: pid_t?) -> Bool {
        let source = CGEventSource(stateID: .hidSystemState)
        guard let keyDown = CGEvent(
            keyboardEventSource: source,
            virtualKey: CGKeyCode(kVK_ANSI_V),
            keyDown: true
        ), let keyUp = CGEvent(
            keyboardEventSource: source,
            virtualKey: CGKeyCode(kVK_ANSI_V),
            keyDown: false
        ) else {
            pasteLog.error("Failed to create Cmd+V events")
            return false
        }

        keyDown.flags = .maskCommand
        keyUp.flags = .maskCommand
        if let targetPID {
            keyDown.postToPid(targetPID)
            usleep(50_000)
            keyUp.postToPid(targetPID)
        } else {
            keyDown.post(tap: .cgSessionEventTap)
            usleep(50_000)
            keyUp.post(tap: .cgSessionEventTap)
        }
        return true
    }

    private static func pasteArguments(targetPID: pid_t?, prompt: Bool) -> [String] {
        var arguments = ["--paste"]
        if prompt {
            arguments.append("--prompt")
        }
        if let targetPID {
            arguments.append(contentsOf: ["--pid", String(targetPID)])
        }
        return arguments
    }

    private enum HelperResult {
        case success
        case accessibilityRequired
        case failed(String)
    }

    private static func runStableHelper(arguments: [String]) -> HelperResult {
        do {
            let helperURLs = try installStableHelperIfNeeded()
            let process = Process()
            let stderrPipe = Pipe()
            process.executableURL = helperURLs.executableURL
            process.arguments = arguments
            process.standardError = stderrPipe
            try process.run()
            process.waitUntilExit()

            if process.terminationStatus == 0 {
                return .success
            }
            if process.terminationStatus == accessibilityRequiredExitCode {
                return .accessibilityRequired
            }
            let stderr = String(data: stderrPipe.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8) ?? ""
            return .failed(stderr.isEmpty ? "Paste helper exited with code \(process.terminationStatus)." : stderr)
        } catch {
            return .failed(error.localizedDescription)
        }
    }

    struct StableHelperURLs: Equatable {
        let bundleURL: URL
        let executableURL: URL
    }

    static func installStableHelperIfNeeded() throws -> StableHelperURLs {
        let bundleURL = stableHelperBundleURL
        let executableURL = stableHelperExecutableURL
        let versionURL = bundleURL.deletingLastPathComponent().appendingPathComponent(".version")
        let installedVersion = try? String(contentsOf: versionURL, encoding: .utf8)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if installedVersion == helperVersion && FileManager.default.isExecutableFile(atPath: executableURL.path) {
            return StableHelperURLs(bundleURL: bundleURL, executableURL: executableURL)
        }

        guard let bundledURL = Bundle.main.url(forResource: helperAppName, withExtension: nil) else {
            throw CocoaError(.fileNoSuchFile)
        }

        if FileManager.default.fileExists(atPath: bundleURL.path) {
            try FileManager.default.removeItem(at: bundleURL)
        }
        try FileManager.default.copyItem(at: bundledURL, to: bundleURL)
        try FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: executableURL.path)
        try helperVersion.write(to: versionURL, atomically: true, encoding: .utf8)
        pasteLog.info("Installed stable paste helper at \(bundleURL.path, privacy: .public)")
        return StableHelperURLs(bundleURL: bundleURL, executableURL: executableURL)
    }

    private static func launchAccessRequestHelper(_ helperURL: URL) {
        let process = Process()
        process.executableURL = helperURL
        process.arguments = ["--request-access"]
        do {
            try process.run()
            pasteLog.info("Launched paste helper Accessibility request pid=\(process.processIdentifier)")
        } catch {
            pasteLog.error("Could not launch paste helper Accessibility request: \(error.localizedDescription, privacy: .public)")
        }
    }

    private static func openAccessibilitySettings() {
        let urls = [
            "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility",
            "x-apple.systempreferences:com.apple.settings.PrivacySecurity.extension?Privacy_Accessibility",
        ]
        for value in urls {
            guard let url = URL(string: value), NSWorkspace.shared.open(url) else { continue }
            return
        }
    }
}
