package com.example.whisperapp

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.asr.ModelSettings
import com.example.asr.ModelSettingsScreen
import com.example.asr.ModelSettingsViewModel
import com.example.asr.ui.theme.AsrTheme
import org.pytorch.executorch.extension.asr.AsrCallback
import org.pytorch.executorch.extension.asr.AsrModule
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class MainActivity : ComponentActivity(), AsrCallback {

    companion object {
        private const val TAG = "MainActivity"
        private const val RECORDING_DURATION_MS = 30000L // 30 seconds
        // Token lengths to remove from transcription output
        private const val START_TOKEN_LENGTH = 37
        private const val END_TOKEN_LENGTH = 13
    }

    private var transcriptionOutput by mutableStateOf("")
    private var rawTranscriptionOutput = ""
    private var buttonText by mutableStateOf("Record")
    private var buttonEnabled by mutableStateOf(true)
    private var statusText by mutableStateOf("")
    private var currentScreen by mutableStateOf(Screen.MAIN)
    private var showWavFileDialog by mutableStateOf(false)

    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopRecordingRunnable: Runnable? = null

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        channelConfig,
        audioFormat
    )

    private lateinit var viewModel: ModelSettingsViewModel
    private lateinit var downloadViewModel: ModelDownloadViewModel

    enum class Screen {
        DOWNLOAD,
        MAIN,
        SETTINGS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Os.setenv("ADSP_LIBRARY_PATH", applicationInfo.nativeLibraryDir, true)
            Os.setenv("LD_LIBRARY_PATH", applicationInfo.nativeLibraryDir, true)
        } catch (e: ErrnoException) {
            finish()
        }

        // Initialize view models
        viewModel = ViewModelProvider(this)[ModelSettingsViewModel::class.java]
        viewModel.setAppStorageDirectory("${filesDir.absolutePath}/whisper")
        viewModel.initialize("/data/local/tmp/whisper", supportsPreprocessor = true)

        downloadViewModel = ViewModelProvider(this)[ModelDownloadViewModel::class.java]
        downloadViewModel.initialize(filesDir.absolutePath)

        // If the first preset is already downloaded, auto-select its paths
        val firstPreset = ModelDownloadViewModel.MODEL_PRESETS[0]
        if (downloadViewModel.isPresetDownloaded(firstPreset)) {
            downloadViewModel.selectPreset(0)
            applyDownloadedModelPaths()
        }

        // Check if minimum buffer size is valid
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size")
            statusText = "Audio recording not supported on this device"
        }

        setContent {
            AsrTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        Screen.DOWNLOAD -> {
                            ModelDownloadScreen(
                                downloadViewModel = downloadViewModel,
                                onDownloadComplete = {
                                    applyDownloadedModelPaths()
                                    viewModel.refreshFileLists()
                                    currentScreen = Screen.SETTINGS
                                },
                                onSkip = {
                                    currentScreen = Screen.SETTINGS
                                }
                            )
                        }
                        Screen.MAIN -> {
                            WhisperScreen(
                                buttonText = buttonText,
                                buttonEnabled = buttonEnabled && viewModel.isReadyForInference(),
                                statusText = statusText,
                                transcriptionResult = transcriptionOutput,
                                modelSettings = viewModel.modelSettings,
                                defaultDirectory = viewModel.defaultDirectory,
                                availableWavFiles = viewModel.availableWavFiles,
                                showWavFileDialog = showWavFileDialog,
                                onRecordClick = { onRecordButtonClick() },
                                onUseWavFileClick = {
                                    viewModel.refreshFileLists()
                                    showWavFileDialog = true
                                },
                                onWavFileSelected = { wavPath ->
                                    showWavFileDialog = false
                                    runWhisperFromFile(wavPath)
                                },
                                onWavDialogDismiss = { showWavFileDialog = false },
                                onSettingsClick = {
                                    viewModel.refreshFileLists()
                                    currentScreen = Screen.SETTINGS
                                }
                            )
                        }
                        Screen.SETTINGS -> {
                            ModelSettingsScreen(
                                viewModel = viewModel,
                                onBackClick = { currentScreen = Screen.MAIN },
                                onDownloadClick = { currentScreen = Screen.DOWNLOAD },
                                showPreprocessor = true
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyDownloadedModelPaths() {
        viewModel.selectModel(downloadViewModel.getModelPath())
        viewModel.selectTokenizer(downloadViewModel.getTokenizerPath())
        viewModel.selectPreprocessor(downloadViewModel.getPreprocessorPath())
    }

    private fun onRecordButtonClick() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    /**
     * Run Whisper inference on the recorded audio file.
     */
    private fun runWhisper() {
        val wavFile = File(getExternalFilesDir(null), "audio_record.wav")
        runWhisperOnWavFile(wavFile.absolutePath)
    }

    /**
     * Run Whisper inference on a WAV file from /data/local/tmp/whisper.
     */
    private fun runWhisperFromFile(wavFilePath: String) {
        buttonEnabled = false
        statusText = "Loading WAV file..."

        Thread {
            try {
                runWhisperOnWavFile(wavFilePath)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing WAV file", e)
                runOnUiThread {
                    statusText = "Error: ${e.message}"
                    buttonEnabled = true
                }
            }
        }.start()
    }

    /**
     * Common method to run Whisper on a WAV file path.
     */
    private fun runWhisperOnWavFile(wavFilePath: String) {
        val settings = viewModel.modelSettings

        if (!settings.isValid()) {
            runOnUiThread {
                statusText = "Please select model and tokenizer in Settings"
                buttonEnabled = true
            }
            return
        }

        rawTranscriptionOutput = ""
        runOnUiThread {
            transcriptionOutput = ""
            statusText = "Loading model..."
            buttonText = "Transcribing..."
            buttonEnabled = false
        }

        val whisperModule = AsrModule(
            modelPath = settings.modelPath,
            tokenizerPath = settings.tokenizerPath,
            dataPath = settings.dataPath.ifBlank { null },
            preprocessorPath = settings.preprocessorPath.ifBlank { null }
        )

        Log.v(TAG, "Starting transcribe for: $wavFilePath")
        runOnUiThread {
            statusText = "Transcribing..."
        }
        val startTime = System.currentTimeMillis()
        whisperModule.transcribe(wavFilePath, callback = this@MainActivity)
        val elapsedTime = System.currentTimeMillis() - startTime
        val elapsedSeconds = elapsedTime / 1000.0
        Log.v(TAG, "Finished transcribe in ${elapsedSeconds}s")

        // Display result in Text view instead of Toast
        // hack to remove start and end tokens; ideally the runner should not do callback on these tokens
        runOnUiThread {
            val minLength = START_TOKEN_LENGTH + END_TOKEN_LENGTH
            if (rawTranscriptionOutput.length > minLength) {
                val endIndex = rawTranscriptionOutput.length - END_TOKEN_LENGTH
                if (endIndex > START_TOKEN_LENGTH) {
                    transcriptionOutput = rawTranscriptionOutput.substring(START_TOKEN_LENGTH, endIndex)
                }
            }
            statusText = "Transcription complete (%.2fs)".format(elapsedSeconds)
            buttonText = "Record"
            buttonEnabled = true
        }
    }

    override fun onToken(result: String) {
        Log.v(TAG, "Called callback: here's the current output")
        rawTranscriptionOutput += result
        runOnUiThread {
            // Strip start token prefix for display while transcribing
            if (rawTranscriptionOutput.length > START_TOKEN_LENGTH) {
                transcriptionOutput = rawTranscriptionOutput.substring(START_TOKEN_LENGTH)
            }
        }
        Log.v(TAG, rawTranscriptionOutput)
    }

    private fun startRecording() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, start recording
                try {
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )

                    if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "AudioRecord initialization failed")
                        statusText = "Failed to initialize audio recorder"
                        return
                    }

                    audioRecord?.startRecording()
                    isRecording = true

                    buttonText = "Recording... (30s)"
                    buttonEnabled = false

                    // Schedule automatic stop after 30 seconds
                    stopRecordingRunnable = Runnable {
                        stopRecording()
                    }
                    handler.postDelayed(stopRecordingRunnable!!, RECORDING_DURATION_MS)

                    val pcmFile = File(getExternalFilesDir(null), "audio_record.pcm")

                    recordingThread = Thread {
                        try {
                            val os = FileOutputStream(pcmFile)
                            val buffer = ByteArray(bufferSize)

                            while (isRecording) {
                                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                                if (read > 0) {
                                    os.write(buffer, 0, read)
                                }
                            }

                            os.close()

                            runOnUiThread {
                                writeWavFile(pcmFile)
                                statusText = "Recording saved"
                                runWhisper()
                            }

                        } catch (e: IOException) {
                            Log.e(TAG, "Recording failed", e)
                            runOnUiThread {
                                statusText = "Recording failed"
                            }
                        }
                    }

                    recordingThread?.start()

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start recording", e)
                    statusText = "Failed to start recording"
                }
            }

            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                statusText = "Audio recording permission is needed to record audio"
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun stopRecording() {
        isRecording = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }

        audioRecord = null
        buttonText = "Record"
        buttonEnabled = true

        recordingThread?.join()
        recordingThread = null
    }

    private fun writeWavFile(pcmFile: File) {
        try {
            val wavFile = File(getExternalFilesDir(null), "audio_record.wav")
            val pcmData = pcmFile.readBytes()

            val wavOut = FileOutputStream(wavFile)

            // Write WAV header for 16-bit mono audio at 16 kHz
            writeWavHeader(wavOut, pcmData.size.toLong(), sampleRate, 1, 16)
            wavOut.write(pcmData)
            wavOut.flush()
            wavOut.fd.sync()
            wavOut.close()

            pcmFile.delete()

            Log.i(TAG, "WAV file saved: ${wavFile.absolutePath}")

        } catch (e: IOException) {
            Log.e(TAG, "Failed to write WAV file", e)
        }
    }

    private fun writeWavHeader(
        out: OutputStream,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = totalAudioLen + 36

        val header = ByteArray(44)

        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        // File size (little-endian)
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()

        // WAVE header
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        // fmt chunk size (16 for PCM)
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0

        // Audio format (1 for PCM)
        header[20] = 1
        header[21] = 0

        // Number of channels
        header[22] = channels.toByte()
        header[23] = 0

        // Sample rate (little-endian)
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()

        // Byte rate (little-endian)
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        // Block align
        header[32] = blockAlign.toByte()
        header[33] = 0

        // Bits per sample
        header[34] = bitsPerSample.toByte()
        header[35] = 0

        // Data chunk header
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        // Data chunk size (little-endian)
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        out.write(header, 0, 44)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            stopRecording()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startRecording()
        } else {
            statusText = "Audio recording permission required"
        }
    }
}

