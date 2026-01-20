/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.executorchllamademo.ui.screens.ChatScreen
import com.example.executorchllamademo.ui.theme.LlamaDemoTheme
import com.example.executorchllamademo.viewmodel.ChatViewModel
import com.google.gson.Gson
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var sharedPreferences: DemoSharedPreferences
    
    // Photo picker for gallery
    private lateinit var pickMedia: ActivityResultLauncher<PickVisualMediaRequest>
    
    // Camera launcher
    private lateinit var takePicture: ActivityResultLauncher<Uri>
    private var cameraImageUri: Uri? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sharedPreferences = DemoSharedPreferences(this)
        
        setupMediaLaunchers()
        
        setContent {
            LlamaDemoTheme {
                ChatScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    
    private fun setupMediaLaunchers() {
        pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                viewModel.setImageUri(it)
            }
        }
        
        takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                cameraImageUri?.let {
                    viewModel.setImageUri(it)
                }
            }
        }
    }
    
    fun openGallery() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    
    fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }
        
        val imageFile = File(cacheDir, "camera_image_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            imageFile
        )
        cameraImageUri?.let { takePicture.launch(it) }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check for settings updates
        val settingsJson = sharedPreferences.getSettings()
        if (settingsJson.isNotEmpty()) {
            val gson = Gson()
            val updatedSettings = gson.fromJson(settingsJson, SettingsFields::class.java)
            
            // Handle clear chat history
            if (updatedSettings.isClearChatHistory) {
                viewModel.clearMessages()
                updatedSettings.saveIsClearChatHistory(false)
                sharedPreferences.addSettings(updatedSettings)
            }
            
            // Handle load model
            if (updatedSettings.isLoadModel) {
                loadModel(updatedSettings)
                updatedSettings.saveLoadModelAction(false)
                sharedPreferences.addSettings(updatedSettings)
            }
            
            viewModel.updateSettings(updatedSettings)
        } else {
            // First launch - show model selection dialog
            askUserToSelectModel()
        }
    }
    
    private fun loadModel(settings: SettingsFields) {
        viewModel.loadModel(
            modelPath = settings.modelFilePath,
            tokenizerPath = settings.tokenizerFilePath,
            temperature = settings.temperature.toFloat()
        ) { success, message ->
            if (!success) {
                ETLogging.getInstance().log("Failed to load model: $message")
            }
        }
    }
    
    private fun askUserToSelectModel() {
        AlertDialog.Builder(this)
            .setTitle(R.string.initial_dialog_title)
            .setMessage(R.string.initial_dialog_message)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    override fun onPause() {
        super.onPause()
        viewModel.saveMessages()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (viewModel.isGenerating.value) {
            viewModel.stopGeneration()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        viewModel.saveMessages()
    }
    
    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
    }
}
