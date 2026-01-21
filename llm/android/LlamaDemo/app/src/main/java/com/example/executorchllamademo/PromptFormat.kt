/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

object PromptFormat {
    const val SYSTEM_PLACEHOLDER = "{{ system_prompt }}"
    const val USER_PLACEHOLDER = "{{ user_prompt }}"
    const val ASSISTANT_PLACEHOLDER = "{{ assistant_response }}"
    const val THINKING_MODE_PLACEHOLDER = "{{ thinking_mode }}"
    const val DEFAULT_SYSTEM_PROMPT = "Answer the questions in a few sentences"

    @JvmStatic
    fun getSystemPromptTemplate(modelType: ModelType?): String {
        return when (modelType) {
            ModelType.LLAMA_3 -> "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n" +
                    SYSTEM_PLACEHOLDER +
                    "<|eot_id|>"
            ModelType.QWEN_3 -> "<|im_start|>system\n$SYSTEM_PLACEHOLDER<|im_end|>\n"
            else -> SYSTEM_PLACEHOLDER
        }
    }

    @JvmStatic
    fun getUserPromptTemplate(modelType: ModelType?): String {
        return when (modelType) {
            ModelType.GEMMA_3 -> "<start_of_turn>user\n$USER_PLACEHOLDER<end_of_turn>\n<start_of_turn>model"
            ModelType.LLAMA_3, ModelType.LLAMA_GUARD_3 -> 
                "<|start_header_id|>user<|end_header_id|>\n" +
                USER_PLACEHOLDER +
                "<|eot_id|>" +
                "<|start_header_id|>assistant<|end_header_id|>\n"
            ModelType.QWEN_3 -> 
                "<|im_start|>user\n" +
                USER_PLACEHOLDER +
                "<|im_end|>\n" +
                "<|im_start|>assistant\n" +
                THINKING_MODE_PLACEHOLDER
            ModelType.LLAVA_1_5 -> " USER: $USER_PLACEHOLDER ASSISTANT:"
            else -> USER_PLACEHOLDER
        }
    }

    @JvmStatic
    fun getStopToken(modelType: ModelType?): String {
        return when (modelType) {
            ModelType.GEMMA_3 -> "<end_of_turn>"
            ModelType.LLAMA_3, ModelType.LLAMA_GUARD_3 -> "<|eot_id|>"
            ModelType.LLAVA_1_5 -> "</s>"
            ModelType.QWEN_3 -> "<|im_end|>"
            ModelType.VOXTRAL -> "</s>"
            else -> ""
        }
    }

    @JvmStatic
    fun getThinkingModeToken(modelType: ModelType?, thinkingMode: Boolean): String {
        return when (modelType) {
            ModelType.QWEN_3 -> if (thinkingMode) "" else "<think>\n\n</think>\n\n\n"
            else -> ""
        }
    }

    @JvmStatic
    fun replaceSpecialToken(modelType: ModelType?, token: String): String {
        return when (modelType) {
            ModelType.QWEN_3 -> when (token) {
                "<|im_end|>" -> ""
                "<think>" -> "Thinking...\n"
                "</think>" -> "\nDone thinking"
                else -> token
            }
            else -> token
        }
    }

    @JvmStatic
    fun getLlavaPresetPrompt(): String {
        return "A chat between a curious human and an artificial intelligence assistant. The assistant" +
                " gives helpful, detailed, and polite answers to the human's questions. USER: "
    }

    @JvmStatic
    fun getLlavaFirstTurnUserPrompt(): String {
        return "$USER_PLACEHOLDER ASSISTANT:"
    }

    @JvmStatic
    fun getFormattedLlamaGuardPrompt(userPrompt: String): String {
        return getUserPromptTemplate(ModelType.LLAMA_GUARD_3)
            .replace(USER_PLACEHOLDER, getLlamaGuardPresetPrompt().replace(USER_PLACEHOLDER, userPrompt))
    }

    @JvmStatic
    fun getLlamaGuardPresetPrompt(): String {
        return """Task: Check if there is unsafe content in 'User' messages in conversations according our safety policy with the below categories.

<BEGIN UNSAFE CONTENT CATEGORIES>
S1: Violent Crimes. 
S2: Non-Violent Crimes. 
S3: Sex Crimes. 
S4: Child Exploitation. 
S5: Defamation. 
S6: Specialized Advice. 
S7: Privacy. 
S8: Intellectual Property. 
S9: Indiscriminate Weapons. 
S10: Hate. 
S11: Self-Harm. 
S12: Sexual Content. 
S13: Elections. 
S14: Code Interpreter Abuse.
<END UNSAFE CONTENT CATEGORIES>

<BEGIN CONVERSATION>

User: $USER_PLACEHOLDER
<END CONVERSATION>
Provide your safety assessment for ONLY THE LAST User message in the above conversation:
 - First line must read 'safe' or 'unsafe'.
 - If unsafe, a second line must include a comma-separated list of violated categories."""
    }
}
