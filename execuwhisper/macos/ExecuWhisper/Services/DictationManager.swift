/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import AppKit
import ApplicationServices
import Carbon.HIToolbox
import os
import SwiftUI

private let dictationLog = Logger(subsystem: "org.pytorch.executorch.ExecuWhisper", category: "DictationManager")
private let maxDictationDuration: TimeInterval = 30 * 60

@MainActor @Observable
final class DictationManager {
    enum State: Equatable {
        case idle
        case listening
        case transcribing
    }

    private(set) var state: State = .idle
    var hotKeyRegistrationError: String?

    var isListening: Bool { state == .listening }

    var overlayStatusText: String {
        switch state {
        case .idle:
            return hotKeyRegistrationError ?? "Ready"
        case .listening:
            return ""
        case .transcribing:
            return ""
        }
    }

    var hotKeyStatusText: String {
        let display = self.hotKeyDisplayText
        if hotKeyRegistrationError != nil {
            return "\(display) unavailable"
        }
        return isHotKeyEnabled ? "\(display) ready" : "\(display) disabled"
    }

    private var isHotKeyEnabled: Bool {
        preferences?.enableGlobalHotkey ?? true
    }

    var hotKeyDisplayText: String {
        preferences?.dictationShortcut.displayString ?? DictationShortcut.controlSpace.displayString
    }

    private let store: TranscriptStore?
    private let preferences: Preferences?
    private let hotKeyManager: GlobalHotKeyManager
    private let stopRequestHandler: (@MainActor () async -> Void)?
    private var panel: DictationPanel?
    private var silenceTimer: Task<Void, Never>?
    private var targetApp: NSRunningApplication?
    private var lastVoiceTime: Date = .now
    private var dictationStartTime: Date?
    private var sawVoiceActivity = false

    init(
        store: TranscriptStore? = nil,
        preferences: Preferences? = nil,
        hotKeyManager: GlobalHotKeyManager = GlobalHotKeyManager(),
        stopRequestHandler: (@MainActor () async -> Void)? = nil
    ) {
        self.store = store
        self.preferences = preferences
        self.hotKeyManager = hotKeyManager
        self.stopRequestHandler = stopRequestHandler
    }

    static func preview(
        stopRequestHandler: (@MainActor () async -> Void)? = nil
    ) -> DictationManager {
        DictationManager(stopRequestHandler: stopRequestHandler)
    }

    func beginPreviewDictation() async {
        state = .listening
    }

    func beginPreviewTranscription() async {
        state = .transcribing
    }

    func finishPreviewDictation() async {
        state = .idle
    }

    func triggerSilenceTimeoutForTesting() {
        requestStopAndPaste(trigger: "test silence timeout")
    }

    func registerHotKey() {
        guard isHotKeyEnabled else {
            hotKeyManager.unregister()
            hotKeyRegistrationError = nil
            return
        }
        let shortcut = preferences?.dictationShortcut ?? .controlSpace
        switch hotKeyManager.register(shortcut: shortcut, { [weak self] in
            Task { @MainActor in
                await self?.toggle()
            }
        }) {
        case .success:
            hotKeyRegistrationError = nil
        case .failure(let error):
            hotKeyRegistrationError = error.localizedDescription
            store?.currentError = error
        }
    }

    func refreshHotKeyRegistration() {
        registerHotKey()
    }

    func unregisterHotKey() {
        hotKeyManager.unregister()
    }

    func toggle() async {
        dictationLog.info("Toggle requested in state=\(String(describing: self.state), privacy: .public) taskCancelled=\(Task.isCancelled)")
        switch state {
        case .idle:
            await startListening()
        case .listening:
            await stopAndPaste(trigger: "toggle")
        case .transcribing:
            break
        }
    }

    func promptForAccessibilityAccess() {
        _ = Self.checkAccessibility(prompt: true)
    }

    private func startListening() async {
        guard let store else { return }
        dictationLog.info("Starting dictation listening flow")

        let started = await store.startDictationCapture()
        guard started else {
            dictationLog.error("Dictation listening start failed before overlay display")
            return
        }

        targetApp = NSWorkspace.shared.frontmostApplication
        dictationLog.info("Captured target app=\(self.targetApp?.localizedName ?? "none", privacy: .public)")
        state = .listening
        lastVoiceTime = .now
        dictationStartTime = .now
        sawVoiceActivity = false
        showPanel()
        startSilenceMonitor()
        dictationLog.info("Dictation overlay shown")
    }

    private func requestStopAndPaste(trigger: String) {
        dictationLog.info("Scheduling stopAndPaste trigger=\(trigger, privacy: .public)")
        let stopRequestHandler = self.stopRequestHandler
        Task { @MainActor [weak self] in
            if let stopRequestHandler {
                await stopRequestHandler()
                return
            }
            await self?.stopAndPaste(trigger: trigger)
        }
    }

