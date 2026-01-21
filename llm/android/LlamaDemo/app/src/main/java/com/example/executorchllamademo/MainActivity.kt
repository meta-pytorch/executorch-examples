/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.Manifest
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.MediaStore
import android.system.ErrnoException
import android.system.Os
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONException
import org.json.JSONObject
import org.pytorch.executorch.ExecutorchRuntimeException
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), Runnable, LlmCallback {

    private lateinit var editTextMessage: EditText
    private lateinit var thinkModeButton: ImageButton
    private lateinit var sendButton: ImageButton
    private lateinit var galleryButton: ImageButton
    private lateinit var cameraButton: ImageButton
    private lateinit var audioButton: ImageButton
    private lateinit var messagesView: ListView
    private lateinit var messageAdapter: MessageAdapter
    private var module: LlmModule? = null
    private var resultMessage: Message? = null
    private lateinit var settingsButton: ImageButton
    private lateinit var memoryView: TextView
    private lateinit var pickGallery: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var cameraRoll: ActivityResultLauncher<Uri>
    private var selectedImageUri: MutableList<Uri>? = null
    private lateinit var mediaPreviewConstraintLayout: ConstraintLayout
    private lateinit var addMediaLayout: LinearLayout
    private var cameraImageUri: Uri? = null
    private lateinit var demoSharedPreferences: DemoSharedPreferences
    private lateinit var currentSettingsFields: SettingsFields
    private lateinit var memoryUpdateHandler: Handler
    private lateinit var memoryUpdater: Runnable
    private var thinkMode = false
    private var promptID = 0
    private lateinit var executor: Executor
    private var sawStartHeaderId = false
    private var audioFileToPrefill: String? = null
    private var shouldAddSystemPrompt = true
    private var isModelReady = false
    private var isGenerating = false

    override fun onResult(result: String) {
        var processedResult = result

        if (processedResult == PromptFormat.getStopToken(currentSettingsFields.modelType)) {
            // For gemma and llava, we need to call stop() explicitly
            if (currentSettingsFields.modelType == ModelType.GEMMA_3 ||
                currentSettingsFields.modelType == ModelType.LLAVA_1_5
            ) {
                module?.stop()
            }
            return
        }

        processedResult = PromptFormat.replaceSpecialToken(currentSettingsFields.modelType, processedResult)

        if (currentSettingsFields.modelType == ModelType.LLAMA_3 &&
            processedResult == "<|start_header_id|>"
        ) {
            sawStartHeaderId = true
        }
        if (currentSettingsFields.modelType == ModelType.LLAMA_3 &&
            processedResult == "<|end_header_id|>"
        ) {
            sawStartHeaderId = false
            return
        }
        if (sawStartHeaderId) {
            return
        }

        val keepResult = !(processedResult == "\n" || processedResult == "\n\n") ||
                resultMessage?.text?.isNotEmpty() == true
        if (keepResult) {
            resultMessage?.appendText(processedResult)
            run()
        }
    }

    override fun onStats(stats: String) {
        runOnUiThread {
            resultMessage?.let { msg ->
                var tps = 0f
                try {
                    val jsonObject = JSONObject(stats)
                    val numGeneratedTokens = jsonObject.getInt("generated_tokens")
                    val inferenceEndMs = jsonObject.getInt("inference_end_ms")
                    val promptEvalEndMs = jsonObject.getInt("prompt_eval_end_ms")
                    tps = numGeneratedTokens.toFloat() / (inferenceEndMs - promptEvalEndMs) * 1000
                } catch (e: JSONException) {
                    Log.e("LLM", "Error parsing JSON: ${e.message}")
                }
                msg.tokensPerSecond = tps
                messageAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun setLocalModel(
        modelPath: String,
        tokenizerPath: String,
        dataPath: String,
        temperature: Float
    ) {
        val modelLoadingMessage = Message("Loading model...", false, MessageType.SYSTEM, 0)
        ETLogging.getInstance().log(
            "Loading model $modelPath with tokenizer $tokenizerPath data path $dataPath"
        )
        runOnUiThread {
            isModelReady = false
            updateSendButtonState()
            messageAdapter.add(modelLoadingMessage)
            messageAdapter.notifyDataSetChanged()
        }

        val runStartTime = System.currentTimeMillis()
        // Create LlmModule with dataPath
        module = if (dataPath.isEmpty()) {
            LlmModule(
                ModelUtils.getModelCategory(
                    currentSettingsFields.modelType,
                    currentSettingsFields.backendType
                ),
                modelPath,
                tokenizerPath,
                temperature
            )
        } else {
            LlmModule(
                ModelUtils.getModelCategory(
                    currentSettingsFields.modelType,
                    currentSettingsFields.backendType
                ),
                modelPath,
                tokenizerPath,
                temperature,
                dataPath
            )
        }

        var loadDuration = System.currentTimeMillis() - runStartTime
        var modelInfo: String

        try {
            module?.load()
            val pteName = modelPath.substringAfterLast('/')
            val tokenizerName = tokenizerPath.substringAfterLast('/')
            modelInfo = "Successfully loaded model. $pteName and tokenizer $tokenizerName in ${loadDuration.toFloat() / 1000} sec. You can send text or image for inference"

            if (currentSettingsFields.modelType == ModelType.LLAVA_1_5) {
                ETLogging.getInstance().log("Llava start prefill prompt")
                module?.prefillPrompt(PromptFormat.getLlavaPresetPrompt())
                ETLogging.getInstance().log("Llava completes prefill prompt")
            }
        } catch (e: ExecutorchRuntimeException) {
            modelInfo = "${e.message}\n"
            loadDuration = 0

            val builder = AlertDialog.Builder(this)
            builder.setTitle("Model Load failure: $modelInfo")
            runOnUiThread {
                val alert = builder.create()
                alert.show()
            }
        }

        val modelLoadedMessage = Message(modelInfo, false, MessageType.SYSTEM, 0)
        val modelLoggingInfo = "Model path: $modelPath\n" +
                "Tokenizer path: $tokenizerPath\n" +
                "Backend: ${currentSettingsFields.backendType}\n" +
                "ModelType: ${ModelUtils.getModelCategory(currentSettingsFields.modelType, currentSettingsFields.backendType)}\n" +
                "Temperature: $temperature\n" +
                "Model loaded time: $loadDuration ms"
        ETLogging.getInstance().log("Load complete. $modelLoggingInfo")

        runOnUiThread {
            isModelReady = true
            updateSendButtonState()
            messageAdapter.remove(modelLoadingMessage)
            messageAdapter.add(modelLoadedMessage)
            messageAdapter.notifyDataSetChanged()
        }
    }

    private fun loadLocalModelAndParameters(
        modelFilePath: String,
        tokenizerFilePath: String,
        dataPath: String,
        temperature: Float
    ) {
        Thread {
            setLocalModel(modelFilePath, tokenizerFilePath, dataPath, temperature)
        }.start()
    }

    private fun populateExistingMessages(existingMsgJSON: String) {
        val gson = Gson()
        val type = object : TypeToken<ArrayList<Message>>() {}.type
        val savedMessages: ArrayList<Message> = gson.fromJson(existingMsgJSON, type)
        for (msg in savedMessages) {
            messageAdapter.add(msg)
        }
        messageAdapter.notifyDataSetChanged()
    }

    private fun setPromptID(): Int {
        return messageAdapter.getMaxPromptID() + 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= 21) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.status_bar)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.nav_bar)
        }

        try {
            Os.setenv("ADSP_LIBRARY_PATH", applicationInfo.nativeLibraryDir, true)
            Os.setenv("LD_LIBRARY_PATH", applicationInfo.nativeLibraryDir, true)
        } catch (e: ErrnoException) {
            finish()
        }

        thinkModeButton = requireViewById(R.id.thinkModeButton)
        editTextMessage = requireViewById(R.id.editTextMessage)
        sendButton = requireViewById(R.id.sendButton)
        sendButton.isEnabled = false

        // Add TextWatcher to enable/disable send button based on input
        editTextMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSendButtonState()
            }
        })

        messagesView = requireViewById(R.id.messages_view)
        messageAdapter = MessageAdapter(this, R.layout.sent_message, ArrayList())
        messagesView.adapter = messageAdapter
        demoSharedPreferences = DemoSharedPreferences(applicationContext)
        val existingMsgJSON = demoSharedPreferences.getSavedMessages()
        if (existingMsgJSON.isNotEmpty()) {
            populateExistingMessages(existingMsgJSON)
            promptID = setPromptID()
        }

        settingsButton = requireViewById(R.id.settings)
        settingsButton.setOnClickListener {
            val myIntent = Intent(this@MainActivity, SettingsActivity::class.java)
            this@MainActivity.startActivity(myIntent)
        }

        thinkModeButton.setOnClickListener {
            if (thinkMode) {
                thinkMode = false
                thinkModeButton.setImageDrawable(
                    ResourcesCompat.getDrawable(resources, R.drawable.baseline_lightbulb_24, null)
                )
            } else {
                thinkMode = true
                thinkModeButton.setImageDrawable(
                    ResourcesCompat.getDrawable(resources, R.drawable.blue_lightbulb_24, null)
                )
            }
            runOnUiThread {
                val thinkingModeText = if (thinkMode) "on" else "off"
                messageAdapter.add(
                    Message("Thinking mode is $thinkingModeText", false, MessageType.SYSTEM, 0)
                )
                messageAdapter.notifyDataSetChanged()
            }
        }

        currentSettingsFields = SettingsFields()
        memoryUpdateHandler = Handler(Looper.getMainLooper())
        onModelRunStopped()
        setupMediaButton()
        setupGalleryPicker()
        setupCameraRoll()
        startMemoryUpdate()
        setupShowLogsButton()
        executor = Executors.newSingleThreadExecutor()
    }

    override fun onPause() {
        super.onPause()
        demoSharedPreferences.addMessages(messageAdapter)
    }

    override fun onResume() {
        super.onResume()
        // Check for if settings parameters have changed
        val gson = Gson()
        val settingsFieldsJSON = demoSharedPreferences.getSettings()
        if (settingsFieldsJSON.isNotEmpty()) {
            val updatedSettingsFields = gson.fromJson(settingsFieldsJSON, SettingsFields::class.java)
            if (updatedSettingsFields == null) {
                // Added this check, because gson.fromJson can return null
                askUserToSelectModel()
                return
            }
            val isUpdated = currentSettingsFields != updatedSettingsFields
            val isLoadModel = updatedSettingsFields.isLoadModel
            setBackendMode(updatedSettingsFields.backendType)
            if (isUpdated) {
                checkForClearChatHistory(updatedSettingsFields)
                // Update current to point to the latest
                currentSettingsFields = SettingsFields(updatedSettingsFields)

                if (isLoadModel) {
                    // If users change the model file, but not pressing loadModelButton, we won't load the new model
                    checkForUpdateAndReloadModel(updatedSettingsFields)
                } else if (module == null) {
                    // Only ask user to select model if no model is currently loaded
                    askUserToSelectModel()
                }
            } else {
                // Settings not updated, but still check if model/tokenizer is not selected
                val modelPath = updatedSettingsFields.modelFilePath
                val tokenizerPath = updatedSettingsFields.tokenizerFilePath
                if (modelPath.isEmpty() || tokenizerPath.isEmpty()) {
                    askUserToSelectModel()
                }
            }
        } else if (module == null) {
            // Only ask user to select model if no model is currently loaded
            askUserToSelectModel()
        }
    }

    private fun setBackendMode(backendType: BackendType) {
        when (backendType) {
            BackendType.XNNPACK, BackendType.QUALCOMM, BackendType.VULKAN -> setXNNPACKMode()
            BackendType.MEDIATEK -> setMediaTekMode()
        }
    }

    private fun setXNNPACKMode() {
        requireViewById<View>(R.id.addMediaButton).visibility = View.VISIBLE
    }

    private fun setMediaTekMode() {
        requireViewById<View>(R.id.addMediaButton).visibility = View.GONE
    }

    private fun checkForClearChatHistory(updatedSettingsFields: SettingsFields) {
        if (updatedSettingsFields.isClearChatHistory) {
            messageAdapter.clear()
            messageAdapter.notifyDataSetChanged()
            demoSharedPreferences.removeExistingMessages()
            // changing to false since chat history has been cleared.
            updatedSettingsFields.saveIsClearChatHistory(false)
            demoSharedPreferences.addSettings(updatedSettingsFields)
            module?.resetContext()
        }
    }

    private fun checkForUpdateAndReloadModel(updatedSettingsFields: SettingsFields) {
        // TODO need to add 'load model' in settings and queue loading based on that
        val modelPath = updatedSettingsFields.modelFilePath
        val tokenizerPath = updatedSettingsFields.tokenizerFilePath
        val dataPath = updatedSettingsFields.dataPath
        val temperature = updatedSettingsFields.temperature
        if (modelPath.isNotEmpty() && tokenizerPath.isNotEmpty()) {
            if (updatedSettingsFields.isLoadModel ||
                modelPath != currentSettingsFields.modelFilePath ||
                tokenizerPath != currentSettingsFields.tokenizerFilePath ||
                dataPath != currentSettingsFields.dataPath ||
                temperature != currentSettingsFields.temperature
            ) {
                loadLocalModelAndParameters(
                    updatedSettingsFields.modelFilePath,
                    updatedSettingsFields.tokenizerFilePath,
                    updatedSettingsFields.dataPath,
                    updatedSettingsFields.temperature.toFloat()
                )
                updatedSettingsFields.saveLoadModelAction(false)
                demoSharedPreferences.addSettings(updatedSettingsFields)
            }
        } else {
            askUserToSelectModel()
        }
    }

    private fun askUserToSelectModel() {
        val askLoadModel = "To get started, select your desired model and tokenizer from the top right corner"
        val askLoadModelMessage = Message(askLoadModel, false, MessageType.SYSTEM, 0)
        ETLogging.getInstance().log(askLoadModel)
        runOnUiThread {
            if (!messageAdapter.isDuplicateSystemMessage(askLoadModel)) {
                messageAdapter.add(askLoadModelMessage)
                messageAdapter.notifyDataSetChanged()
            }
            AlertDialog.Builder(this)
                .setTitle("Please Select a Model")
                .setMessage("Please select a model and tokenizer from the settings (top right corner) to get started.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun setupShowLogsButton() {
        val showLogsButton = requireViewById<ImageButton>(R.id.showLogsButton)
        showLogsButton.setOnClickListener {
            val myIntent = Intent(this@MainActivity, LogsActivity::class.java)
            this@MainActivity.startActivity(myIntent)
        }
    }

    private fun setupMediaButton() {
        addMediaLayout = requireViewById(R.id.addMediaLayout)
        addMediaLayout.visibility = View.GONE // We hide this initially

        val addMediaButton = requireViewById<ImageButton>(R.id.addMediaButton)
        addMediaButton.setOnClickListener {
            if (addMediaLayout.visibility == View.VISIBLE) {
                // Collapse: hide the media layout and change icon back to +
                addMediaLayout.visibility = View.GONE
                addMediaButton.setImageResource(R.drawable.baseline_add_24)
            } else {
                // Expand: show the media layout and change icon to collapse (down arrow)
                addMediaLayout.visibility = View.VISIBLE
                addMediaButton.setImageResource(R.drawable.expand_circle_down)
            }
        }

        galleryButton = requireViewById(R.id.galleryButton)
        galleryButton.setOnClickListener {
            // Launch the photo picker and let the user choose only images.
            pickGallery.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    .build()
            )
        }

        audioButton = requireViewById(R.id.audioButton)
        audioButton.setOnClickListener {
            addMediaLayout.visibility = View.GONE
            val audioFiles = SettingsActivity.listLocalFile("/data/local/tmp/audio/", arrayOf(".bin"))
            AlertDialog.Builder(this)
                .setTitle("Select audio feature path")
                .setSingleChoiceItems(audioFiles, -1) { dialog, item ->
                    audioFileToPrefill = audioFiles[item]
                    messageAdapter.add(
                        Message("Selected audio: $audioFileToPrefill", false, MessageType.SYSTEM, 0)
                    )
                    messageAdapter.notifyDataSetChanged()
                    dialog.dismiss()
                }
                .create()
                .show()
        }

        cameraButton = requireViewById(R.id.cameraButton)
        cameraButton.setOnClickListener {
            Log.d("CameraRoll", "Check permission")
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_IMAGE_CAPTURE
                )
            } else {
                launchCamera()
            }
        }
    }

    private fun setupCameraRoll() {
        // Registers a camera roll activity launcher.
        cameraRoll = registerForActivityResult(ActivityResultContracts.TakePicture()) { result ->
            if (result && cameraImageUri != null) {
                Log.d("CameraRoll", "Photo saved to uri: $cameraImageUri")
                addMediaLayout.visibility = View.GONE
                val uris = mutableListOf<Uri>()
                uris.add(cameraImageUri!!)
                showMediaPreview(uris)
            } else {
                // Delete the temp image file based on the url since the photo is not successfully taken
                cameraImageUri?.let { uri ->
                    contentResolver.delete(uri, null, null)
                    Log.d("CameraRoll", "No photo taken. Delete temp uri")
                }
            }
        }

        mediaPreviewConstraintLayout = requireViewById(R.id.mediaPreviewConstraintLayout)
        val mediaPreviewCloseButton = requireViewById<ImageButton>(R.id.mediaPreviewCloseButton)
        mediaPreviewCloseButton.setOnClickListener {
            mediaPreviewConstraintLayout.visibility = View.GONE
            selectedImageUri = null
        }

        val addMoreImageButton = requireViewById<ImageButton>(R.id.addMoreImageButton)
        addMoreImageButton.setOnClickListener {
            Log.d("addMore", "clicked")
            mediaPreviewConstraintLayout.visibility = View.GONE
            // Direct user to select type of input
            cameraButton.callOnClick()
        }
    }

    private fun updateMemoryUsage(): String {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
            ?: return "---"
        activityManager.getMemoryInfo(memoryInfo)
        val totalMem = memoryInfo.totalMem / (1024 * 1024)
        val availableMem = memoryInfo.availMem / (1024 * 1024)
        val usedMem = totalMem - availableMem
        return "${usedMem}MB"
    }

    private fun startMemoryUpdate() {
        memoryView = requireViewById(R.id.ram_usage_live)
        memoryUpdater = object : Runnable {
            override fun run() {
                memoryView.text = updateMemoryUsage()
                memoryUpdateHandler.postDelayed(this, 1000)
            }
        }
        memoryUpdateHandler.post(memoryUpdater)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_IMAGE_CAPTURE && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Log.d("CameraRoll", "Permission denied")
            }
        }
    }

    private fun launchCamera() {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, "New Picture")
            put(MediaStore.Images.Media.DESCRIPTION, "From Camera")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera/")
        }
        cameraImageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        cameraImageUri?.let { cameraRoll.launch(it) }
    }

    private fun setupGalleryPicker() {
        // Registers a photo picker activity launcher in single-select mode.
        pickGallery = registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(MAX_NUM_OF_IMAGES)
        ) { uris ->
            if (uris.isNotEmpty()) {
                Log.d("PhotoPicker", "Selected URIs: $uris")
                addMediaLayout.visibility = View.GONE
                for (uri in uris) {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                showMediaPreview(uris)
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }

        mediaPreviewConstraintLayout = requireViewById(R.id.mediaPreviewConstraintLayout)
        val mediaPreviewCloseButton = requireViewById<ImageButton>(R.id.mediaPreviewCloseButton)
        mediaPreviewCloseButton.setOnClickListener {
            mediaPreviewConstraintLayout.visibility = View.GONE
            selectedImageUri = null
        }

        val addMoreImageButton = requireViewById<ImageButton>(R.id.addMoreImageButton)
        addMoreImageButton.setOnClickListener {
            Log.d("addMore", "clicked")
            mediaPreviewConstraintLayout.visibility = View.GONE
            // Direct user to select type of input
            galleryButton.callOnClick()
        }
    }

    private fun getInputImageSideSize(): Int {
        return when (currentSettingsFields.modelType) {
            ModelType.LLAVA_1_5 -> 336
            ModelType.GEMMA_3 -> 896
            else -> throw IllegalArgumentException("Unsupported model type: ${currentSettingsFields.modelType}")
        }
    }

    private fun getProcessedImagesForModel(uris: List<Uri>?): List<ETImage> {
        val imageList = mutableListOf<ETImage>()
        uris?.forEach { uri ->
            imageList.add(ETImage(contentResolver, uri, getInputImageSideSize()))
        }
        return imageList
    }

    private fun showMediaPreview(uris: List<Uri>) {
        if (selectedImageUri == null) {
            selectedImageUri = uris.toMutableList()
        } else {
            selectedImageUri?.addAll(uris)
        }

        if ((selectedImageUri?.size ?: 0) > MAX_NUM_OF_IMAGES) {
            selectedImageUri = selectedImageUri?.subList(0, MAX_NUM_OF_IMAGES)
            Toast.makeText(this, "Only max $MAX_NUM_OF_IMAGES images are allowed", Toast.LENGTH_SHORT).show()
        }
        Log.d("mSelectedImageUri", "${selectedImageUri?.size} $selectedImageUri")

        mediaPreviewConstraintLayout.visibility = View.VISIBLE

        val imageViews = listOf<ImageView>(
            requireViewById(R.id.mediaPreviewImageView1),
            requireViewById(R.id.mediaPreviewImageView2),
            requireViewById(R.id.mediaPreviewImageView3),
            requireViewById(R.id.mediaPreviewImageView4),
            requireViewById(R.id.mediaPreviewImageView5)
        )

        // Hide all the image views (reset state)
        imageViews.forEach { it.visibility = View.GONE }

        // Only show/render those that have proper Image URIs
        selectedImageUri?.forEachIndexed { index, uri ->
            imageViews[index].visibility = View.VISIBLE
            imageViews[index].setImageURI(uri)
        }

        // For LLava, we want to call prefill_image as soon as an image is selected
        // Llava only support 1 image for now
        if (currentSettingsFields.modelType == ModelType.LLAVA_1_5 ||
            currentSettingsFields.modelType == ModelType.GEMMA_3
        ) {
            val processedImageList = getProcessedImagesForModel(selectedImageUri)
            if (processedImageList.isNotEmpty()) {
                messageAdapter.add(
                    Message("Llava - Starting image Prefill.", false, MessageType.SYSTEM, 0)
                )
                messageAdapter.notifyDataSetChanged()
                executor.execute {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
                    ETLogging.getInstance().log("Starting runnable prefill image")
                    val img = processedImageList[0]
                    ETLogging.getInstance().log("Llava start prefill image")
                    if (currentSettingsFields.modelType == ModelType.LLAVA_1_5) {
                        module?.prefillImages(
                            img.getInts(),
                            img.width,
                            img.height,
                            ModelUtils.VISION_MODEL_IMAGE_CHANNELS
                        )
                    } else if (currentSettingsFields.modelType == ModelType.GEMMA_3) {
                        module?.prefillImages(
                            img.getFloats(),
                            img.width,
                            img.height,
                            ModelUtils.VISION_MODEL_IMAGE_CHANNELS
                        )
                    }
                }
            }
        }
    }

    private fun addSelectedImagesToChatThread(selectedImageUri: List<Uri>?) {
        if (selectedImageUri == null) {
            return
        }
        mediaPreviewConstraintLayout.visibility = View.GONE
        for (imageURI in selectedImageUri) {
            Log.d("image uri ", "test ${imageURI.path}")
            messageAdapter.add(Message(imageURI.toString(), true, MessageType.IMAGE, 0))
        }
        messageAdapter.notifyDataSetChanged()
    }

    private fun updateSendButtonState() {
        val hasText = editTextMessage.text.toString().trim().isNotEmpty()
        val enabled = isModelReady && !isGenerating && hasText
        sendButton.isEnabled = enabled
        sendButton.alpha = if (enabled) 1.0f else 0.3f
    }

    private fun onModelRunStarted() {
        isGenerating = true
        sendButton.isEnabled = true
        sendButton.alpha = 1.0f
        sendButton.setImageResource(R.drawable.baseline_stop_24)
        sendButton.setOnClickListener {
            module?.stop()
        }
    }

    private fun onModelRunStopped() {
        isGenerating = false
        updateSendButtonState()
        sendButton.setImageResource(R.drawable.baseline_send_24)
        sendButton.setOnClickListener {
            try {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
            } catch (e: Exception) {
                ETLogging.getInstance().log("Keyboard dismissal error: ${e.message}")
            }
            addSelectedImagesToChatThread(selectedImageUri)
            val rawPrompt = editTextMessage.text.toString()
            val finalPrompt: String
            // For LLaVA, the first turn uses a special template since the preset prompt
            // already ends with "USER: "
            if (currentSettingsFields.modelType == ModelType.LLAVA_1_5 && shouldAddSystemPrompt) {
                finalPrompt = PromptFormat.getLlavaFirstTurnUserPrompt()
                    .replace(PromptFormat.USER_PLACEHOLDER, rawPrompt)
            } else {
                finalPrompt = (if (shouldAddSystemPrompt) currentSettingsFields.getFormattedSystemPrompt() else "") +
                        currentSettingsFields.getFormattedUserPrompt(rawPrompt, thinkMode)
            }
            shouldAddSystemPrompt = false
            // We store raw prompt into message adapter, because we don't want to show the extra
            // tokens from system prompt
            messageAdapter.add(Message(rawPrompt, true, MessageType.TEXT, promptID))
            messageAdapter.notifyDataSetChanged()
            editTextMessage.setText("")
            resultMessage = Message("", false, MessageType.TEXT, promptID)
            messageAdapter.add(resultMessage)
            // Scroll to bottom of the list
            messagesView.smoothScrollToPosition(messageAdapter.count - 1)
            // After images are added to prompt and chat thread, we clear the imageURI list
            // Note: This has to be done after imageURIs are no longer needed by LlmModule
            selectedImageUri = null
            promptID++
            executor.execute {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
                ETLogging.getInstance().log("starting runnable generate()")
                runOnUiThread { onModelRunStarted() }
                val generateStartTime = System.currentTimeMillis()
                if (ModelUtils.getModelCategory(
                        currentSettingsFields.modelType,
                        currentSettingsFields.backendType
                    ) == ModelUtils.VISION_MODEL
                ) {
                    if (currentSettingsFields.modelType == ModelType.VOXTRAL && audioFileToPrefill != null) {
                        prefillVoxtralAudio(audioFileToPrefill!!, finalPrompt)
                        audioFileToPrefill = null
                        module?.generate("", ModelUtils.VISION_MODEL_SEQ_LEN, this@MainActivity, false)
                    } else {
                        module?.generate(finalPrompt, ModelUtils.VISION_MODEL_SEQ_LEN, this@MainActivity, false)
                    }
                } else if (currentSettingsFields.modelType == ModelType.LLAMA_GUARD_3) {
                    val llamaGuardPromptForClassification =
                        PromptFormat.getFormattedLlamaGuardPrompt(rawPrompt)
                    ETLogging.getInstance().log("Running inference.. prompt=$llamaGuardPromptForClassification")
                    module?.generate(
                        llamaGuardPromptForClassification,
                        llamaGuardPromptForClassification.length + 64,
                        this@MainActivity,
                        false
                    )
                } else {
                    ETLogging.getInstance().log("Running inference.. prompt=$finalPrompt")
                    module?.generate(finalPrompt, ModelUtils.TEXT_MODEL_SEQ_LEN, this@MainActivity, false)
                }

                val generateDuration = System.currentTimeMillis() - generateStartTime
                resultMessage?.totalGenerationTime = generateDuration
                runOnUiThread { onModelRunStopped() }
                ETLogging.getInstance().log("Inference completed")
            }
        }
        messageAdapter.notifyDataSetChanged()
    }

    private fun prefillVoxtralAudio(audioFeaturePath: String, textPrompt: String) {
        try {
            val byteData = Files.readAllBytes(Paths.get(audioFeaturePath))
            val buffer = ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN)
            val floatCount = byteData.size / java.lang.Float.BYTES
            val floats = FloatArray(floatCount)

            // Read floats from the buffer
            for (i in 0 until floatCount) {
                floats[i] = buffer.float
            }
            val bins = 128
            val frames = 3000
            val batchSize = floatCount / (bins * frames)
            module?.prefillPrompt("<s>[INST][BEGIN_AUDIO]")
            module?.prefillAudio(floats, batchSize, bins, frames)
            module?.prefillPrompt("$textPrompt[/INST]")
        } catch (e: IOException) {
            Log.e("AudioPrefill", "Audio file error")
        }
    }

    override fun run() {
        runOnUiThread {
            messageAdapter.notifyDataSetChanged()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        if (addMediaLayout.visibility == View.VISIBLE) {
            addMediaLayout.visibility = View.GONE
        } else {
            // Default behavior of back button
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        memoryUpdateHandler.removeCallbacks(memoryUpdater)
        // This is to cover the case where the app is shutdown when user is on MainActivity but
        // never clicked on the logsActivity
        ETLogging.getInstance().saveLogs()
    }

    companion object {
        private const val MAX_NUM_OF_IMAGES = 5
        private const val REQUEST_IMAGE_CAPTURE = 1
    }
}
