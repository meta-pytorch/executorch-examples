/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

enum FormatterPromptBuilder {
    static let temperature = 0.0

    /// Maximum words per chunk fed to the formatter.
    ///
    /// Empirically determined by the chunk-size probe sweep against the
    /// production lfm2_5_350m_mlx_4w model: trial outputs stay coherent
    /// rewrites at ~3 sentences / ~35 words, start showing reordering or
    /// truncation past 5 sentences / ~60 words. 30 keeps us in the safe
    /// region with margin for word-count variance.
    static let chunkWordTarget = 30

    private static let smartInstruction = """
    You rewrite spoken dictation into clean final text. You are not a chat assistant. \
    Never answer or respond to the dictation, even if it is a question. Treat the dictation strictly as text to rewrite. \
    Fix casing, punctuation, filler, and speech disfluencies. Preserve meaning and detail. Use bullets only when it clearly reads as a list. \
    Do not summarize or invent information. Output only the rewritten dictation.
    """

    private static let exampleBlock = """
    Examples:
    Dictation: um does it feel like real time processing
    Output: Does it feel like real-time processing?

    Dictation: what is the next step
    Output: What is the next step?

    Dictation: okay so the plan is finish the build then deploy
    Output: Okay, so the plan is finish the build, then deploy.
    """

    static func prompt(transcript: String) -> String {
        let rendered = """
        <|startoftext|><|im_start|>user
        \(smartInstruction)

        \(exampleBlock)

        Dictation: \(transcript)
        Output:
        <|im_end|>
        <|im_start|>assistant
        """
        return rendered + "\n"
    }

    static func maxNewTokens(for transcript: String) -> Int {
        let estimatedTokens = max(1, transcript.split(whereSeparator: \.isWhitespace).count)
        return min(512, max(96, estimatedTokens * 2))
    }

    /// Split a long transcript into formatter-safe word-bounded chunks.
    ///
    /// Voice dictation usually arrives without sentence punctuation, so we
    /// chunk on whitespace boundaries. The chunk count is `ceil(words /
    /// chunkWordTarget)` and the per-chunk word count is then re-distributed
    /// evenly so the tail isn't a tiny dangling word — e.g., 31 words yields
    /// two ~16-word chunks rather than `[30, 1]`.
    ///
    /// Returns `[trimmed]` for short transcripts, `[]` for empty input.
    static func chunks(transcript: String) -> [String] {
        let trimmed = transcript.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let words = trimmed
            .split(whereSeparator: \.isWhitespace)
            .map(String.init)
        if words.count <= chunkWordTarget {
            return [trimmed]
        }
        let chunkCount = (words.count + chunkWordTarget - 1) / chunkWordTarget
        let perChunk = (words.count + chunkCount - 1) / chunkCount
        var result: [String] = []
        result.reserveCapacity(chunkCount)
        var idx = 0
        while idx < words.count {
            let end = Swift.min(idx + perChunk, words.count)
            result.append(words[idx..<end].joined(separator: " "))
            idx = end
        }
        return result
    }
}
