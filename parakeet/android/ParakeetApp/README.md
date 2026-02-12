# Parakeet Demo App

This app demonstrates running the Parakeet TDT speech recognition model on Android using ExecuTorch.

> **Note:** The ExecuTorch `ParakeetModule` API is not yet released. We will give a snapshot AAR soon.

## Export Model Files

Export the model `.pte` and tokenizer files following the instructions at:
https://github.com/pytorch/executorch/tree/main/examples/models/parakeet

This app requires a model `.pte` and a tokenizer `.model` file.

## Run the App

1. Open ParakeetApp in Android Studio
2. Copy the `executorch.aar` library (with parakeet JNI bindings) into `app/libs`
3. Build and run on device

## Download Models

The app includes a built-in download screen to fetch the Parakeet TDT 0.6B (INT4) model from HuggingFace:
- Model: `parakeet_int4.pte`
- Tokenizer: `tokenizer.model`

Alternatively, push files manually:
```bash
adb push model.pte /data/local/tmp/parakeet/
adb push tokenizer.model /data/local/tmp/parakeet/
```
