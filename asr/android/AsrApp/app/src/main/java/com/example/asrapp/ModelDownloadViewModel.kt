package com.example.asrapp

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class FileType { MODEL, TOKENIZER, PREPROCESSOR }

data class PresetFile(
    val url: String,
    val filename: String,
    val description: String,
    val type: FileType
)

data class AsrModelPreset(
    val id: String,
    val displayName: String,
    val modelType: ModelType,
    val files: List<PresetFile>
)

enum class DownloadStatus {
    NOT_STARTED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

class ModelDownloadViewModel : ViewModel() {

    companion object {
        private const val TAG = "ModelDownloadViewModel"
        private const val MODELS_SUBDIRECTORY = "asr"

        // Whisper URLs
        private const val TINY_INT8_URL =
            "https://huggingface.co/larryliu0820/whisper-tiny-INT8-INT4-ExecuTorch-XNNPACK/resolve/main"
        private const val SMALL_INT8_URL =
            "https://huggingface.co/larryliu0820/whisper-small-INT8-INT4-ExecuTorch-XNNPACK/resolve/main"
        private const val MEDIUM_INT8_URL =
            "https://huggingface.co/larryliu0820/whisper-medium-INT8-INT4-ExecuTorch-XNNPACK/resolve/main"
        private const val TINY_FP32_URL =
            "https://huggingface.co/larryliu0820/whisper-tiny-ExecuTorch-XNNPACK/resolve/main"
        private const val SMALL_FP32_URL =
            "https://huggingface.co/larryliu0820/whisper-small-ExecuTorch-XNNPACK/resolve/main"
        private const val MEDIUM_FP32_URL =
            "https://huggingface.co/larryliu0820/whisper-medium-ExecuTorch-XNNPACK/resolve/main"

        // Parakeet URLs
        private const val PARAKEET_BASE_URL =
            "https://huggingface.co/larryliu0820/parakeet-tdt-0.6b-v3-executorch/resolve/main/xnnpack/int4"

        // Shared Whisper files (tokenizer + preprocessor)
        private val WHISPER_TOKENIZER = PresetFile(
            url = "$TINY_INT8_URL/tokenizer.json",
            filename = "tokenizer.json",
            description = "Tokenizer",
            type = FileType.TOKENIZER
        )
        private val WHISPER_PREPROCESSOR = PresetFile(
            url = "$TINY_INT8_URL/whisper_preprocessor.pte",
            filename = "whisper_preprocessor.pte",
            description = "Preprocessor",
            type = FileType.PREPROCESSOR
        )

        val MODEL_PRESETS = listOf(
            // Whisper presets
            AsrModelPreset(
                id = "whisper_tiny_int8",
                displayName = "Whisper Tiny (INT8/INT4)",
                modelType = ModelType.WHISPER,
                files = listOf(
                    PresetFile(
                        url = "$TINY_INT8_URL/model.pte",
                        filename = "whisper_tiny_int8int4.pte",
                        description = "Whisper Tiny INT8/INT4 Model",
                        type = FileType.MODEL
                    ),
                    WHISPER_TOKENIZER,
                    WHISPER_PREPROCESSOR
                )
            ),
            AsrModelPreset(
                id = "whisper_small_int8",
                displayName = "Whisper Small (INT8/INT4)",
                modelType = ModelType.WHISPER,
                files = listOf(
                    PresetFile(
                        url = "$SMALL_INT8_URL/model.pte",
                        filename = "whisper_small_int8int4.pte",
                        description = "Whisper Small INT8/INT4 Model",
                        type = FileType.MODEL
                    ),
                    WHISPER_TOKENIZER,
                    WHISPER_PREPROCESSOR
                )
            ),
            AsrModelPreset(
                id = "whisper_medium_int8",
                displayName = "Whisper Medium (INT8/INT4)",
                modelType = ModelType.WHISPER,
                files = listOf(
                    PresetFile(
                        url = "$MEDIUM_INT8_URL/model.pte",
                        filename = "whisper_medium_int8int4.pte",
                        description = "Whisper Medium INT8/INT4 Model",
                        type = FileType.MODEL
                    ),
                    WHISPER_TOKENIZER,
                    WHISPER_PREPROCESSOR
                )
            ),
            AsrModelPreset(
                id = "whisper_tiny_fp32",
                displayName = "Whisper Tiny (FP32)",
                modelType = ModelType.WHISPER,
                files = listOf(
                    PresetFile(
                        url = "$TINY_FP32_URL/model.pte",
                        filename = "whisper_tiny_fp32.pte",
                        description = "Whisper Tiny FP32 Model",
                        type = FileType.MODEL
                    ),
                    WHISPER_TOKENIZER,
                    WHISPER_PREPROCESSOR
                )
            ),
            AsrModelPreset(
                id = "whisper_small_fp32",
                displayName = "Whisper Small (FP32)",
                modelType = ModelType.WHISPER,
                files = listOf(
                    PresetFile(
                        url = "$SMALL_FP32_URL/model.pte",
                        filename = "whisper_small_fp32.pte",
                        description = "Whisper Small FP32 Model",
                        type = FileType.MODEL
                    ),
                    WHISPER_TOKENIZER,
                    WHISPER_PREPROCESSOR
                )
            ),
            AsrModelPreset(
                id = "whisper_medium_fp32",
                displayName = "Whisper Medium (FP32)",
                modelType = ModelType.WHISPER,
                files = listOf(
                    PresetFile(
                        url = "$MEDIUM_FP32_URL/model.pte",
                        filename = "whisper_medium_fp32.pte",
                        description = "Whisper Medium FP32 Model",
                        type = FileType.MODEL
                    ),
                    WHISPER_TOKENIZER,
                    WHISPER_PREPROCESSOR
                )
            ),
            // Parakeet preset
            AsrModelPreset(
                id = "parakeet_int4",
                displayName = "Parakeet TDT 0.6B (INT4)",
                modelType = ModelType.PARAKEET,
                files = listOf(
                    PresetFile(
                        url = "$PARAKEET_BASE_URL/model.pte",
                        filename = "parakeet_int4.pte",
                        description = "Parakeet TDT 0.6B INT4 Model",
                        type = FileType.MODEL
                    ),
                    PresetFile(
                        url = "$PARAKEET_BASE_URL/tokenizer.model",
                        filename = "parakeet_tokenizer.model",
                        description = "Tokenizer",
                        type = FileType.TOKENIZER
                    )
                )
            )
        )
    }

