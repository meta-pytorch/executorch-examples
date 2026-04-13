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
struct SessionCompatibilityTests {
    @Test
    func decodesLegacySessionsWithoutRichMetadata() throws {
        let json = """
        {
          "id": "6BDF20D0-6E25-43EB-81A4-34748EF304F6",
          "date": 0,
          "title": "Legacy Session",
          "transcript": "hello world",
          "duration": 12.5
        }
        """

        let session = try JSONDecoder().decode(Session.self, from: Data(json.utf8))

        #expect(session.rawTranscript == nil)
        #expect(session.tags.isEmpty)
        #expect(!session.pinned)
        #expect(session.usedSnippetIDs.isEmpty)
        #expect(session.previewText == "hello world")
    }

    @Test
    func persistencePathsUseExecuWhisperAppSupportDirectory() {
        #expect(PersistencePaths.appSupportDirectory.lastPathComponent == "ExecuWhisper")
        #expect(PersistencePaths.sessionsURL.deletingLastPathComponent() == PersistencePaths.appSupportDirectory)
        #expect(PersistencePaths.modelsDirectoryURL.deletingLastPathComponent() == PersistencePaths.appSupportDirectory)
        #expect(PersistencePaths.replacementsURL.deletingLastPathComponent() == PersistencePaths.appSupportDirectory)
        #expect(PersistencePaths.snippetsURL.deletingLastPathComponent() == PersistencePaths.appSupportDirectory)
    }

    @Test
    func loadsSessionsWithoutRestoringSelection() throws {
        let sessionsURL = makeTemporaryDirectory().appendingPathComponent("sessions.json")
        let older = Session(
            id: UUID(uuidString: "6BDF20D0-6E25-43EB-81A4-34748EF304F6")!,
            date: Date(timeIntervalSinceReferenceDate: 60),
            title: "Older",
            transcript: "older transcript",
            duration: 3
        )
        let newer = Session(
            id: UUID(uuidString: "2C3F45D0-6E25-43EB-81A4-34748EF304F6")!,
            date: Date(timeIntervalSinceReferenceDate: 120),
            title: "Newer",
            transcript: "newer transcript",
            duration: 4
        )
        let data = try JSONEncoder().encode([older, newer])
        try data.write(to: sessionsURL, options: .atomic)

        let store = TranscriptStore(
            preferences: Preferences(),
            downloader: ModelDownloader(),
            sessionsURL: sessionsURL
        )

        #expect(store.sessions.count == 2)
        #expect(store.selectedSessionID == nil)
    }

    private func makeTemporaryDirectory() -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }
}
