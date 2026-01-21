/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import org.json.JSONException
import org.json.JSONObject
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule

/**
 * A helper class to handle all model running logic.
 * Separates UI logic from model runner logic and automatically handles
 * generate() requests on a worker thread.
 *
 * @param modelFilePath Path to the model file
 * @param tokenizerFilePath Path to the tokenizer file
 * @param temperature Temperature for generation (currently uses fixed 0.8f)
 * @param callback Callback for model events
 */
class ModelRunner(
    private val modelFilePath: String,
    private val tokenizerFilePath: String,
    temperature: Float,
    private val callback: ModelRunnerCallback
) : LlmCallback {

    val module: LlmModule = LlmModule(modelFilePath, tokenizerFilePath, 0.8f)

    private val handlerThread: HandlerThread = HandlerThread("ModelRunner").apply { start() }
    private val handler: Handler = ModelRunnerHandler(handlerThread.looper, this)

    init {
        handler.sendEmptyMessage(ModelRunnerHandler.MESSAGE_LOAD_MODEL)
    }

    fun generate(prompt: String): Int {
        val msg = Message.obtain(handler, ModelRunnerHandler.MESSAGE_GENERATE, prompt)
        msg.sendToTarget()
        return 0
    }

    fun stop() {
        module.stop()
    }

    override fun onResult(result: String) {
        callback.onTokenGenerated(result)
    }

    override fun onStats(stats: String) {
        var tps = 0f
        try {
            val jsonObject = JSONObject(stats)
            val numGeneratedTokens = jsonObject.getInt("generated_tokens")
            val inferenceEndMs = jsonObject.getInt("inference_end_ms")
            val promptEvalEndMs = jsonObject.getInt("prompt_eval_end_ms")
            tps = numGeneratedTokens.toFloat() / (inferenceEndMs - promptEvalEndMs) * 1000
        } catch (e: JSONException) {
            // Ignore parsing errors
        }
        callback.onStats("tokens/second: $tps")
    }

    /**
     * Internal handler for processing model operations on background thread.
     */
    private class ModelRunnerHandler(
        looper: Looper,
        private val modelRunner: ModelRunner
    ) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_LOAD_MODEL -> {
                    val status = modelRunner.module.load()
                    modelRunner.callback.onModelLoaded(status)
                }
                MESSAGE_GENERATE -> {
                    modelRunner.module.generate(msg.obj as String, modelRunner)
                    modelRunner.callback.onGenerationStopped()
                }
            }
        }

        companion object {
            const val MESSAGE_LOAD_MODEL = 1
            const val MESSAGE_GENERATE = 2
        }
    }
}
