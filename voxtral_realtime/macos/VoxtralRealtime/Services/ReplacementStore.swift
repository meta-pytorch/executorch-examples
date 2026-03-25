/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

@MainActor @Observable
final class ReplacementStore {
    var entries: [ReplacementEntry] = []

    private let fileURL: URL

    init(fileURL: URL = PersistencePaths.replacementsURL) {
        self.fileURL = fileURL
        load()
    }

    func add(_ entry: ReplacementEntry) {
        entries.insert(entry, at: 0)
        save()
    }

    func update(_ entry: ReplacementEntry) {
        guard let index = entries.firstIndex(where: { $0.id == entry.id }) else { return }
        entries[index] = entry
        save()
    }

    func delete(_ entry: ReplacementEntry) {
        entries.removeAll { $0.id == entry.id }
        save()
    }

    func toggleEnabled(for id: UUID) {
        guard let index = entries.firstIndex(where: { $0.id == id }) else { return }
        entries[index].isEnabled.toggle()
        save()
    }

    private func load() {
        let legacyURL = fileURL.deletingLastPathComponent().appendingPathComponent("dictionary.json")

        if let data = try? Data(contentsOf: fileURL),
           let decoded = try? JSONDecoder().decode([ReplacementEntry].self, from: data) {
            entries = decoded
            return
        }

        if let data = try? Data(contentsOf: legacyURL),
           let decoded = try? JSONDecoder().decode([ReplacementEntry].self, from: data) {
            entries = decoded
            save()
            try? FileManager.default.removeItem(at: legacyURL)
            return
        }

        entries = Self.defaultEntries
        if !entries.isEmpty {
            save()
        }
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }

    private static let defaultEntries: [ReplacementEntry] = [
        ReplacementEntry(trigger: "mtia", replacement: "MTIA"),
        ReplacementEntry(trigger: "mvai", replacement: "MVAI"),
        ReplacementEntry(trigger: "executorch", replacement: "ExecuTorch", requiresWordBoundary: false),
    ]
}
