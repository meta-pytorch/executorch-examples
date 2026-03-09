# ASR Demo App

This app demonstrates running speech recognition models on Android using ExecuTorch. It supports both **Whisper** and **Parakeet** model families.

## Supported Models

| Model | Type | Details |
|-------|------|---------|
| Whisper Tiny/Small/Medium (INT8/INT4) | Streaming | Requires model, tokenizer, and preprocessor |
| Whisper Tiny/Small/Medium (FP32) | Streaming | Requires model, tokenizer, and preprocessor |
| Parakeet TDT 0.6B (INT4) | Synchronous | Requires model and tokenizer |

## Export Model Files

- **Whisper**: Follow the instructions at https://github.com/pytorch/executorch/tree/main/examples/models/whisper
- **Parakeet**: Follow the instructions at https://github.com/pytorch/executorch/tree/main/examples/models/parakeet

## Run the App

1. Open AsrApp in Android Studio
2. Copy the `executorch.aar` library (with ASR and Parakeet JNI bindings) into `app/libs/`
3. Build and run on device

## Download Models

The app includes a built-in download screen to fetch models from HuggingFace. Alternatively, push files manually:

```bash
adb push model.pte /data/local/tmp/asr/
adb push tokenizer.json /data/local/tmp/asr/
adb push whisper_preprocessor.pte /data/local/tmp/asr/  # Whisper only
```

## Recording Behavior

- **Whisper**: Click to start recording; automatically stops after 30 seconds
- **Parakeet**: Click to start recording; click again to stop (no time limit)
