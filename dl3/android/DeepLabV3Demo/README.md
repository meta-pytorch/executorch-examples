# ExecuTorch Android Demo App

This guide explains how to setup ExecuTorch for Android using a demo app. The app employs a [DeepLab v3](https://pytorch.org/hub/pytorch_vision_deeplabv3_resnet101/) model for image segmentation tasks. Models are exported to ExecuTorch using [XNNPACK FP32 backend](https://pytorch.org/executorch/main/backends-xnnpack.html#xnnpack-backend).

## Prerequisites
* Download and install [Android Studio and SDK 34](https://developer.android.com/studio).
* (For exporting the DL3 model) Python 3.10+ with `executorch` package installed.

## Step 1: Export the model (Optional)
The app can download the model automatically. If you want to export it yourself, run:
```bash
python dl3/python/export.py
```

## Step 2: Set up your device or emulator
You can run the app on either a physical device or an emulator.

### Using a Physical Device
* Connect your device to your computer via USB.
* Enable USB debugging on your device.

### Using an Emulator
* Open Android Studio and create a new virtual device.
* Start the emulator by clicking the "Play" button next to the device name.

## Step 3: Build, install, and run the app
### On your terminal
```bash
cd dl3/android/DeepLabV3Demo
./gradlew installDebug
adb shell am start -W -S -n org.pytorch.executorchexamples.dl3/.MainActivity
```

### On Android Studio
Open Android Studio and open the project path `dl3/android/DeepLabV3Demo`. Wait for gradle sync to complete.
Then simply press "Run app" button (Control + r) to run the app.

## Step 4: Download or Push the model

### Option A: Download from the app (Recommended)
The app includes a **"Download Model"** button that automatically downloads and extracts the model. Simply tap the button and wait for the download to complete.

### Option B: Push manually via adb
If you exported the model yourself or want to use a custom model:
```bash
adb push dl3_xnnpack_fp32.pte /data/local/tmp/dl3_xnnpack_fp32.pte
```

> **Note:** If you want to use a QNN lowered model, modify the maven executorch dependency to [executorch-qnn](https://mvnrepository.com/artifact/org.pytorch/executorch-android-qnn) and rebuild the app.

## Step 5: Load and Test Custom Images
You can test image segmentation on your own images (supported formats: .jpg, .jpeg, .png) without rebuilding the APK.

### How to Use
1. Push your image to the device:
   ```bash
   adb push <path to your image> /sdcard/Pictures/
   ```

2. In the app:
   - Tap the **"Load And Refresh"** button
   - If prompted, grant permission to access the /sdcard/Pictures/ folder
   - The image should appear immediately
   - Tap **"Run"** to perform segmentation

### Supported Classes
The app detects all 21 PASCAL VOC classes including: Person, Dog, Cat, Car, Bicycle, Bird, and more. Each class is highlighted with a distinct color overlay.

## Step 6: Run unit test
### On your terminal
```bash
./gradlew connectedAndroidTest
```

### On Android Studio
Open `app/src/androidTest/java/org/pytorch/executorchexamples/dl3/SanityCheck.java` and click the "Play" button for `public class SanityCheck`.
