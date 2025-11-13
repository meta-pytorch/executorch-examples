# Whisper Metal Backend Example

This directory contains an end-to-end example for running Whisper on ExecuTorch Metal backend.

## Prerequisites

- macOS with Apple Silicon (M1/M2/M3/M4)
- Conda environment manager

## Getting the scripts

Start by cloning the `executorch-examples` repo, in order to get the scripts:

```bash
git clone git@github.com:meta-pytorch/executorch-examples.git
cd executorch-examples
```

The Metal backend scripts are located under `metal/`

## Whisper Example

The Whisper example demonstrates how to:

1. Set up your environment
2. Export the Whisper model to ExecuTorch format
3. Build the Whisper Metal runner
4. Run inference on audio input

**Run Whisper end-to-end:**

```bash
metal/whisper/e2e.sh \
  --artifact-dir <artifact_dir> \
  --env-name <env_name> \
  --clone-et \
  --create-env \
  --setup-env \
  --export \
  --build \
  --run --audio-path <audio_path> \
  [--model-name <model_name>]
```

**Required Arguments:**

- `<artifact_dir>` - Directory to store artifacts (e.g., `~/Desktop/whisper`)
- `<env_name>` - Name of the conda environment to create (e.g., `whisper-example`)
- `<audio_path>` - Path to your audio file for inference (e.g., `~/Desktop/audio.wav`)

**Optional Arguments:**

- `<model_name>` - HuggingFace Whisper model name (default: `openai/whisper-large-v3-turbo`)
  - Available models: `openai/whisper-tiny`, `openai/whisper-base`, `openai/whisper-small`,
    `openai/whisper-medium`, `openai/whisper-large`, `openai/whisper-large-v3-turbo`

**Example (default large-v3-turbo model):**

```bash
metal/whisper/e2e.sh \
  --artifact-dir ~/Desktop/whisper \
  --env-name whisper-example \
  --clone-et \
  --create-env \
  --setup-env \
  --export \
  --build \
  --run --audio-path ~/Desktop/audio.wav
```

**Example (using small model):**

```bash
metal/whisper/e2e.sh \
  --artifact-dir ~/Desktop/whisper \
  --env-name whisper-example \
  --clone-et \
  --create-env \
  --setup-env \
  --export \
  --model-name openai/whisper-small \
  --build \
  --run --audio-path ~/Desktop/audio.wav
```

This will automatically:

1. Setup the environment:

- Clone the executorch repo
- Create a conda environment named `whisper-example`
- Install all dependencies

2. Export the Whisper model to the `~/Desktop/whisper` directory
3. Build the Whisper Metal runner
4. Run inference on `~/Desktop/whisper/audio.wav`

### Step-by-Step Manual Process

If you prefer to run each step individually, follow these steps:

#### Step 1: Set up the environment

```bash
git clone git@github.com:pytorch/executorch.git
cd executorch
conda create -yn <env_name> python=3.11
conda activate <env_name>
/path/to/metal/setup_python_env.sh
```

#### Step 2: Export the Model

```bash
/path/to/metal/whisper/export.sh <artifact_dir> [model_name]
```

**Arguments:**

- `<artifact_dir>` - Directory to store exported model files (required, e.g., `~/Desktop/whisper`)
- `[model_name]` - HuggingFace Whisper model name (optional, default: `openai/whisper-large-v3-turbo`)
  - Available models: `openai/whisper-tiny`, `openai/whisper-base`, `openai/whisper-small`,
    `openai/whisper-medium`, `openai/whisper-large`, `openai/whisper-large-v3-turbo`

**Examples:**

```bash
# Export default large-v3-turbo model
/path/to/metal/whisper/export.sh ~/Desktop/whisper

# Export small model
/path/to/metal/whisper/export.sh ~/Desktop/whisper openai/whisper-small
```

This will:

- Download the specified Whisper model from HuggingFace
- Export it to ExecuTorch format with Metal optimizations
- Save model files (`.pte`), metadata, and preprocessor to the specified directory

#### Step 3: Build the Whisper Metal Runner

```bash
/path/to/metal/whisper/build.sh
```

#### Step 4: Run Inference

```bash
/path/to/metal/whisper/run.sh <audio_path> <artifact_dir>
```

**Arguments:**

- `<audio_path>` - Path to your audio file (required, e.g., `/path/to/audio.wav`)
- `<artifact_dir>` - Directory containing exported model files (required, e.g., `~/Desktop/whisper`)

**Example:**

```bash
/path/to/metal/whisper/run.sh ~/Desktop/audio.wav ~/Desktop/whisper
```

This will:

- Validate that all required model files exist
- Load the model and preprocessor
- Run inference on the provided audio
- Display timing information

## Available Whisper Models

The following Whisper models are supported:

| Model Name     | HuggingFace ID (for export)     | Parameters | Mel Features | Relative Speed | Use Case                       |
| -------------- | ------------------------------- | ---------- | ------------ | -------------- | ------------------------------ |
| Tiny           | `openai/whisper-tiny`           | 39M        | 80           | Fastest        | Quick transcription, real-time |
| Base           | `openai/whisper-base`           | 74M        | 80           | Very Fast      | Good balance for real-time     |
| Small          | `openai/whisper-small`          | 244M       | 80           | Fast           | Recommended for most use cases |
| Medium         | `openai/whisper-medium`         | 769M       | 80           | Moderate       | Higher accuracy needed         |
| Large          | `openai/whisper-large`          | 1550M      | 80           | Slower         | Best accuracy                  |
| Large V3       | `openai/whisper-large-v3`       | 1550M      | **128**      | Slower         | Latest architecture            |
| Large V3 Turbo | `openai/whisper-large-v3-turbo` | 809M       | **128**      | Fast           | Default, good balance          |

### Mel Features Configuration

The export scripts automatically configure the correct mel feature size based on the model:

- **80 mel features**: Used by all standard models (tiny, base, small, medium, large, large-v2)
- **128 mel features**: Used only by large-v3 and large-v3-turbo variants

**Important:** The preprocessor must match the model's expected feature size, or you'll encounter tensor shape mismatch errors. The export scripts handle this automatically.

### Tokenizer Configuration

**Important Note:** All Whisper models downloaded from HuggingFace now use the updated tokenizer format where:

- Token `50257` = `<|endoftext|>`
- Token `50258` = `<|startoftranscript|>` (used as `decoder_start_token_id`)

The whisper_runner automatically uses `decoder_start_token_id=50258` for all models, so you don't need to worry about tokenizer compatibility when exporting and running any Whisper variant.
