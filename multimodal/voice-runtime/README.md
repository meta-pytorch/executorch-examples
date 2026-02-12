# Whisper Voice Runtime

ExecuTorch-based speech-to-text inference using Whisper with XNNPACK backend.

## Prerequisites

- Python 3.10+
- optimum-executorch
- transformers
- librosa or soundfile (for audio loading)

## Installation

```bash
pip install optimum-executorch transformers librosa
```

## Download Model

```bash
hf download larryliu0820/whisper-tiny-ExecuTorch-XNNPACK --local-dir models/whisper-tiny-ExecuTorch-XNNPACK
```

## Usage

```bash
# Transcribe audio file
python whisper_inference.py --audio_path obama_short20.wav

# With custom model path
python whisper_inference.py \
    --audio_path obama_short20.wav \
    --model_dir models/whisper-tiny-ExecuTorch-XNNPACK
```

## Test Audio

The `obama_short20.wav` file is a 20-second speech sample for testing transcription.

## Command Line Arguments

| Argument | Default | Description |
|----------|---------|-------------|
| `--audio_path` | Required | Input audio file (WAV, MP3, etc.) |
| `--model_dir` | `models/whisper-tiny-ExecuTorch-XNNPACK` | Model directory |

## References

- [Whisper](https://github.com/openai/whisper)
- [ExecuTorch](https://github.com/pytorch/executorch)
- [optimum-executorch](https://github.com/huggingface/optimum-executorch)
