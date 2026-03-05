#!/usr/bin/env bash
#
# Build a self-contained Voxtral Realtime DMG with all model files bundled.
#
# Prerequisites:
#   - Create and activate the et-metal conda env with ExecuTorch + Metal backend
#   - Build the voxtral_realtime_runner binary
#   - Download model files from HuggingFace (or pass --download-models)
#
# Usage:
#   conda activate et-metal
#   ./scripts/build.sh                        # uses default paths
#   ./scripts/build.sh --download-models      # also downloads models from HuggingFace
#
# Environment variables (override defaults):
#   EXECUTORCH_PATH   path to executorch repo     (default: ~/executorch)
#   MODEL_DIR         path to model artifacts     (default: ~/voxtral_realtime_quant_metal)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

export EXECUTORCH_PATH="${EXECUTORCH_PATH:-${HOME}/executorch}"
export MODEL_DIR="${MODEL_DIR:-${HOME}/voxtral_realtime_quant_metal}"
RUNNER_PATH="${EXECUTORCH_PATH}/cmake-out/examples/models/voxtral_realtime/voxtral_realtime_runner"
LIBOMP_PATH="/opt/homebrew/opt/libomp/lib/libomp.dylib"
EXPECTED_CONDA_ENV="et-metal"

BUILD_DIR="${PROJECT_DIR}/build"
SCHEME="VoxtralRealtime"
CONFIG="Release"
APP_NAME="Voxtral Realtime"
DMG_OUTPUT="${PROJECT_DIR}/VoxtralRealtime.dmg"

DOWNLOAD_MODELS=false
for arg in "$@"; do
  case "${arg}" in
    --download-models) DOWNLOAD_MODELS=true ;;
    -h|--help)
      echo "Usage: ./scripts/build.sh [--download-models]"
      echo ""
      echo "Builds a self-contained DMG with all model files bundled."
      echo ""
      echo "Before running this script, set up the et-metal conda environment:"
      echo ""
      echo "  # One-time setup"
      echo "  conda create -n et-metal python=3.10 -y"
      echo "  conda activate et-metal"
      echo "  git clone https://github.com/pytorch/executorch/ ~/executorch"
      echo "  cd ~/executorch"
      echo "  EXECUTORCH_BUILD_KERNELS_TORCHAO=1 TORCHAO_BUILD_EXPERIMENTAL_MPS=1 ./install_executorch.sh"
      echo "  make voxtral_realtime-metal"
      echo "  brew install libomp xcodegen"
      echo "  pip install huggingface_hub"
      echo ""
      echo "  # Then build"
      echo "  conda activate et-metal"
      echo "  ./scripts/build.sh --download-models"
      echo ""
      echo "Options:"
      echo "  --download-models   Download model artifacts from HuggingFace before building"
      echo "  -h, --help          Show this help message"
      echo ""
      echo "Environment variables:"
      echo "  EXECUTORCH_PATH     Path to executorch repo (default: ~/executorch)"
      echo "  MODEL_DIR           Path to model artifacts (default: ~/voxtral_realtime_quant_metal)"
      exit 0
      ;;
    *) echo "Unknown argument: ${arg}. Use --help for usage." >&2; exit 1 ;;
  esac
done

# ---------------------------------------------------------------------------
echo ""
echo "=== Voxtral Realtime — Build Pipeline ==="
echo ""

# ---------------------------------------------------------------------------
# Step 0: Check conda environment
# ---------------------------------------------------------------------------
echo "--- Step 0: Checking conda environment ---"

