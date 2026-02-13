# Parakeet Demo App

This app demonstrates running the Parakeet TDT speech recognition model on Android using ExecuTorch.

## Download ExecuTorch AAR

Download the prebuilt ExecuTorch AAR (with Parakeet JNI bindings) and place it in `app/libs/`:

```bash
mkdir -p app/libs
curl -L -o app/libs/executorch.aar https://gha-artifacts.s3.amazonaws.com/pytorch/executorch/21934561658/artifacts/executorch.aar
```

## Export Model Files

Export the model `.pte` and tokenizer files following the instructions at:
https://github.com/pytorch/executorch/tree/main/examples/models/parakeet

This app requires a model `.pte` and a tokenizer `.model` file.

## Run the App

1. Download the ExecuTorch AAR (see above)
2. Open ParakeetApp in Android Studio
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
