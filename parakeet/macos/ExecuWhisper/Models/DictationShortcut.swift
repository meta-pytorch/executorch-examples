/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import AppKit
import Carbon.HIToolbox
import Foundation

struct DictationShortcut: Codable, Equatable, Sendable {
    var keyCode: UInt32
    var carbonModifiers: UInt32
    var keyDisplay: String

    static let controlSpace = DictationShortcut(
        keyCode: UInt32(kVK_Space),
        carbonModifiers: UInt32(controlKey),
        keyDisplay: "Space"
    )

    init(keyCode: UInt32, carbonModifiers: UInt32, keyDisplay: String) {
        self.keyCode = keyCode
        self.carbonModifiers = carbonModifiers
        self.keyDisplay = keyDisplay
    }

    init?(event: NSEvent) {
        let carbonModifiers = Self.carbonModifiers(from: event.modifierFlags)
        guard carbonModifiers != 0 else { return nil }
        guard let keyDisplay = Self.keyDisplay(for: event) else { return nil }
        self.init(
            keyCode: UInt32(event.keyCode),
            carbonModifiers: carbonModifiers,
            keyDisplay: keyDisplay
        )
    }

    var displayString: String {
        var value = ""
        if carbonModifiers & UInt32(controlKey) != 0 {
            value += "⌃"
        }
        if carbonModifiers & UInt32(optionKey) != 0 {
            value += "⌥"
        }
        if carbonModifiers & UInt32(shiftKey) != 0 {
            value += "⇧"
        }
        if carbonModifiers & UInt32(cmdKey) != 0 {
            value += "⌘"
        }
        return value + keyDisplay
    }

    static func carbonModifiers(from flags: NSEvent.ModifierFlags) -> UInt32 {
        let sanitized = flags.intersection(.deviceIndependentFlagsMask)
        var value: UInt32 = 0
        if sanitized.contains(.control) {
            value |= UInt32(controlKey)
        }
        if sanitized.contains(.option) {
            value |= UInt32(optionKey)
        }
        if sanitized.contains(.shift) {
            value |= UInt32(shiftKey)
        }
        if sanitized.contains(.command) {
            value |= UInt32(cmdKey)
        }
        return value
    }

    private static func keyDisplay(for event: NSEvent) -> String? {
        switch Int(event.keyCode) {
        case kVK_Space:
            return "Space"
        case kVK_Return:
            return "Return"
        case kVK_Tab:
            return "Tab"
        case kVK_Delete:
            return "Delete"
        case kVK_ForwardDelete:
            return "Fn-Delete"
        case kVK_Escape:
            return "Esc"
        case kVK_LeftArrow:
            return "Left"
        case kVK_RightArrow:
            return "Right"
        case kVK_UpArrow:
            return "Up"
        case kVK_DownArrow:
            return "Down"
        default:
            break
        }

        guard let characters = event.charactersIgnoringModifiers?
            .trimmingCharacters(in: .whitespacesAndNewlines),
              !characters.isEmpty
        else {
            return nil
        }
        return characters.uppercased()
    }
}
