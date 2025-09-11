# Whisper Demo App

This app runs the Whisper model with the Qualcomm backend in ExecuTorch. The model itself runs on the Qualcomm HTP (Hexagon Tensor Processor), an AI accelerator in Qualcomm SoCs. You need an Android smartphone with a [supported Qualcomm chipset](https://github.com/pytorch/executorch/tree/main/backends/qualcomm).

### Build the ExecuTorch Android library

Checkout [Whisper JNI Bindings](https://github.com/pytorch/executorch/pull/13525) (if it is not merged already). Build the [ExecuTorch Android library with QNN backend](https://github.com/pytorch/executorch/blob/main/examples/demo-apps/android/LlamaDemo/docs/delegates/qualcomm_README.md).

### Export the audio processing and model .pte files

There are two steps, audio processing and the Whisper model (encoder+decoder), which are both done via ExecuTorch.

1) Run the script `extension/audio/mel_spectrogram.py` to export `whisper_preprocess.pte`
2) Run the script `examples/qualcomm/oss_scripts/whisper/whisper.py` to export `whisper_qnn_16a8w.pte`

Move these two .pte files along with `tokenizer.json` to `/data/local/tmp/whisper` on device.

### Run app

Open WhisperApp in Android studio. Copy the Android library `executorch.aar` which should have audio JNI bindings and the Qualcomm HTP libraries, into `app/libs`.



https://github.com/user-attachments/assets/ff8c71c5-b734-4ed4-8382-70a429830665
