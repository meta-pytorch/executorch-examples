/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Carbon.HIToolbox
import Foundation

final class GlobalHotKeyManager {
    private var hotKeyRef: EventHotKeyRef?
    private var eventHandlerRef: EventHandlerRef?
    private var callback: (@MainActor () -> Void)?

    func register(
        shortcut: DictationShortcut,
        _ callback: @escaping @MainActor () -> Void
    ) -> Result<Void, RunnerError> {
        unregister()
        self.callback = callback

        guard shortcut.carbonModifiers != 0 else {
            unregister()
            return .failure(.hotKeyRegistrationFailed(description: "Shortcuts must include at least one modifier key."))
        }

        installEventHandler()

        let hotKeyID = EventHotKeyID(signature: OSType(0x4557_4853), id: 1)
        var ref: EventHotKeyRef?
        let status = RegisterEventHotKey(
            shortcut.keyCode,
            shortcut.carbonModifiers,
            hotKeyID,
            GetApplicationEventTarget(),
            0,
            &ref
        )

        guard status == noErr else {
            unregister()
            return .failure(.hotKeyRegistrationFailed(description: Self.errorMessage(for: status, shortcut: shortcut)))
        }

        hotKeyRef = ref
        return .success(())
    }

    func unregister() {
        if let ref = hotKeyRef {
            UnregisterEventHotKey(ref)
            hotKeyRef = nil
        }
        if let handler = eventHandlerRef {
            RemoveEventHandler(handler)
            eventHandlerRef = nil
        }
        callback = nil
    }

    private func installEventHandler() {
        var eventType = EventTypeSpec(
            eventClass: OSType(kEventClassKeyboard),
            eventKind: UInt32(kEventHotKeyPressed)
        )
        let selfPtr = Unmanaged.passUnretained(self).toOpaque()

        InstallEventHandler(
            GetApplicationEventTarget(),
            { _, _, userData in
                guard let userData else { return OSStatus(eventNotHandledErr) }
                let manager = Unmanaged<GlobalHotKeyManager>.fromOpaque(userData).takeUnretainedValue()
                manager.handleHotKey()
                return noErr
            },
            1,
            &eventType,
            selfPtr,
            &eventHandlerRef
        )
    }

    private func handleHotKey() {
        guard let callback else { return }
        Task { @MainActor in
            callback()
        }
    }

    private static func errorMessage(for status: OSStatus, shortcut: DictationShortcut) -> String {
        let display = shortcut.displayString
        switch Int(status) {
        case eventHotKeyExistsErr:
            return "\(display) is already registered. Check macOS keyboard or input-source shortcuts."
        case eventHotKeyInvalidErr:
            return "macOS rejected the \(display) hotkey registration."
        default:
            return "macOS returned OSStatus \(status). \(display) may already be reserved by the system."
        }
    }
}
