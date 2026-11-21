package com.mathpal.app.inference

import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class InferenceEngine(
    private val modelPath: String,
    private val tokenizerPath: String
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var llmModule: LlmModule? = null

    @Volatile
    var isLoaded: Boolean = false
        private set

    private fun ensureModule(): LlmModule {
        return llmModule ?: LlmModule(modelPath, tokenizerPath, TEMPERATURE).also {
            llmModule = it
        }
    }

    fun loadAsync(onComplete: (success: Boolean, durationMs: Long) -> Unit) {
        executor.execute {
            val startTime = System.currentTimeMillis()
            try {
                val module = ensureModule()
                module.load()
                isLoaded = true
                val duration = System.currentTimeMillis() - startTime
                onComplete(true, duration)
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                onComplete(false, duration)
            }
        }
    }

    fun generate(prompt: String, seqLen: Int, callback: LlmCallback) {
        executor.execute {
            try {
                val module = ensureModule()
                if (!isLoaded) {
                    module.load()
                    isLoaded = true
                }
                module.generate(prompt, seqLen, callback, false)
            } catch (e: Exception) {
                callback.onResult("")
            }
        }
    }

    fun stop() {
        llmModule?.stop()
    }

    fun resetContext() {
        llmModule?.resetContext()
    }

    fun release() {
        stop()
        executor.execute {
            llmModule?.resetNative()
            llmModule = null
            isLoaded = false
        }
        executor.shutdown()
    }

    companion object {
        private const val TEMPERATURE = 0.1f
    }
}
