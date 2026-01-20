/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule

/**
 * Sanity check test for model loading and generation.
 *
 * Model filenames can be configured via instrumentation arguments:
 * - modelFile: name of the .pte file (default: stories110M.pte)
 * - tokenizerFile: name of the tokenizer file (default: tokenizer.model)
 */
@RunWith(AndroidJUnit4::class)
class SanityCheck : LlmCallback {

    companion object {
        private const val RESOURCE_PATH = "/data/local/tmp/llama/"
        private const val DEFAULT_MODEL_FILE = "stories110M.pte"
        private const val DEFAULT_TOKENIZER_FILE = "tokenizer.model"
    }

    private lateinit var modelFile: String
    private lateinit var tokenizerFile: String
    private val results = mutableListOf<String>()

    @Before
    fun setUp() {
        // Read model filenames from instrumentation arguments
        val args = InstrumentationRegistry.getArguments()
        modelFile = args.getString("modelFile", DEFAULT_MODEL_FILE) ?: DEFAULT_MODEL_FILE
        tokenizerFile = args.getString("tokenizerFile", DEFAULT_TOKENIZER_FILE) ?: DEFAULT_TOKENIZER_FILE
        Log.i("SanityCheck", "Using model: $modelFile, tokenizer: $tokenizerFile")
    }

    @Test
    fun testLoadAndGenerate() {
        val tokenizerPath = RESOURCE_PATH + tokenizerFile
        val modelPath = RESOURCE_PATH + modelFile
        val module = LlmModule(modelPath, tokenizerPath, 0.8f)

        val loadResult = module.load()
        // Check that the model can be loaded successfully
        assertEquals(0, loadResult)

        // Run a testing prompt
        module.generate("How do you do! I'm testing llm on mobile device", this)

        // Verify we got some response
        assertFalse("Should receive at least one result token", results.isEmpty())
    }

    override fun onResult(result: String) {
        results.add(result)
    }

    override fun onStats(result: String) {
        // Not measuring performance for now
    }
}
