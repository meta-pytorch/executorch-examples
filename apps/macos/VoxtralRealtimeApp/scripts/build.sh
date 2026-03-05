#!/usr/bin/env bash
#
# Build a self-contained Voxtral Realtime DMG with all model files bundled.
#
# Prerequisites:
#   - Activate the conda env with ExecuTorch + Metal backend installed
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
#   CONDA_ENV         expected conda env name     (default: et-metal)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

EXECUTORCH_PATH="${EXECUTORCH_PATH:-${HOME}/executorch}"
MODEL_DIR="${MODEL_DIR:-${HOME}/voxtral_realtime_quant_metal}"
RUNNER_PATH="${EXECUTORCH_PATH}/cmake-out/examples/models/voxtral_realtime/voxtral_realtime_runner"
LIBOMP_PATH="/opt/homebrew/opt/libomp/lib/libomp.dylib"
EXPECTED_CONDA_ENV="${CONDA_ENV:-et-metal}"

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
      echo "Before running this script:"
      echo "  1. conda activate ${EXPECTED_CONDA_ENV}"
      echo "  2. Build ExecuTorch runner (if not already done):"
      echo "     cd ${EXECUTORCH_PATH} && make voxtral_realtime-metal"
      echo "  3. Download models (or pass --download-models):"
      echo "     hf download mistralai/Voxtral-Mini-4B-Realtime-2602-Executorch --local-dir ${MODEL_DIR}"
      echo ""
      echo "Options:"
      echo "  --download-models   Download model artifacts from HuggingFace before building"
      echo "  -h, --help          Show this help message"
      echo ""
      echo "Environment variables:"
      echo "  EXECUTORCH_PATH     Path to executorch repo (default: ~/executorch)"
      echo "  MODEL_DIR           Path to model artifacts (default: ~/voxtral_realtime_quant_metal)"
      echo "  CONDA_ENV           Expected conda env name (default: et-metal)"
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

if [[ -z "${CONDA_DEFAULT_ENV:-}" ]]; then
  echo ""
  echo "ERROR: No conda environment is active." >&2
  echo "" >&2
  echo "This build requires a conda env with ExecuTorch installed (Metal backend)." >&2
  echo "The runner binary and model download tools depend on it." >&2
  echo "" >&2
  echo "If you haven't set up the environment yet:" >&2
  echo "" >&2
  echo "  # Create and activate the conda environment" >&2
  echo "  conda create -n ${EXPECTED_CONDA_ENV} python=3.10 -y" >&2
  echo "  conda activate ${EXPECTED_CONDA_ENV}" >&2
  echo "" >&2
  echo "  # Install ExecuTorch with Metal (MPS) backend" >&2
  echo "  cd ${EXECUTORCH_PATH}" >&2
  echo "  EXECUTORCH_BUILD_KERNELS_TORCHAO=1 TORCHAO_BUILD_EXPERIMENTAL_MPS=1 ./install_executorch.sh" >&2
  echo "" >&2
  echo "  # Build the voxtral realtime runner" >&2
  echo "  make voxtral_realtime-metal" >&2
  echo "" >&2
  echo "  # Install model download tool" >&2
  echo "  pip install huggingface_hub" >&2
  echo "" >&2
  echo "If you already have the environment, activate it and re-run:" >&2
  echo "  conda activate ${EXPECTED_CONDA_ENV}" >&2
  echo "  ./scripts/build.sh" >&2
  exit 1
fi

echo "✓ Conda environment active: ${CONDA_DEFAULT_ENV}"

if [[ "${CONDA_DEFAULT_ENV}" != "${EXPECTED_CONDA_ENV}" ]]; then
  echo "  (expected '${EXPECTED_CONDA_ENV}' — set CONDA_ENV to suppress this warning)"
fi
echo ""

# ---------------------------------------------------------------------------
# Step 1: Check prerequisites
# ---------------------------------------------------------------------------
echo "--- Step 1: Checking prerequisites ---"

