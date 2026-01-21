/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

/**
 * Defines the media capabilities supported by a model.
 */
enum class MediaCapability {
    TEXT,      // Text-only models
    IMAGE,     // Image input support
    AUDIO      // Audio input support
}

enum class ModelType(
    private val patterns: Array<String>,
    val mediaCapabilities: Set<MediaCapability>
) {
    GEMMA_3(
        arrayOf("gemma"),
        setOf(MediaCapability.TEXT, MediaCapability.IMAGE)
    ),
    LLAMA_3(
        arrayOf("llama"),
        setOf(MediaCapability.TEXT)
    ),
    LLAVA_1_5(
        arrayOf("llava"),
        setOf(MediaCapability.TEXT, MediaCapability.IMAGE)
    ),
    LLAMA_GUARD_3(
        arrayOf("llama_guard", "llama-guard", "llamaguard"),
        setOf(MediaCapability.TEXT)
    ),
    QWEN_3(
        arrayOf("qwen"),
        setOf(MediaCapability.TEXT)
    ),
    VOXTRAL(
        arrayOf("voxtral"),
        setOf(MediaCapability.TEXT, MediaCapability.AUDIO)
    );

    /**
     * Checks if this model supports image input.
     */
    fun supportsImage(): Boolean = mediaCapabilities.contains(MediaCapability.IMAGE)

    /**
     * Checks if this model supports audio input.
     */
    fun supportsAudio(): Boolean = mediaCapabilities.contains(MediaCapability.AUDIO)

    /**
     * Checks if this model is text-only (no image or audio support).
     */
    fun isTextOnly(): Boolean = mediaCapabilities.size == 1 && mediaCapabilities.contains(MediaCapability.TEXT)

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
            val fileName = filePath.substringAfterLast('/')
            val lowerFileName = fileName.lowercase()

            // Check more specific patterns first (LLAMA_GUARD before LLAMA, LLAVA before LLAMA)
            if (LLAMA_GUARD_3.matchesFileName(lowerFileName)) {
                return LLAMA_GUARD_3
            }
            if (LLAVA_1_5.matchesFileName(lowerFileName)) {
                return LLAVA_1_5
            }

            // Check remaining types
            return values().firstOrNull { type ->
                type != LLAMA_GUARD_3 && type != LLAVA_1_5 && type.matchesFileName(lowerFileName)
            }
        }
    }
}
