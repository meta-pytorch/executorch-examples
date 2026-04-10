/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

struct TextProcessingResult: Sendable, Equatable {
    let rawText: String
    let outputText: String
    let tags: [String]
    let usedSnippetIDs: [UUID]
    let skippedSnippetExpansion: Bool

    var transformed: Bool {
        rawText != outputText
    }
}

@MainActor
final class TextPipeline {
    enum Context: Sendable {
        case dictation
        case sessionSave
        case wakePhraseCheck
    }

    private let replacementStore: ReplacementStore
    private let snippetStore: SnippetStore

    init(replacementStore: ReplacementStore, snippetStore: SnippetStore) {
        self.replacementStore = replacementStore
        self.snippetStore = snippetStore
    }

    func process(_ text: String, context: Context) -> TextProcessingResult {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return TextProcessingResult(
                rawText: text,
                outputText: "",
                tags: [],
                usedSnippetIDs: [],
                skippedSnippetExpansion: false
            )
        }

        let literalPrefix = "literal "
        let literalCommand = trimmed.lowercased().hasPrefix(literalPrefix)
        let baseText: String
        if literalCommand {
            baseText = String(trimmed.dropFirst(literalPrefix.count))
        } else {
            baseText = trimmed
        }

        let snippetResolution = resolveSnippet(in: baseText, allowExpansion: context == .dictation && !literalCommand)
        let afterSnippets = snippetResolution.text
        let replacementsApplied = snippetResolution.usedSnippetIDs.isEmpty
            ? applyReplacements(to: afterSnippets)
            : afterSnippets
        let styleApplied = applyStyle(to: replacementsApplied)

        var tags: [String] = []
        if replacementsApplied != afterSnippets {
            tags.append("replacement")
        }
        if !snippetResolution.usedSnippetIDs.isEmpty {
            tags.append("snippet")
        }
        if literalCommand {
            tags.append("literal")
        }

        return TextProcessingResult(
            rawText: text,
            outputText: styleApplied,
            tags: tags,
            usedSnippetIDs: snippetResolution.usedSnippetIDs,
            skippedSnippetExpansion: literalCommand
        )
    }

    func normalizeForWakePhrase(_ text: String) -> String {
        let processed = process(text, context: .wakePhraseCheck).outputText
        let folded = processed
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .replacingOccurrences(of: #"[^a-z0-9\s]"#, with: " ", options: .regularExpression)
        return folded
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func applyReplacements(to text: String) -> String {
        replacementStore.entries
            .filter(\.isEnabled)
            .sorted { $0.trigger.count > $1.trigger.count }
            .reduce(text) { partial, entry in
                replace(entry: entry, in: partial)
            }
    }

    private func replace(entry: ReplacementEntry, in text: String) -> String {
        guard !entry.trigger.isEmpty else { return text }

        let escaped = NSRegularExpression.escapedPattern(for: entry.trigger)
        let pattern = entry.requiresWordBoundary ? #"\b\#(escaped)\b"# : escaped
        let options: NSRegularExpression.Options = entry.isCaseSensitive ? [] : [.caseInsensitive]

        guard let regex = try? NSRegularExpression(pattern: pattern, options: options) else {
            return text
        }

        let range = NSRange(text.startIndex..., in: text)
        let matches = regex.matches(in: text, options: [], range: range)
        guard !matches.isEmpty else { return text }

        var output = text
        for match in matches.reversed() {
            guard let matchRange = Range(match.range, in: output) else { continue }
            let original = String(output[matchRange])
            let replacement = preserveCaseIfNeeded(original: original, replacement: entry.replacement)
            output.replaceSubrange(matchRange, with: replacement)
        }
        return output
    }

    private func preserveCaseIfNeeded(original: String, replacement: String) -> String {
        if original == original.uppercased() {
            return replacement.uppercased()
        }
        if original == original.lowercased() {
            return replacement
        }
        if let first = original.first, String(first) == String(first).uppercased() {
            return replacement.prefix(1).uppercased() + replacement.dropFirst()
        }
        return replacement
    }

    private func resolveSnippet(in text: String, allowExpansion: Bool) -> (text: String, usedSnippetIDs: [UUID]) {
        guard allowExpansion else {
            return (text, [])
        }

        let trigger = text.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .trimmingCharacters(in: .punctuationCharacters)

        if let snippet = snippetStore.snippets.first(where: {
            $0.isEnabled && $0.trigger.compare(trigger, options: .caseInsensitive) == .orderedSame
        }) {
            snippetStore.markUsed(snippet.id)
            return (snippet.content, [snippet.id])
        }

        return (text, [])
    }

    private func applyStyle(to text: String) -> String {
        // Style is intentionally a no-op in v1. Keep the stage so later presets
        // can plug into the same transformation pipeline.
        text
    }
}