    var downloadStatus by mutableStateOf(DownloadStatus.NOT_STARTED)
        private set

    var downloadProgress by mutableFloatStateOf(0f)
        private set

    var currentFileIndex by mutableIntStateOf(0)
        private set

    var totalFileCount by mutableIntStateOf(0)
        private set

    var currentFileName by mutableStateOf("")
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var selectedPresetIndex by mutableIntStateOf(0)
        private set

    private lateinit var modelsDir: String

    fun initialize(filesDir: String) {
        modelsDir = filesDir + "/" + MODELS_SUBDIRECTORY
    }

    fun getModelDir(): String = modelsDir

    fun selectPreset(index: Int) {
        selectedPresetIndex = index
    }

    fun getSelectedPreset(): AsrModelPreset = MODEL_PRESETS[selectedPresetIndex]

    fun getModelPath(): String {
        val file = getSelectedPreset().files.first { it.type == FileType.MODEL }
        return "$modelsDir/${file.filename}"
    }

    fun getTokenizerPath(): String {
        val file = getSelectedPreset().files.first { it.type == FileType.TOKENIZER }
        return "$modelsDir/${file.filename}"
    }

    fun getPreprocessorPath(): String {
        val file = getSelectedPreset().files.firstOrNull { it.type == FileType.PREPROCESSOR }
        return if (file != null) "$modelsDir/${file.filename}" else ""
    }

    fun isPresetDownloaded(preset: AsrModelPreset): Boolean {
        return preset.files.all { File("$modelsDir/${it.filename}").exists() }
    }

    fun downloadSelectedPreset() {
        if (downloadStatus == DownloadStatus.DOWNLOADING) return

        val preset = getSelectedPreset()

        val filesToDownload = preset.files.filter { file ->
            !File("$modelsDir/${file.filename}").exists()
        }

        if (filesToDownload.isEmpty()) {
            downloadStatus = DownloadStatus.COMPLETED
            return
        }

        downloadStatus = DownloadStatus.DOWNLOADING
        downloadProgress = 0f
        currentFileIndex = 0
        totalFileCount = filesToDownload.size
        errorMessage = null

        viewModelScope.launch {
            try {
                val dir = File(modelsDir)
                if (!dir.exists()) {
                    dir.mkdirs()
                }

                for ((index, fileInfo) in filesToDownload.withIndex()) {
                    currentFileIndex = index
                    currentFileName = fileInfo.filename
                    val targetFile = File("$modelsDir/${fileInfo.filename}")

                    val success = downloadFile(fileInfo, targetFile)
                    if (!success) {
                        downloadStatus = DownloadStatus.FAILED
                        return@launch
                    }
                    downloadProgress = (index + 1).toFloat() / filesToDownload.size
                }

                downloadStatus = DownloadStatus.COMPLETED
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                downloadStatus = DownloadStatus.FAILED
                errorMessage = "Download failed: ${e.message}"
            }
        }
    }

    fun resetStatus() {
        downloadStatus = DownloadStatus.NOT_STARTED
        errorMessage = null
    }

    private suspend fun downloadFile(
        fileInfo: PresetFile,
        targetFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Downloading ${fileInfo.filename} from ${fileInfo.url}")
            val url = URL(fileInfo.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode}")
            }

            val tempFile = File(targetFile.absolutePath + ".tmp")
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            tempFile.renameTo(targetFile)

            Log.i(TAG, "Downloaded ${fileInfo.filename} successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download ${fileInfo.filename}", e)
            withContext(Dispatchers.Main) {
                errorMessage = "Failed to download ${fileInfo.filename}: ${e.message}"
            }
            false
        }
    }
}
