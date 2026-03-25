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
    case session(UUID)
}

struct SidebarView: View {
    @Environment(TranscriptStore.self) private var store
    @Binding var activePage: SidebarPage
    @State private var searchText = ""
    @State private var renamingSessionID: UUID?
    @State private var renameText = ""

    var body: some View {
        List(selection: $activePage) {
            Section {
                Label("Home", systemImage: "house")
                    .tag(SidebarPage.home)
                Label("Replacements", systemImage: "arrow.2.squarepath")
                    .tag(SidebarPage.replacements)
                Label("Snippets", systemImage: "text.append")
                    .tag(SidebarPage.snippets)
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

            if !recentDictations.isEmpty {
                Section("Recent Dictations") {
                    ForEach(recentDictations) { session in
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

    private struct SessionSection: Identifiable {
        let id = UUID()
        let title: String
        let sessions: [Session]
    }

    private var visibleSessions: [Session] {
        if searchText.isEmpty { return store.sessions }
        return store.sessions.filter {
            $0.transcript.localizedCaseInsensitiveContains(searchText) ||
            $0.title.localizedCaseInsensitiveContains(searchText) ||
            ($0.rawTranscript?.localizedCaseInsensitiveContains(searchText) ?? false) ||
            $0.tags.joined(separator: " ").localizedCaseInsensitiveContains(searchText)
        }
    }

    private var pinnedSessions: [Session] {
        visibleSessions.filter(\.pinned)
    }

    private var recentDictations: [Session] {
        visibleSessions
            .filter { !$0.pinned && $0.source == .dictation }
            .prefix(5)
            .map { $0 }
    }

    private var historySections: [SessionSection] {
        let hiddenIDs = Set(pinnedSessions.map(\.id) + recentDictations.map(\.id))
        let remainder = visibleSessions.filter { !hiddenIDs.contains($0.id) }
        let calendar = Calendar.current
        let grouped = Dictionary(grouping: remainder) { session -> String in
            if calendar.isDateInToday(session.date) {
                return "Today"
            }
            if calendar.isDateInYesterday(session.date) {
                return "Yesterday"
            }
            return "Earlier"
        }
        return ["Today", "Yesterday", "Earlier"].compactMap { key in
            guard let sessions = grouped[key], !sessions.isEmpty else { return nil }
            return SessionSection(title: key, sessions: sessions)
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
                Spacer(minLength: 0)
                sourceBadge(for: session)
            }
            Text(session.previewText.prefix(100).description)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(2)
            HStack(spacing: 6) {
                Text(session.date, format: .dateTime.month(.abbreviated).day().hour().minute())
                Text("·")
                Text(formattedDuration(session.duration))
                if session.wakeTriggered {
                    Text("wake")
                }
                ForEach(session.tags.prefix(2), id: \.self) { tag in
                    Text(tag)
                }
            }
            .font(.caption2)
            .foregroundStyle(.tertiary)
        }
        .padding(.vertical, 2)
    }

    private func sourceBadge(for session: Session) -> some View {
        Text(session.source == .dictation ? "Dictation" : "Transcript")
            .font(.caption2.weight(.medium))
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(
                (session.source == .dictation ? Color.accentColor : Color.secondary).opacity(0.12),
                in: Capsule()
            )
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
                Button(format.fileExtension.uppercased()) {
                    store.exportSession(session, format: format)
                }
            }
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
