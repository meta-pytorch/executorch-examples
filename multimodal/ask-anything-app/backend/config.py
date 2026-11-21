"""Configuration for Ask Anything backend."""
from pathlib import Path

# Base paths
APP_DIR = Path(__file__).parent.parent
MULTIMODAL_DIR = APP_DIR.parent

# Model paths
GEMMA3_MODEL_PATH = str(
    MULTIMODAL_DIR / "text-image-runtime" / "gemma3" / "GEMMA3_4B_XNNPACK_INT8_INT4.pte"
)
GEMMA3_PROCESSOR_PATH = str(MULTIMODAL_DIR / "text-image-runtime" / "gemma3")
GEMMA3_HF_MODEL_ID = "google/gemma-3-4b-it"

WHISPER_MODEL_DIR = str(
    MULTIMODAL_DIR / "voice-runtime" / "models" / "whisper-tiny-ExecuTorch-XNNPACK"
)

# Server config
HOST = "0.0.0.0"
PORT = 8000
CORS_ORIGINS = [
    "http://localhost:5173",
    "http://127.0.0.1:5173",
    "http://localhost:3000",
]
