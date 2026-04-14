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
struct TextPipelineTests {
    @Test
    func replacementsApplyLongestMatchFirst() {
        let sandbox = makeSandbox()
        let replacementStore = ReplacementStore(fileURL: sandbox.appendingPathComponent("replacements.json"))
        replacementStore.entries = [
            ReplacementEntry(trigger: "young", replacement: "Young"),
            ReplacementEntry(trigger: "young han", replacement: "Younghan"),
            ReplacementEntry(trigger: "mtia", replacement: "MTIA"),
        ]
        let pipeline = TextPipeline(replacementStore: replacementStore)

        let result = pipeline.process("young han joined mtia")

        #expect(result.outputText == "Younghan joined MTIA")
        #expect(result.tags == ["replacement"])
    }

    @Test
    func replacementsPreserveCaseAndWordBoundaryRules() {
        let sandbox = makeSandbox()
        let replacementStore = ReplacementStore(fileURL: sandbox.appendingPathComponent("replacements.json"))
        replacementStore.entries = [
            ReplacementEntry(trigger: "executorch", replacement: "ExecuTorch"),
            ReplacementEntry(trigger: "ml", replacement: "ML"),
        ]
        let pipeline = TextPipeline(replacementStore: replacementStore)

        let result = pipeline.process("EXECUTORCH powers xml and ml")

        #expect(result.outputText == "EXECUTORCH powers xml and ML")
    }

    @Test
    func processLeavesTextUnchangedWhenNoRulesMatch() {
        let sandbox = makeSandbox()
        let replacementStore = ReplacementStore(fileURL: sandbox.appendingPathComponent("replacements.json"))
        replacementStore.entries = []
        let pipeline = TextPipeline(replacementStore: replacementStore)

        let result = pipeline.process("plain transcript text")

        #expect(result.outputText == "plain transcript text")
        #expect(result.tags.isEmpty)
        #expect(!result.transformed)
    }

    @Test
    func transcriptStorePersistsRawAndProcessedTranscripts() throws {
        let sandbox = makeSandbox()
        let replacementStore = ReplacementStore(fileURL: sandbox.appendingPathComponent("replacements.json"))
        replacementStore.entries = [
            ReplacementEntry(trigger: "executorch", replacement: "ExecuTorch"),
        ]
        let pipeline = TextPipeline(replacementStore: replacementStore)
        let sessionsURL = sandbox.appendingPathComponent("sessions.json")
        let store = TranscriptStore(
            preferences: Preferences(),
            downloader: ModelDownloader(),
            sessionsURL: sessionsURL,
            textPipeline: pipeline
        )

        store.storeCompletedTranscription(rawText: "executorch rocks", duration: 3)

        let saved = try #require(store.sessions.first)
        #expect(saved.rawTranscript == "executorch rocks")
        #expect(saved.transcript == "ExecuTorch rocks")
        #expect(saved.tags == ["replacement"])
    }

    @Test
    func dictationTranscriptionProcessesTextWithoutPersistingHistory() {
        let sandbox = makeSandbox()
        let replacementStore = ReplacementStore(fileURL: sandbox.appendingPathComponent("replacements.json"))
        replacementStore.entries = [
            ReplacementEntry(trigger: "executorch", replacement: "ExecuTorch"),
        ]
        let pipeline = TextPipeline(replacementStore: replacementStore)
        let sessionsURL = sandbox.appendingPathComponent("sessions.json")
        let store = TranscriptStore(
            preferences: Preferences(),
            downloader: ModelDownloader(),
            sessionsURL: sessionsURL,
            textPipeline: pipeline
        )

        let result = store.storeDictationTranscription(rawText: "executorch rocks", duration: 3)

        #expect(result.outputText == "ExecuTorch rocks")
        #expect(store.sessions.isEmpty)
        #expect(store.selectedSessionID == nil)
        #expect(store.liveTranscript == "ExecuTorch rocks")
    }

    private func makeSandbox() -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }
}
