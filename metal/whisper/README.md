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
  --run --audio-path <audio_path>
```

**Required Arguments:**

- `<artifact_dir>` - Directory to store artifacts (e.g., `~/Desktop/whisper`)
- `<env_name>` - Name of the conda environment to create (e.g., `whisper-example`)
- `<audio_path>` - Path to your audio file for inference (e.g., `~/Desktop/audio.wav`)

**Example:**

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
/path/to/metal/whisper/export.sh <artifact_dir>
```

**Arguments:**
- `<artifact_dir>` - Directory to store exported model files (e.g., `~/Desktop/whisper`)

This will:
- Download the Whisper model
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
- `<audio_path>` - Path to your audio file (e.g., `/path/to/audio.wav`)
- `<artifact_dir>` - Directory containing exported model files (e.g., `~/Desktop/whisper`)

This will:
- Validate that all required model files exist
- Load the model and preprocessor
- Run inference on the provided audio
- Display timing information
