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

    var transformed: Bool {
        rawText != outputText
    }
}

@MainActor
final class TextPipeline {
    enum Context: Sendable {
        case standard
        case dictation
    }

    private let replacementStore: ReplacementStore
    private let snippetStore: SnippetStore?

    init(replacementStore: ReplacementStore, snippetStore: SnippetStore? = nil) {
        self.replacementStore = replacementStore
        self.snippetStore = snippetStore
    }

    func process(_ text: String, context: Context = .standard) -> TextProcessingResult {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return TextProcessingResult(rawText: text, outputText: "", tags: [], usedSnippetIDs: [])
        }

        let replaced = applyReplacements(to: trimmed)
        if context == .dictation, let snippet = snippetStore?.matchingSnippet(for: replaced) {
            snippetStore?.markUsed(snippet.id)
            var tags: [String] = ["snippet"]
            if replaced != trimmed {
                tags.insert("replacement", at: 0)
            }
            return TextProcessingResult(
                rawText: trimmed,
                outputText: snippet.content,
                tags: tags,
                usedSnippetIDs: [snippet.id]
            )
        }
        let styled = applyStyle(to: replaced)
        let tags = styled == trimmed ? [] : ["replacement"]
        return TextProcessingResult(rawText: trimmed, outputText: styled, tags: tags, usedSnippetIDs: [])
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

        let matches = regex.matches(in: text, range: NSRange(text.startIndex..., in: text))
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

    private func applyStyle(to text: String) -> String {
        text
    }
}
