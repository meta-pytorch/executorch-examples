/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Testing

struct FormatterPromptBuilderTests {
    @Test
    func smartPromptInstructsRewriteAndForbidsAnswering() {
        let prompt = FormatterPromptBuilder.prompt(
            transcript: "um please clean this up"
        )

        #expect(prompt.contains("You rewrite spoken dictation into clean final text."))
        #expect(prompt.contains("You are not a chat assistant."))
        #expect(prompt.contains("Never answer or respond to the dictation"))
        #expect(prompt.contains("Fix casing, punctuation, filler, and speech disfluencies."))
        #expect(prompt.contains("Preserve meaning and detail."))
        #expect(prompt.contains("Use bullets only when it clearly reads as a list."))
        #expect(prompt.contains("Do not summarize or invent information."))
        #expect(prompt.contains("Output only the rewritten dictation."))
        #expect(!prompt.contains("Mode:"))
        #expect(prompt.contains("um please clean this up"))
        #expect(prompt.hasSuffix("<|im_start|>assistant\n"))
    }

    @Test
    func smartPromptIncludesQuestionExampleSoModelDoesNotAnswerQuestions() {
        let prompt = FormatterPromptBuilder.prompt(
            transcript: "does it feel like real-time processing?"
        )

        #expect(prompt.contains("Examples:"))
        #expect(prompt.contains("Dictation: um does it feel like real time processing"))
        #expect(prompt.contains("Output: Does it feel like real-time processing?"))
        #expect(prompt.contains("Dictation: does it feel like real-time processing?"))
        #expect(prompt.contains("Output:"))
    }

    @Test
    func smartPromptDoesNotContainLegacyModeInstructions() {
        let prompt = FormatterPromptBuilder.prompt(
            transcript: "new section launch notes bullet first item"
        )

        #expect(!prompt.contains("Mode: Bullet Notes"))
        #expect(!prompt.contains("Write the final email body."))
        #expect(!prompt.contains("Custom rewrite instruction:"))
        #expect(!prompt.contains("Meeting Notes"))
        #expect(!prompt.contains("Action Items"))
        #expect(!prompt.contains("Summary"))
    }

    @Test
    func smartPromptCanGuideListLikeDictationWithoutDedicatedMode() {
        let prompt = FormatterPromptBuilder.prompt(
            transcript: "todo update helper docs test formatter download follow up with Alex"
        )

        #expect(prompt.contains("Use bullets only when it clearly reads as a list."))
        #expect(prompt.contains("todo update helper docs test formatter download follow up with Alex"))
        #expect(!prompt.contains("Mode: Bullet Notes"))
        #expect(!prompt.contains("recipient"))
        #expect(!prompt.contains("signoff"))
    }

    @Test
    func smartPromptCanHandleSpokenEmailIntentWithoutDedicatedMode() {
        let prompt = FormatterPromptBuilder.prompt(
            transcript: "turn this into an email thanks for the update"
        )

        #expect(prompt.contains("You rewrite spoken dictation into clean final text."))
        #expect(prompt.contains("turn this into an email thanks for the update"))
        #expect(!prompt.contains("Mode: Email"))
        #expect(!prompt.contains("Write the final email body."))
    }

    @Test
    func smartPromptIgnoresRemovedCustomInstructionSurface() {
        let prompt = FormatterPromptBuilder.prompt(
            transcript: "write this for a design review"
        )

        #expect(!prompt.contains("Mode: Custom"))
        #expect(!prompt.contains("Custom rewrite instruction:"))
        #expect(!prompt.contains("Make this sound crisp and decisive."))
        #expect(prompt.contains("Preserve meaning and detail."))
        #expect(prompt.contains("Output only the rewritten dictation."))
    }

    @Test
    func promptPreservesRawTranscriptVerbatim() {
        let transcript = "line one\n\"quoted text\"\nnew paragraph"

        let prompt = FormatterPromptBuilder.prompt(
            transcript: transcript
        )

        #expect(prompt.contains(transcript))
        #expect(!prompt.contains("Transcript:\n\"\"\""))
    }

    @Test
    func maxNewTokensScalesWithTranscriptLengthWithinBounds() {
        #expect(FormatterPromptBuilder.maxNewTokens(for: "short text") == 96)

        let longTranscript = Array(repeating: "word", count: 400).joined(separator: " ")
        #expect(FormatterPromptBuilder.maxNewTokens(for: longTranscript) == 512)
    }

    @Test
    func chunksReturnsEmptyForEmptyOrWhitespaceTranscript() {
        #expect(FormatterPromptBuilder.chunks(transcript: "").isEmpty)
        #expect(FormatterPromptBuilder.chunks(transcript: "   \n\t  ").isEmpty)
    }

    @Test
    func chunksReturnsSingleChunkWhenUnderTarget() {
        let transcript = "hello can you hear me i am smart"
        let chunks = FormatterPromptBuilder.chunks(transcript: transcript)
        #expect(chunks == [transcript])
    }

    @Test
    func chunksReturnsSingleChunkAtExactlyTargetWordCount() {
        let words = (1...FormatterPromptBuilder.chunkWordTarget).map { "word\($0)" }
        let transcript = words.joined(separator: " ")
        let chunks = FormatterPromptBuilder.chunks(transcript: transcript)
        #expect(chunks.count == 1)
        #expect(chunks[0] == transcript)
    }

    @Test
    func chunksDistributesEvenlyToAvoidTinyTail() {
        // 31 words should split into two ~16-word chunks rather than [30, 1].
        let words = (1...FormatterPromptBuilder.chunkWordTarget + 1).map { "word\($0)" }
        let transcript = words.joined(separator: " ")
        let chunks = FormatterPromptBuilder.chunks(transcript: transcript)
        #expect(chunks.count == 2)
        let counts = chunks.map { $0.split(whereSeparator: \.isWhitespace).count }
        #expect(counts.allSatisfy { $0 >= 15 && $0 <= 16 })
        // Concatenating chunks back recovers the original transcript word-for-word.
        let rejoined = chunks.joined(separator: " ")
        #expect(rejoined == transcript)
    }

    @Test
    func chunksProducesBoundedChunksAt100Sentences() {
        // ~1200 words is the upper-bound the product targets (≈100 sentences
        // of dictation). Each chunk should still be at or under
        // chunkWordTarget so the formatter sees only short prompts.
        let words = (1...1200).map { "word\($0)" }
        let transcript = words.joined(separator: " ")
        let chunks = FormatterPromptBuilder.chunks(transcript: transcript)
        let target = FormatterPromptBuilder.chunkWordTarget
        #expect(chunks.count >= words.count / target)
        for chunk in chunks {
            let count = chunk.split(whereSeparator: \.isWhitespace).count
            #expect(count <= target)
        }
        // Words are preserved in order across chunks.
        let rejoined = chunks.joined(separator: " ")
        #expect(rejoined == transcript)
    }

    @Test
    func chunksTrimsLeadingAndTrailingWhitespaceForSingleChunk() {
        let chunks = FormatterPromptBuilder.chunks(
            transcript: "  hello can you hear me  "
        )
        #expect(chunks == ["hello can you hear me"])
    }
}