@Composable
fun WhisperScreen(
    buttonText: String,
    buttonEnabled: Boolean,
    statusText: String,
    transcriptionResult: String,
    modelSettings: ModelSettings,
    defaultDirectory: String,
    availableWavFiles: List<String>,
    showWavFileDialog: Boolean,
    onRecordClick: () -> Unit,
    onUseWavFileClick: () -> Unit,
    onWavFileSelected: (String) -> Unit,
    onWavDialogDismiss: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Whisper Demo",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Model info card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Model Configuration",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (modelSettings.isValid()) {
                    Text(
                        text = "Model: ${modelSettings.modelPath.substringAfterLast('/')}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Tokenizer: ${modelSettings.tokenizerPath.substringAfterLast('/')}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (modelSettings.hasPreprocessor()) {
                        Text(
                            text = "Preprocessor: ${modelSettings.preprocessorPath.substringAfterLast('/')}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = "Preprocessor: None (raw WAV mode)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (modelSettings.dataPath.isNotBlank()) {
                        Text(
                            text = "Data: ${modelSettings.dataPath.substringAfterLast('/')}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text(
                        text = "No model configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Please configure models in Settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Audio input buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRecordClick,
                enabled = buttonEnabled,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = buttonText)
            }

            OutlinedButton(
                onClick = onUseWavFileClick,
                enabled = buttonEnabled,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Use WAV File")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Settings button
        OutlinedButton(
            onClick = onSettingsClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Settings")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (transcriptionResult.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Transcription",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = transcriptionResult,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }

    // WAV file selection dialog
    if (showWavFileDialog) {
        WavFileSelectionDialog(
            files = availableWavFiles,
            defaultDirectory = defaultDirectory,
            onDismiss = onWavDialogDismiss,
            onSelect = onWavFileSelected
        )
    }
}

@Composable
fun WavFileSelectionDialog(
    files: List<String>,
    defaultDirectory: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var selectedFile by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select WAV File") },
        text = {
            if (files.isEmpty()) {
                Column {
                    Text("No WAV files found in $defaultDirectory")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Use adb to push WAV files:\nadb push audio.wav $defaultDirectory/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    files.forEach { filePath ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = filePath == selectedFile,
                                onClick = { selectedFile = filePath }
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
            if (files.isNotEmpty()) {
                TextButton(
                    onClick = { selectedFile?.let { onSelect(it) } },
                    enabled = selectedFile != null
                ) {
                    Text("Transcribe")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
