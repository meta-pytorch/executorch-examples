/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation
import Testing

@MainActor
struct SessionHistoryTests {
    @Test
    func historySectionsGroupByRecencyAndExcludePinnedSessions() {
        let calendar = Calendar(identifier: .gregorian)
        let referenceDate = Date(timeIntervalSinceReferenceDate: 86_400 * 10)
        let sessions = [
            Session(
                title: "Today pinned",
                transcript: "latest transcript",
                duration: 8,
                rawTranscript: "latest raw",
                tags: ["replacement"],
                pinned: true
            ),
            Session(
                date: referenceDate,
                title: "Today",
                transcript: "today transcript",
                duration: 6
            ),
            Session(
                date: referenceDate.addingTimeInterval(-86_400),
                title: "Yesterday",
                transcript: "yesterday transcript",
                duration: 5
            ),
            Session(
                date: referenceDate.addingTimeInterval(-86_400 * 3),
                title: "Earlier",
                transcript: "earlier transcript",
                duration: 4
            ),
        ]

        let pinned = SessionHistory.pinnedSessions(in: sessions, matching: "", referenceDate: referenceDate, calendar: calendar)
        let sections = SessionHistory.sections(in: sessions, matching: "", referenceDate: referenceDate, calendar: calendar)

        #expect(pinned.count == 1)
        #expect(pinned.first?.title == "Today pinned")
        #expect(sections.map(\.title) == ["Today", "Yesterday", "Earlier"])
        #expect(sections.flatMap(\.sessions).allSatisfy { !$0.pinned })
    }

    @Test
    func historySearchMatchesTranscriptTitleRawTranscriptAndTags() {
        let referenceDate = Date(timeIntervalSinceReferenceDate: 86_400 * 5)
        let sessions = [
            Session(
                date: referenceDate,
                title: "Tagged",
                transcript: "clean transcript",
                duration: 3,
                rawTranscript: "spoken words",
                tags: ["replacement"]
            ),
            Session(
                date: referenceDate.addingTimeInterval(-86_400),
                title: "Meeting Notes",
                transcript: "summary text",
                duration: 4
            ),
        ]

        #expect(SessionHistory.visibleSessions(in: sessions, matching: "spoken").count == 1)
        #expect(SessionHistory.visibleSessions(in: sessions, matching: "replacement").count == 1)
        #expect(SessionHistory.visibleSessions(in: sessions, matching: "meeting").count == 1)
        #expect(SessionHistory.visibleSessions(in: sessions, matching: "summary").count == 1)
    }

    @Test
    func togglePinnedUpdatesStoredSession() throws {
        let sessionsURL = makeSandbox().appendingPathComponent("sessions.json")
        let initial = Session(title: "Pin me", transcript: "text", duration: 3)
        try JSONEncoder().encode([initial]).write(to: sessionsURL, options: .atomic)

        let store = TranscriptStore(
            preferences: Preferences(),
            downloader: ModelDownloader(),
            sessionsURL: sessionsURL
        )

        store.togglePinned(initial)

        let updated = try #require(store.sessions.first)
        #expect(updated.pinned)
    }

    @Test
    func deleteSessionsRemovesAllSelectedHistoryItems() throws {
        let sessionsURL = makeSandbox().appendingPathComponent("sessions.json")
        let first = Session(title: "First", transcript: "first", duration: 3)
        let second = Session(title: "Second", transcript: "second", duration: 4)
        let keep = Session(title: "Keep", transcript: "keep", duration: 5)
        try JSONEncoder().encode([first, second, keep]).write(to: sessionsURL, options: .atomic)

        let store = TranscriptStore(
            preferences: Preferences(),
            downloader: ModelDownloader(),
            sessionsURL: sessionsURL
        )
        store.selectedSessionID = first.id
        store.selectedHistorySessionIDs = [first.id, second.id]

        store.deleteSessions(ids: [first.id, second.id])

        #expect(store.sessions.map(\.id) == [keep.id])
        #expect(store.selectedSessionID == nil)
        #expect(store.selectedHistorySessionIDs.isEmpty)
    }

    private func makeSandbox() -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }
}
