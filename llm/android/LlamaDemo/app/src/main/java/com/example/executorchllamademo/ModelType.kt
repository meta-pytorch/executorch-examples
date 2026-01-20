/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

enum class ModelType(private vararg val patterns: String) {
    GEMMA_3("gemma"),
    LLAMA_3("llama"),
    LLAVA_1_5("llava"),
    LLAMA_GUARD_3("llama_guard", "llama-guard", "llamaguard"),
    QWEN_3("qwen"),
    VOXTRAL("voxtral");

    /**
     * Returns a copy of the file name patterns associated with this model type.
     */
    fun getPatterns(): Array<String> = patterns.toList().toTypedArray()

    /**
     * Checks if the given lowercase filename contains any of this model type's patterns.
     */
    private fun matchesFileName(lowerFileName: String): Boolean {
        return patterns.any { lowerFileName.contains(it) }
    }

    companion object {
        /**
         * Detects the ModelType from a file path based on partial matches in the filename.
         * Returns null if no match is found.
         */
        @JvmStatic
        fun fromFilePath(filePath: String?): ModelType? {
            if (filePath.isNullOrEmpty()) {
                return null
            }
            // Extract just the filename from the path
            val lastSeparatorIndex = filePath.lastIndexOf('/')
            val fileName = if (lastSeparatorIndex >= 0 && lastSeparatorIndex < filePath.length - 1) {
                filePath.substring(lastSeparatorIndex + 1)
            } else {
                filePath
            }
            val lowerFileName = fileName.lowercase()
            
            // Check more specific patterns first (LLAMA_GUARD before LLAMA, LLAVA before LLAMA)
            if (LLAMA_GUARD_3.matchesFileName(lowerFileName)) {
                return LLAMA_GUARD_3
            }
            if (LLAVA_1_5.matchesFileName(lowerFileName)) {
                return LLAVA_1_5
            }
            // Check remaining types
            for (type in values()) {
                if (type != LLAMA_GUARD_3 && type != LLAVA_1_5 && type.matchesFileName(lowerFileName)) {
                    return type
                }
            }
            return null
        }
    }
}
