#!/usr/bin/env bash
#
# Build a self-contained Voxtral Realtime DMG with all model files bundled.
#
# Usage:
#   ./scripts/build.sh                        # uses default paths
#   ./scripts/build.sh --download-models      # also downloads models from HuggingFace
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

EXECUTORCH_PATH="${EXECUTORCH_PATH:-${HOME}/executorch}"
MODEL_DIR="${MODEL_DIR:-${HOME}/voxtral_realtime_quant_metal}"
RUNNER_PATH="${EXECUTORCH_PATH}/cmake-out/examples/models/voxtral_realtime/voxtral_realtime_runner"
LIBOMP_PATH="/opt/homebrew/opt/libomp/lib/libomp.dylib"

BUILD_DIR="${PROJECT_DIR}/build"
SCHEME="VoxtralRealtime"
CONFIG="Release"
APP_NAME="Voxtral Realtime"
DMG_OUTPUT="${PROJECT_DIR}/VoxtralRealtime.dmg"

DOWNLOAD_MODELS=false
for arg in "$@"; do
  case "${arg}" in
    --download-models) DOWNLOAD_MODELS=true ;;
    *) echo "Unknown argument: ${arg}" >&2; exit 1 ;;
  esac
done

# ---------------------------------------------------------------------------
echo ""
echo "=== Voxtral Realtime — Build Pipeline ==="
echo ""

# ---------------------------------------------------------------------------
# Step 1: Check prerequisites
# ---------------------------------------------------------------------------
echo "--- Step 1: Checking prerequisites ---"

ERRORS=()

if ! command -v xcodegen &>/dev/null; then
  ERRORS+=("xcodegen not found. Install with: brew install xcodegen")
fi

if ! command -v xcodebuild &>/dev/null; then
  ERRORS+=("xcodebuild not found. Install Xcode from the App Store.")
fi

if [[ ! -f "${RUNNER_PATH}" ]]; then
  ERRORS+=("Runner binary not found at: ${RUNNER_PATH}")
  ERRORS+=("  Build it: cd ${EXECUTORCH_PATH} && make voxtral_realtime-metal")
fi

if [[ ! -f "${LIBOMP_PATH}" ]]; then
  ERRORS+=("libomp not found at: ${LIBOMP_PATH}")
  ERRORS+=("  Install it: brew install libomp")
fi

# Step 1b: Download models if requested, or check they exist
if [[ "${DOWNLOAD_MODELS}" == true ]]; then
  echo "Downloading models from HuggingFace..."
  if ! command -v hf &>/dev/null && ! command -v huggingface-cli &>/dev/null; then
    ERRORS+=("huggingface-cli not found. Install with: pip install huggingface_hub")
  else
    if command -v hf &>/dev/null; then
      hf download mistralai/Voxtral-Mini-4B-Realtime-2602-Executorch --local-dir "${MODEL_DIR}"
    else
      huggingface-cli download mistralai/Voxtral-Mini-4B-Realtime-2602-Executorch --local-dir "${MODEL_DIR}"
    fi
    echo "✓ Models downloaded to ${MODEL_DIR}"
  fi
fi

MODEL_FILES=("model-metal-int4.pte" "preprocessor.pte" "tekken.json")
for f in "${MODEL_FILES[@]}"; do
  if [[ ! -f "${MODEL_DIR}/${f}" ]]; then
    ERRORS+=("Model file not found: ${MODEL_DIR}/${f}")
  fi
done

if [[ ${#ERRORS[@]} -gt 0 ]]; then
  echo ""
  echo "ERROR: Missing prerequisites:" >&2
  for e in "${ERRORS[@]}"; do
    echo "  ✗ ${e}" >&2
  done
  echo ""
  echo "To download models:  ./scripts/build.sh --download-models" >&2
  echo "Or manually:         hf download mistralai/Voxtral-Mini-4B-Realtime-2602-Executorch --local-dir ~/voxtral_realtime_quant_metal" >&2
  exit 1
fi

echo "✓ xcodegen found"
echo "✓ Runner binary found"
echo "✓ libomp found"
echo "✓ All model files present"
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
echo "  DMG: ${DMG_OUTPUT}"
echo "  App: ${APP_PATH}"
echo ""
echo "To distribute: upload the DMG to GitHub Releases."
echo ""
