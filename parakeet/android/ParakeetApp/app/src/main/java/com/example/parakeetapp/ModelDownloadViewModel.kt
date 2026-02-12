package com.example.parakeetapp

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

data class ParakeetModelPreset(
    val id: String,
    val displayName: String,
    val modelFile: ModelFileInfo,
    val tokenizerFile: ModelFileInfo
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
        private const val MODELS_SUBDIRECTORY = "parakeet"

        private const val PARAKEET_BASE_URL =
            "https://huggingface.co/larryliu0820/parakeet-tdt-0.6b-v3-executorch/resolve/main/xnnpack/int4"

        val MODEL_PRESETS = listOf(
            ParakeetModelPreset(
                id = "parakeet_int4",
                displayName = "Parakeet TDT 0.6B (INT4)",
                modelFile = ModelFileInfo(
                    url = "$PARAKEET_BASE_URL/model.pte",
                    filename = "parakeet_int4.pte",
                    description = "Parakeet TDT 0.6B INT4 Model"
                ),
                tokenizerFile = ModelFileInfo(
                    url = "$PARAKEET_BASE_URL/tokenizer.model",
                    filename = "tokenizer.model",
                    description = "Tokenizer"
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

    fun getSelectedPreset(): ParakeetModelPreset = MODEL_PRESETS[selectedPresetIndex]

    fun getModelPath(): String = "$modelsDir/${getSelectedPreset().modelFile.filename}"
    fun getTokenizerPath(): String = "$modelsDir/${getSelectedPreset().tokenizerFile.filename}"

    fun isPresetDownloaded(preset: ParakeetModelPreset): Boolean {
        val modelExists = File("$modelsDir/${preset.modelFile.filename}").exists()
        val tokenizerExists = File("$modelsDir/${preset.tokenizerFile.filename}").exists()
        return modelExists && tokenizerExists
    }

    fun downloadSelectedPreset() {
        if (downloadStatus == DownloadStatus.DOWNLOADING) return

        val preset = getSelectedPreset()

        val filesToDownload = mutableListOf<ModelFileInfo>()

        // Add tokenizer file if not already present
        if (!File("$modelsDir/${preset.tokenizerFile.filename}").exists()) {
            filesToDownload.add(preset.tokenizerFile)
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
