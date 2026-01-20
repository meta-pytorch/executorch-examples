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
import org.json.JSONException
import org.json.JSONObject
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule

/**
 * A helper class to handle all model running logic within this class.
 */
class ModelRunner(
    private val modelFilePath: String,
    private val tokenizerFilePath: String,
    temperature: Float,
    private val callback: ModelRunnerCallback
) : LlmCallback {

    var module: LlmModule? = null
        private set

    private val handlerThread: HandlerThread = HandlerThread("ModelRunner")
    private val handler: Handler

    init {
        module = LlmModule(modelFilePath, tokenizerFilePath, 0.8f)
        handlerThread.start()
        handler = ModelRunnerHandler(handlerThread.looper, this)
        handler.sendEmptyMessage(MESSAGE_LOAD_MODEL)
    }

    fun generate(prompt: String): Int {
        val msg = android.os.Message.obtain(handler, MESSAGE_GENERATE, prompt)
        msg.sendToTarget()
        return 0
    }

    fun stop() {
        module?.stop()
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
            // Ignore parse errors
        }
        callback.onStats("tokens/second: $tps")
    }

    companion object {
        const val MESSAGE_LOAD_MODEL = 1
        const val MESSAGE_GENERATE = 2
    }

    private class ModelRunnerHandler(
        looper: Looper,
        private val modelRunner: ModelRunner
    ) : Handler(looper) {

        override fun handleMessage(msg: android.os.Message) {
            when (msg.what) {
                MESSAGE_LOAD_MODEL -> {
                    val status = modelRunner.module?.load() ?: -1
                    modelRunner.callback.onModelLoaded(status)
                }
                MESSAGE_GENERATE -> {
                    modelRunner.module?.generate(msg.obj as String, modelRunner)
                    modelRunner.callback.onGenerationStopped()
                }
            }
        }
    }
}
