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
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import org.pytorch.executorch.extension.audio.WhisperCallback
import org.pytorch.executorch.extension.audio.WhisperModule
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder


class MainActivity : ComponentActivity(),  WhisperCallback {

    companion object {
        private const val TAG = "MainActivity"
        private const val RECORDING_DURATION_MS = 5000L // 5 seconds
        private var Output = ""
    }

    private var isRecording = false
    private lateinit var recordButton: Button
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


    @Throws(IOException::class)
    fun readWavPcmBytes(filePath: String): ByteArray {
        val WAV_HEADER_SIZE = 44 // Standard header size for PCM WAV
        val file = File(filePath)
        val fis = FileInputStream(file)
        try {
            val totalSize = file.length()
            assert (totalSize > WAV_HEADER_SIZE)
            val pcmSize = (totalSize - WAV_HEADER_SIZE).toInt()
            val pcmBytes = ByteArray(pcmSize)
            // Skip the header
            val skipped = fis.skip(WAV_HEADER_SIZE.toLong())
            if (skipped != WAV_HEADER_SIZE.toLong()) throw IOException("Failed to skip WAV header")
            // Read PCM data
            val read = fis.read(pcmBytes)
            if (read != pcmSize) throw IOException("Failed to read all PCM data")
            return pcmBytes
        } finally {
            fis.close()
        }
    }


    private fun convertPcm16ToFloat(audioBytes: ByteArray): FloatArray {
        val totalSamples = audioBytes.size / 2  // 2 bytes per 16-bit sample
        val floatSamples = FloatArray(totalSamples)

        // Create ByteBuffer with little-endian byte order (standard for WAV)
        val byteBuffer = ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until totalSamples) {
            val sample = byteBuffer.short.toInt()
            // Normalize 16-bit PCM to [-1.0, 1.0]
            floatSamples[i] = if (sample < 0) {
                sample / 32768.0f
            } else {
                sample / 32767.0f
            }

        }

        return floatSamples
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        // enableEdgeToEdge()

        try {
            Os.setenv("ADSP_LIBRARY_PATH", applicationInfo.nativeLibraryDir, true)
            Os.setenv("LD_LIBRARY_PATH", applicationInfo.nativeLibraryDir, true)
        } catch (e: ErrnoException) {
            finish()
        }

        setContentView(R.layout.activity_main)

        recordButton = findViewById(R.id.record_button)
        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        // Check if minimum buffer size is valid
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size")
            Toast.makeText(this, "Audio recording not supported on this device", Toast.LENGTH_LONG).show()
        }

    }

    private fun runWhisper() {
        // The entire audio flow:
        val wavFile = File(getExternalFilesDir(null), "audio_record.wav") // do this better
        val absolutePath: String = wavFile.absolutePath
        val PCMBytes = readWavPcmBytes(absolutePath)
        val inputFloatArray = convertPcm16ToFloat(PCMBytes)

        val tensor1 = Tensor.fromBlob(inputFloatArray,
            longArrayOf(inputFloatArray.size.toLong())
        )
        val module = Module.load("/data/local/tmp/whisper/whisper_preprocess.pte")
        val eValue1 = EValue.from(tensor1)
        val result = module.forward(eValue1)[0].toTensor().dataAsFloatArray

        // Convert result (FloatArray) to raw ByteArray to feed into runner transcribe function
        val byteBuffer = ByteBuffer.allocate(result.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        result.forEach { byteBuffer.putFloat(it) }
        val byteArray = arrayOf(byteBuffer.array())

        val whisperModule = WhisperModule("/data/local/tmp/whisper/whisper_qnn_16a8w.pte",
            "/data/local/tmp/whisper/tokenizer.json")

        Log.v(TAG, "Starting transcribe")
        whisperModule.transcribe(128, byteArray, this@MainActivity) // this runs runner.transcribe()
        Log.v(TAG, "Finished transcribe")
        Toast.makeText(this, Output.substring(37, Output.length-13), Toast.LENGTH_LONG).show()
        // hack to remove start and end tokens; ideally the runner should not do callback on these tokens

    }

    override fun onResult(result: String) {
        Log.v(TAG, "Called callback: here's the current output")
        Output += result
        Log.v(TAG, Output)

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
                        Toast.makeText(this, "Failed to initialize audio recorder", Toast.LENGTH_SHORT).show()
                        return
                    }

                    audioRecord?.startRecording()
                    isRecording = true
                    // recordButton.text = "Stop Recording"

                    recordButton.text = "Recording... (5s)"
                    recordButton.isEnabled = false // Disable button during recording

                    // Schedule automatic stop after 5 seconds
                    stopRecordingRunnable = Runnable {
                        stopRecording()
                    }
                    handler.postDelayed(stopRecordingRunnable!!, RECORDING_DURATION_MS)

                    // recordButton.text = "Finished recording"

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
                                Toast.makeText(this@MainActivity, "Recording saved", Toast.LENGTH_SHORT).show()
                                runWhisper()
                            }



                        } catch (e: IOException) {
                            Log.e(TAG, "Recording failed", e)
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Recording failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    recordingThread?.start()

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start recording", e)
                    Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show()
                }
            }

            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // Show rationale and request permission
                Toast.makeText(
                    this,
                    "Audio recording permission is needed to record audio",
                    Toast.LENGTH_LONG
                ).show()
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }

            else -> {
                // Request permission directly
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
        recordButton.text = "Finished Recording"

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
            Toast.makeText(this, "Audio recording permission required", Toast.LENGTH_LONG).show()
        }
    }

}
