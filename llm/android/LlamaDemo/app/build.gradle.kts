/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

// Model files configuration for instrumentation tests
val modelFilesBaseUrl = "https://ossci-android.s3.amazonaws.com/executorch/stories/snapshot-20260114"
val deviceModelDir = "/data/local/tmp/llama"
val modelFiles = listOf(
  "stories110M.pte",
  "tokenizer.model"
)

fun execCmd(vararg args: String): String {
  val process = ProcessBuilder(*args)
    .redirectErrorStream(true)
    .start()
  val output = process.inputStream.bufferedReader().readText().trim()
  process.waitFor()
  return output
}

fun execCmdWithExitCode(vararg args: String): Pair<Int, String> {
  val process = ProcessBuilder(*args)
    .redirectErrorStream(true)
    .start()
  val output = process.inputStream.bufferedReader().readText().trim()
  val exitCode = process.waitFor()
  return Pair(exitCode, output)
}

tasks.register("pushModelFiles") {
  description = "Download model files from S3 and push to connected Android device if not present"
  group = "verification"

  doLast {
    // Check if adb is available
    val adbPath = android.adbExecutable.absolutePath
    val (adbCheckCode, _) = execCmdWithExitCode(adbPath, "devices")
    if (adbCheckCode != 0) {
      throw GradleException("adb is not available or no device connected")
    }

    // Check which files need to be pushed
    val filesToPush = modelFiles.filter { fileName ->
      val devicePath = "$deviceModelDir/$fileName"
      val (exitCode, _) = execCmdWithExitCode(adbPath, "shell", "test -f $devicePath && echo exists")
      exitCode != 0
    }

    if (filesToPush.isEmpty()) {
      logger.lifecycle("All model files already present on device")
      return@doLast
    }

    logger.lifecycle("Need to push ${filesToPush.size} model file(s): ${filesToPush.joinToString(", ")}")

    // Create temp directory using mktemp
    val tempDir = execCmd("mktemp", "-d")
    logger.lifecycle("Using temp directory: $tempDir")

    try {
      // Create device directory
      execCmd(adbPath, "shell", "mkdir -p $deviceModelDir")

      for (fileName in filesToPush) {
        val localPath = "$tempDir/$fileName"
        val checksumPath = "$tempDir/$fileName.sha256sums"
        val devicePath = "$deviceModelDir/$fileName"

        // Download file
        logger.lifecycle("Downloading $fileName...")
        val (dlCode, dlOutput) = execCmdWithExitCode(
          "curl", "-fL", "-o", localPath, "$modelFilesBaseUrl/$fileName"
        )
        if (dlCode != 0) {
          throw GradleException("Failed to download $fileName: $dlOutput")
        }

        // Download and verify checksum
        logger.lifecycle("Verifying checksum for $fileName...")
        val (csDownloadCode, csDownloadOutput) = execCmdWithExitCode(
          "curl", "-fL", "-o", checksumPath, "$modelFilesBaseUrl/$fileName.sha256sums"
        )
        if (csDownloadCode != 0) {
          throw GradleException("Failed to download checksum for $fileName: $csDownloadOutput")
        }

        // Verify checksum (run sha256sum in the temp directory)
        val (verifyCode, verifyOutput) = execCmdWithExitCode(
          "bash", "-c", "cd $tempDir && sha256sum -c $fileName.sha256sums"
        )
        if (verifyCode != 0) {
          throw GradleException("Checksum verification failed for $fileName: $verifyOutput")
        }
        logger.lifecycle("Checksum verified for $fileName")

        // Push to device
        logger.lifecycle("Pushing $fileName to device...")
        val (pushCode, pushOutput) = execCmdWithExitCode(adbPath, "push", localPath, devicePath)
        if (pushCode != 0) {
          throw GradleException("Failed to push $fileName to device: $pushOutput")
        }
        logger.lifecycle("Successfully pushed $fileName")
      }
    } finally {
      // Clean up temp directory
      logger.lifecycle("Cleaning up temp directory...")
      execCmd("rm", "-rf", tempDir)
    }

    logger.lifecycle("All model files pushed successfully")
  }
}

// Make all connected Android test tasks depend on pushModelFiles
tasks.whenTaskAdded {
  if (name.startsWith("connected") && name.endsWith("AndroidTest")) {
    dependsOn("pushModelFiles")
  }
}

val qnnVersion: String? = project.findProperty("qnnVersion") as? String
val useLocalAar: Boolean? = (project.findProperty("useLocalAar") as? String)?.toBoolean()

android {
  namespace = "com.example.executorchllamademo"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.example.executorchllamademo"
    testApplicationId = "com.example.executorchllamademo.test"
    minSdk = 28
    targetSdk = 33
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables { useSupportLibrary = true }
    externalNativeBuild { cmake { cppFlags += "" } }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }
  buildFeatures { compose = true }
  composeOptions { kotlinCompilerExtensionVersion = "1.4.3" }
  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
  implementation("androidx.core:core-ktx:1.9.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
  implementation("androidx.activity:activity-compose:1.7.0")
  implementation(platform("androidx.compose:compose-bom:2023.03.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("androidx.camera:camera-core:1.3.0-rc02")
  implementation("androidx.constraintlayout:constraintlayout:2.2.0-alpha12")
  implementation("com.facebook.fbjni:fbjni:0.5.1")
  implementation("com.google.code.gson:gson:2.8.6")
  if (useLocalAar == true) {
    implementation(files("libs/executorch.aar"))
  } else {
    implementation("org.pytorch:executorch-android:1.0.1")
    // https://mvnrepository.com/artifact/org.pytorch/executorch-android-qnn
    // Uncomment this to enable QNN
    // implementation("org.pytorch:executorch-android-qnn:1.0.1")

    // https://mvnrepository.com/artifact/org.pytorch/executorch-android-vulkan
    // uncomment to enable vulkan
    // implementation("org.pytorch:executorch-android-vulkan:1.0.1")
  }
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.activity:activity:1.9.0")
  implementation("org.json:json:20250107")
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.1.5")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
  androidTestImplementation(platform("androidx.compose:compose-bom:2023.03.00"))
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-tooling")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}
