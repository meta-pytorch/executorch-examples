/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

struct SidebarView: View {
    @Environment(TranscriptStore.self) private var store
    @State private var searchText = ""
    @State private var renamingSessionID: UUID?
    @State private var renameText = ""

    var body: some View {
        @Bindable var store = store

        List(selection: $store.selectedSessionID) {
            if store.hasActiveSession {
                liveRow
            }

            Section("History") {
                ForEach(filteredSessions) { session in
                    sessionRow(session)
                        .tag(session.id)
                        .contextMenu { sessionContextMenu(session) }
                }
            }
        }
        .listStyle(.sidebar)
        .searchable(text: $searchText, placement: .sidebar, prompt: "Search history")
        .overlay {
            if store.sessions.isEmpty && !store.hasActiveSession {
                ContentUnavailableView(
                    "No History",
                    systemImage: "waveform",
                    description: Text("Transcriptions will appear here")
                )
            }
        }
        .sheet(item: renamingBinding) { session in
            RenameSheet(title: renameText) { newTitle in
                store.renameSession(session, to: newTitle)
                renamingSessionID = nil
            } onCancel: {
                renamingSessionID = nil
            }
        }
    }

    private var renamingBinding: Binding<Session?> {
        Binding(
            get: {
                guard let id = renamingSessionID else { return nil }
                return store.sessions.first { $0.id == id }
            },
            set: { _ in renamingSessionID = nil }
        )
    }

    private var filteredSessions: [Session] {
        if searchText.isEmpty { return store.sessions }
        return store.sessions.filter {
            $0.transcript.localizedCaseInsensitiveContains(searchText) ||
            $0.title.localizedCaseInsensitiveContains(searchText)
        }
    }

    private var liveRow: some View {
        HStack {
            if store.isPaused {
                Image(systemName: "pause.fill")
                    .foregroundStyle(.orange)
                    .frame(width: 24)
            } else {
                AudioLevelView(level: store.audioLevel, barCount: 6)
                    .frame(width: 24)
            }
            VStack(alignment: .leading) {
                Text(store.isPaused ? "Paused" : "Transcribing...")
                    .font(.headline)
                Text(store.liveTranscript.prefix(60).description)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .listRowBackground(
            (store.isPaused ? Color.orange : Color.accentColor).opacity(0.08)
        )
    }

    private func sessionRow(_ session: Session) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(session.displayTitle)
                .font(.headline)
                .lineLimit(1)
            Text(session.transcript.prefix(80).description)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(2)
            HStack(spacing: 6) {
                Text(session.date, format: .dateTime.month(.abbreviated).day().hour().minute())
                Text("·")
                Text(formattedDuration(session.duration))
            }
            .font(.caption2)
            .foregroundStyle(.tertiary)
        }
        .padding(.vertical, 2)
    }

    @ViewBuilder
    private func sessionContextMenu(_ session: Session) -> some View {
        Button("Rename...") {
            renameText = session.title
            renamingSessionID = session.id
        }
        Button("Copy Transcript") {
            NSPasteboard.general.clearContents()
            NSPasteboard.general.setString(session.transcript, forType: .string)
        }
        Divider()
        Button("Delete", role: .destructive) {
            store.deleteSession(session)
        }
    }

    private func formattedDuration(_ duration: TimeInterval) -> String {
        let minutes = Int(duration) / 60
        let seconds = Int(duration) % 60
        return String(format: "%d:%02d", minutes, seconds)
    }
}

private struct RenameSheet: View {
    @State var title: String
    let onSave: (String) -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Text("Rename")
                .font(.headline)
            TextField("Title", text: $title)
                .textFieldStyle(.roundedBorder)
                .frame(minWidth: 250)
                .onSubmit { onSave(title) }
            HStack {
                Button("Cancel", role: .cancel) { onCancel() }
                    .keyboardShortcut(.cancelAction)
                Button("Save") { onSave(title) }
                    .keyboardShortcut(.defaultAction)
                    .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .padding(20)
    }
}
