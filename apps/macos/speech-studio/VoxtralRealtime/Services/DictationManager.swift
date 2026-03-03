import AppKit
import Carbon.HIToolbox
import os

private let log = Logger(subsystem: "com.younghan.VoxtralRealtime", category: "Dictation")

@MainActor @Observable
final class DictationManager {
    enum State: Equatable {
        case idle
        case listening
    }

    private(set) var state: State = .idle
    var isListening: Bool { state == .listening }

    private static let silenceTimeout: TimeInterval = 2.0
    private static let silenceThreshold: Float = 0.02

    private let store: TranscriptStore
    private var panel: DictationPanel?
    private var hotKeyRef: EventHotKeyRef?
    private var eventHandlerRef: EventHandlerRef?
    private var silenceTimer: Task<Void, Never>?
    private var lastVoiceTime: Date = .now
    private var targetApp: NSRunningApplication?

    init(store: TranscriptStore) {
        self.store = store
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

        if let app = targetApp {
            app.activate()
            log.info("Re-activated: \(app.localizedName ?? "?")")
        }

        try? await Task.sleep(for: .milliseconds(300))
        pasteText(text)
    }

    // MARK: - Silence Detection

    private func startSilenceMonitor() {
        silenceTimer?.cancel()
        silenceTimer = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                guard let self, self.state == .listening else { break }

                let level = self.store.audioLevel

                if level > Self.silenceThreshold {
                    self.lastVoiceTime = .now
                }

                let silenceDuration = Date.now.timeIntervalSince(self.lastVoiceTime)
                let hasText = !self.store.dictationText.isEmpty

                if hasText && silenceDuration >= Self.silenceTimeout {
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

    private func pasteText(_ text: String) {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(text, forType: .string)

        let keyDown = CGEvent(keyboardEventSource: nil, virtualKey: CGKeyCode(kVK_ANSI_V), keyDown: true)
        keyDown?.flags = .maskCommand
        keyDown?.post(tap: .cgSessionEventTap)

        usleep(50_000)

        let keyUp = CGEvent(keyboardEventSource: nil, virtualKey: CGKeyCode(kVK_ANSI_V), keyDown: false)
        keyUp?.flags = .maskCommand
        keyUp?.post(tap: .cgSessionEventTap)

        log.info("Pasted \(text.count) chars via Cmd+V (clipboard set as fallback)")
    }

    // MARK: - Accessibility

    static func checkAccessibility(prompt: Bool = false) -> Bool {
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue(): prompt] as CFDictionary
        return AXIsProcessTrustedWithOptions(options)
    }
}
