/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

private struct SnippetEditorItem: Identifiable {
    let id = UUID()
    let snippet: Snippet
    let isEditing: Bool
}

struct SnippetManagementView: View {
    @Environment(SnippetStore.self) private var snippetStore
    @State private var searchText = ""
    @State private var editorItem: SnippetEditorItem?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                TextField("Search snippets", text: $searchText)
                    .textFieldStyle(.roundedBorder)
                Button("Add") {
                    editorItem = SnippetEditorItem(snippet: Snippet(), isEditing: false)
                }
                .buttonStyle(.borderedProminent)
            }

            if filteredSnippets.isEmpty {
                ContentUnavailableView(
                    "No Snippets",
                    systemImage: "text.append",
                    description: Text("Create reusable templates and trigger them with commands like 'insert snippet daily standup'.")
                )
            } else {
                List {
                    ForEach(filteredSnippets) { snippet in
                        HStack(alignment: .top, spacing: 12) {
                            Toggle("", isOn: binding(for: snippet.id))
                                .labelsHidden()
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(snippet.name)
                                        .font(.headline)
                                    if let lastUsedAt = snippet.lastUsedAt {
                                        Text(lastUsedAt, format: .relative(presentation: .named))
                                            .font(.caption2)
                                            .foregroundStyle(.tertiary)
                                    }
                                }
                                Text("Say: insert snippet \(snippet.trigger)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Text(snippet.content)
                                    .font(.caption)
                                    .foregroundStyle(.tertiary)
                                    .lineLimit(3)
                                if !snippet.notes.isEmpty {
                                    Text(snippet.notes)
                                        .font(.caption)
                                        .foregroundStyle(.tertiary)
                                }
                            }
                            Spacer()
                            Button("Edit") {
                                editorItem = SnippetEditorItem(snippet: snippet, isEditing: true)
                            }
                            .buttonStyle(.borderless)
                        }
                        .contextMenu {
                            Button("Edit") {
                                editorItem = SnippetEditorItem(snippet: snippet, isEditing: true)
                            }
                            Button(snippet.isEnabled ? "Disable" : "Enable") {
                                snippetStore.toggleEnabled(for: snippet.id)
                            }
                            Divider()
                            Button("Delete", role: .destructive) {
                                snippetStore.delete(snippet)
                            }
                        }
                    }
                }
                .listStyle(.inset)
            }
        }
        .sheet(item: $editorItem) { item in
            SnippetEditor(snippet: item.snippet, isEditing: item.isEditing) { snippet in
                if item.isEditing {
                    snippetStore.update(snippet)
                } else {
                    snippetStore.add(snippet)
                }
                editorItem = nil
            } onCancel: {
                editorItem = nil
            }
            .frame(width: 480, height: 460)
        }
    }

    private var filteredSnippets: [Snippet] {
        guard !searchText.isEmpty else { return snippetStore.snippets }
        return snippetStore.snippets.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            $0.trigger.localizedCaseInsensitiveContains(searchText) ||
            $0.content.localizedCaseInsensitiveContains(searchText) ||
            $0.notes.localizedCaseInsensitiveContains(searchText)
        }
    }

    private func binding(for id: UUID) -> Binding<Bool> {
        Binding(
            get: {
                snippetStore.snippets.first(where: { $0.id == id })?.isEnabled ?? false
            },
            set: { _ in
                snippetStore.toggleEnabled(for: id)
            }
        )
    }
}

private struct SnippetEditor: View {
    @State var snippet: Snippet
    let isEditing: Bool
    let onSave: (Snippet) -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(isEditing ? "Edit Snippet" : "Add Snippet")
                .font(.headline)

            TextField("Display name", text: $snippet.name)
                .textFieldStyle(.roundedBorder)
            TextField("Trigger phrase", text: $snippet.trigger)
                .textFieldStyle(.roundedBorder)

            VStack(alignment: .leading, spacing: 6) {
                Text("Content")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextEditor(text: $snippet.content)
                    .font(.body.monospaced())
                    .frame(minHeight: 180)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(.quaternary))
            }

            TextField("Notes (optional)", text: $snippet.notes)
                .textFieldStyle(.roundedBorder)
            Toggle("Enabled", isOn: $snippet.isEnabled)

            HStack {
                Spacer()
                Button("Cancel", role: .cancel) {
                    onCancel()
                }
                Button("Save") {
                    onSave(snippet)
                }
                .keyboardShortcut(.defaultAction)
                .disabled(
                    snippet.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                    snippet.trigger.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                    snippet.content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                )
            }
        }
        .padding(24)
    }
}
