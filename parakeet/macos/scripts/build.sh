#!/usr/bin/env bash
#
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.
#
# Build the ExecuWhisper macOS app.
#
# By default this builds a lightweight app bundle that downloads the model on
# first launch. Pass --bundle-models if you want to embed model artifacts into
# the app bundle for offline testing/distribution.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

export EXECUTORCH_PATH="${EXECUTORCH_PATH:-${HOME}/executorch}"
export MODEL_DIR="${MODEL_DIR:-${HOME}/parakeet_metal}"
HELPER_PATH="${EXECUTORCH_PATH}/cmake-out/examples/models/parakeet/parakeet_helper"
LIBOMP_HOMEBREW="/opt/homebrew/opt/libomp/lib/libomp.dylib"
LIBOMP_LLVM="/opt/llvm-openmp/lib/libomp.dylib"
EXPECTED_CONDA_ENV="et-metal"

BUILD_DIR="${PROJECT_DIR}/build"
SCHEME="ExecuWhisper"
CONFIG="Release"
APP_NAME="ExecuWhisper"

DOWNLOAD_MODELS=false
BUNDLE_MODELS=false

for arg in "$@"; do
  case "${arg}" in
    --download-models) DOWNLOAD_MODELS=true ;;
    --bundle-models) BUNDLE_MODELS=true ;;
    -h|--help)
      echo "Usage: ./scripts/build.sh [--download-models] [--bundle-models]"
      echo ""
      echo "Builds the ExecuWhisper macOS app."
      echo ""
      echo "Options:"
      echo "  --download-models   Download model artifacts into MODEL_DIR before building"
      echo "  --bundle-models     Copy model.pte and tokenizer.model into the app bundle"
      echo "  -h, --help          Show this help message"
      echo ""
      echo "Environment variables:"
      echo "  EXECUTORCH_PATH     Path to executorch repo (default: ~/executorch)"
      echo "  MODEL_DIR           Path to Parakeet model artifacts (default: ~/parakeet_metal)"
      echo ""
      echo "Typical local setup:"
      echo "  conda activate et-metal"
      echo "  cd ~/executorch"
      echo "  make parakeet-metal"
      echo "  cd ${PROJECT_DIR}"
      echo "  ./scripts/build.sh"
      echo ""
      echo "Create a DMG after building:"
      echo "  ./scripts/create_dmg.sh \"./build/Build/Products/Release/ExecuWhisper.app\" \"./ExecuWhisper.dmg\""
      exit 0
      ;;
    *)
      echo "Unknown argument: ${arg}" >&2
      exit 1
      ;;
  esac
done

echo ""
echo "=== ExecuWhisper Build ==="
echo ""

echo "--- Step 0: Checking environment ---"
if [[ -z "${CONDA_DEFAULT_ENV:-}" ]]; then
  echo "WARNING: No conda environment is active." >&2
  echo "  Expected: ${EXPECTED_CONDA_ENV}" >&2
elif [[ "${CONDA_DEFAULT_ENV}" != "${EXPECTED_CONDA_ENV}" ]]; then
  echo "WARNING: Active conda env is '${CONDA_DEFAULT_ENV}', expected '${EXPECTED_CONDA_ENV}'." >&2
fi

ERRORS=()

if ! command -v xcodegen >/dev/null 2>&1; then
  ERRORS+=("xcodegen not found - install with: brew install xcodegen")
fi

if ! command -v xcodebuild >/dev/null 2>&1; then
  ERRORS+=("xcodebuild not found - install Xcode from the App Store")
fi

if [[ ! -d "${EXECUTORCH_PATH}" ]]; then
  ERRORS+=("ExecuTorch repo not found at ${EXECUTORCH_PATH}")
fi

if [[ ! -f "${HELPER_PATH}" ]]; then
  ERRORS+=("Parakeet helper not found at ${HELPER_PATH}")
  ERRORS+=("  Build it with: conda activate et-metal && cd ${EXECUTORCH_PATH} && make parakeet-metal")
fi

if [[ ! -f "${LIBOMP_HOMEBREW}" && ! -f "${LIBOMP_LLVM}" ]]; then
  ERRORS+=("libomp.dylib not found in expected locations")
  ERRORS+=("  Install it with: brew install libomp")
fi

if [[ "${DOWNLOAD_MODELS}" == true ]]; then
  echo "--- Step 1: Downloading models ---"
  if ! command -v hf >/dev/null 2>&1; then
    ERRORS+=("The 'hf' CLI is required for --download-models. Install with: pip install huggingface_hub")
  else
    hf download younghan-meta/Parakeet-TDT-ExecuTorch-Metal --local-dir "${MODEL_DIR}"
    echo "Downloaded model artifacts to ${MODEL_DIR}"
  fi
fi

if [[ "${BUNDLE_MODELS}" == true ]]; then
  for file in model.pte tokenizer.model; do
    if [[ ! -f "${MODEL_DIR}/${file}" ]]; then
      ERRORS+=("Missing ${MODEL_DIR}/${file} required for --bundle-models")
    fi
  done
fi

if [[ ${#ERRORS[@]} -gt 0 ]]; then
  echo ""
  echo "ERROR: Missing prerequisites:" >&2
  for error in "${ERRORS[@]}"; do
    echo "  - ${error}" >&2
  done
  exit 1
fi

echo "xcodegen: $(command -v xcodegen)"
echo "xcodebuild: $(command -v xcodebuild)"
echo "ExecuTorch: ${EXECUTORCH_PATH}"
echo "Helper: ${HELPER_PATH}"
echo "Bundle models: ${BUNDLE_MODELS}"
echo ""

echo "--- Step 2: Generating Xcode project ---"
cd "${PROJECT_DIR}"
xcodegen generate
echo "Generated ${SCHEME}.xcodeproj"
echo ""

echo "--- Step 3: Building app ---"
mkdir -p "${BUILD_DIR}"
BUILD_LOG="${BUILD_DIR}/build.log"

set +e
BUNDLE_MODEL_ARTIFACTS=$([[ "${BUNDLE_MODELS}" == true ]] && echo 1 || echo 0) \
xcodebuild \
  -project "${SCHEME}.xcodeproj" \
  -scheme "${SCHEME}" \
  -configuration "${CONFIG}" \
  -derivedDataPath "${BUILD_DIR}" \
  build \
  > "${BUILD_LOG}" 2>&1
BUILD_EXIT=$?
set -e

if [[ ${BUILD_EXIT} -ne 0 ]]; then
  echo ""
  echo "ERROR: xcodebuild failed (exit code ${BUILD_EXIT})." >&2
  echo "Last 30 lines:" >&2
  tail -30 "${BUILD_LOG}" >&2
  echo "" >&2
  echo "Full log: ${BUILD_LOG}" >&2
  exit 1
fi

APP_PATH="${BUILD_DIR}/Build/Products/${CONFIG}/${APP_NAME}.app"
if [[ ! -d "${APP_PATH}" ]]; then
  echo "ERROR: Build succeeded but app not found at ${APP_PATH}" >&2
  echo "Full log: ${BUILD_LOG}" >&2
  exit 1
fi

echo "Built app: ${APP_PATH}"
echo "Build log: ${BUILD_LOG}"
echo ""
echo "On first launch, ExecuWhisper downloads model artifacts into:"
echo "  ~/Library/Application Support/ExecuWhisper/models"
echo ""
echo "To create a DMG:"
echo "  ./scripts/create_dmg.sh \"${APP_PATH}\" \"${PROJECT_DIR}/ExecuWhisper.dmg\""
echo ""
