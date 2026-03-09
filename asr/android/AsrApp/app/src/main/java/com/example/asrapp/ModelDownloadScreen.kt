package com.example.asrapp

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ModelDownloadScreen(
    downloadViewModel: ModelDownloadViewModel,
    onDownloadComplete: () -> Unit,
    onSkip: () -> Unit
) {
    val status = downloadViewModel.downloadStatus
    val progress = downloadViewModel.downloadProgress
    val currentFileName = downloadViewModel.currentFileName
    val error = downloadViewModel.errorMessage
    val selectedIndex = downloadViewModel.selectedPresetIndex
    val isDownloading = status == DownloadStatus.DOWNLOADING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Download Model",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Preset selection grouped by model type
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Select Model",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Group presets by model type
                val groupedPresets = ModelDownloadViewModel.MODEL_PRESETS
                    .withIndex()
                    .groupBy { it.value.modelType }

                // Whisper Models section
                groupedPresets[ModelType.WHISPER]?.let { whisperPresets ->
                    Text(
                        text = "Whisper Models",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )

                    whisperPresets.forEach { (index, preset) ->
                        PresetRow(
                            preset = preset,
                            isSelected = index == selectedIndex,
                            isDownloaded = downloadViewModel.isPresetDownloaded(preset),
                            isDownloading = isDownloading,
                            onSelect = {
                                downloadViewModel.selectPreset(index)
                                downloadViewModel.resetStatus()
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Parakeet Models section
                groupedPresets[ModelType.PARAKEET]?.let { parakeetPresets ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Parakeet Models",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )

                    parakeetPresets.forEach { (index, preset) ->
                        PresetRow(
                            preset = preset,
                            isSelected = index == selectedIndex,
                            isDownloaded = downloadViewModel.isPresetDownloaded(preset),
                            isDownloading = isDownloading,
                            onSelect = {
                                downloadViewModel.selectPreset(index)
                                downloadViewModel.resetStatus()
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Files to download
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Files",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))

                val preset = downloadViewModel.getSelectedPreset()

                preset.files.forEachIndexed { index, file ->
                    FileStatusRow(file.description, file.filename, downloadViewModel)
                    if (index < preset.files.size - 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
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
                        text = "All files downloaded!",
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
                    onClick = { downloadViewModel.downloadSelectedPreset() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (status == DownloadStatus.FAILED) "Retry Download" else "Download")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back")
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

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        downloadViewModel.resetStatus()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Download Another Model")
                }
            }
        }
    }
}

@Composable
private fun PresetRow(
    preset: AsrModelPreset,
    isSelected: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onSelect: () -> Unit
) {
    val modelFile = preset.files.first { it.type == FileType.MODEL }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = { if (!isDownloading) onSelect() },
            enabled = !isDownloading
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.displayName,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = modelFile.filename,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isDownloaded) {
            Text(
                text = "\u2713",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FileStatusRow(
    description: String,
    filename: String,
    downloadViewModel: ModelDownloadViewModel
) {
    val status = downloadViewModel.downloadStatus
    val currentFileName = downloadViewModel.currentFileName
    val modelsDir = downloadViewModel.getModelDir()
    val fileExists = java.io.File("$modelsDir/$filename").exists()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = filename,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = when {
                fileExists || status == DownloadStatus.COMPLETED -> "\u2713"
                status == DownloadStatus.DOWNLOADING && currentFileName == filename -> "\u2B07"
                else -> "\u25CB"
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
