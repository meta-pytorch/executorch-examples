/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation
import Testing

struct SessionCompatibilityTests {
    @Test
    func decodesLegacySessionsWithoutNewMetadata() throws {
        let json = """
        {
          "id": "6BDF20D0-6E25-43EB-81A4-34748EF304F6",
          "date": "2026-03-24T10:30:00Z",
          "title": "Legacy Session",
          "transcript": "hello world",
          "duration": 12.5
        }
        """

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601

        let session = try decoder.decode(Session.self, from: Data(json.utf8))

        #expect(session.source == .transcription)
        #expect(session.rawTranscript == nil)
        #expect(session.tags.isEmpty)
        #expect(session.usedSnippetIDs.isEmpty)
        #expect(!session.wakeTriggered)
        #expect(!session.pinned)
        #expect(session.transcript == "hello world")
    }

    @Test
    func exportFormatsIncludeRichSessionMetadata() throws {
        let session = Session(
            id: UUID(uuidString: "6BDF20D0-6E25-43EB-81A4-34748EF304F6")!,
            date: Date(timeIntervalSince1970: 1_742_814_600),
            title: "Wake Dictation",
            transcript: "hello world",
            duration: 12.5,
            source: .dictation,
            rawTranscript: "hey torch hello world",
            tags: ["replacement", "snippet"],
            wakeTriggered: true
        )

        let json = SessionExportFormat.json.render(session)
        let srt = SessionExportFormat.srt.render(session)

        #expect(json.contains("\"wakeTriggered\" : true"))
        #expect(json.contains("\"rawTranscript\" : \"hey torch hello world\""))
        #expect(json.contains("\"tags\" : ["))
        #expect(srt.contains("00:00:00,000 --> 00:00:12,500"))
        #expect(srt.contains("hello world"))
    }
}
