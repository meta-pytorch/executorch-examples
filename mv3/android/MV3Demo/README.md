# MV3 Android Demo

This is a sample Android application demonstrating MobileNet v3 (MV3) image classification using PyTorch ExecuTorch.

## Features

- **MobileNet v3 Inference**: Runs the MV3 model on Android.
- **Live Camera Feed**: Real-time classification on camera frames.
- **Image Selection**: Pick images from the gallery for classification.
- **Material Design 3**: Modern UI with a bottom app bar and intuitive controls.

## Prerequisites

- Android SDK (API 34+)
- JDK 17
- ExecuTorch libraries (configured via Gradle)

## Setup

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/meta-pytorch/executorch-examples.git
    cd executorch-examples/mv3/android/MV3Demo
    ```

2.  **Build the project**:
    ```bash
    ./gradlew :app:assembleDebug
    ```

3.  **Install on device**:
    ```bash
    ./gradlew :app:installDebug
    ```

## Usage

- **Local Model**: The app attempts to load `mv3.pte` from the app's internal storage. If missing, it offers a download button (currently configured with a placeholder URL).
- **Live Camera**: Grant camera permissions to use the real-time classification feature.
- **Pick Image**: Select an image from your device to classify it.

## Architecture

- **UI**: Jetpack Compose
- **Camera**: CameraX (Preview + ImageAnalysis)
- **Inference**: ExecuTorch Android API
- **Image Processing**: `TensorImageUtils` for bitmap-to-tensor conversion

## Testing

The app includes an instrumentation test that validates the complete image classification workflow.

### What the test does

1. Launches the app
2. Downloads the MV3 model if not already present
3. Downloads a cat image from HuggingFace
4. Runs inference on the image
5. Validates that the model correctly classifies it as a cat

### Running the test

1. **Connect a device or start an emulator**

2. **Build and install the test APKs**:
    ```bash
    ./gradlew installDebug installDebugAndroidTest
    ```

3. **Run the test**:
    ```bash
    adb shell am instrument -w -r \
      -e class 'org.pytorch.executorchexamples.mv3.UIWorkflowTest#testCatImageClassification' \
      org.pytorch.executorchexamples.mv3.test/androidx.test.runner.AndroidJUnitRunner
    ```

    Or run all tests via Gradle:
    ```bash
    ./gradlew connectedDebugAndroidTest
    ```

### Test output

The test logs classification results to logcat with the tag `MV3_RESULT`:
```bash
adb logcat -s MV3_RESULT
```

## License

BSD-3-Clause
