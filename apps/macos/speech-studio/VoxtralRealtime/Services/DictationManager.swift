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

    private let store: TranscriptStore
    private var panel: DictationPanel?
    private var hotKeyRef: EventHotKeyRef?
    private var eventHandlerRef: EventHandlerRef?

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
        let hotKeyID = EventHotKeyID(signature: OSType(0x5654_5254), id: 1) // "VTRT"
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

        state = .listening
        showPanel()

        await store.startDictation()
        log.info("Dictation started")
    }

    private func stopAndPaste() async {
        let text = await store.stopDictation()
        state = .idle
        dismissPanel()
        log.info("Dictation stopped, text length: \(text.count)")

        guard !text.isEmpty else { return }
        pasteText(text)
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

    // MARK: - Paste via CGEvent

    private func pasteText(_ text: String) {
        let pasteboard = NSPasteboard.general
        let previousContents = pasteboard.string(forType: .string)

        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
            self.simulatePaste()

            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                if let previous = previousContents {
                    pasteboard.clearContents()
                    pasteboard.setString(previous, forType: .string)
                }
            }
        }
    }

    private func simulatePaste() {
        let source = CGEventSource(stateID: .hidSystemState)

        let keyDown = CGEvent(keyboardEventSource: source, virtualKey: CGKeyCode(kVK_ANSI_V), keyDown: true)
        keyDown?.flags = .maskCommand
        keyDown?.post(tap: .cghidEventTap)

        let keyUp = CGEvent(keyboardEventSource: source, virtualKey: CGKeyCode(kVK_ANSI_V), keyDown: false)
        keyUp?.flags = .maskCommand
        keyUp?.post(tap: .cghidEventTap)

        log.info("Simulated Cmd+V paste")
    }

    // MARK: - Accessibility

    static func checkAccessibility(prompt: Bool = false) -> Bool {
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue(): prompt] as CFDictionary
        return AXIsProcessTrustedWithOptions(options)
    }
}
