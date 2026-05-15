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
export FORMATTER_MODEL_DIR="${FORMATTER_MODEL_DIR:-${HOME}/lfm2_5_mlx}"
export PARAKEET_HELPER_PATH="${PARAKEET_HELPER_PATH:-${EXECUTORCH_PATH}/cmake-out/examples/models/parakeet/parakeet_helper}"
export FORMATTER_HELPER_PATH="${FORMATTER_HELPER_PATH:-${EXECUTORCH_PATH}/cmake-out/examples/models/llama/lfm25_formatter_helper}"
export FORMATTER_METALLIB_PATH="${FORMATTER_METALLIB_PATH:-$(dirname "${FORMATTER_HELPER_PATH}")/mlx.metallib}"
LIBOMP_HOMEBREW="/opt/homebrew/opt/libomp/lib/libomp.dylib"
LIBOMP_LLVM="/opt/llvm-openmp/lib/libomp.dylib"
EXPECTED_CONDA_ENV="et-metal"

BUILD_DIR="${PROJECT_DIR}/build"
SCHEME="ExecuWhisper"
CONFIG="Release"
APP_NAME="ExecuWhisper"

DOWNLOAD_MODELS=false
BUNDLE_MODELS=false
CHECK_ONLY=false

for arg in "$@"; do
  case "${arg}" in
    --download-models) DOWNLOAD_MODELS=true ;;
    --bundle-models) BUNDLE_MODELS=true ;;
    --check) CHECK_ONLY=true ;;
    -h|--help)
      echo "Usage: ./scripts/build.sh [--download-models] [--bundle-models]"
      echo ""
      echo "Builds the ExecuWhisper macOS app."
      echo ""
      echo "Options:"
      echo "  --download-models   Download Parakeet and LFM2.5 artifacts before building"
      echo "  --bundle-models     Copy ASR and formatter model artifacts into the app bundle"
      echo "  --check             Verify generated project settings and exit"
      echo "  -h, --help          Show this help message"
      echo ""
      echo "Environment variables:"
      echo "  EXECUTORCH_PATH     Path to executorch repo (default: ~/executorch)"
      echo "  PARAKEET_HELPER_PATH Path to parakeet_helper (default: EXECUTORCH_PATH/cmake-out/examples/models/parakeet/parakeet_helper)"
      echo "  FORMATTER_HELPER_PATH Path to lfm25_formatter_helper (default: EXECUTORCH_PATH/cmake-out/examples/models/llama/lfm25_formatter_helper)"
      echo "  FORMATTER_METALLIB_PATH Path to mlx.metallib (default: directory of FORMATTER_HELPER_PATH/mlx.metallib)"
      echo "  MODEL_DIR           Path to Parakeet model artifacts (default: ~/parakeet_metal)"
      echo "  FORMATTER_MODEL_DIR Path to LFM2.5 formatter artifacts (default: ~/lfm2_5_mlx)"
      echo ""
      echo "Typical local setup:"
      echo "  cd ~/executorch"
      echo "  gh pr checkout https://github.com/pytorch/executorch/pull/18861  # until parakeet_helper lands"
      echo "  conda activate et-metal"
      echo "  make parakeet-metal"
      echo "  make lfm_2_5_formatter-mlx"
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

if [[ ! -f "${PARAKEET_HELPER_PATH}" ]]; then
  ERRORS+=("Parakeet helper not found at ${PARAKEET_HELPER_PATH}")
  ERRORS+=("  Build it from pytorch/executorch#18861 with: cd ${EXECUTORCH_PATH} && gh pr checkout https://github.com/pytorch/executorch/pull/18861 && conda activate et-metal && make parakeet-metal")
fi

if [[ ! -f "${FORMATTER_HELPER_PATH}" ]]; then
  ERRORS+=("LFM2.5 formatter helper not found at ${FORMATTER_HELPER_PATH}")
  ERRORS+=("  Build it with: conda activate et-mlx && cd ${EXECUTORCH_PATH} && make lfm_2_5_formatter-mlx")
fi

if [[ ! -f "${FORMATTER_METALLIB_PATH}" ]]; then
  ERRORS+=("MLX metallib not found at ${FORMATTER_METALLIB_PATH}")
  ERRORS+=("  Rebuild the formatter helper with: conda activate et-mlx && cd ${EXECUTORCH_PATH} && make lfm_2_5_formatter-mlx")
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
    hf download younghan-meta/LFM2.5-350M-ExecuWhisper-Formatter \
      lfm2_5_350m_mlx_4w.pte tokenizer.json tokenizer_config.json \
      --local-dir "${FORMATTER_MODEL_DIR}"
    echo "Downloaded Parakeet artifacts to ${MODEL_DIR}"
    echo "Downloaded LFM2.5 formatter artifacts to ${FORMATTER_MODEL_DIR}"
  fi
fi

