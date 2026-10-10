package com.mathpal.app.inference

object PromptFormatter {

    private const val SYSTEM_PROMPT =
        "You are a helpful math tutor for 8th graders. " +
        "Solve problems step by step. " +
        "Put your final numeric answer after ####."

    fun formatMathPrompt(question: String): String =
        "<|im_start|>system\n" +
        "$SYSTEM_PROMPT<|im_end|>\n" +
        "<|im_start|>user\n" +
        "$question<|im_end|>\n" +
        "<|im_start|>assistant\n"

    fun formatFollowUp(question: String): String =
        "<|im_start|>user\n" +
        "$question<|im_end|>\n" +
        "<|im_start|>assistant\n"
}
