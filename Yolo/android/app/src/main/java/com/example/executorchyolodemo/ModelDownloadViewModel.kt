/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchyolodemo

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

enum class DownloadStatus {
    NOT_STARTED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

class ModelDownloadViewModel : ViewModel() {

    companion object {
        private const val TAG = "ModelDownloadViewModel"
        const val MODELS_SUBDIRECTORY = "yolo"

        private const val YOLO_DETECTOR_URL =
            "https://huggingface.co/larryliu0820/yolo26s-ExecuTorch-XNNPACK/resolve/main/yolo26s_dynamic_xnnpack.pte"
        private const val BIRD_CLASSIFIER_URL =
            "https://huggingface.co/psiddh/bird-classifier-executorch/resolve/main/bird_classifier.pte"

        val MODEL_FILES = listOf(
            ModelFileInfo(
                url = YOLO_DETECTOR_URL,
                filename = "yolo_detector.pte",
                description = "YOLO Bird Detector"
            ),
            ModelFileInfo(
                url = BIRD_CLASSIFIER_URL,
                filename = "bird_classifier.pte",
                description = "Bird Species Classifier"
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

    private lateinit var modelsDir: String

    fun initialize(filesDir: String) {
        modelsDir = filesDir + "/" + MODELS_SUBDIRECTORY
    }

    fun getModelDir(): String = modelsDir

    fun allModelsDownloaded(): Boolean {
        return MODEL_FILES.all { File("$modelsDir/${it.filename}").exists() }
    }

    fun isFileDownloaded(filename: String): Boolean {
        return File("$modelsDir/$filename").exists()
    }

    fun downloadModels() {
        if (downloadStatus == DownloadStatus.DOWNLOADING) return

        val filesToDownload = MODEL_FILES.filter {
            !File("$modelsDir/${it.filename}").exists()
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
