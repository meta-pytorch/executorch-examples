package com.example.whisperapp

import android.content.Context
import android.content.SharedPreferences

/**
 * Manager class for persisting model settings using SharedPreferences.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Save model settings to persistent storage.
     */
    fun saveSettings(settings: ModelSettings) {
        prefs.edit().apply {
            putString(KEY_MODEL_PATH, settings.modelPath)
            putString(KEY_TOKENIZER_PATH, settings.tokenizerPath)
            putString(KEY_PREPROCESSOR_PATH, settings.preprocessorPath)
            putString(KEY_DATA_PATH, settings.dataPath)
            apply()
        }
    }

    /**
     * Load model settings from persistent storage.
     */
    fun loadSettings(): ModelSettings {
        return ModelSettings(
            modelPath = prefs.getString(KEY_MODEL_PATH, "") ?: "",
            tokenizerPath = prefs.getString(KEY_TOKENIZER_PATH, "") ?: "",
            preprocessorPath = prefs.getString(KEY_PREPROCESSOR_PATH, "") ?: "",
            dataPath = prefs.getString(KEY_DATA_PATH, "") ?: ""
        )
    }

    /**
     * Clear all saved settings.
     */
    fun clearSettings() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "whisper_settings"
        private const val KEY_MODEL_PATH = "model_path"
        private const val KEY_TOKENIZER_PATH = "tokenizer_path"
        private const val KEY_PREPROCESSOR_PATH = "preprocessor_path"
        private const val KEY_DATA_PATH = "data_path"
    }
}
