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
    private let vadService = VadService()
    private var panel: DictationPanel?
    private var hotKeyRef: EventHotKeyRef?
    private var eventHandlerRef: EventHandlerRef?
    private var silenceTimer: Task<Void, Never>?
    private var wakeCheckTask: Task<Void, Never>?
    private var lastVoiceTime: Date = .now
    private var targetApp: NSRunningApplication?
    private var dictationStartedAt: Date?
    private var wakeTriggeredForCurrentSession = false

    init(store: TranscriptStore, preferences: Preferences) {
        self.store = store
        self.preferences = preferences
    }

    nonisolated func cleanup() {
        MainActor.assumeIsolated {
            unregisterHotKey()
            Task { await self.vadService.stop() }
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
        Task { await startWakeListeningIfNeeded() }
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
        wakeCheckTask?.cancel()
        Task { await vadService.stop() }
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

    // MARK: - Wake Control

    func restartWakeListening() async {
        await startWakeListeningIfNeeded()
    }

    func stopWakeListening() async {
        await vadService.stop()
        store.wakeState = .disabled
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
        await vadService.stop()
        wakeCheckTask?.cancel()

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
        dictationStartedAt = .now
        wakeTriggeredForCurrentSession = false
        store.wakeState = .active
        showPanel()

        await store.startDictation()
        startSilenceMonitor()
        log.info("Dictation started")
    }

    private func stopAndPaste() async {
        guard state == .listening else { return }

        silenceTimer?.cancel()
        silenceTimer = nil

        let rawText = await store.stopDictation()
        state = .idle
        let duration = dictationStartedAt.map { Date.now.timeIntervalSince($0) } ?? 0
        dictationStartedAt = nil

        dismissPanel()
        log.info("Dictation stopped, text length: \(rawText.count)")

        defer {
            store.wakeState = preferences.enableSileroVAD ? .listening : .disabled
            Task { await startWakeListeningIfNeeded() }
        }

        guard !rawText.isEmpty else { return }

        let result = store.processDictationText(rawText)
        let text = result.outputText
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

    private var consecutiveSilencePolls = 0

    private func startSilenceMonitor() {
        silenceTimer?.cancel()
        consecutiveSilencePolls = 0
        silenceTimer = Task { @MainActor [weak self] in
            let pollIntervalMs = 250
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(pollIntervalMs))
                guard let self, self.state == .listening else { break }

                let level = self.store.audioLevel

                if level > Float(self.preferences.silenceThreshold) {
                    self.lastVoiceTime = .now
                    self.consecutiveSilencePolls = 0
                } else {
                    self.consecutiveSilencePolls += 1
                }

                if self.store.wakeState == .checkingPhrase {
                    continue
                }

                let hasText = !self.store.dictationText.isEmpty
                let requiredPolls = max(1, Int(self.preferences.silenceTimeout * 1000) / pollIntervalMs)

                if hasText && self.consecutiveSilencePolls >= requiredPolls {
                    let silenceDuration = Date.now.timeIntervalSince(self.lastVoiceTime)
                    log.info("Auto-stop: \(String(format: "%.1f", silenceDuration))s silence (\(self.consecutiveSilencePolls) consecutive polls, level: \(String(format: "%.4f", level)))")
                    await self.stopAndPaste()
                    break
                }
            }
        }
    }

    private func startWakeListeningIfNeeded() async {
        guard preferences.enableSileroVAD, state == .idle, !store.isDictating else {
            store.wakeState = preferences.enableSileroVAD ? .listening : .disabled
            return
        }

        guard await HealthCheck.liveMicPermission() == .authorized else {
            store.wakeState = .disabled
            return
        }

        if !FileManager.default.isExecutableFile(atPath: preferences.vadRunnerPath) ||
            !FileManager.default.fileExists(atPath: preferences.vadModelPath) {
            store.wakeState = .disabled
            return
        }

        if !store.isModelReady {
            await store.preloadModel()
            while store.modelState == .loading {
                try? await Task.sleep(for: .milliseconds(100))
            }
        }

        store.wakeState = .listening

        do {
            try await vadService.start(
                runnerPath: preferences.vadRunnerPath,
                modelPath: preferences.vadModelPath,
                threshold: Float(preferences.vadThreshold),
                hangoverMs: Int(preferences.vadHangoverMilliseconds)
            ) { [weak self] event in
                guard let self else { return }
                Task { @MainActor in
                    await self.handleVadEvent(event)
                }
            }
        } catch {
            log.error("Failed to start VAD: \(error.localizedDescription)")
            store.wakeState = .disabled
        }
    }

    private func handleVadEvent(_ event: VadService.Event) async {
        switch event {
        case .ready:
            store.wakeState = .listening
        case let .speechSegment(samples):
            guard state == .idle else { return }
            await checkWakeSegment(samples: samples)
        case .silenceDetected:
            log.warning("VAD detected microphone silence — stopping wake listening")
            await vadService.stop()
            store.wakeState = .disabled
            store.currentError = .microphoneSilent
        case .stopped:
            if state == .idle && preferences.enableSileroVAD {
                store.wakeState = .listening
            }
        case let .error(message):
            log.error("VAD error: \(message)")
            store.wakeState = .disabled
        }
    }

    private func checkWakeSegment(samples: [Float]) async {
        wakeCheckTask?.cancel()

        if !preferences.enableWakePhrase {
            await vadService.stop()
            targetApp = NSWorkspace.shared.frontmostApplication
            wakeTriggeredForCurrentSession = false
            dictationStartedAt = .now
            state = .listening
            store.wakeState = .active
            showPanel()
            await store.startDictation(initialSamples: samples, skipMicCheck: true)
            startSilenceMonitor()
            return
        }

        store.wakeState = .checkingPhrase

        await store.startDictation(initialSamples: samples, skipMicCheck: true)

        let keywords = Self.wakeKeywords(from: store.normalizeWakePhrase(preferences.wakePhrase))
        let checkDurationNs = UInt64(preferences.wakeCheckSeconds * 1_000_000_000)
        let deadline = DispatchTime.now().uptimeNanoseconds + checkDurationNs

        wakeCheckTask = Task { @MainActor [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                let normalized = self.store.normalizeWakePhrase(self.store.dictationText)
                if !keywords.isEmpty && keywords.allSatisfy({ normalized.contains($0) }) {
                    await self.vadService.stop()
                    self.targetApp = NSWorkspace.shared.frontmostApplication
                    self.wakeTriggeredForCurrentSession = true
                    self.dictationStartedAt = .now
                    self.state = .listening
                    self.store.stripLeadingWakePhrase(self.preferences.wakePhrase)
                    try? await Task.sleep(for: .milliseconds(200))
                    self.store.stripLeadingPunctuation()
                    self.store.wakeState = .active
                    self.showPanel()
                    self.startSilenceMonitor()
                    log.info("Wake keyword matched — dictation active")
                    return
                }
                if DispatchTime.now().uptimeNanoseconds >= deadline {
                    _ = await self.store.stopDictation()
                    self.store.wakeState = .listening
                    log.info("Speech segment did not contain wake keyword — continuing to listen")
                    return
                }
                try? await Task.sleep(for: .milliseconds(100))
            }
        }
    }

    private static let fillerWords: Set<String> = [
        "hey", "hi", "hello", "ok", "okay", "yo", "oh", "ah", "um", "uh",
    ]

    private static func wakeKeywords(from normalizedPhrase: String) -> [String] {
        let words = normalizedPhrase.split(separator: " ").map(String.init)
        let significant = words.filter { !fillerWords.contains($0) }
        return significant.isEmpty ? words : significant
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
