import SwiftUI

@main
struct VoxtralRealtimeApp: App {
    @State private var preferences = Preferences()
    @State private var store: TranscriptStore
    @State private var dictation: DictationManager

    init() {
        let prefs = Preferences()
        let s = TranscriptStore(preferences: prefs)
        _preferences = State(initialValue: prefs)
        _store = State(initialValue: s)
        _dictation = State(initialValue: DictationManager(store: s))
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(store)
                .environment(preferences)
                .frame(minWidth: 600, minHeight: 400)
                .onAppear {
                    if !DictationManager.checkAccessibility(prompt: false) {
                        _ = DictationManager.checkAccessibility(prompt: true)
                    }
                    dictation.registerHotKey()
                }
        }
        .defaultSize(width: 900, height: 600)
        .windowToolbarStyle(.unified)
        .commands {
            CommandGroup(replacing: .newItem) {}

            CommandMenu("Transcription") {
                switch store.sessionState {
                case .idle:
                    Button("Start Transcription") {
                        Task { await store.startTranscription() }
                    }
                    .keyboardShortcut("R", modifiers: [.command, .shift])

                case .loading:
                    Button("Loading...") {}
                        .disabled(true)

                case .transcribing:
                    Button("Pause") {
                        Task { await store.pauseTranscription() }
                    }
                    .keyboardShortcut(".", modifiers: .command)

                case .paused:
                    Button("Resume") {
                        Task { await store.resumeTranscription() }
                    }
                    .keyboardShortcut("R", modifiers: [.command, .shift])
                }

                if store.hasActiveSession {
                    Button("End Session") {
                        Task { await store.endSession() }
                    }
                    .keyboardShortcut(.return, modifiers: .command)
                }

                Divider()

                if store.isModelReady && !store.hasActiveSession {
                    Button("Unload Model") {
                        Task { await store.unloadModel() }
                    }
                    .keyboardShortcut("U", modifiers: [.command, .shift])

                    Divider()
                }

                Button("Copy Transcript") {
                    let text = store.hasActiveSession ? store.liveTranscript : selectedSessionTranscript
                    guard !text.isEmpty else { return }
                    NSPasteboard.general.clearContents()
                    NSPasteboard.general.setString(text, forType: .string)
                }
                .keyboardShortcut("C", modifiers: [.command, .shift])
                .disabled(currentTranscript.isEmpty)
            }

            CommandMenu("Dictation") {
                Button(dictation.isListening ? "Stop Dictation" : "Start Dictation") {
                    Task { await dictation.toggle() }
                }
                .disabled(!store.isModelReady && store.modelState != .unloaded)
            }
        }

        Settings {
            SettingsView()
                .environment(preferences)
        }
    }

    private var selectedSessionTranscript: String {
        guard let id = store.selectedSessionID else { return "" }
        return store.sessions.first(where: { $0.id == id })?.transcript ?? ""
    }

    private var currentTranscript: String {
        store.hasActiveSession ? store.liveTranscript : selectedSessionTranscript
    }
}
