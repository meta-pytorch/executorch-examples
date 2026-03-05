/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import AppKit
import Carbon.HIToolbox
import os

private let log = Logger(subsystem: "org.pytorch.executorch.VoxtralRealtime", category: "Dictation")

@MainActor @Observable
final class DictationManager {
    enum State: Equatable {
        case idle
        case listening
    }

    private(set) var state: State = .idle
    var isListening: Bool { state == .listening }

    private let store: TranscriptStore
    private let preferences: Preferences
    private var panel: DictationPanel?
    private var hotKeyRef: EventHotKeyRef?
    private var eventHandlerRef: EventHandlerRef?
    private var silenceTimer: Task<Void, Never>?
    private var lastVoiceTime: Date = .now
    private var targetApp: NSRunningApplication?

    init(store: TranscriptStore, preferences: Preferences) {
        self.store = store
        self.preferences = preferences
    }

    nonisolated func cleanup() {
        MainActor.assumeIsolated {
            unregisterHotKey()
        }
    }

    // MARK: - Global Hotkey (Ctrl+Space)

    func registerHotKey() {
        let hotKeyID = EventHotKeyID(signature: OSType(0x5654_5254), id: 1)
        var ref: EventHotKeyRef?
        let status = RegisterEventHotKey(
            UInt32(kVK_Space),
            UInt32(controlKey),
            hotKeyID,
            GetApplicationEventTarget(),
            0,
            &ref
        )
        if status == noErr {
            hotKeyRef = ref
            log.info("Global hotkey registered: Ctrl+Space")
        } else {
            log.error("Failed to register hotkey: \(status)")
        }

        installEventHandler()
    }

    func unregisterHotKey() {
        if let ref = hotKeyRef {
            UnregisterEventHotKey(ref)
            hotKeyRef = nil
        }
        if let handler = eventHandlerRef {
            RemoveEventHandler(handler)
            eventHandlerRef = nil
        }
    }

    private func installEventHandler() {
        var eventType = EventTypeSpec(eventClass: OSType(kEventClassKeyboard), eventKind: UInt32(kEventHotKeyPressed))
        let selfPtr = Unmanaged.passUnretained(self).toOpaque()

        InstallEventHandler(
            GetApplicationEventTarget(),
            { _, event, userData -> OSStatus in
                guard let userData, let event else { return OSStatus(eventNotHandledErr) }
                let manager = Unmanaged<DictationManager>.fromOpaque(userData).takeUnretainedValue()
                Task { @MainActor in
                    await manager.toggle()
                }
                return noErr
            },
            1,
            &eventType,
            selfPtr,
            &eventHandlerRef
        )
    }

    // MARK: - Toggle

    func toggle() async {
        switch state {
        case .idle:
            await startListening()
        case .listening:
            await stopAndPaste()
        }
    }

    // MARK: - Listening

    private func startListening() async {
        guard store.isModelReady || store.modelState == .unloaded else { return }

        let micStatus = await HealthCheck.liveMicPermission()
        if micStatus == .notDetermined {
            _ = await HealthCheck.requestMicrophoneAccess()
        }
        guard await HealthCheck.liveMicPermission() == .authorized else {
            log.error("Microphone permission not granted — cannot start dictation")
            store.currentError = .microphonePermissionDenied
            return
        }

        if !store.isModelReady {
            await store.preloadModel()
            while store.modelState == .loading {
                try? await Task.sleep(for: .milliseconds(100))
            }
            guard store.isModelReady else { return }
        }

        targetApp = NSWorkspace.shared.frontmostApplication
        log.info("Target app: \(self.targetApp?.localizedName ?? "none")")

        state = .listening
        lastVoiceTime = .now
        showPanel()

        await store.startDictation()
        startSilenceMonitor()
        log.info("Dictation started")
    }

    private func stopAndPaste() async {
        guard state == .listening else { return }

        silenceTimer?.cancel()
        silenceTimer = nil

        let text = await store.stopDictation()
        state = .idle

        dismissPanel()
        log.info("Dictation stopped, text length: \(text.count)")

        guard !text.isEmpty else { return }

        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(text, forType: .string)

        if let app = targetApp {
            app.activate()
            log.info("Re-activated: \(app.localizedName ?? "?")")
        }

        try? await Task.sleep(for: .milliseconds(300))

        if Self.checkAccessibility(prompt: false) {
            pasteViaKeyEvent()
        } else {
            log.warning("Accessibility permission lost — text is on clipboard, prompting user to re-grant")
            _ = Self.checkAccessibility(prompt: true)
        }
    }

    // MARK: - Silence Detection

    private func startSilenceMonitor() {
        silenceTimer?.cancel()
        silenceTimer = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                guard let self, self.state == .listening else { break }

                let level = self.store.audioLevel

                if level > Float(self.preferences.silenceThreshold) {
                    self.lastVoiceTime = .now
                }

                let silenceDuration = Date.now.timeIntervalSince(self.lastVoiceTime)
                let hasText = !self.store.dictationText.isEmpty

                if hasText && silenceDuration >= self.preferences.silenceTimeout {
                    log.info("Auto-stop: \(String(format: "%.1f", silenceDuration))s silence (level: \(String(format: "%.4f", level)))")
                    await self.stopAndPaste()
                    break
                }
            }
        }
    }

    // MARK: - Panel

    private func showPanel() {
        let overlay = DictationOverlayView(store: store)
        panel = DictationPanel(contentView: overlay)
        panel?.showCentered()
    }

    private func dismissPanel() {
        panel?.dismiss()
        panel = nil
    }

    // MARK: - Paste

    private func pasteViaKeyEvent() {
        guard let keyDown = CGEvent(keyboardEventSource: nil, virtualKey: CGKeyCode(kVK_ANSI_V), keyDown: true),
              let keyUp = CGEvent(keyboardEventSource: nil, virtualKey: CGKeyCode(kVK_ANSI_V), keyDown: false)
        else {
            log.error("CGEvent creation failed — Accessibility permission is not granted for this binary. Remove the app from Accessibility settings, re-add it, and restart.")
            return
        }

        keyDown.flags = .maskCommand
        keyDown.post(tap: .cgSessionEventTap)

        usleep(50_000)

        keyUp.flags = .maskCommand
        keyUp.post(tap: .cgSessionEventTap)

        log.info("Auto-pasted via Cmd+V")
    }

    // MARK: - Accessibility

    static func checkAccessibility(prompt: Bool = false) -> Bool {
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue(): prompt] as CFDictionary
        return AXIsProcessTrustedWithOptions(options)
    }
}
