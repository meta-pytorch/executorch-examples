#!/bin/bash
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

set -e

# Initialize variables
CLONE=false
CREATE_ENV=false
SETUP_ENV=false
EXPORT=false
BUILD=false
RUN=false
EXECUTORCH_PATH=""
ARTIFACT_DIR=""
ENV_NAME=""
AUDIO_PATH=""
MODEL_NAME="openai/whisper-large-v3-turbo"

echo "Current script path: $(realpath "$0")"
SCRIPT_DIR="$(realpath "$(dirname "$(realpath "$0")")")"
echo "Scripts directory: $SCRIPT_DIR"
SCRIPT_PARENT="$(realpath "$SCRIPT_DIR/..")"
echo "Scripts parent: $SCRIPT_PARENT"

# Function to display usage
usage() {
  echo "Usage: $0 [OPTIONS]"
  echo ""
  echo "Options:"
  echo "  --artifact-dir DIR     Path to the directory were artifacts will be placed (required)"
  echo "  --env-name NAME        Name of the conda environment (required)"
  echo "  --clone-et             Clone the executorch repository"
  echo "  --executorch-path DIR  Path to the executorch repo (required if --clone-et not used)"
  echo "  --create-env           Create the Python environment"
  echo "  --setup-env            Set up the Python environment"
  echo "  --export               Export the Whisper model"
  echo "  --model-name NAME      HuggingFace model name (optional, default: openai/whisper-large-v3-turbo)"
  echo "  --build                Build the Whisper runner"
  echo "  --audio-path PATH      Path to the input audio file"
  echo "  --run                  Run the Whisper model"
  echo "  -h, --help             Display this help message"
  echo ""
  echo "Example:"
  echo "  $0 --env-name metal-backend --setup-env --export --build --audio-path audio.wav --run"
  echo "  $0 --env-name metal-backend --export --model-name openai/whisper-small --build --audio-path audio.wav --run"
  exit 1
}

# Parse command-line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --clone-et)
      CLONE=true
      shift
      ;;
    --executorch-path)
      EXECUTORCH_PATH="$2"
      shift 2
      ;;
    --env-name)
      ENV_NAME="$2"
      shift 2
      ;;
    --create-env)
      CREATE_ENV=true
      shift
      ;;
    --setup-env)
      SETUP_ENV=true
      shift
      ;;
    --artifact-dir)
      ARTIFACT_DIR="$2"
      shift 2
      ;;
    --export)
      EXPORT=true
      shift
      ;;
    --model-name)
      MODEL_NAME="$2"
      shift 2
      ;;
    --build)
      BUILD=true
      shift
      ;;
    --audio-path)
      AUDIO_PATH="$2"
      shift 2
      ;;
    --run)
      RUN=true
      shift
      ;;
    -h|--help)
      usage
      ;;
    *)
      echo "Error: Unknown option: $1"
      usage
      ;;
  esac
done

# Validate required options
if [ -z "$ARTIFACT_DIR" ]; then
  echo "Error: --artifact-dir is required"
  exit 1
fi

if [ -z "$ENV_NAME" ]; then
  echo "Error: --env-name is required"
  exit 1
fi

if [ "$CLONE" = false ]; then
  if [ -z "$EXECUTORCH_PATH" ]; then
    echo "Error: --executorch-path is required when not using --clone"
    exit 1
  fi
fi

if [ "$RUN" = true ]; then
  if [ -z "$AUDIO_PATH" ]; then
    echo "Error: --audio-path is required when using --run"
    exit 1
  fi
fi

# Clone and cd into executorch if --clone flag is set
if [ "$CLONE" = true ]; then
  if [ -n "$EXECUTORCH_PATH" ]; then
    echo "Error: --executorch-path should not be provided when using --clone"
    exit 1
  fi

  mkdir -p "$ARTIFACT_DIR"
  cd "$ARTIFACT_DIR"
  echo "Cloning executorch repository..."
  git clone git@github.com:pytorch/executorch.git

  # Set EXECUTORCH_PATH to ARTIFACT_DIR/executorch after cloning
  EXECUTORCH_PATH="${ARTIFACT_DIR}/executorch"
fi

cd "$EXECUTORCH_PATH"

# Execute create-env
if [ "$CREATE_ENV" = true ]; then
  echo "Creating the Python environment $ENV_NAME ..."
  conda create -yn "$ENV_NAME" python=3.11
fi

# Execute setup-env
if [ "$SETUP_ENV" = true ]; then
  echo "Setting up Python environment $ENV_NAME ..."
  echo " - Script: $SCRIPT_PARENT/setup_python_env.sh"
  conda run -n "$ENV_NAME" "$SCRIPT_PARENT/setup_python_env.sh"

fi

# Execute export
if [ "$EXPORT" = true ]; then
  echo "Exporting Whisper model to $ARTIFACT_DIR ..."
  echo " - Model: $MODEL_NAME"
  echo " - Script: $SCRIPT_DIR/export.sh"
  conda run -n "$ENV_NAME" "$SCRIPT_DIR/export.sh" "$ARTIFACT_DIR" "$MODEL_NAME"
fi

# Execute build
if [ "$BUILD" = true ]; then
  echo "Building Whisper runner ..."
  echo " - Script: $SCRIPT_DIR/build.sh"
  conda run -n "$ENV_NAME" "$SCRIPT_DIR/build.sh"
fi

# Execute run
if [ "$RUN" = true ]; then
  echo "Running Whisper with audio: $AUDIO_PATH"
  echo " - Script: $SCRIPT_DIR/run.sh"
  "$SCRIPT_DIR/run.sh" "$AUDIO_PATH" "$ARTIFACT_DIR"
fi

echo "Done!"
