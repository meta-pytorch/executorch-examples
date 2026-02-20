plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val useLocalAar: Boolean? = (project.findProperty("useLocalAar") as? String)?.toBoolean()

android {
    namespace = "com.example.parakeetapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.parakeetapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("com.example.asr:lib")
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
    if (useLocalAar == true) {
        implementation(files("libs/executorch.aar"))
    } else {
        implementation("org.pytorch:executorch-android:1.1.0")
    }
    implementation("com.facebook.fbjni:fbjni:0.5.1")
}
