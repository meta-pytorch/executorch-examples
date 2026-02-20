package com.example.asr

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

    var availablePreprocessors by mutableStateOf<List<String>>(emptyList())
        private set

    var availableDataFiles by mutableStateOf<List<String>>(emptyList())
        private set

    var availableWavFiles by mutableStateOf<List<String>>(emptyList())
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var defaultDirectory: String = ""
        private set

    private var supportsPreprocessor: Boolean = false
    private var appStorageDirectory: String? = null

    /**
     * Initialize the ViewModel with app-specific configuration.
     *
     * @param defaultDirectory The default directory to scan for model files
     *                         (e.g., "/data/local/tmp/whisper" or "/data/local/tmp/parakeet")
     * @param supportsPreprocessor Whether the app supports preprocessor file selection
     */
    fun initialize(defaultDirectory: String, supportsPreprocessor: Boolean = false) {
        this.defaultDirectory = defaultDirectory
        this.supportsPreprocessor = supportsPreprocessor
        refreshFileLists()
    }

    /**
     * Set the app-internal storage directory so we can scan it too.
     *
     * @param dir The full path to the app storage directory
     *            (e.g., "${filesDir}/whisper" or "${filesDir}/parakeet")
     */
    fun setAppStorageDirectory(dir: String) {
        appStorageDirectory = dir
        refreshFileLists()
    }

    /**
     * Scan all known directories for available model files.
     */
    fun refreshFileLists() {
        val directories = buildList {
            if (defaultDirectory.isNotEmpty()) add(defaultDirectory)
            appStorageDirectory?.let { add(it) }
        }

        availableModels = listLocalFilesFromDirs(directories, ModelSettings.MODEL_EXTENSIONS)
        availableTokenizers = listLocalFilesFromDirs(directories, ModelSettings.TOKENIZER_EXTENSIONS)
        if (supportsPreprocessor) {
            availablePreprocessors = listLocalFilesFromDirs(directories, ModelSettings.MODEL_EXTENSIONS)
        }
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

    fun selectPreprocessor(path: String) {
        modelSettings = modelSettings.copy(preprocessorPath = path)
    }

    fun selectDataFile(path: String) {
        modelSettings = modelSettings.copy(dataPath = path)
    }

    fun clearPreprocessor() {
        selectPreprocessor("")
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
