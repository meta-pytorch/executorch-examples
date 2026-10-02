# Qwen3 Text Runtime

ExecuTorch-based inference for Qwen3-0.6B with XNNPACK backend.

## Prerequisites

- Python 3.10+
- optimum-executorch
- transformers

## Installation

```bash
pip install optimum-executorch transformers
```

## Download Model

```bash
hf download larryliu0820/Qwen3-0.6B-ExecuTorch-XNNPACK --local-dir models/Qwen3-0.6B-ExecuTorch-XNNPACK
```

## Usage

```bash
# Basic usage
python qwen_inference.py --prompt "What is the capital of France?"

# With chat template
python qwen_inference.py --chat --prompt "Hello, how are you?"

# Enable thinking mode (shows reasoning)
python qwen_inference.py --chat --thinking --prompt "Solve: 2x + 5 = 15"

# Adjust generation length
python qwen_inference.py --prompt "Explain quantum computing" --max_seq_len 256
```

## Command Line Arguments

| Argument | Default | Description |
|----------|---------|-------------|
| `--model_dir` | `models/Qwen3-0.6B-ExecuTorch-XNNPACK` | Model directory |
| `--prompt` | - | Input prompt |
| `--max_seq_len` | `128` | Maximum sequence length |
| `--chat` | `false` | Use chat template formatting |
| `--thinking` | `false` | Enable thinking mode |
| `--echo` | `false` | Include prompt in output |
