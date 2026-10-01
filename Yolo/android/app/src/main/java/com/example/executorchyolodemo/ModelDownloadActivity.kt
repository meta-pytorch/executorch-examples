/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchyolodemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme

class ModelDownloadActivity : ComponentActivity() {

    private val downloadViewModel: ModelDownloadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel.initialize(filesDir.absolutePath)

        // Skip download screen if all models already downloaded
        if (downloadViewModel.allModelsDownloaded()) {
            launchBirdDetection()
            return
        }

        setContent {
            MaterialTheme {
                ModelDownloadScreen(
                    downloadViewModel = downloadViewModel,
                    onDownloadComplete = { launchBirdDetection() }
                )
            }
        }
    }

    private fun launchBirdDetection() {
        val intent = Intent(this, BirdDetectionActivity::class.java)
        intent.putExtra("model_dir", downloadViewModel.getModelDir())
        startActivity(intent)
        finish()
    }
}
