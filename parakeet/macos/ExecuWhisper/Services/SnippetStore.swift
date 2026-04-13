/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

@MainActor @Observable
final class SnippetStore {
    var snippets: [Snippet] = []

    private let fileURL: URL

    init(fileURL: URL = PersistencePaths.snippetsURL) {
        self.fileURL = fileURL
        load()
    }

    static func preview(entries: [Snippet] = []) -> SnippetStore {
        let store = SnippetStore(
            fileURL: FileManager.default.temporaryDirectory
                .appendingPathComponent(UUID().uuidString)
                .appendingPathExtension("json")
        )
        store.snippets = entries
        return store
    }

    func add(_ snippet: Snippet) {
        snippets.insert(snippet, at: 0)
        save()
    }

    func update(_ snippet: Snippet) {
        guard let index = snippets.firstIndex(where: { $0.id == snippet.id }) else { return }
        snippets[index] = snippet
        save()
    }

    func delete(_ snippet: Snippet) {
        snippets.removeAll { $0.id == snippet.id }
        save()
    }

    func toggleEnabled(for id: UUID) {
        guard let index = snippets.firstIndex(where: { $0.id == id }) else { return }
        snippets[index].isEnabled.toggle()
        save()
    }

    func matchingSnippet(for text: String) -> Snippet? {
        let normalizedText = Self.normalize(text)
        guard !normalizedText.isEmpty else { return nil }
        return snippets.first {
            $0.isEnabled && Self.normalize($0.trigger) == normalizedText
        }
    }

    func markUsed(_ snippetID: UUID, at date: Date = .now) {
        guard let index = snippets.firstIndex(where: { $0.id == snippetID }) else { return }
        snippets[index].lastUsedAt = date
        save()
    }

    private func load() {
        guard
            let data = try? Data(contentsOf: fileURL),
            let decoded = try? JSONDecoder().decode([Snippet].self, from: data)
        else {
            snippets = Self.defaultSnippets
            if !snippets.isEmpty {
                save()
            }
            return
        }
        snippets = decoded
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(snippets) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }

    private static func normalize(_ text: String) -> String {
        text
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .lowercased()
    }

    private static let defaultSnippets: [Snippet] = [
        Snippet(
            name: "Daily Standup",
            trigger: "daily standup",
            content: "Yesterday:\n- \n\nToday:\n- \n\nBlockers:\n- "
        ),
        Snippet(
            name: "Email Signature",
            trigger: "email signature",
            content: "Best,\nYounghan"
        ),
    ]
}
