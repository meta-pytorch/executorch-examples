# Voxtral Metal Backend Example

This directory contains an end-to-end example for running Voxtral on ExecuTorch Metal backend.

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

## Voxtral Example

The Voxtral example demonstrates how to:
1. Set up your environment
2. Export the Mistral Voxtral model to ExecuTorch format
3. Build the Voxtral Metal runner
4. Run inference on audio input

**Run Voxtral end-to-end:**

```bash
metal/voxtral/e2e.sh \
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

- `<artifact_dir>` - Directory to store artifacts (e.g., `~/Desktop/voxtral`)
- `<env_name>` - Name of the conda environment to create (e.g., `voxtral-example`)
- `<audio_path>` - Path to your audio file for inference (e.g., `~/Desktop/audio.wav`)

**Example:**

```bash
metal/voxtral/e2e.sh \
  --artifact-dir ~/Desktop/voxtral \
  --env-name voxtral-example \
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
  - Create a conda environment named `voxtral-example`
  - Install all dependencies
2. Export the Voxtral model to the `~/Desktop/voxtral` directory
3. Build the Voxtral Metal runner
4. Run inference on `~/Desktop/voxtral/audio.wav`

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
/path/to/metal/voxtral/export.sh <artifact_dir>
```

**Arguments:**
- `<artifact_dir>` - Directory to store exported model files (e.g., `~/Desktop/voxtral`)

This will:
- Download the Mistral Voxtral model
- Export it to ExecuTorch format with Metal optimizations
- Save model files (`.pte`), metadata, and preprocessor to the specified directory

#### Step 3: Build the Voxtral Metal Runner

```bash
/path/to/metal/voxtral/build.sh
```

#### Step 4: Run Inference

```bash
/path/to/metal/voxtral/run.sh <audio_path> <artifact_dir>
```

**Arguments:**
- `<audio_path>` - Path to your audio file (e.g., `/path/to/audio.wav`)
- `<artifact_dir>` - Directory containing exported model files (e.g., `~/Desktop/voxtral`)

This will:
- Validate that all required model files exist
- Load the model and preprocessor
- Run inference on the provided audio
- Display timing information
