/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.gson.Gson
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var backendTextView: TextView
    private lateinit var modelTextView: TextView
    private lateinit var tokenizerTextView: TextView
    private lateinit var dataPathTextView: TextView
    private lateinit var modelTypeTextView: TextView
    private lateinit var systemPromptEditText: EditText
    private lateinit var userPromptEditText: EditText
    private lateinit var loadModelButton: Button

    // settingsFields is the single source of truth for all settings
    lateinit var settingsFields: SettingsFields

    private lateinit var demoSharedPreferences: DemoSharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (Build.VERSION.SDK_INT >= 21) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.status_bar)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.nav_bar)
        }

        ViewCompat.setOnApplyWindowInsetsListener(requireViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        demoSharedPreferences = DemoSharedPreferences(baseContext)
        settingsFields = SettingsFields()
        setupSettings()
    }

    private fun setupSettings() {
        backendTextView = requireViewById(R.id.backendTextView)
        modelTextView = requireViewById(R.id.modelTextView)
        tokenizerTextView = requireViewById(R.id.tokenizerTextView)
        dataPathTextView = requireViewById(R.id.dataPathTextView)
        modelTypeTextView = requireViewById(R.id.modelTypeTextView)
        val backendImageButton = requireViewById<ImageButton>(R.id.backendImageButton)
        val modelImageButton = requireViewById<ImageButton>(R.id.modelImageButton)
        val tokenizerImageButton = requireViewById<ImageButton>(R.id.tokenizerImageButton)
        val dataPathImageButton = requireViewById<ImageButton>(R.id.dataPathImageButton)
        val modelTypeImageButton = requireViewById<ImageButton>(R.id.modelTypeImageButton)
        systemPromptEditText = requireViewById(R.id.systemPromptText)
        userPromptEditText = requireViewById(R.id.userPromptText)
        loadSettings()

        // TODO: The two setOnClickListeners will be removed after file path issue is resolved
        backendImageButton.setOnClickListener { setupBackendSelectorDialog() }
        requireViewById<View>(R.id.backendLayout).setOnClickListener { setupBackendSelectorDialog() }

        modelImageButton.setOnClickListener { setupModelSelectorDialog() }
        requireViewById<View>(R.id.modelLayout).setOnClickListener { setupModelSelectorDialog() }

        tokenizerImageButton.setOnClickListener { setupTokenizerSelectorDialog() }
        requireViewById<View>(R.id.tokenizerLayout).setOnClickListener { setupTokenizerSelectorDialog() }

        dataPathImageButton.setOnClickListener { setupDataPathSelectorDialog() }
        requireViewById<View>(R.id.dataPathLayout).setOnClickListener { setupDataPathSelectorDialog() }

        modelTypeImageButton.setOnClickListener { setupModelTypeSelectorDialog() }
        requireViewById<View>(R.id.modelTypeLayout).setOnClickListener { setupModelTypeSelectorDialog() }

        val modelFilePath = settingsFields.modelFilePath
        if (modelFilePath.isNotEmpty()) {
            modelTextView.text = getFilenameFromPath(modelFilePath)
        }

        val tokenizerFilePath = settingsFields.tokenizerFilePath
        if (tokenizerFilePath.isNotEmpty()) {
            tokenizerTextView.text = getFilenameFromPath(tokenizerFilePath)
        }

        val dataPath = settingsFields.dataPath
        if (dataPath.isNotEmpty()) {
            dataPathTextView.text = getFilenameFromPath(dataPath)
        }

        val modelType = settingsFields.modelType
        ETLogging.getInstance().log("mModelType from settings $modelType")
        modelTypeTextView.text = modelType.toString()

        val backendType = settingsFields.backendType
        ETLogging.getInstance().log("mBackendType from settings $backendType")
        backendTextView.text = backendType.toString()
        setBackendSettingMode()

        setupParameterSettings()
        setupPromptSettings()
        setupClearChatHistoryButton()
        setupLoadModelButton()
    }

    private fun setupLoadModelButton() {
        loadModelButton = requireViewById(R.id.loadModelButton)
        // Enable button if valid pre-filled paths are available from previous session
        updateLoadModelButtonState()
        loadModelButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Load Model")
                .setMessage("Do you really want to load the new model?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    // Save current UI selections to settings before loading
                    saveSettings()
                    settingsFields.saveLoadModelAction(true)
                    loadModelButton.isEnabled = false
                    onBackPressed()
                }
                .setNegativeButton(android.R.string.no, null)
                .show()
        }
    }

    private fun setupClearChatHistoryButton() {
        val clearChatButton = requireViewById<Button>(R.id.clearChatButton)
        clearChatButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Chat History")
                .setMessage("Do you really want to delete chat history?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    settingsFields.saveIsClearChatHistory(true)
                }
                .setNegativeButton(android.R.string.no, null)
                .show()
        }
    }

    private fun setupParameterSettings() {
        setupTemperatureSettings()
    }

    private fun setupTemperatureSettings() {
        val temperature = settingsFields.temperature
        val temperatureEditText = requireViewById<EditText>(R.id.temperatureEditText)
        temperatureEditText.setText(temperature.toString())
        temperatureEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val newTemperature = s.toString().toDoubleOrNull() ?: return
                settingsFields.saveParameters(newTemperature)
                // This is needed because temperature is changed together with model loading
                // Once temperature is no longer in LlmModule constructor, we can remove this
                settingsFields.saveLoadModelAction(true)
                demoSharedPreferences.addSettings(settingsFields)
            }
        })
    }

    private fun setupPromptSettings() {
        setupSystemPromptSettings()
        setupUserPromptSettings()
    }

    private fun setupSystemPromptSettings() {
        val systemPrompt = settingsFields.systemPrompt
        systemPromptEditText.setText(systemPrompt)
        systemPromptEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                settingsFields.savePrompts(s.toString(), settingsFields.userPrompt)
            }
        })

        val resetSystemPrompt = requireViewById<ImageButton>(R.id.resetSystemPrompt)
        resetSystemPrompt.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset System Prompt")
                .setMessage("Do you really want to reset system prompt?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    // Clear the messageAdapter and sharedPreference
                    systemPromptEditText.setText(PromptFormat.DEFAULT_SYSTEM_PROMPT)
                }
                .setNegativeButton(android.R.string.no, null)
                .show()
        }
    }

    private fun setupUserPromptSettings() {
        val userPrompt = settingsFields.userPrompt
        userPromptEditText.setText(userPrompt)
        userPromptEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString()
                if (isValidUserPrompt(text)) {
                    settingsFields.savePrompts(settingsFields.systemPrompt, text)
                } else {
                    showInvalidPromptDialog()
                }
            }
        })

        val resetUserPrompt = requireViewById<ImageButton>(R.id.resetUserPrompt)
        resetUserPrompt.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Prompt Template")
                .setMessage("Do you really want to reset the prompt template?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    // Clear the messageAdapter and sharedPreference
                    userPromptEditText.setText(PromptFormat.getUserPromptTemplate(settingsFields.modelType))
                }
                .setNegativeButton(android.R.string.no, null)
                .show()
        }
    }

    private fun isValidUserPrompt(userPrompt: String): Boolean {
        return userPrompt.contains(PromptFormat.USER_PLACEHOLDER)
    }

    private fun showInvalidPromptDialog() {
        AlertDialog.Builder(this)
            .setTitle("Invalid Prompt Format")
            .setMessage(
                "Prompt format must contain ${PromptFormat.USER_PLACEHOLDER}. Do you want to reset prompt format?"
            )
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(android.R.string.yes) { _, _ ->
                userPromptEditText.setText(PromptFormat.getUserPromptTemplate(settingsFields.modelType))
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    private fun setupBackendSelectorDialog() {
        // Convert enum to list
        val backendTypes = BackendType.entries.map { it.toString() }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select backend type")
            .setSingleChoiceItems(backendTypes, -1) { dialog, item ->
                backendTextView.text = backendTypes[item]
                settingsFields.saveBackendType(BackendType.valueOf(backendTypes[item]))
                setBackendSettingMode()
                updateLoadModelButtonState()
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun setupModelSelectorDialog() {
        val pteFiles = listLocalFile("/data/local/tmp/llama/", arrayOf(".pte"))

        val builder = AlertDialog.Builder(this)
            .setTitle("Select model path")

        if (pteFiles.isEmpty()) {
            builder.setMessage("No model files (.pte) found in /data/local/tmp/llama/\n\nPlease push model files using:\nadb push <model>.pte /data/local/tmp/llama/")
                .setPositiveButton(android.R.string.ok, null)
        } else {
            builder.setSingleChoiceItems(pteFiles, -1) { dialog, item ->
                settingsFields.saveModelPath(pteFiles[item])
                modelTextView.text = getFilenameFromPath(pteFiles[item])
                autoSelectModelType(pteFiles[item])
                updateLoadModelButtonState()
                dialog.dismiss()
            }
        }

        builder.create().show()
    }

    private fun autoSelectModelType(filePath: String) {
        val detectedType = ModelType.fromFilePath(filePath)
        if (detectedType != null) {
            modelTypeTextView.text = detectedType.toString()
            userPromptEditText.setText(PromptFormat.getUserPromptTemplate(detectedType))
            settingsFields.saveModelType(detectedType)
        }
    }

    private fun setupDataPathSelectorDialog() {
        val dataPathFiles = listLocalFile("/data/local/tmp/llama/", arrayOf(".ptd"))

        val builder = AlertDialog.Builder(this)
            .setTitle("Select data path")

        if (dataPathFiles.isEmpty()) {
            // No .ptd files found, show message with "(unused)" option
            builder.setMessage("No data files (.ptd) found in /data/local/tmp/llama/\n\nData files are optional. You can proceed without one, or push data files using:\nadb push <data>.ptd /data/local/tmp/llama/")
                .setPositiveButton("Use no data path") { _, _ ->
                    settingsFields.saveDataPath("")
                    dataPathTextView.text = "no data path selected"
                    updateLoadModelButtonState()
                }
                .setNegativeButton(android.R.string.cancel, null)
        } else {
            val dataPathOptions = dataPathFiles + "(unused)"

            builder.setSingleChoiceItems(dataPathOptions, -1) { dialog, item ->
                if (dataPathOptions[item] != "(unused)") {
                    settingsFields.saveDataPath(dataPathOptions[item])
                    dataPathTextView.text = getFilenameFromPath(dataPathOptions[item])
                } else {
                    settingsFields.saveDataPath("")
                    dataPathTextView.text = "no data path selected"
                }
                updateLoadModelButtonState()
                dialog.dismiss()
            }
        }

        builder.create().show()
    }

    private fun setupModelTypeSelectorDialog() {
        // Convert enum to list
        val modelTypes = ModelType.entries.map { it.toString() }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select model type")
            .setSingleChoiceItems(modelTypes, -1) { dialog, item ->
                modelTypeTextView.text = modelTypes[item]
                val selectedModelType = ModelType.valueOf(modelTypes[item])
                settingsFields.saveModelType(selectedModelType)
                userPromptEditText.setText(PromptFormat.getUserPromptTemplate(selectedModelType))
                updateLoadModelButtonState()
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun setupTokenizerSelectorDialog() {
        val tokenizerFiles = listLocalFile("/data/local/tmp/llama/", arrayOf(".bin", ".json", ".model"))

        val builder = AlertDialog.Builder(this)
            .setTitle("Select tokenizer path")

        if (tokenizerFiles.isEmpty()) {
            builder.setMessage("No tokenizer files (.bin, .json, .model) found in /data/local/tmp/llama/\n\nPlease push tokenizer files using:\nadb push <tokenizer> /data/local/tmp/llama/")
                .setPositiveButton(android.R.string.ok, null)
        } else {
            builder.setSingleChoiceItems(tokenizerFiles, -1) { dialog, item ->
                settingsFields.saveTokenizerPath(tokenizerFiles[item])
                tokenizerTextView.text = getFilenameFromPath(tokenizerFiles[item])
                updateLoadModelButtonState()
                dialog.dismiss()
            }
        }

        builder.create().show()
    }

    private fun getFilenameFromPath(uriFilePath: String?): String {
        if (uriFilePath == null) {
            return ""
        }
        return uriFilePath.substringAfterLast('/')
    }

    private fun updateLoadModelButtonState() {
        // Enable button only if valid model and tokenizer paths are selected
        val modelFilePath = settingsFields.modelFilePath
        val tokenizerFilePath = settingsFields.tokenizerFilePath
        val hasValidPaths = modelFilePath.isNotEmpty() && tokenizerFilePath.isNotEmpty()
        loadModelButton.isEnabled = hasValidPaths
    }

    private fun setBackendSettingMode() {
        val backendType = settingsFields.backendType
        when (backendType) {
            BackendType.XNNPACK, BackendType.QUALCOMM, BackendType.VULKAN -> setXNNPACKSettingMode()
            BackendType.MEDIATEK -> setMediaTekSettingMode()
        }
    }

    private fun setXNNPACKSettingMode() {
        requireViewById<View>(R.id.modelLayout).visibility = View.VISIBLE
        requireViewById<View>(R.id.tokenizerLayout).visibility = View.VISIBLE
        requireViewById<View>(R.id.dataPathLayout).visibility = View.VISIBLE
        requireViewById<View>(R.id.parametersView).visibility = View.VISIBLE
        requireViewById<View>(R.id.temperatureLayout).visibility = View.VISIBLE
    }

    private fun setMediaTekSettingMode() {
        requireViewById<View>(R.id.modelLayout).visibility = View.GONE
        requireViewById<View>(R.id.tokenizerLayout).visibility = View.GONE
        requireViewById<View>(R.id.dataPathLayout).visibility = View.GONE
        requireViewById<View>(R.id.parametersView).visibility = View.GONE
        requireViewById<View>(R.id.temperatureLayout).visibility = View.GONE
        // For MediaTek, only set default paths if they're empty - preserve existing selections
        if (settingsFields.modelFilePath.isEmpty()) {
            settingsFields.saveModelPath("/in/mtk/llama/runner")
        }
        if (settingsFields.tokenizerFilePath.isEmpty()) {
            settingsFields.saveTokenizerPath("/in/mtk/llama/runner")
        }
    }

    private fun loadSettings() {
        val gson = Gson()
        val settingsFieldsJSON = demoSharedPreferences.getSettings()
        if (settingsFieldsJSON.isNotEmpty()) {
            settingsFields = gson.fromJson(settingsFieldsJSON, SettingsFields::class.java)
        }
    }

    private fun saveSettings() {
        // All values are now stored directly in settingsFields, so just persist to SharedPreferences
        demoSharedPreferences.addSettings(settingsFields)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        saveSettings()
    }

    companion object {
        @JvmField
        val TEMPERATURE_MIN_VALUE = 0.0

        private fun fileHasExtension(file: String, suffix: Array<String>): Boolean {
            return suffix.any { file.endsWith(it) }
        }

        @JvmStatic
        fun listLocalFile(path: String, suffix: Array<String>): Array<String> {
            val directory = File(path)
            if (directory.exists() && directory.isDirectory) {
                val files = directory.listFiles { _, name -> fileHasExtension(name, suffix) }
                return files?.filter { it.isFile && fileHasExtension(it.name, suffix) }
                    ?.map { it.absolutePath }
                    ?.toTypedArray()
                    ?: emptyArray()
            }
            return emptyArray()
        }
    }
}
