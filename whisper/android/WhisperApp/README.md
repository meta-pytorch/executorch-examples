# Whisper Demo App

This app runs the Whisper model in ExecuTorch.

## Build the ExecuTorch Android library

Build the [ExecuTorch Android library with QNN backend](https://github.com/pytorch/executorch/blob/main/examples/demo-apps/android/LlamaDemo/docs/delegates/qualcomm_README.md).

## Export the audio processing and model .pte files

There are two steps, audio processing and the Whisper model (encoder+decoder), which are both done via ExecuTorch.

1. Run `extension/audio/mel_spectrogram.py` to export `whisper_preprocess.pte`
2. Run `examples/qualcomm/oss_scripts/whisper/whisper.py` to export `whisper_qnn_16a8w.pte`

Move these two `.pte` files along with `tokenizer.json` to `/data/local/tmp/whisper` on device.

## Run the app

1. Open WhisperApp in Android Studio
2. Copy the Android library `executorch.aar` (with audio JNI bindings) into `app/libs`
3. Build and run on device

## Demo

https://github.com/user-attachments/assets/ff8c71c5-b734-4ed4-8382-70a429830665
