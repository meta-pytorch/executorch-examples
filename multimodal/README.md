# Multimodal ExecuTorch Examples

This directory contains examples demonstrating multimodal AI inference using ExecuTorch with various backends (XNNPACK, Metal).

## Projects

| Directory | Description | Model |
|-----------|-------------|-------|
| [ask-anything-app](./ask-anything-app) | Web app with camera + chat interface | Gemma3 Vision + Whisper |
| [text-runtime](./text-runtime) | Text generation | Qwen3-0.6B |
| [text-image-runtime](./text-image-runtime) | Vision-language inference | Gemma3 4B |
| [voice-runtime](./voice-runtime) | Speech-to-text | Whisper Tiny |
| [object-detection-runtime](./object-detection-runtime) | Object detection | YOLO26m |

## Quick Start

### 1. Install Dependencies

```bash
pip install executorch optimum-executorch transformers pillow librosa
```

### 2. Download Models

```bash
# Text (Qwen3)
hf download larryliu0820/Qwen3-0.6B-ExecuTorch-XNNPACK --local-dir text-runtime/models/Qwen3-0.6B-ExecuTorch-XNNPACK

# Vision-Language (Gemma3)
hf download lucylq/gemma3 --local-dir text-image-runtime/models/gemma3

# Voice (Whisper)
hf download larryliu0820/whisper-tiny-ExecuTorch-XNNPACK --local-dir voice-runtime/models/whisper-tiny-ExecuTorch-XNNPACK

# Object Detection (YOLO)
hf download larryliu0820/yolo26m-ExecuTorch-XNNPACK --local-dir object-detection-runtime/models/yolo26m-ExecuTorch-XNNPACK
```

### 3. Run Examples

```bash
# Text generation
cd text-runtime
python qwen_inference.py --chat --prompt "Hello!"

# Vision-language
cd text-image-runtime
python runtime_inference.py --image_path example.jpg --prompt "What is this?"

# Speech-to-text
cd voice-runtime
python whisper_inference.py --audio_path obama_short20.wav

# Object detection
cd object-detection-runtime
python yolo_test.py --image example.jpg
```

## Ask Anything App

A full-stack web application combining vision and voice:

```bash
cd ask-anything-app

# Backend
pip install -r requirements.txt
python -m backend.main

# Frontend (new terminal)
npm install
npm run dev
```

Open http://localhost:5173 - point your camera and ask questions!

## References

- [ExecuTorch](https://github.com/pytorch/executorch)
- [optimum-executorch](https://github.com/huggingface/optimum-executorch)
- [XNNPACK](https://github.com/google/XNNPACK)
