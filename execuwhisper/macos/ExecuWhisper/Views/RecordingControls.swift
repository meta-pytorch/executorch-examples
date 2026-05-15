/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

struct RecordingControls: ToolbarContent {
    @Environment(TranscriptStore.self) private var store
    @Environment(ModelDownloader.self) private var downloader
    @Environment(Preferences.self) private var preferences

    var body: some ToolbarContent {
        ToolbarItem(placement: .primaryAction) {
            HStack(spacing: 6) {
                if shouldShowBulkDeleteButton {
                    deleteSelectedButton
                }
                if store.isHelperLoading {
                    preloadIndicator
                }
                recordButton
                if let session = currentSession {
                    exportButton(for: session)
                }
                if !currentTranscript.isEmpty {
                    copyButton
                }
            }
        }
    }

    private var preloadIndicator: some View {
        HStack(spacing: 6) {
            ProgressView()
                .controlSize(.small)
            Text("Preloading...")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(.background.secondary, in: Capsule())
        .help(store.helperStatusMessage.isEmpty ? "Preloading model" : store.helperStatusMessage)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Preloading model")
    }

    private var recordButton: some View {
        Button {
            Task {
                if store.isRecording {
                    await store.stopRecordingAndTranscribe()
                } else {
                    await store.startRecording()
                }
            }
        } label: {
            switch store.sessionState {
            case .idle:
                Label("Record", systemImage: "mic.fill")
            case .recording:
                Label("Stop and Transcribe", systemImage: "stop.circle.fill")
                    .foregroundStyle(.orange)
            case .transcribing:
                ProgressView()
                    .controlSize(.small)
            }
        }
        .keyboardShortcut("R", modifiers: [.command, .shift])
        .disabled(store.isTranscribing || downloader.isDownloading || (!store.isModelReady && !store.isRecording))
        .help(recordButtonHelp)
    }

    private var copyButton: some View {
        Button {
            NSPasteboard.general.clearContents()
            NSPasteboard.general.setString(currentTranscript, forType: .string)
        } label: {
            Label("Copy Transcript", systemImage: "doc.on.doc")
        }
        .keyboardShortcut("C", modifiers: [.command, .shift])
        .help("Copy the selected transcript")
    }

    private func exportButton(for session: Session) -> some View {
        Menu {
            ForEach(SessionExportFormat.allCases, id: \.rawValue) { format in
                Button(format.title) {
                    store.exportSession(session, format: format)
                }
            }
        } label: {
            Label("Export", systemImage: "square.and.arrow.down")
        }
        .help("Export the selected transcript")
    }

    private var deleteSelectedButton: some View {
        Button(role: .destructive) {
            store.deleteSessions(ids: store.selectedHistorySessionIDs)
        } label: {
            Label("Delete Selected", systemImage: "trash")
        }
        .help("Delete \(store.selectedHistorySessionIDs.count) selected history items")
    }

    private var currentSession: Session? {
        guard !store.hasActiveSession, let id = store.selectedSessionID else { return nil }
        return store.sessions.first(where: { $0.id == id })
    }

    private var currentTranscript: String {
        if store.hasActiveSession {
            return store.liveTranscript
        }
        return currentSession?.transcript ?? ""
    }

    private var recordButtonHelp: String {
        switch store.sessionState {
        case .idle:
            return "Start recording (Cmd-Shift-R)"
        case .recording:
            return "Stop recording and transcribe (Cmd-Shift-R)"
        case .transcribing:
            return "Transcribing..."
        }
    }

    private var shouldShowBulkDeleteButton: Bool {
        !store.hasActiveSession && store.selectedHistorySessionIDs.count > 1
    }
}
