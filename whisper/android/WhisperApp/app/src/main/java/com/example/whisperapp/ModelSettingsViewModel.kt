package com.example.whisperapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.io.File

/**
 * ViewModel for managing model file selection and settings.
 */
class ModelSettingsViewModel : ViewModel() {

    var modelSettings by mutableStateOf(ModelSettings())
        private set

    var availableModels by mutableStateOf<List<String>>(emptyList())
        private set

    var availableTokenizers by mutableStateOf<List<String>>(emptyList())
        private set

    var availablePreprocessors by mutableStateOf<List<String>>(emptyList())
        private set

    var availableDataFiles by mutableStateOf<List<String>>(emptyList())
        private set

    var availableWavFiles by mutableStateOf<List<String>>(emptyList())
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var settingsManager: SettingsManager? = null

    /**
     * Initialize the ViewModel with context-dependent components.
     */
    fun initialize(settingsManager: SettingsManager) {
        this.settingsManager = settingsManager
        modelSettings = settingsManager.loadSettings()
        refreshFileLists()
    }

    /**
     * Scan the default directory for available model files.
     */
    fun refreshFileLists() {
        val directory = ModelSettings.DEFAULT_DIRECTORY

        availableModels = listLocalFiles(directory, ModelSettings.MODEL_EXTENSIONS)
        availableTokenizers = listLocalFiles(directory, ModelSettings.TOKENIZER_EXTENSIONS)
        availablePreprocessors = listLocalFiles(directory, ModelSettings.MODEL_EXTENSIONS)
        availableDataFiles = listLocalFiles(directory, ModelSettings.DATA_EXTENSIONS)
        availableWavFiles = listLocalFiles(directory, WAV_EXTENSIONS)

        // Clear error if files are found
        if (availableModels.isNotEmpty() || availableTokenizers.isNotEmpty()) {
            errorMessage = null
        }
    }

    /**
     * Select a model file.
     */
    fun selectModel(path: String) {
        modelSettings = modelSettings.copy(modelPath = path)
        saveSettings()
    }

    /**
     * Select a tokenizer file.
     */
    fun selectTokenizer(path: String) {
        modelSettings = modelSettings.copy(tokenizerPath = path)
        saveSettings()
    }

    /**
     * Select a preprocessor file. Pass empty string to clear.
     */
    fun selectPreprocessor(path: String) {
        modelSettings = modelSettings.copy(preprocessorPath = path)
        saveSettings()
    }

    /**
     * Select a data file. Pass empty string to clear.
     */
    fun selectDataFile(path: String) {
        modelSettings = modelSettings.copy(dataPath = path)
        saveSettings()
    }

    /**
     * Clear the preprocessor selection.
     */
    fun clearPreprocessor() {
        selectPreprocessor("")
    }

    /**
     * Clear the data file selection.
     */
    fun clearDataFile() {
        selectDataFile("")
    }

    private fun saveSettings() {
        settingsManager?.saveSettings(modelSettings)
    }

    /**
     * Check if the current settings are valid for inference.
     */
    fun isReadyForInference(): Boolean {
        return modelSettings.isValid()
    }

    companion object {
        val WAV_EXTENSIONS = arrayOf(".wav")

        /**
         * List files in the given directory matching the specified extensions.
         */
        fun listLocalFiles(path: String, extensions: Array<String>): List<String> {
            val directory = File(path)
            if (!directory.exists() || !directory.isDirectory) {
                return emptyList()
            }

            return directory.listFiles { _, name ->
                extensions.any { ext -> name.endsWith(ext, ignoreCase = true) }
            }?.filter { it.isFile }
                ?.map { it.absolutePath }
                ?.sorted()
                ?: emptyList()
        }
    }
}
