/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.pytorch.executorchexamples.dl3

import android.content.Intent
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    companion object {
        private const val MODEL_URL = "https://ossci-android.s3.amazonaws.com/executorch/models/snapshot-20260116/dl3_xnnpack_fp32.pte"
        private const val MODEL_FILENAME = "dl3_xnnpack_fp32.pte"
        private val SAMPLE_IMAGES = arrayOf("corgi.jpeg", "deeplab.jpg", "dog.jpg")
        private const val CLASSNUM = 21

        // Colors for all 21 PASCAL VOC classes
        private val CLASS_COLORS = intArrayOf(
            0x00000000, // 0: Background (transparent)
            0xFFE6194B.toInt(), // 1: Aeroplane (red)
            0xFF3CB44B.toInt(), // 2: Bicycle (green)
            0xFFFFE119.toInt(), // 3: Bird (yellow)
            0xFF4363D8.toInt(), // 4: Boat (blue)
            0xFFF58231.toInt(), // 5: Bottle (orange)
            0xFF911EB4.toInt(), // 6: Bus (purple)
            0xFF46F0F0.toInt(), // 7: Car (cyan)
            0xFFF032E6.toInt(), // 8: Cat (magenta)
            0xFFBCF60C.toInt(), // 9: Chair (lime)
            0xFFFABEBE.toInt(), // 10: Cow (pink)
            0xFF008080.toInt(), // 11: Dining Table (teal)
            0xFF00FF00.toInt(), // 12: Dog (bright green)
            0xFF9A6324.toInt(), // 13: Horse (brown)
            0xFFFFD8B1.toInt(), // 14: Motorbike (peach)
            0xFFFF0000.toInt(), // 15: Person (red)
            0xFF800000.toInt(), // 16: Potted Plant (maroon)
            0xFF0000FF.toInt(), // 17: Sheep (blue)
            0xFF808000.toInt(), // 18: Sofa (olive)
            0xFFE6BEFF.toInt(), // 19: Train (lavender)
            0xFFAA6E28.toInt(), // 20: TV/Monitor (tan)
        )
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
            DeepLabV3App()
        }
    }

    @Composable
    fun DeepLabV3App() {
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var currentSampleIndex by remember { mutableStateOf(0) }
        var isProcessing by remember { mutableStateOf(false) }
        var inferenceTime by remember { mutableStateOf<Long?>(null) }
        var modelReady by remember { mutableStateOf(false) }
        var isDownloading by remember { mutableStateOf(false) }
        var canReset by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        // Load initial sample image
        LaunchedEffect(Unit) {
            bitmap = loadSampleImage(currentSampleIndex)
            loadModelOrShowDownloadButton { ready ->
                modelReady = ready
            }
        }

        // Image picker launcher
        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                try {
                    val inputStream: InputStream? = contentResolver.openInputStream(it)
                    val selectedBitmap = BitmapFactory.decodeStream(inputStream)
                    if (selectedBitmap != null) {
                        bitmap = Bitmap.createScaledBitmap(selectedBitmap, 224, 224, true)
                        inferenceTime = null
                        canReset = false
                        showToast("Image loaded - tap Run to segment")
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
                            .fillMaxWidth()
                            .testTag("imageBox"),
                        contentAlignment = Alignment.Center
                    ) {
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Segmentation Image",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("segmentationImage")
                            )
                        }

                        if (isProcessing || isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.testTag("progressIndicator"))
                        }
                    }

                    // Inference time
                    inferenceTime?.let {
                        Text(
                            text = "Inference: $it ms",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .testTag("inferenceTime")
                        )
                    }

                    // Control buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Download button (only visible if model not ready)
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
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("downloadButton")
                                ) {
                                    Text(if (isDownloading) "Downloading..." else "Download Model")
                                }
                            }
                        }

                        if (modelReady) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Next sample button
                                Button(
                                    onClick = {
                                        currentSampleIndex = (currentSampleIndex + 1) % SAMPLE_IMAGES.size
                                        bitmap = loadSampleImage(currentSampleIndex)
                                        inferenceTime = null
                                        canReset = false
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("nextButton")
                                ) {
                                    Text("Next")
                                }

                                // Pick image button
                                Button(
                                    onClick = {
                                        imagePickerLauncher.launch("image/*")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pickButton")
                                ) {
                                    Text("Pick")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Run segmentation button
                                Button(
                                    onClick = {
                                        bitmap?.let { bmp ->
                                            scope.launch {
                                                isProcessing = true
                                                inferenceTime = null
                                                val result = runSegmentation(bmp)
                                                bitmap = result.first
                                                inferenceTime = result.second
                                                canReset = result.third
                                                isProcessing = false
                                                if (!result.third) {
                                                    showToast("No objects detected")
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isProcessing && modelReady,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("runButton")
                                ) {
                                    Text("Run")
                                }

                                // Reset button
                                Button(
                                    onClick = {
                                        bitmap = loadSampleImage(currentSampleIndex)
                                        inferenceTime = null
                                        canReset = false
                                    },
                                    enabled = canReset,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("resetButton")
                                ) {
                                    Text("Reset")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadSampleImage(index: Int): Bitmap? {
        return try {
            val imageName = SAMPLE_IMAGES[index]
            val bmp = BitmapFactory.decodeStream(assets.open(imageName))
            bmp?.let { Bitmap.createScaledBitmap(it, 224, 224, true) }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading sample image", e)
            null
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

    private suspend fun runSegmentation(inputBitmap: Bitmap): Triple<Bitmap, Long, Boolean> =
        withContext(Dispatchers.Default) {
            val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                inputBitmap,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                TensorImageUtils.TORCHVISION_NORM_STD_RGB
            )

            // Ensure module is loaded before running inference
            val loadedModule = module ?: throw IllegalStateException("Module not loaded. Please download the model first.")
            
            val startTime = SystemClock.elapsedRealtime()
            val outputTensor = loadedModule.forward(EValue.from(inputTensor))[0].toTensor()
            val inferenceTime = SystemClock.elapsedRealtime() - startTime
            Log.d("ImageSegmentation", "inference time (ms): $inferenceTime")

            val scores = outputTensor.dataAsFloatArray
            val width = inputBitmap.width
            val height = inputBitmap.height

            // Get original pixels for blending
            val originalPixels = IntArray(width * height)
            inputBitmap.getPixels(originalPixels, 0, width, 0, 0, width, height)

            val intValues = IntArray(width * height)
            var imageSegmentationSuccess = false

            for (j in 0 until height) {
                for (k in 0 until width) {
                    var maxi = 0
                    var maxnum = -Double.MAX_VALUE
                    for (i in 0 until CLASSNUM) {
                        val score = scores[i * (width * height) + j * width + k]
                        if (score > maxnum) {
                            maxnum = score.toDouble()
                            maxi = i
                        }
                    }
                    val pixelIndex = j * width + k
                    val classColor = CLASS_COLORS[maxi]

                    if (maxi == 0) {
                        // Background: show original image
                        intValues[pixelIndex] = originalPixels[pixelIndex]
                    } else {
                        // Blend segmentation color with original at 50% opacity
                        intValues[pixelIndex] = blendColors(originalPixels[pixelIndex], classColor, 0.5f)
                        imageSegmentationSuccess = true
                    }
                }
            }

            val bmpSegmentation = Bitmap.createScaledBitmap(inputBitmap, width, height, true)
            val outputBitmap = bmpSegmentation.copy(bmpSegmentation.config ?: Bitmap.Config.ARGB_8888, true)
            outputBitmap.setPixels(intValues, 0, width, 0, 0, width, height)
            val transferredBitmap = Bitmap.createScaledBitmap(
                outputBitmap,
                inputBitmap.width,
                inputBitmap.height,
                true
            )

            Triple(transferredBitmap, inferenceTime, imageSegmentationSuccess)
        }

    private fun blendColors(background: Int, foreground: Int, alpha: Float): Int {
        val bgR = (background shr 16) and 0xFF
        val bgG = (background shr 8) and 0xFF
        val bgB = background and 0xFF
        val fgR = (foreground shr 16) and 0xFF
        val fgG = (foreground shr 8) and 0xFF
        val fgB = foreground and 0xFF
        val r = (bgR * (1 - alpha) + fgR * alpha).toInt()
        val g = (bgG * (1 - alpha) + fgG * alpha).toInt()
        val b = (bgB * (1 - alpha) + fgB * alpha).toInt()
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
