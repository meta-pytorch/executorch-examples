/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

private struct ReplacementEditorItem: Identifiable {
    let id = UUID()
    let entry: ReplacementEntry
    let isEditing: Bool
}

struct ReplacementManagementView: View {
    @Environment(ReplacementStore.self) private var replacementStore
    @State private var searchText = ""
    @State private var editorItem: ReplacementEditorItem?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                TextField("Search replacements", text: $searchText)
                    .textFieldStyle(.roundedBorder)
                Button("Add") {
                    editorItem = ReplacementEditorItem(entry: ReplacementEntry(), isEditing: false)
                }
                .buttonStyle(.borderedProminent)
            }

            if filteredEntries.isEmpty {
                ContentUnavailableView(
                    "No Replacements",
                    systemImage: "arrow.2.squarepath",
                    description: Text("Add names, acronyms, and product terms you want corrected automatically.")
                )
            } else {
                List {
                    ForEach(filteredEntries) { entry in
                        HStack(alignment: .top, spacing: 12) {
                            Toggle("", isOn: binding(for: entry.id))
                                .labelsHidden()
                            VStack(alignment: .leading, spacing: 4) {
                                Text(entry.replacement)
                                    .font(.headline)
                                Text("Trigger: \(entry.trigger)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                if !entry.notes.isEmpty {
                                    Text(entry.notes)
                                        .font(.caption)
                                        .foregroundStyle(.tertiary)
                                }
                            }
                            Spacer()
                            Button("Edit") {
                                editorItem = ReplacementEditorItem(entry: entry, isEditing: true)
                            }
                            .buttonStyle(.borderless)
                        }
                        .contextMenu {
                            Button("Edit") {
                                editorItem = ReplacementEditorItem(entry: entry, isEditing: true)
                            }
                            Button(entry.isEnabled ? "Disable" : "Enable") {
                                replacementStore.toggleEnabled(for: entry.id)
                            }
                            Divider()
                            Button("Delete", role: .destructive) {
                                replacementStore.delete(entry)
                            }
                        }
                    }
                }
                .listStyle(.inset)
            }
        }
        .sheet(item: $editorItem) { item in
            ReplacementEntryEditor(entry: item.entry, isEditing: item.isEditing) { entry in
                if item.isEditing {
                    replacementStore.update(entry)
                } else {
                    replacementStore.add(entry)
                }
                editorItem = nil
            } onCancel: {
                editorItem = nil
            }
            .frame(width: 420)
        }
    }

    private var filteredEntries: [ReplacementEntry] {
        guard !searchText.isEmpty else { return replacementStore.entries }
        return replacementStore.entries.filter {
            $0.trigger.localizedCaseInsensitiveContains(searchText) ||
            $0.replacement.localizedCaseInsensitiveContains(searchText) ||
            $0.notes.localizedCaseInsensitiveContains(searchText)
        }
    }

    private func binding(for id: UUID) -> Binding<Bool> {
        Binding(
            get: {
                replacementStore.entries.first(where: { $0.id == id })?.isEnabled ?? false
            },
            set: { _ in
                replacementStore.toggleEnabled(for: id)
            }
        )
    }
}

private struct ReplacementEntryEditor: View {
    @State var entry: ReplacementEntry
    let isEditing: Bool
    let onSave: (ReplacementEntry) -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(isEditing ? "Edit Replacement" : "Add Replacement")
                .font(.headline)

            TextField("Trigger phrase", text: $entry.trigger)
                .textFieldStyle(.roundedBorder)
            TextField("Replacement", text: $entry.replacement)
                .textFieldStyle(.roundedBorder)
            TextField("Notes (optional)", text: $entry.notes)
                .textFieldStyle(.roundedBorder)

            Toggle("Case sensitive", isOn: $entry.isCaseSensitive)
            Toggle("Require word boundary", isOn: $entry.requiresWordBoundary)
            Toggle("Enabled", isOn: $entry.isEnabled)

            HStack {
                Spacer()
                Button("Cancel", role: .cancel) {
                    onCancel()
                }
                Button("Save") {
                    onSave(entry)
                }
                .keyboardShortcut(.defaultAction)
                .disabled(
                    entry.trigger.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                    entry.replacement.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                )
            }
        }
        .padding(24)
    }
}
