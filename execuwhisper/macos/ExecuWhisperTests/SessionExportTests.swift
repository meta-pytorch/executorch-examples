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
struct SessionExportTests {
    @Test
    func jsonExportIncludesRawTranscriptAndTags() {
        let session = Session(
            id: UUID(uuidString: "6BDF20D0-6E25-43EB-81A4-34748EF304F6")!,
            date: Date(timeIntervalSince1970: 1_742_814_600),
            title: "Meeting",
            transcript: "clean transcript",
            duration: 12.5,
            rawTranscript: "spoken transcript",
            tags: ["replacement"]
        )

        let json = SessionExportFormat.json.render(session)

        #expect(json.contains("\"rawTranscript\" : \"spoken transcript\""))
        #expect(json.contains("\"tags\" : ["))
        #expect(json.contains("\"title\" : \"Meeting\""))
    }

    @Test
    func srtExportUsesSessionDuration() {
        let session = Session(
            title: "Timed",
            transcript: "subtitle line",
            duration: 12.5
        )

        let srt = SessionExportFormat.srt.render(session)

        #expect(srt.contains("00:00:00,000 --> 00:00:12,500"))
        #expect(srt.contains("subtitle line"))
    }

    @Test
    func transcriptStoreWritesExportFiles() throws {
        let sandbox = makeSandbox()
        let sessionsURL = sandbox.appendingPathComponent("sessions.json")
        let store = TranscriptStore(
            preferences: Preferences(),
            downloader: ModelDownloader(),
            sessionsURL: sessionsURL
        )
        let session = Session(
            title: "Export me",
            transcript: "clean transcript",
            duration: 7,
            rawTranscript: "spoken transcript",
            tags: ["replacement"]
        )
        let exportURL = sandbox.appendingPathComponent("export.json")

        try store.writeSessionExport(session, format: .json, to: exportURL)

        let contents = try String(contentsOf: exportURL)
        #expect(contents.contains("\"clean transcript\""))
        #expect(contents.contains("\"spoken transcript\""))
    }

    private func makeSandbox() -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }
}
