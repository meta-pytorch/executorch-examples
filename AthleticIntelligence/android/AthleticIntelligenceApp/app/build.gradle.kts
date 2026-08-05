plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.example.executorch.athleticintelligence"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.executorch.athleticintelligence"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    // .pte assets are already compressed; don't let aapt re-compress
    androidResources { noCompress += "pte" }
    testOptions {
        // Android framework stubs (e.g. android.util.Log) return defaults instead of
        // throwing in JVM unit tests, so production logging can't break hermetic tests.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("org.pytorch:executorch-android:1.1.0")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // Mockito 5's default inline mock maker can mock final Android stub classes
    // (e.g. android.graphics.Bitmap) in plain JVM unit tests, without Robolectric.
    testImplementation("org.mockito:mockito-core:5.12.0")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val checkRtmwModelAsset by tasks.registering {
    val model = layout.projectDirectory.file("src/main/assets/rtmw_l_int8.pte")
    doLast {
        if (!model.asFile.isFile) {
            throw GradleException(
                "Missing app/src/main/assets/rtmw_l_int8.pte. " +
                    "Run 'python tools/rtmw/download_rtmw.py' and the export commands " +
                    "documented in README.md, then copy the generated int8 model into app assets.",
            )
        }
    }
}

tasks.matching {
    it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets"
}.configureEach {
    dependsOn(checkRtmwModelAsset)
}
