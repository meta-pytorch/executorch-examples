import SwiftUI

struct RecordingControls: ToolbarContent {
    @Environment(TranscriptStore.self) private var store

    var body: some ToolbarContent {
        ToolbarItem(placement: .primaryAction) {
            HStack(spacing: 6) {
                pauseResumeButton
                if store.hasActiveSession {
                    endSessionButton
                }
                if store.isModelReady && !store.hasActiveSession {
                    unloadButton
                }
            }
        }
    }

    private var pauseResumeButton: some View {
        Button {
            Task { await store.togglePauseResume() }
        } label: {
            if store.isLoading {
                ProgressView()
                    .controlSize(.small)
            } else {
                Label(pauseResumeLabel, systemImage: pauseResumeIcon)
                    .foregroundStyle(store.isTranscribing ? .orange : .primary)
            }
        }
        .keyboardShortcut(store.isTranscribing ? "." : "R",
                          modifiers: store.isTranscribing ? .command : [.command, .shift])
        .disabled(store.isLoading)
        .help(pauseResumeHelp)
    }

    private var unloadButton: some View {
        Button {
            Task { await store.unloadModel() }
        } label: {
            Label("Unload Model", systemImage: "eject.fill")
        }
        .help("Unload model to free resources")
    }

    private var endSessionButton: some View {
        Button {
            Task { await store.endSession() }
        } label: {
            Label("Done", systemImage: "checkmark.circle.fill")
        }
        .keyboardShortcut(.return, modifiers: .command)
        .help("End session and save (⌘↩)")
    }

    private var pauseResumeLabel: String {
        switch store.sessionState {
        case .idle: "Transcribe"
        case .loading: "Loading..."
        case .transcribing: "Pause"
        case .paused: "Resume"
        }
    }

    private var pauseResumeIcon: String {
        switch store.sessionState {
        case .idle: "mic.fill"
        case .loading: "hourglass"
        case .transcribing: "pause.fill"
        case .paused: "play.fill"
        }
    }

    private var pauseResumeHelp: String {
        switch store.sessionState {
        case .idle: "Start transcription (⌘⇧R)"
        case .loading: "Loading model..."
        case .transcribing: "Pause transcription (⌘.)"
        case .paused: "Resume transcription (⌘⇧R)"
        }
    }
}