ERRORS=()
WARNINGS=()

# Build tools
if ! command -v xcodegen &>/dev/null; then
  ERRORS+=("xcodegen not found. Install with: brew install xcodegen")
fi

if ! command -v xcodebuild &>/dev/null; then
  ERRORS+=("xcodebuild not found. Install Xcode from the App Store.")
fi

# Runner binary (built from ExecuTorch with Metal backend)
if [[ ! -f "${RUNNER_PATH}" ]]; then
  ERRORS+=("Runner binary not found at: ${RUNNER_PATH}")
  ERRORS+=("  You need to build it inside the conda env:")
  ERRORS+=("    conda activate ${EXPECTED_CONDA_ENV}")
  ERRORS+=("    cd ${EXECUTORCH_PATH}")
  ERRORS+=("    EXECUTORCH_BUILD_KERNELS_TORCHAO=1 TORCHAO_BUILD_EXPERIMENTAL_MPS=1 ./install_executorch.sh")
  ERRORS+=("    make voxtral_realtime-metal")
fi

# libomp (runner runtime dependency)
if [[ ! -f "${LIBOMP_PATH}" ]]; then
  ERRORS+=("libomp not found at: ${LIBOMP_PATH}")
  ERRORS+=("  Install it: brew install libomp")
fi

# Download models if requested
if [[ "${DOWNLOAD_MODELS}" == true ]]; then
  echo "Downloading models from HuggingFace..."
  if ! command -v hf &>/dev/null && ! command -v huggingface-cli &>/dev/null; then
    ERRORS+=("huggingface-cli not found. Install with: pip install huggingface_hub")
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
  ERRORS+=("  Download with: hf download mistralai/Voxtral-Mini-4B-Realtime-2602-Executorch --local-dir ${MODEL_DIR}")
  ERRORS+=("  Or run: ./scripts/build.sh --download-models")
fi

# Report
if [[ ${#ERRORS[@]} -gt 0 ]]; then
  echo ""
  echo "ERROR: Missing prerequisites:" >&2
  for e in "${ERRORS[@]}"; do
    echo "  ✗ ${e}" >&2
  done
  echo "" >&2
  echo "Full setup guide:" >&2
  echo "  conda activate ${EXPECTED_CONDA_ENV}" >&2
  echo "  cd ${EXECUTORCH_PATH} && EXECUTORCH_BUILD_KERNELS_TORCHAO=1 TORCHAO_BUILD_EXPERIMENTAL_MPS=1 ./install_executorch.sh" >&2
  echo "  make voxtral_realtime-metal" >&2
  echo "  brew install libomp xcodegen" >&2
  echo "  pip install huggingface_hub" >&2
  echo "  hf download mistralai/Voxtral-Mini-4B-Realtime-2602-Executorch --local-dir ${MODEL_DIR}" >&2
  exit 1
fi

echo "✓ xcodegen found"
echo "✓ xcodebuild found"
echo "✓ Runner binary found: ${RUNNER_PATH}"
echo "✓ libomp found"
echo "✓ All model files present in ${MODEL_DIR}"
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
xcodebuild \
  -project VoxtralRealtime.xcodeproj \
  -scheme "${SCHEME}" \
  -configuration "${CONFIG}" \
  -derivedDataPath "${BUILD_DIR}" \
  build \
  2>&1 | tail -5

APP_PATH="${BUILD_DIR}/Build/Products/${CONFIG}/${APP_NAME}.app"
if [[ ! -d "${APP_PATH}" ]]; then
  echo "ERROR: Build succeeded but app not found at: ${APP_PATH}" >&2
  exit 1
fi
echo "✓ App built at: ${APP_PATH}"
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
echo "  DMG:         ${DMG_OUTPUT}"
echo "  App:         ${APP_PATH}"
echo "  Conda env:   ${CONDA_DEFAULT_ENV}"
echo ""
echo "To distribute: upload the DMG to GitHub Releases."
echo ""
