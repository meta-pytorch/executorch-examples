# Whisper Demo App

This app demonstrates running the Whisper speech recognition model on Android using ExecuTorch.

> **Note:** The ExecuTorch `AsrModule` API is not yet released. We will give a snapshot AAR soon™

## Export Model Files

Export the audio preprocessor and model `.pte` files following the instructions at:
https://github.com/pytorch/executorch/tree/main/examples/models/whisper

This app requires both a model `.pte` and a preprocessor `.pte` file.

## Run the App

1. Open WhisperApp in Android Studio
2. Copy the `executorch.aar` library (with audio JNI bindings) into `app/libs`
3. Build and run on device

## Demo

https://github.com/user-attachments/assets/eb4c4ae6-b89f-4eb4-a291-549a42c95f54
