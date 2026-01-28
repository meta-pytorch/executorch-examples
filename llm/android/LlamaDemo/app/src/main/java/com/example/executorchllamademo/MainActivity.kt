/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.executorchllamademo.ui.screens.ChatScreen
import com.example.executorchllamademo.ui.theme.LlamaDemoTheme
import com.example.executorchllamademo.ui.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    private lateinit var pickGallery: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var cameraRoll: ActivityResultLauncher<Uri>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private var cameraImageUri: Uri? = null
    private var chatViewModel: ChatViewModel? = null
    private var appearanceMode by mutableStateOf(AppearanceMode.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        loadAppearanceMode()
        setupPermissionLauncher()
        setupGalleryPicker()
        setupCameraRoll()

        setContent {
            val isDarkTheme = when (appearanceMode) {
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
                AppearanceMode.SYSTEM -> isSystemInDarkTheme()
            }

            LlamaDemoTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: ChatViewModel = viewModel()
                    chatViewModel = viewModel

                    ChatScreen(
                        viewModel = viewModel,
                        onBackClick = {
                            startActivity(Intent(this@MainActivity, WelcomeActivity::class.java))
                            finish()
                        },
                        onSettingsClick = {
                            startActivity(Intent(this@MainActivity, ModelSettingsActivity::class.java))
                        },
                        onLogsClick = {
                            startActivity(Intent(this@MainActivity, LogsActivity::class.java))
                        },
                        onGalleryClick = {
                            pickGallery.launch(
                                PickVisualMediaRequest.Builder()
                                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    .build()
                            )
                        },
                        onCameraClick = {
                            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                                != PackageManager.PERMISSION_GRANTED
                            ) {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                launchCamera()
                            }
                        },
                        audioFiles = ModelSettingsActivity.listLocalFile("/data/local/tmp/audio/", arrayOf(".bin")).toList(),
                        onAudioFileSelected = { audioFile ->
                            chatViewModel?.setAudioFile(audioFile)
                        }
                    )
                }
            }
        }
    }

    private fun setupPermissionLauncher() {
        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                launchCamera()
            } else {
                Log.d("CameraRoll", "Permission denied")
            }
        }
    }

    private fun setupGalleryPicker() {
        pickGallery = registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(MAX_NUM_OF_IMAGES)
        ) { uris ->
            if (uris.isNotEmpty()) {
                Log.d("PhotoPicker", "Selected URIs: $uris")
                for (uri in uris) {
                    // Try to take persistable permission, but don't fail if not supported
                    // (e.g., file:// URIs don't support persistable permissions)
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: SecurityException) {
                        Log.d("PhotoPicker", "Could not take persistable permission for $uri: ${e.message}")
                    }
                    chatViewModel?.addImage(uri)
                }
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }
    }

    private fun setupCameraRoll() {
        cameraRoll = registerForActivityResult(ActivityResultContracts.TakePicture()) { result ->
            if (result && cameraImageUri != null) {
                Log.d("CameraRoll", "Photo saved to uri: $cameraImageUri")
                chatViewModel?.addImage(cameraImageUri!!)
            } else {
                cameraImageUri?.let { uri ->
                    contentResolver.delete(uri, null, null)
                    Log.d("CameraRoll", "No photo taken. Delete temp uri")
                }
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

    private fun loadAppearanceMode() {
        val prefs = DemoSharedPreferences(this)
        appearanceMode = prefs.getAppSettings().appearanceMode
    }

    override fun onResume() {
        super.onResume()
        loadAppearanceMode()
        chatViewModel?.checkAndLoadSettings()
    }

    override fun onPause() {
        super.onPause()
        chatViewModel?.saveMessages()
    }

    override fun onDestroy() {
        super.onDestroy()
        ETLogging.getInstance().saveLogs()
    }

    companion object {
        private const val MAX_NUM_OF_IMAGES = 5
    }
}