    private func stopAndPaste(trigger: String) async {
        guard state == .listening, let store else { return }
        dictationLog.info("Stopping dictation listening flow trigger=\(trigger, privacy: .public) taskCancelled=\(Task.isCancelled)")
        state = .transcribing
        silenceTimer?.cancel()
        silenceTimer = nil

        do {
            let result = try await store.finishDictationCapture()
            dictationLog.info("Dictation produced outputLength=\(result.outputText.count) rawLength=\(result.rawText.count) tags=\(result.tags, privacy: .public)")
            if DiagnosticLogging.shouldLogTranscriptsPublicly {
                dictationLog.info("Dictation raw: \(result.rawText, privacy: .public)")
                dictationLog.info("Dictation final: \(result.outputText, privacy: .public)")
            } else {
                dictationLog.info("Dictation raw: \(result.rawText, privacy: .private)")
                dictationLog.info("Dictation final: \(result.outputText, privacy: .private)")
            }
            defer {
                dismissPanel()
                state = .idle
                dictationStartTime = nil
            }

            guard !result.outputText.isEmpty else { return }

            NSPasteboard.general.clearContents()
            NSPasteboard.general.setString(result.outputText, forType: .string)
            dictationLog.info("Copied dictated text to pasteboard")

            if let app = targetApp {
                app.activate()
                dictationLog.info("Reactivated target app=\(app.localizedName ?? "unknown", privacy: .public)")
            }
            try? await Task.sleep(for: .milliseconds(300))

            switch PasteController.paste(targetPID: targetApp?.processIdentifier) {
            case .pastedWithAppPermission:
                dictationLog.info("Posted synthetic Cmd+V with app Accessibility permission")
            case .pastedWithStableHelper:
                dictationLog.info("Posted synthetic Cmd+V with stable paste helper")
            case .accessibilityRequired:
                dictationLog.error("Accessibility permission missing for app and stable paste helper")
                store.currentError = .accessibilityPermissionDenied
            case .failed(let message):
                dictationLog.error("Auto-paste failed: \(message, privacy: .public)")
                store.currentError = .accessibilityPermissionDenied
            }
        } catch RunnerError.dictationNotActive {
            dictationLog.info("Dictation stop ignored: no active recording (likely a duplicate trigger).")
            dismissPanel()
            state = .idle
            dictationStartTime = nil
        } catch let error as RunnerError {
            dictationLog.error("Dictation failed with RunnerError: \(error.localizedDescription, privacy: .public)")
            store.currentError = error
            dismissPanel()
            state = .idle
            dictationStartTime = nil
        } catch {
            dictationLog.error("Dictation failed with unexpected error: \(error.localizedDescription, privacy: .public)")
            store.currentError = .transcriptionFailed(description: error.localizedDescription)
            dismissPanel()
            state = .idle
            dictationStartTime = nil
        }
    }

    private func startSilenceMonitor() {
        guard let store, let preferences else { return }
        silenceTimer?.cancel()
        silenceTimer = Task { @MainActor [weak self] in
            let pollIntervalMs = 250
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(pollIntervalMs))
                guard let self, self.state == .listening else { break }

                if let dictationStartTime,
                   Date.now.timeIntervalSince(dictationStartTime) >= maxDictationDuration {
                    dictationLog.info("Maximum dictation duration reached; stopping automatically")
                    self.requestStopAndPaste(trigger: "maximum duration")
                    break
                }

                if store.audioLevel > Float(preferences.silenceThreshold) {
                    self.lastVoiceTime = .now
                    self.sawVoiceActivity = true
                    continue
                }

                if self.sawVoiceActivity,
                   Date.now.timeIntervalSince(self.lastVoiceTime) >= preferences.silenceTimeout {
                    dictationLog.info("Silence timeout reached; stopping dictation automatically")
                    self.requestStopAndPaste(trigger: "silence timeout")
                    break
                }
            }
        }
    }

    private func showPanel() {
        guard let store else { return }
        let overlay = DictationOverlayView()
            .environment(store)
            .environment(self)
        panel = DictationPanel(contentView: overlay)
        panel?.showCentered(on: screenForTargetApp())
        dictationLog.info("Overlay panel created and presented")
    }

    private func dismissPanel() {
        panel?.dismiss()
        panel = nil
        dictationLog.info("Overlay panel dismissed")
    }

    static func checkAccessibility(prompt: Bool = false) -> Bool {
        if prompt {
            PasteController.promptForAccessibilityAccess()
            return PasteController.checkAccessibility(prompt: false)
        }
        return PasteController.checkAccessibility(prompt: false)
    }

    private func screenForTargetApp() -> NSScreen? {
        guard let targetApp else { return nil }
        let options: CGWindowListOption = [.optionOnScreenOnly, .excludeDesktopElements]
        guard let windows = CGWindowListCopyWindowInfo(options, kCGNullWindowID) as? [[String: Any]] else {
            return nil
        }
        guard let bounds = windows.first(where: { info in
            (info[kCGWindowOwnerPID as String] as? pid_t) == targetApp.processIdentifier
                && (info[kCGWindowLayer as String] as? Int) == 0
        })?[kCGWindowBounds as String] as? [String: CGFloat],
              let x = bounds["X"],
              let y = bounds["Y"],
              let width = bounds["Width"],
              let height = bounds["Height"]
        else {
            return nil
        }
        let windowRect = CGRect(x: x, y: y, width: width, height: height)
        return NSScreen.screens.first { $0.frame.intersects(windowRect) }
    }
}
