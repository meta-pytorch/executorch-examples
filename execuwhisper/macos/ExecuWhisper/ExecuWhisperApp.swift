/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import AppKit
import SwiftUI

@main
struct ExecuWhisperApp: App {
    @State private var preferences = Preferences()
    @State private var downloader = ModelDownloader()
    @State private var replacementStore = ReplacementStore()
    @State private var store: TranscriptStore
    @State private var dictationManager: DictationManager

    init() {
        let prefs = Preferences()
        let downloader = ModelDownloader()
        let replacementStore = ReplacementStore()
        let formatterBridge = FormatterBridge()
        let textPipeline = TextPipeline(
            replacementStore: replacementStore,
            formatterBridge: formatterBridge
        ) {
            TextPipeline.FormatterPaths(
                runnerPath: prefs.formatterRunnerPath,
                modelPath: prefs.formatterModelPath,
                tokenizerPath: prefs.formatterTokenizerPath,
                tokenizerConfigPath: prefs.formatterTokenizerConfigPath
            )
        }
        let store = TranscriptStore(
            preferences: prefs,
            downloader: downloader,
            textPipeline: textPipeline
        )
        let dictationManager = DictationManager(store: store, preferences: prefs)
        _preferences = State(initialValue: prefs)
        _downloader = State(initialValue: downloader)
        _replacementStore = State(initialValue: replacementStore)
        _store = State(initialValue: store)
        _dictationManager = State(initialValue: dictationManager)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(store)
                .environment(preferences)
                .environment(downloader)
                .environment(replacementStore)
                .environment(dictationManager)
                .frame(minWidth: 700, minHeight: 460)
                .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in
                    Task { await store.runHealthCheck() }
                }
        }
        .defaultSize(width: 960, height: 640)
        .windowToolbarStyle(.unified)
        .commands {
            CommandGroup(replacing: .newItem) {}

            CommandMenu("Transcription") {
                switch store.sessionState {
                case .idle:
                    Button("Start Recording") {
                        Task { await store.startRecording() }
                    }
                    .keyboardShortcut("R", modifiers: [.command, .shift])
                    .disabled(!store.isModelReady)

                case .recording:
                    Button("Stop and Transcribe") {
                        Task { await store.stopRecordingAndTranscribe() }
                    }
                    .keyboardShortcut("R", modifiers: [.command, .shift])

                case .transcribing:
                    Button("Transcribing...") {}
                        .disabled(true)
                }

                Button("Import Audio...") {
                    store.importAudioFileWithPanel()
                }
                .disabled(store.hasActiveSession || downloader.isDownloading)

                if store.healthResult?.shouldOfferModelDownload == true && !downloader.isDownloading {
                    Divider()
                    Button("Download Model") {
                        Task { await store.downloadModel() }
                    }
                }

                if store.resourcesReady && !store.hasActiveSession {
                    Divider()
                    switch store.helperState {
                    case .unloaded:
                        Button("Preload Model") {
                            Task { await store.preloadModel() }
                        }
                        .keyboardShortcut("L", modifiers: [.command, .shift])

                    case .loading:
                        Button("Warming Model...") {}
                            .disabled(true)

                    case .warm:
                        Button("Unload Model") {
                            Task { await store.unloadModel() }
                        }
                        .keyboardShortcut("U", modifiers: [.command, .shift])

                    case .failed:
                        Button("Retry Preload") {
                            Task { await store.preloadModel() }
                        }
                    }
                }

                Divider()

                Button("Copy Transcript") {
                    let text = currentTranscript
                    guard !text.isEmpty else { return }
                    NSPasteboard.general.clearContents()
                    NSPasteboard.general.setString(text, forType: .string)
                }
                .keyboardShortcut("C", modifiers: [.command, .shift])
                .disabled(currentTranscript.isEmpty)
            }

            CommandMenu("Dictation") {
                Button(dictationManager.isListening ? "Stop Dictation" : "Start Dictation") {
                    Task { await dictationManager.toggle() }
                }
                .disabled(store.isTranscribing)
            }
        }

        Settings {
            SettingsView(usesFixedWindowSize: true)
                .environment(preferences)
                .environment(dictationManager)
        }
    }

    private var currentTranscript: String {
        if store.hasActiveSession {
            return store.liveTranscript
        }
        guard let id = store.selectedSessionID else { return "" }
        return store.sessions.first(where: { $0.id == id })?.transcript ?? ""
    }
}
