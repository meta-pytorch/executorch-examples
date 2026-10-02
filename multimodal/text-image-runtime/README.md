# Gemma3 Vision-Language Runtime

ExecuTorch-based inference for Gemma3 4B multimodal model with XNNPACK backend.

## Prerequisites

- Python 3.10+
- ExecuTorch with Python bindings
- transformers
- Pillow

## Installation

```bash
pip install executorch transformers pillow torch
```

## Download Model

Download the Gemma3 4B quantized model from Hugging Face:

```bash
hf download lucylq/gemma3 --local-dir models/gemma3
```

## Usage

### Using runtime_inference.py (Recommended)

The full-featured inference script with direct ExecuTorch portable_lib API:

```bash
# Basic usage
python runtime_inference.py \
    --image_path example.jpg \
    --prompt "What is in this image?"

# With custom model path
python runtime_inference.py \
    --model_path models/gemma3/GEMMA3_4B_XNNPACK_INT8_INT4.pte \
    --image_path example.jpg \
    --prompt "How many people are in this image?"

# Adjust generation parameters
python runtime_inference.py \
    --image_path example.jpg \
    --prompt "Describe this scene" \
    --max_new_tokens 256 \
    --temperature 0.7
```

### Using run.py (Simple CLI)

Simplified interface for quick testing:

```bash
python run.py \
    --model_path models/gemma3/GEMMA3_4B_XNNPACK_INT8_INT4.pte \
    --image_path example.jpg \
    --prompt "What do you see?"
```

## Command Line Arguments

### runtime_inference.py

| Argument | Default | Description |
|----------|---------|-------------|
| `--model_path` | `gemma3/GEMMA3_4B_XNNPACK_INT8_INT4.pte` | Path to .pte model |
| `--image_path` | Required | Input image path |
| `--prompt` | Required | Text prompt for the model |
| `--max_new_tokens` | `128` | Maximum tokens to generate |
| `--temperature` | `0.8` | Sampling temperature |

## Model Details

- **Model**: Gemma3 4B Vision-Language
- **Quantization**: INT8/INT4 mixed precision
- **Backend**: XNNPACK (CPU optimized)
- **Size**: ~3.5GB
