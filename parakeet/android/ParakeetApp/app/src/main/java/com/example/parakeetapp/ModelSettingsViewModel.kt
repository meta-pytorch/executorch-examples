package com.example.parakeetapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.io.File

/**
 * ViewModel for managing model file selection and settings.
 * Settings are kept in memory only (not persisted).
 */
class ModelSettingsViewModel : ViewModel() {

    var modelSettings by mutableStateOf(ModelSettings())
        private set

    var availableModels by mutableStateOf<List<String>>(emptyList())
        private set

    var availableTokenizers by mutableStateOf<List<String>>(emptyList())
        private set

    var availableDataFiles by mutableStateOf<List<String>>(emptyList())
        private set

    var availableWavFiles by mutableStateOf<List<String>>(emptyList())
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var appStorageDirectory: String? = null

    /**
     * Initialize the ViewModel by scanning for available files.
     */
    fun initialize() {
        refreshFileLists()
    }

    /**
     * Set the app-internal storage directory (filesDir/parakeet) so we can scan it too.
     */
    fun setAppStorageDirectory(filesDir: String) {
        appStorageDirectory = "$filesDir/parakeet"
        refreshFileLists()
    }

    /**
     * Scan all known directories for available model files.
     */
    fun refreshFileLists() {
        val directories = buildList {
            add(ModelSettings.DEFAULT_DIRECTORY)
            appStorageDirectory?.let { add(it) }
        }

        availableModels = listLocalFilesFromDirs(directories, ModelSettings.MODEL_EXTENSIONS)
        availableTokenizers = listLocalFilesFromDirs(directories, ModelSettings.TOKENIZER_EXTENSIONS)
        availableDataFiles = listLocalFilesFromDirs(directories, ModelSettings.DATA_EXTENSIONS)
        availableWavFiles = listLocalFilesFromDirs(directories, WAV_EXTENSIONS)

        if (availableModels.isNotEmpty() || availableTokenizers.isNotEmpty()) {
            errorMessage = null
        }
    }

    fun selectModel(path: String) {
        modelSettings = modelSettings.copy(modelPath = path)
    }

    fun selectTokenizer(path: String) {
        modelSettings = modelSettings.copy(tokenizerPath = path)
    }

    fun selectDataFile(path: String) {
        modelSettings = modelSettings.copy(dataPath = path)
    }

    fun clearDataFile() {
        selectDataFile("")
    }

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

        /**
         * List files from multiple directories, deduplicated by absolute path.
         */
        fun listLocalFilesFromDirs(dirs: List<String>, extensions: Array<String>): List<String> {
            return dirs.flatMap { listLocalFiles(it, extensions) }
                .distinct()
                .sorted()
        }
    }
}
