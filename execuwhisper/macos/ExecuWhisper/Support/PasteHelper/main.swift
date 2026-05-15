/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import ApplicationServices
import Carbon.HIToolbox
import Foundation

private enum ExitCode {
    static let success: Int32 = 0
    static let accessibilityRequired: Int32 = 2
    static let invalidArguments: Int32 = 64
    static let eventCreationFailed: Int32 = 70
}

private let helperVersion = "2"

private func accessibilityTrusted(prompt: Bool) -> Bool {
    let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue(): prompt] as CFDictionary
    return AXIsProcessTrustedWithOptions(options)
}

private func targetPID(from arguments: [String]) -> pid_t? {
    guard let index = arguments.firstIndex(of: "--pid"),
          arguments.indices.contains(index + 1),
          let value = Int32(arguments[index + 1])
    else {
        return nil
    }
    return value
}

private func postPasteShortcut(targetPID: pid_t?) -> Int32 {
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
        return ExitCode.eventCreationFailed
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
    return ExitCode.success
}

private func waitForLikelyTextTarget(targetPID: pid_t?, timeoutSeconds: TimeInterval = 1.0) {
    guard let targetPID else { return }
    let appElement = AXUIElementCreateApplication(targetPID)
    let deadline = Date().addingTimeInterval(timeoutSeconds)
    let textRoles: Set<String> = [
        kAXTextFieldRole as String,
        kAXTextAreaRole as String,
        kAXComboBoxRole as String,
    ]

    while Date() < deadline {
        var focused: CFTypeRef?
        let focusedStatus = AXUIElementCopyAttributeValue(
            appElement,
            kAXFocusedUIElementAttribute as CFString,
            &focused
        )
        if focusedStatus == .success, let focused {
            var role: CFTypeRef?
            let roleStatus = AXUIElementCopyAttributeValue(
                focused as! AXUIElement,
                kAXRoleAttribute as CFString,
                &role
            )
            if roleStatus == .success,
               let roleString = role as? String,
               textRoles.contains(roleString) {
                return
            }
        }
        usleep(50_000)
    }
}

let arguments = Array(CommandLine.arguments.dropFirst())
let shouldPrompt = arguments.contains("--prompt")

if arguments.contains("--version") {
    print(helperVersion)
    exit(ExitCode.success)
}

if arguments.contains("--request-access") {
    if accessibilityTrusted(prompt: true) {
        exit(ExitCode.success)
    }
    sleep(120)
    exit(accessibilityTrusted(prompt: false) ? ExitCode.success : ExitCode.accessibilityRequired)
}

if arguments.contains("--check") {
    exit(accessibilityTrusted(prompt: shouldPrompt) ? ExitCode.success : ExitCode.accessibilityRequired)
}

guard arguments.contains("--paste") else {
    exit(ExitCode.invalidArguments)
}

guard accessibilityTrusted(prompt: shouldPrompt) else {
    exit(ExitCode.accessibilityRequired)
}

waitForLikelyTextTarget(targetPID: targetPID(from: arguments))
exit(postPasteShortcut(targetPID: targetPID(from: arguments)))