if [[ -z "${CONDA_DEFAULT_ENV:-}" || "${CONDA_DEFAULT_ENV}" == "base" ]]; then
  echo ""
  echo "ERROR: The et-metal conda environment is not active." >&2
  echo "" >&2
  echo "This build requires a dedicated conda env with ExecuTorch installed" >&2
  echo "(Metal/MPS backend). All steps below must run inside this env." >&2
  echo "" >&2

  if [[ -z "${CONDA_DEFAULT_ENV:-}" ]]; then
    echo "No conda environment is active." >&2
  else
    echo "You are in the 'base' env — ExecuTorch should not be installed in base." >&2
  fi

  echo "" >&2
  echo "=== One-time setup ===" >&2
  echo "" >&2
  echo "  # 1. Create the et-metal conda environment" >&2
  echo "  conda create -n et-metal python=3.10 -y" >&2
  echo "  conda activate et-metal" >&2
  echo "" >&2
  echo "  # 2. Clone and install ExecuTorch with Metal (MPS) backend" >&2
  echo "  git clone https://github.com/pytorch/executorch/ ~/executorch" >&2
  echo "  cd ~/executorch" >&2
  echo "  EXECUTORCH_BUILD_KERNELS_TORCHAO=1 TORCHAO_BUILD_EXPERIMENTAL_MPS=1 ./install_executorch.sh" >&2
  echo "" >&2
  echo "  # 3. Build the voxtral realtime runner" >&2
  echo "  make voxtral_realtime-metal" >&2
  echo "" >&2
  echo "  # 4. Install tools" >&2
  echo "  brew install libomp xcodegen" >&2
  echo "  pip install huggingface_hub" >&2
  echo "" >&2
  echo "=== Then build ===" >&2
  echo "" >&2
  echo "  conda activate et-metal" >&2
  echo "  cd $(pwd)" >&2
  echo "  ./scripts/build.sh --download-models" >&2
  exit 1
fi

if [[ "${CONDA_DEFAULT_ENV}" != "${EXPECTED_CONDA_ENV}" ]]; then
  echo "WARNING: Active conda env is '${CONDA_DEFAULT_ENV}', expected '${EXPECTED_CONDA_ENV}'." >&2
  echo "  Continuing, but make sure ExecuTorch with Metal backend is installed in this env." >&2
  echo ""
fi

echo "✓ Conda environment active: ${CONDA_DEFAULT_ENV}"
echo ""

# ---------------------------------------------------------------------------
# Step 1: Check prerequisites
# ---------------------------------------------------------------------------
echo "--- Step 1: Checking prerequisites ---"

ERRORS=()

# Build tools
if ! command -v xcodegen &>/dev/null; then
  ERRORS+=("xcodegen not found — install with: brew install xcodegen")
fi

if ! command -v xcodebuild &>/dev/null; then
  ERRORS+=("xcodebuild not found — install Xcode from the App Store")
fi

# ExecuTorch repo
if [[ ! -d "${EXECUTORCH_PATH}" ]]; then
  ERRORS+=("ExecuTorch repo not found at: ${EXECUTORCH_PATH}")
  ERRORS+=("  Clone it:  git clone https://github.com/pytorch/executorch/ ${EXECUTORCH_PATH}")
  ERRORS+=("  Or set:    EXECUTORCH_PATH=/your/path ./scripts/build.sh")
fi

# Runner binary (built from ExecuTorch with Metal backend)
if [[ ! -f "${RUNNER_PATH}" ]]; then
  ERRORS+=("Runner binary not found at: ${RUNNER_PATH}")
  ERRORS+=("  Build it (inside conda env):")
  ERRORS+=("    conda activate et-metal")
  ERRORS+=("    cd ${EXECUTORCH_PATH}")
  ERRORS+=("    EXECUTORCH_BUILD_KERNELS_TORCHAO=1 TORCHAO_BUILD_EXPERIMENTAL_MPS=1 ./install_executorch.sh")
  ERRORS+=("    make voxtral_realtime-metal")
fi

# libomp (runner runtime dependency)
if [[ ! -f "${LIBOMP_PATH}" ]]; then
  ERRORS+=("libomp not found at: ${LIBOMP_PATH}")
  ERRORS+=("  Install:   brew install libomp")
fi

