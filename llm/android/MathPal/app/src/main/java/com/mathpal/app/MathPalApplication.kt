package com.mathpal.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.mathpal.app.inference.InferenceEngine

class MathPalApplication : Application() {

    lateinit var inferenceEngine: InferenceEngine
        private set

    var isModelLoaded = false
        private set

    override fun onCreate() {
        super.onCreate()
        inferenceEngine = InferenceEngine(
            modelPath = "/data/local/tmp/llama/model.pte",
            tokenizerPath = "/data/local/tmp/llama/tokenizer.json",
        )
    }

    fun loadModel(onComplete: (Boolean, Long) -> Unit) {
        inferenceEngine.loadAsync { success, durationMs ->
            isModelLoaded = success
            onComplete(success, durationMs)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!::inferenceEngine.isInitialized) return
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                inferenceEngine.resetContext()
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                inferenceEngine.release()
                isModelLoaded = false
            }
        }
    }
}
