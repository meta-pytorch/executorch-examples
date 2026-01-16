# ExecuTorch Android Demo App

This guide explains how to setup ExecuTorch for Android using a demo app. The app employs a [DeepLab v3](https://pytorch.org/hub/pytorch_vision_deeplabv3_resnet101/) model for image segmentation tasks. Models are exported to ExecuTorch using [XNNPACK FP32 backend](https://pytorch.org/executorch/main/backends-xnnpack.html#xnnpack-backend).

## Features
- **Image Segmentation**: Detects and highlights all 21 PASCAL VOC classes (Person, Dog, Cat, Car, etc.)
- **Overlay Visualization**: Segmentation mask blends with the original image at 50% opacity
- **Inference Time Display**: Shows model inference latency in milliseconds
- **In-App Model Download**: Download the model directly from the app
- **Image Picker**: Select any image from your device's gallery
- **Sample Images**: 3 built-in sample images for quick testing

## Prerequisites
* Download and install [Android Studio and SDK 34](https://developer.android.com/studio).
* (For exporting the DL3 model) Python 3.10+ with `executorch` package installed.

## Step 1: Export the Model (Optional)
The app can download the model automatically. If you want to export it yourself:
```bash
cd dl3/python
python export.py
```

## Step 2: Set Up Your Device or Emulator

### Using a Physical Device
* Connect your device to your computer via USB.
* Enable USB debugging on your device.

### Using an Emulator
* Open Android Studio and create a new virtual device.
* Start the emulator by clicking the "Play" button next to the device name.

## Step 3: Build and Run the App

### Using Terminal
```bash
cd dl3/android/DeepLabV3Demo
./gradlew installDebug
adb shell am start -n org.pytorch.executorchexamples.dl3/.MainActivity
```

### Using Android Studio
1. Open the project at `dl3/android/DeepLabV3Demo`
2. Wait for Gradle sync to complete
3. Click "Run app" (Control + R)

## Step 4: Get the Model

### Option A: Download from the App (Recommended)
When the app launches, tap the **"Download Model"** button. The model will be downloaded and extracted automatically.

### Option B: Export and Push Manually
If you exported the model yourself or want to use a custom model, you need to copy it to the app's private storage. Since the app is built in debug mode, we can use `run-as`:

```bash
# 1. Push to device temporary storage
adb push dl3_xnnpack_fp32.pte /data/local/tmp/

# 2. Copy to app's private storage using run-as
adb shell "run-as org.pytorch.executorchexamples.dl3 cp /data/local/tmp/dl3_xnnpack_fp32.pte files/"
```

> **Note:** For QNN backend, change the maven dependency to [executorch-qnn](https://mvnrepository.com/artifact/org.pytorch/executorch-android-qnn) and rebuild the app.

## Step 5: Using the App

### Sample Images
Tap **"Next sample image"** to cycle through 3 built-in sample images.

### Pick Your Own Image
1. Tap **"Pick Image"** to open your device's gallery
2. Select any image (it will be automatically resized to 224x224)
3. Tap **"Run"** to perform segmentation

### Run Segmentation
1. Tap **"Run"** to start inference
2. The segmentation overlay appears blended with the original image
3. Inference time is displayed below the image

### Reset
Tap **"Reset"** to restore the original image without the segmentation overlay.

## Supported Classes
The app detects all 21 PASCAL VOC classes with distinct color overlays:

| Class | Color | Class | Color |
|-------|-------|-------|-------|
| Person | Red | Dog | Green |
| Cat | Magenta | Car | Cyan |
| Bird | Yellow | Bicycle | Green |
| Boat | Blue | Bottle | Orange |
| And 13 more... | | | |

## Step 6: Run Unit Tests

### Using Terminal
```bash
./gradlew connectedAndroidTest
```

### Using Android Studio
Open `app/src/androidTest/java/org/pytorch/executorchexamples/dl3/SanityCheck.java` and click the Play button.
