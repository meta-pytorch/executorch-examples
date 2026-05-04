/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchyolodemo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ModelDownloadScreen(
    downloadViewModel: ModelDownloadViewModel,
    onDownloadComplete: () -> Unit
) {
    val status = downloadViewModel.downloadStatus
    val progress = downloadViewModel.downloadProgress
    val currentFileName = downloadViewModel.currentFileName
    val error = downloadViewModel.errorMessage
    val isDownloading = status == DownloadStatus.DOWNLOADING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Bird Detection Models",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Download the required models to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Files list
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Model Files",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))

                ModelDownloadViewModel.MODEL_FILES.forEach { fileInfo ->
                    FileStatusRow(fileInfo, downloadViewModel)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Download progress
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Downloading $currentFileName...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (status == DownloadStatus.COMPLETED) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "All models downloaded!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (status) {
            DownloadStatus.NOT_STARTED, DownloadStatus.FAILED -> {
                Button(
                    onClick = { downloadViewModel.downloadModels() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (status == DownloadStatus.FAILED) "Retry Download" else "Download Models")
                }
            }

            DownloadStatus.DOWNLOADING -> {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Downloading...")
                }
            }

            DownloadStatus.COMPLETED -> {
                Button(
                    onClick = onDownloadComplete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue")
                }
            }
        }
    }
}

@Composable
private fun FileStatusRow(
    fileInfo: ModelFileInfo,
    downloadViewModel: ModelDownloadViewModel
) {
    val status = downloadViewModel.downloadStatus
    val currentFileName = downloadViewModel.currentFileName
    val fileExists = downloadViewModel.isFileDownloaded(fileInfo.filename)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = fileInfo.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = fileInfo.filename,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = when {
                fileExists || status == DownloadStatus.COMPLETED -> "✓"
                status == DownloadStatus.DOWNLOADING && currentFileName == fileInfo.filename -> "⬇"
                else -> "○"
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
