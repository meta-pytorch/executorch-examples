package com.example.whisperapp

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

data class ModelFileInfo(
    val url: String,
    val filename: String,
    val description: String
)

data class WhisperModelPreset(
    val id: String,
    val displayName: String,
    val modelFile: ModelFileInfo
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
        private const val MODELS_SUBDIRECTORY = "whisper"

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

        val SHARED_FILES = listOf(
            ModelFileInfo(
                url = "$TINY_INT8_URL/tokenizer.json",
                filename = "tokenizer.json",
                description = "Tokenizer"
            ),
            ModelFileInfo(
                url = "$TINY_INT8_URL/whisper_preprocessor.pte",
                filename = "whisper_preprocessor.pte",
                description = "Preprocessor"
            )
        )

        val MODEL_PRESETS = listOf(
            WhisperModelPreset(
                id = "tiny_int8",
                displayName = "Whisper Tiny (INT8/INT4)",
                modelFile = ModelFileInfo(
                    url = "$TINY_INT8_URL/model.pte",
                    filename = "whisper_tiny_int8int4.pte",
                    description = "Whisper Tiny INT8/INT4 Model"
                )
            ),
            WhisperModelPreset(
                id = "small_int8",
                displayName = "Whisper Small (INT8/INT4)",
                modelFile = ModelFileInfo(
                    url = "$SMALL_INT8_URL/model.pte",
                    filename = "whisper_small_int8int4.pte",
                    description = "Whisper Small INT8/INT4 Model"
                )
            ),
            WhisperModelPreset(
                id = "medium_int8",
                displayName = "Whisper Medium (INT8/INT4)",
                modelFile = ModelFileInfo(
                    url = "$MEDIUM_INT8_URL/model.pte",
                    filename = "whisper_medium_int8int4.pte",
                    description = "Whisper Medium INT8/INT4 Model"
                )
            ),
            WhisperModelPreset(
                id = "tiny_fp32",
                displayName = "Whisper Tiny (FP32)",
                modelFile = ModelFileInfo(
                    url = "$TINY_FP32_URL/model.pte",
                    filename = "whisper_tiny_fp32.pte",
                    description = "Whisper Tiny FP32 Model"
                )
            ),
            WhisperModelPreset(
                id = "small_fp32",
                displayName = "Whisper Small (FP32)",
                modelFile = ModelFileInfo(
                    url = "$SMALL_FP32_URL/model.pte",
                    filename = "whisper_small_fp32.pte",
                    description = "Whisper Small FP32 Model"
                )
            ),
            WhisperModelPreset(
                id = "medium_fp32",
                displayName = "Whisper Medium (FP32)",
                modelFile = ModelFileInfo(
                    url = "$MEDIUM_FP32_URL/model.pte",
                    filename = "whisper_medium_fp32.pte",
                    description = "Whisper Medium FP32 Model"
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

    fun getSelectedPreset(): WhisperModelPreset = MODEL_PRESETS[selectedPresetIndex]

    fun getModelPath(): String = "$modelsDir/${getSelectedPreset().modelFile.filename}"
    fun getTokenizerPath(): String = "$modelsDir/${SHARED_FILES[0].filename}"
    fun getPreprocessorPath(): String = "$modelsDir/${SHARED_FILES[1].filename}"

    fun isPresetDownloaded(preset: WhisperModelPreset): Boolean {
        val modelExists = File("$modelsDir/${preset.modelFile.filename}").exists()
        val sharedExist = SHARED_FILES.all { File("$modelsDir/${it.filename}").exists() }
        return modelExists && sharedExist
    }

    fun downloadSelectedPreset() {
        if (downloadStatus == DownloadStatus.DOWNLOADING) return

        val preset = getSelectedPreset()

        val filesToDownload = mutableListOf<ModelFileInfo>()

        // Add shared files if not already present
        for (shared in SHARED_FILES) {
            if (!File("$modelsDir/${shared.filename}").exists()) {
                filesToDownload.add(shared)
            }
        }

        // Add model file if not already present
        if (!File("$modelsDir/${preset.modelFile.filename}").exists()) {
            filesToDownload.add(preset.modelFile)
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
        fileInfo: ModelFileInfo,
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
