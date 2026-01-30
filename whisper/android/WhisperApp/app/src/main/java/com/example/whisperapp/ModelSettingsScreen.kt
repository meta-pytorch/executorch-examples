package com.example.whisperapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Settings screen for selecting model files.
 */
@Composable
fun ModelSettingsScreen(
    viewModel: ModelSettingsViewModel,
    onBackClick: () -> Unit
) {
    var showModelDialog by remember { mutableStateOf(false) }
    var showTokenizerDialog by remember { mutableStateOf(false) }
    var showPreprocessorDialog by remember { mutableStateOf(false) }
    var showDataDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Model Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Model file selection
        FileSelectionRow(
            label = "Model File (.pte)",
            selectedPath = viewModel.modelSettings.modelPath,
            required = true,
            onClick = { showModelDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tokenizer file selection
        FileSelectionRow(
            label = "Tokenizer File",
            selectedPath = viewModel.modelSettings.tokenizerPath,
            required = true,
            onClick = { showTokenizerDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Preprocessor file selection (optional)
        FileSelectionRow(
            label = "Preprocessor (.pte) - Optional",
            selectedPath = viewModel.modelSettings.preprocessorPath,
            required = false,
            onClick = { showPreprocessorDialog = true },
            onClear = if (viewModel.modelSettings.hasPreprocessor()) {
                { viewModel.clearPreprocessor() }
            } else null
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Data file selection (optional)
        FileSelectionRow(
            label = "Data File (.ptd) - Optional",
            selectedPath = viewModel.modelSettings.dataPath,
            required = false,
            onClick = { showDataDialog = true },
            onClear = if (viewModel.modelSettings.dataPath.isNotBlank()) {
                { viewModel.clearDataFile() }
            } else null
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Refresh button
        OutlinedButton(
            onClick = { viewModel.refreshFileLists() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh File Lists")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status indicator
        if (viewModel.isReadyForInference()) {
            Text(
                text = "✓ Ready for inference",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = "⚠ Model and Tokenizer are required",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Back button
        Button(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Recording")
        }

        // Info text
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Files should be in ${ModelSettings.DEFAULT_DIRECTORY}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (viewModel.modelSettings.hasPreprocessor()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Preprocessor will convert WAV to mel-spectrogram",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No preprocessor: WAV audio will be used directly",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // File selection dialogs
    if (showModelDialog) {
        FileSelectionDialog(
            title = "Select Model File",
            files = viewModel.availableModels,
            currentSelection = viewModel.modelSettings.modelPath,
            onDismiss = { showModelDialog = false },
            onSelect = {
                viewModel.selectModel(it)
                showModelDialog = false
            }
        )
    }

    if (showTokenizerDialog) {
        FileSelectionDialog(
            title = "Select Tokenizer File",
            files = viewModel.availableTokenizers,
            currentSelection = viewModel.modelSettings.tokenizerPath,
            onDismiss = { showTokenizerDialog = false },
            onSelect = {
                viewModel.selectTokenizer(it)
                showTokenizerDialog = false
            }
        )
    }

    if (showPreprocessorDialog) {
        FileSelectionDialog(
            title = "Select Preprocessor (Optional)",
            files = viewModel.availablePreprocessors,
            currentSelection = viewModel.modelSettings.preprocessorPath,
            onDismiss = { showPreprocessorDialog = false },
            onSelect = {
                viewModel.selectPreprocessor(it)
                showPreprocessorDialog = false
            },
            allowNone = true
        )
    }

    if (showDataDialog) {
        FileSelectionDialog(
            title = "Select Data File (Optional)",
            files = viewModel.availableDataFiles,
            currentSelection = viewModel.modelSettings.dataPath,
            onDismiss = { showDataDialog = false },
            onSelect = {
                viewModel.selectDataFile(it)
                showDataDialog = false
            },
            allowNone = true
        )
    }
}

/**
 * Row displaying file selection information.
 */
@Composable
fun FileSelectionRow(
    label: String,
    selectedPath: String,
    required: Boolean,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )

            Row {
                if (onClear != null) {
                    TextButton(onClick = onClear) {
                        Text("Clear")
                    }
                }
                Button(onClick = onClick) {
                    Text("Select")
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (selectedPath.isNotBlank()) {
            Text(
                text = selectedPath.substringAfterLast('/'),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = selectedPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = if (required) "Not selected (Required)" else "Not selected (Optional)",
                style = MaterialTheme.typography.bodySmall,
                color = if (required) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Dialog for selecting a file from a list.
 */
@Composable
fun FileSelectionDialog(
    title: String,
    files: List<String>,
    currentSelection: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    allowNone: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (files.isEmpty()) {
                Column {
                    Text("No files found in ${ModelSettings.DEFAULT_DIRECTORY}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Use adb to push files:\nadb push <file> ${ModelSettings.DEFAULT_DIRECTORY}/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (allowNone) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect("") },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentSelection.isBlank(),
                                onClick = { onSelect("") }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("None", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    files.forEach { filePath ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(filePath) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = filePath == currentSelection,
                                onClick = { onSelect(filePath) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = filePath.substringAfterLast('/'),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = filePath,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
