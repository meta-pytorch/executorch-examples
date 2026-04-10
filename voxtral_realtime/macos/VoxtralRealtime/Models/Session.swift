/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

enum SessionSource: String, Codable, Sendable, Hashable {
    case transcription
    case dictation
}

struct Session: Identifiable, Codable, Sendable, Hashable {
    let id: UUID
    let date: Date
    var title: String
    var transcript: String
    var duration: TimeInterval
    var source: SessionSource
    var rawTranscript: String?
    var tags: [String]
    var wakeTriggered: Bool
    var pinned: Bool
    var usedSnippetIDs: [UUID]

    init(
        id: UUID = UUID(),
        date: Date = .now,
        title: String = "",
        transcript: String = "",
        duration: TimeInterval = 0,
        source: SessionSource = .transcription,
        rawTranscript: String? = nil,
        tags: [String] = [],
        wakeTriggered: Bool = false,
        pinned: Bool = false,
        usedSnippetIDs: [UUID] = []
    ) {
        self.id = id
        self.date = date
        self.title = title
        self.transcript = transcript
        self.duration = duration
        self.source = source
        self.rawTranscript = rawTranscript
        self.tags = tags
        self.wakeTriggered = wakeTriggered
        self.pinned = pinned
        self.usedSnippetIDs = usedSnippetIDs
    }

    enum CodingKeys: String, CodingKey {
        case id
        case date
        case title
        case transcript
        case duration
        case source
        case rawTranscript
        case tags
        case wakeTriggered
        case pinned
        case usedSnippetIDs
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        date = try container.decodeIfPresent(Date.self, forKey: .date) ?? .now
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? ""
        transcript = try container.decodeIfPresent(String.self, forKey: .transcript) ?? ""
        duration = try container.decodeIfPresent(TimeInterval.self, forKey: .duration) ?? 0
        source = try container.decodeIfPresent(SessionSource.self, forKey: .source) ?? .transcription
        rawTranscript = try container.decodeIfPresent(String.self, forKey: .rawTranscript)
        tags = try container.decodeIfPresent([String].self, forKey: .tags) ?? []
        wakeTriggered = try container.decodeIfPresent(Bool.self, forKey: .wakeTriggered) ?? false
        pinned = try container.decodeIfPresent(Bool.self, forKey: .pinned) ?? false
        usedSnippetIDs = try container.decodeIfPresent([UUID].self, forKey: .usedSnippetIDs) ?? []
    }

    var displayTitle: String {
        if !title.isEmpty { return title }
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }

    var previewText: String {
        transcript.isEmpty ? rawTranscript ?? "" : transcript
    }
}
