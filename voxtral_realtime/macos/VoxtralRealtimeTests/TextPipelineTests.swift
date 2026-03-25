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
        let snippetStore = SnippetStore(fileURL: sandbox.appendingPathComponent("snippets.json"))
        snippetStore.snippets = []
        let pipeline = TextPipeline(replacementStore: replacementStore, snippetStore: snippetStore)

        let result = pipeline.process("young han joined mtia", context: .dictation)

        #expect(result.outputText == "Younghan joined MTIA")
        #expect(result.tags.contains("replacement"))
    }

    @Test
    func snippetExpandsOnlyFromExplicitCommand() {
        let sandbox = makeSandbox()
        let replacementStore = ReplacementStore(fileURL: sandbox.appendingPathComponent("replacements.json"))
        replacementStore.entries = []
        let snippetStore = SnippetStore(fileURL: sandbox.appendingPathComponent("snippets.json"))
        let standup = Snippet(name: "Standup", trigger: "daily standup", content: "Yesterday:\n- ")
        snippetStore.snippets = [standup]
        let pipeline = TextPipeline(replacementStore: replacementStore, snippetStore: snippetStore)

        let expanded = pipeline.process("insert snippet daily standup", context: .dictation)
        let untouched = pipeline.process("daily standup", context: .dictation)

        #expect(expanded.outputText == "Yesterday:\n- ")
        #expect(expanded.usedSnippetIDs == [standup.id])
        #expect(untouched.outputText == "daily standup")
    }

    @Test
    func literalPrefixSkipsSnippetExpansion() {
        let sandbox = makeSandbox()
        let replacementStore = ReplacementStore(fileURL: sandbox.appendingPathComponent("replacements.json"))
        replacementStore.entries = []
        let snippetStore = SnippetStore(fileURL: sandbox.appendingPathComponent("snippets.json"))
        snippetStore.snippets = [Snippet(name: "Standup", trigger: "daily standup", content: "template")]
        let pipeline = TextPipeline(replacementStore: replacementStore, snippetStore: snippetStore)

        let result = pipeline.process("literal insert snippet daily standup", context: .dictation)

        #expect(result.outputText == "insert snippet daily standup")
        #expect(result.skippedSnippetExpansion)
        #expect(result.usedSnippetIDs.isEmpty)
    }

    @Test
    func wakePhraseNormalizationUsesReplacementsAndStripsNoise() {
        let sandbox = makeSandbox()
        let replacementStore = ReplacementStore(fileURL: sandbox.appendingPathComponent("replacements.json"))
        replacementStore.entries = [
            ReplacementEntry(trigger: "torchhh", replacement: "torch"),
        ]
        let snippetStore = SnippetStore(fileURL: sandbox.appendingPathComponent("snippets.json"))
        snippetStore.snippets = []
        let pipeline = TextPipeline(replacementStore: replacementStore, snippetStore: snippetStore)

        let normalized = pipeline.normalizeForWakePhrase(" Hey, torchhh!! ")

        #expect(normalized == "hey torch")
    }

    private func makeSandbox() -> URL {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }
}