if [[ "${BUNDLE_MODELS}" == true ]]; then
  for file in model.pte tokenizer.model; do
    if [[ ! -f "${MODEL_DIR}/${file}" ]]; then
      ERRORS+=("Missing ${MODEL_DIR}/${file} required for --bundle-models")
    fi
  done
  for file in lfm2_5_350m_mlx_4w.pte tokenizer.json tokenizer_config.json; do
    if [[ ! -f "${FORMATTER_MODEL_DIR}/${file}" ]]; then
      ERRORS+=("Missing ${FORMATTER_MODEL_DIR}/${file} required for --bundle-models")
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
echo "Parakeet helper: ${PARAKEET_HELPER_PATH}"
echo "Formatter helper: ${FORMATTER_HELPER_PATH}"
echo "Formatter metallib: ${FORMATTER_METALLIB_PATH}"
echo "Bundle models: ${BUNDLE_MODELS}"
echo ""

echo "--- Step 2: Generating Xcode project ---"
cd "${PROJECT_DIR}"
xcodegen generate
echo "Generated ${SCHEME}.xcodeproj"
echo ""

if [[ "${CHECK_ONLY}" == true ]]; then
  echo "--- Step 3: Verifying project settings ---"
  ./scripts/verify_project_settings.sh
  exit 0
fi

echo "--- Step 3: Building app ---"
mkdir -p "${BUILD_DIR}"
BUILD_LOG="${BUILD_DIR}/build.log"

# Forward DEVELOPMENT_TEAM through to xcodebuild as an explicit build setting
# when set in the environment. Without this, the project (which intentionally
# leaves DEVELOPMENT_TEAM unset in project.yml so users supply their own)
# won't pick up the env var during signing.
TEAM_OVERRIDE=()
if [[ -n "${DEVELOPMENT_TEAM:-}" ]]; then
  TEAM_OVERRIDE=(DEVELOPMENT_TEAM="${DEVELOPMENT_TEAM}")
fi

set +e
BUNDLE_MODEL_ARTIFACTS=$([[ "${BUNDLE_MODELS}" == true ]] && echo 1 || echo 0) \
xcodebuild \
  -project "${SCHEME}.xcodeproj" \
  -scheme "${SCHEME}" \
  -configuration "${CONFIG}" \
  -derivedDataPath "${BUILD_DIR}" \
  "${TEAM_OVERRIDE[@]}" \
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

SIGNING_IDENTITY="$(
  xcodebuild \
    -project "${SCHEME}.xcodeproj" \
    -scheme "${SCHEME}" \
    -configuration "${CONFIG}" \
    -showBuildSettings 2>/dev/null \
    | awk -F= '
      /EXPANDED_CODE_SIGN_IDENTITY =/ { gsub(/^[ \t]+|[ \t]+$/, "", $2); if ($2 != "") expanded=$2 }
      /CODE_SIGN_IDENTITY =/ { gsub(/^[ \t]+|[ \t]+$/, "", $2); if ($2 != "") identity=$2 }
      END { if (expanded != "") print expanded; else if (identity != "") print identity }
    '
)"
DEVELOPMENT_TEAM="$(
  xcodebuild \
    -project "${SCHEME}.xcodeproj" \
    -scheme "${SCHEME}" \
    -configuration "${CONFIG}" \
    -showBuildSettings 2>/dev/null \
    | awk -F= '/DEVELOPMENT_TEAM =/ { gsub(/^[ \t]+|[ \t]+$/, "", $2); print $2; exit }'
)"

if [[ "${SIGNING_IDENTITY}" == "Apple Development" && -n "${DEVELOPMENT_TEAM}" ]]; then
  RESOLVED_IDENTITY="$(
    security find-identity -v -p codesigning 2>/dev/null \
      | awk -v team="(${DEVELOPMENT_TEAM})" '
        index($0, team) && $0 !~ /CSSMERR/ {
          sub(/^[[:space:]]*[0-9]+\)[[:space:]]*/, "", $0)
          print $1
          exit
        }
      '
  )"
  if [[ -n "${RESOLVED_IDENTITY}" ]]; then
    SIGNING_IDENTITY="${RESOLVED_IDENTITY}"
  fi
fi
if [[ "${SIGNING_IDENTITY}" == "Apple Development" ]]; then
  RESOLVED_IDENTITY="$(
    security find-identity -v -p codesigning 2>/dev/null \
      | awk '
        /Apple Development:/ && $0 !~ /CSSMERR/ {
          sub(/^[[:space:]]*[0-9]+\)[[:space:]]*/, "", $0)
          print $1
          exit
        }
      '
  )"
  if [[ -n "${RESOLVED_IDENTITY}" ]]; then
    SIGNING_IDENTITY="${RESOLVED_IDENTITY}"
  fi
fi

if [[ -n "${SIGNING_IDENTITY}" && "${SIGNING_IDENTITY}" != "-" ]]; then
  echo "--- Step 4: Signing bundled helpers ---"
  ./scripts/sign_release.sh "${APP_PATH}" "${SIGNING_IDENTITY}"
else
  echo "--- Step 4: Skipping helper signing (ad-hoc or unsigned build) ---"
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
