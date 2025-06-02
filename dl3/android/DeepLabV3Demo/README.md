# ExecuTorch Android Demo App

This guide explains how to setup ExecuTorch for Android using a demo app. The app employs a [DeepLab v3](https://pytorch.org/hub/pytorch_vision_deeplabv3_resnet101/) model for image segmentation tasks. Models are exported to ExecuTorch using [XNNPACK FP32 backend](https://pytorch.org/executorch/main/backends-xnnpack.html#xnnpack-backend).

## Prerequisites
* Download and install [Android Studio and SDK 34](https://developer.android.com/studio).
* (For exporting the DL3 model) Python 3.10+ with `executorch` package installed.

## Step 1: Export the model
Run the script in `dl3/python/export.py` to export the model.

## Step 2: Set up your device or emulator
You can run the app on either a physical device or an emulator. To set up your device or emulator, follow these steps:

### Using a Physical Device
* Connect your device to your computer via USB.
* Enable USB debugging on your device.

### Using an Emulator
* Open Android Studio and create a new virtual device.
* Start the emulator by clicking the "Play" button next to the device name.

## Step 3: Build, install, and run the app on your phone
### On your terminal
(`cd dl3/android/DeepLabV3Demo` first)
```
./gradlew installDebug
adb shell am start -W -S -n org.pytorch.executorchexamples.dl3/.MainActivity
```

### On Android Studio
Open Android Studio and open the project path `dl3/android/DeepLanV3Demo`. Wait for gradle sync to complete.
Then simply press "Run app" button (Control + r) to run the app either on physical device / emulator.

## Step 4: Push the model to the phone or emulator
The app loads a hardcoded model path (`/data/local/tmp/dl3_xnnpack_fp32.pte`) on the phone.
Run the following adb command to push the model.
```
adb push dl3_xnnpack_fp32.pte /data/local/tmp/dl3_xnnpack_fp32.pte
```

## Step 5: Run unit test
### On your terminal
```
./gradlew connectedAndroidTest
```

### On Android Studio
In Android Studio project, open file `app/src/androidTest/java/org/pytorch/executorchexamples/dl3/SanityCheck.java`,
and click the "Play" button for `public class SanityCheck` (Control + Shift + r).