# Download models if requested
if [[ "${DOWNLOAD_MODELS}" == true ]]; then
  echo "Downloading models from HuggingFace (~6.2 GB)..."
  if ! command -v hf &>/dev/null && ! command -v huggingface-cli &>/dev/null; then
    ERRORS+=("Neither 'hf' nor 'huggingface-cli' found — install with: pip install huggingface_hub")
  else
    HF_CMD="hf"
    if ! command -v hf &>/dev/null; then
      HF_CMD="huggingface-cli"
    fi
    ${HF_CMD} download mistralai/Voxtral-Mini-4B-Realtime-2602-Executorch --local-dir "${MODEL_DIR}"
    echo "✓ Models downloaded to ${MODEL_DIR}"
  fi
fi

# Model files
MODEL_FILES=("model-metal-int4.pte" "preprocessor.pte" "tekken.json")
MISSING_MODELS=()
for f in "${MODEL_FILES[@]}"; do
  if [[ ! -f "${MODEL_DIR}/${f}" ]]; then
    MISSING_MODELS+=("${f}")
  fi
done

if [[ ${#MISSING_MODELS[@]} -gt 0 ]]; then
  ERRORS+=("Model files missing from ${MODEL_DIR}:")
  for f in "${MISSING_MODELS[@]}"; do
    ERRORS+=("  - ${f}")
  done
  ERRORS+=("  Download: hf download mistralai/Voxtral-Mini-4B-Realtime-2602-Executorch --local-dir ${MODEL_DIR}")
  ERRORS+=("  Or run:   ./scripts/build.sh --download-models")
fi

# Report errors
if [[ ${#ERRORS[@]} -gt 0 ]]; then
  echo ""
  echo "ERROR: Missing prerequisites:" >&2
  for e in "${ERRORS[@]}"; do
    echo "  ✗ ${e}" >&2
  done
  exit 1
fi

echo "✓ xcodegen: $(which xcodegen)"
echo "✓ xcodebuild: $(which xcodebuild)"
echo "✓ ExecuTorch: ${EXECUTORCH_PATH}"
echo "✓ Runner binary: ${RUNNER_PATH}"
echo "✓ libomp: ${LIBOMP_PATH}"
echo "✓ Model files: ${MODEL_DIR}"
echo ""

# ---------------------------------------------------------------------------
# Step 2: Generate Xcode project
# ---------------------------------------------------------------------------
echo "--- Step 2: Generating Xcode project ---"
cd "${PROJECT_DIR}"
xcodegen generate
echo "✓ VoxtralRealtime.xcodeproj generated"
echo ""

# ---------------------------------------------------------------------------
# Step 3: Build the app (Release)
# ---------------------------------------------------------------------------
echo "--- Step 3: Building ${SCHEME} (${CONFIG}) ---"

BUILD_LOG="${BUILD_DIR}/build.log"
mkdir -p "${BUILD_DIR}"

set +e
xcodebuild \
  -project VoxtralRealtime.xcodeproj \
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
  echo "Last 20 lines of build log:" >&2
  echo "" >&2
  tail -20 "${BUILD_LOG}" >&2
  echo "" >&2
  echo "Full log: ${BUILD_LOG}" >&2
  exit 1
fi

tail -3 "${BUILD_LOG}"

APP_PATH="${BUILD_DIR}/Build/Products/${CONFIG}/${APP_NAME}.app"
if [[ ! -d "${APP_PATH}" ]]; then
  echo "ERROR: Build succeeded but app not found at: ${APP_PATH}" >&2
  echo "Full log: ${BUILD_LOG}" >&2
  exit 1
fi
echo "✓ App built: ${APP_PATH}"
echo ""

# ---------------------------------------------------------------------------
# Step 4: Create DMG
# ---------------------------------------------------------------------------
echo "--- Step 4: Creating DMG ---"
rm -f "${DMG_OUTPUT}"
"${SCRIPT_DIR}/create_dmg.sh" "${APP_PATH}" "${DMG_OUTPUT}"
echo ""

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo "=== Build Complete ==="
echo ""
echo "  DMG:       ${DMG_OUTPUT}"
echo "  App:       ${APP_PATH}"
echo "  Conda env: ${CONDA_DEFAULT_ENV}"
echo "  Build log: ${BUILD_LOG}"
echo ""
echo "To distribute: upload the DMG to GitHub Releases."
echo ""
