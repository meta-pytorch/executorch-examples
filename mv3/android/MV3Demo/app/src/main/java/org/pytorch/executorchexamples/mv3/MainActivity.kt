/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.pytorch.executorchexamples.mv3

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.exp

class MainActivity : ComponentActivity() {
    companion object {
        private const val MODEL_URL = "https://ossci-android.s3.amazonaws.com/executorch/models/snapshot-20260116/mv3_xnnpack_fp32.pte" // TODO: Update with real MV3 URL if different
        private const val MODEL_FILENAME = "mv3.pte"
    }

    private var module: Module? = null
    private lateinit var modelPath: String

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Os.setenv("ADSP_LIBRARY_PATH", applicationInfo.nativeLibraryDir, true)
            Os.setenv("LD_LIBRARY_PATH", applicationInfo.nativeLibraryDir, true)
        } catch (e: ErrnoException) {
            finish()
        }

        modelPath = filesDir.absolutePath + "/" + MODEL_FILENAME

        setContent {
            MV3App()
        }
    }

    @Composable
    fun MV3App() {
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var isProcessing by remember { mutableStateOf(false) }
        var isDownloading by remember { mutableStateOf(false) }
        var modelReady by remember { mutableStateOf(false) }
        var classificationResults by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }
        var inferenceTime by remember { mutableStateOf<Long?>(null) }
        
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            loadModelOrShowDownloadButton { ready ->
                modelReady = ready
            }
        }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                try {
                    val inputStream: InputStream? = contentResolver.openInputStream(it)
                    val selectedBitmap = BitmapFactory.decodeStream(inputStream)
                    if (selectedBitmap != null) {
                        bitmap = Bitmap.createScaledBitmap(selectedBitmap, 224, 224, true)
                        classificationResults = emptyList()
                        inferenceTime = null
                        showToast("Image loaded")
                    }
                    inputStream?.close()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error loading picked image", e)
                    showToast("Failed to load image")
                }
            }
        }

        MaterialTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Image display
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = "Input Image",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("Pick an image to start", style = MaterialTheme.typography.bodyLarge)
                        }

                        if (isProcessing || isDownloading) {
                            CircularProgressIndicator()
                        }
                    }

                    // Inference time
                    inferenceTime?.let {
                        Text(
                            text = "Inference: $it ms",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    // Results list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(classificationResults) { result ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = result.first, style = MaterialTheme.typography.bodyMedium)
                                Text(text = String.format("%.2f", result.second), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!modelReady) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isDownloading = true
                                        val success = downloadModel()
                                        isDownloading = false
                                        if (success) {
                                            loadModelOrShowDownloadButton { ready ->
                                                modelReady = ready
                                            }
                                            showToast("Model downloaded successfully!")
                                        } else {
                                            showToast("Download failed")
                                        }
                                    }
                                },
                                enabled = !isDownloading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (isDownloading) "Downloading..." else "Download Model")
                            }
                        } else {
                            Button(
                                onClick = { },
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text("Model Loaded")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        bitmap?.let { bmp ->
                                            scope.launch {
                                                isProcessing = true
                                                val result = runInference(bmp)
                                                classificationResults = result.first
                                                inferenceTime = result.second
                                                isProcessing = false
                                            }
                                        } ?: showToast("Please pick an image first")
                                    },
                                    enabled = !isProcessing && modelReady,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Run")
                                }

                                Button(
                                    onClick = {
                                        imagePickerLauncher.launch("image/*")
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Pick")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadModelOrShowDownloadButton(onResult: (Boolean) -> Unit) {
        val modelFile = File(modelPath)
        if (modelFile.exists()) {
            try {
                module = Module.load(modelPath)
                onResult(true)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load model", e)
                showToast("Failed to load model: ${e.message}")
                onResult(false)
            }
        } else {
            onResult(false)
        }
    }

    private suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode}")
            }

            connection.inputStream.use { input ->
                FileOutputStream(modelPath).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to download model", e)
            withContext(Dispatchers.Main) {
                showToast("Download failed: ${e.message}")
            }
            false
        }
    }

    private suspend fun runInference(inputBitmap: Bitmap): Pair<List<Pair<String, Float>>, Long> =
        withContext(Dispatchers.Default) {
             val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                inputBitmap,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                TensorImageUtils.TORCHVISION_NORM_STD_RGB
            )

            val loadedModule = module ?: throw IllegalStateException("Module not loaded")

            val startTime = SystemClock.elapsedRealtime()
            val outputTensor = loadedModule.forward(EValue.from(inputTensor))[0].toTensor()
            val inferenceTime = SystemClock.elapsedRealtime() - startTime

            val scores = outputTensor.dataAsFloatArray
            val top3 = getTopK(scores, 3)
            
            val results = top3.map { (index, score) ->
                val label = if (index in ImageNetClasses.IMAGENET_CLASSES.indices) {
                    ImageNetClasses.IMAGENET_CLASSES[index]
                } else {
                    "Unknown($index)"
                }
                label to score
            }

            results to inferenceTime
        }
    
    // Softmax function
    private fun softmax(scores: FloatArray): FloatArray {
        val max = scores.maxOrNull() ?: 0f
        val expScores = scores.map { exp(it - max) }
        val sumExp = expScores.sum()
        return expScores.map { (it / sumExp).toFloat() }.toFloatArray()
    }

    private fun getTopK(scores: FloatArray, k: Int): List<Pair<Int, Float>> {
        // Apply softmax first if needed, but for ranking, raw scores are fine. 
        // Showing raw scores might not be ideal, but user asked for "scores".
        // Usually softmax is better for display.
        val probabilities = softmax(scores)
        
        return probabilities.withIndex()
            .sortedByDescending { it.value }
            .take(k)
            .map { it.index to it.value }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
