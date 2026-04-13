/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

enum SidebarPage: Hashable {
    case home
    case replacements
    case snippets
    case settings
    case session(UUID)
}

struct SidebarView: View {
    @Environment(TranscriptStore.self) private var store
    @Binding var selectedPages: Set<SidebarPage>
    @State private var searchText = ""
    @State private var renamingSessionID: UUID?
    @State private var renameText = ""

    var body: some View {
        List(selection: $selectedPages) {
            Section {
                Label("Home", systemImage: "house")
                    .tag(SidebarPage.home)
                Label("Replacements", systemImage: "arrow.2.squarepath")
                    .tag(SidebarPage.replacements)
                Label("Snippets", systemImage: "text.append")
                    .tag(SidebarPage.snippets)
                Label("Settings", systemImage: "gear")
                    .tag(SidebarPage.settings)
            }

            if store.hasActiveSession {
                liveRow
            }

            if !pinnedSessions.isEmpty {
                Section("Pinned") {
                    ForEach(pinnedSessions) { session in
                        sessionRow(session)
                            .tag(SidebarPage.session(session.id))
                            .contextMenu { sessionContextMenu(session) }
                    }
                }
            }

            ForEach(historySections) { section in
                Section(section.title) {
                    ForEach(section.sessions) { session in
                        sessionRow(session)
                            .tag(SidebarPage.session(session.id))
                            .contextMenu { sessionContextMenu(session) }
                    }
                }
            }
        }
        .listStyle(.sidebar)
        .searchable(text: $searchText, placement: .sidebar, prompt: "Search history")
        .onDeleteCommand {
            deleteSelectedSessions()
        }
        .overlay {
            if store.sessions.isEmpty && !store.hasActiveSession {
                ContentUnavailableView(
                    "No History",
                    systemImage: "waveform",
                    description: Text("Record audio to create your first transcript")
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

    private var pinnedSessions: [Session] {
        SessionHistory.pinnedSessions(in: store.sessions, matching: searchText)
    }

    private var historySections: [SessionHistorySection] {
        SessionHistory.sections(in: store.sessions, matching: searchText)
    }

    private var liveRow: some View {
        HStack {
            if store.isRecording {
                AudioLevelView(level: store.audioLevel, barCount: 6)
                    .frame(width: 24)
            } else {
                ProgressView()
                    .controlSize(.small)
                    .frame(width: 24)
            }

            VStack(alignment: .leading) {
                Text(store.isRecording ? "Recording..." : "Transcribing...")
                    .font(.headline)
                Text(store.statusMessage)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .listRowBackground(Color.accentColor.opacity(0.08))
    }

    private func sessionRow(_ session: Session) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                if session.pinned {
                    Image(systemName: "pin.fill")
                        .font(.caption2)
                        .foregroundStyle(.yellow)
                }
                Text(session.displayTitle)
                    .font(.headline)
                    .lineLimit(1)
            }
            Text(session.previewText.prefix(100).description)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(2)
            HStack(spacing: 6) {
                Text(session.date, format: .dateTime.month(.abbreviated).day().hour().minute())
                Text("·")
                Text(formattedDuration(session.duration))
                ForEach(session.tags.prefix(2), id: \.self) { tag in
                    Text(tag)
                }
            }
            .font(.caption2)
            .foregroundStyle(.tertiary)
        }
        .padding(.vertical, 2)
    }

    @ViewBuilder
    private func sessionContextMenu(_ session: Session) -> some View {
        Button(session.pinned ? "Unpin" : "Pin") {
            store.togglePinned(session)
        }
        Button("Rename...") {
            renameText = session.title
            renamingSessionID = session.id
        }
        Button("Copy Transcript") {
            NSPasteboard.general.clearContents()
            NSPasteboard.general.setString(session.transcript, forType: .string)
        }
        Menu("Export") {
            ForEach(SessionExportFormat.allCases, id: \.rawValue) { format in
                Button(format.title) {
                    store.exportSession(session, format: format)
                }
            }
        }
        Divider()
        if store.selectedHistorySessionIDs.count > 1 && store.selectedHistorySessionIDs.contains(session.id) {
            Button("Delete Selected (\(store.selectedHistorySessionIDs.count))", role: .destructive) {
                deleteSelectedSessions()
            }
            Divider()
        }
        Button("Delete", role: .destructive) {
            store.deleteSession(session)
        }
    }

    private func formattedDuration(_ duration: TimeInterval) -> String {
        let minutes = Int(duration) / 60
        let seconds = Int(duration) % 60
        return String(format: "%d:%02d", minutes, seconds)
    }

    private func deleteSelectedSessions() {
        guard store.selectedHistorySessionIDs.count > 1 else { return }
        store.deleteSessions(ids: store.selectedHistorySessionIDs)
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
