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
Open Android Studio and open the project path `dl3/android/DeepLabV3Demo`. Wait for gradle sync to complete.
Then simply press "Run app" button (Control + r) to run the app either on physical device / emulator.

## Step 4: Push the model to the phone or emulator
The app loads a hardcoded model path (`/data/local/tmp/dl3_xnnpack_fp32.pte`) on the phone.
Run the following adb command to push the model.
```
adb push dl3_xnnpack_fp32.pte /data/local/tmp/dl3_xnnpack_fp32.pte
```

Note: If you want to use a QNN lowered model, you mush modify the maven executorch dependency to [executorch-qnn](https://mvnrepository.com/artifact/org.pytorch/executorch-android-qnn) and rebuild the app.

## Step 5: Load and Test Custom Images (No APK Rebuild Needed)
You can now test image segmentation on your own images (supported formats: .jpg, .jpeg, .png) without rebuilding the APK. The app supports loading .jpg, .jpeg, and .png images from the /sdcard/Pictures/ directory, with user-granted permissions.

### How to Use
#### a. Push your image to the device:
```
adb push <path to your image> /sdcard/Pictures/

```
#### b. After the push, In the app:
- Tap the "Load And Refresh" button.
- If prompted, grant the app permission to access the /sdcard/Pictures/ folder.
- The image should appear immediately in the app.
- You can now run dynamic image segmentation and view the results.

Tip: You can use images containing Dog, Sheep, Person, or any combination of these classes.

This feature enhances the testing/validation process by allowing users to quickly test different images without the need to rebuild the application.

## Step 6: Run unit test
### On your terminal
```
./gradlew connectedAndroidTest
```

### On Android Studio
In Android Studio project, open file `app/src/androidTest/java/org/pytorch/executorchexamples/dl3/SanityCheck.java`,
and click the "Play" button for `public class SanityCheck` (Control + Shift + r).
