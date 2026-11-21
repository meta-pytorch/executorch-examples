"""Whisper speech-to-text module using ExecutorTorch runtime."""
import sys
import tempfile
import time
from pathlib import Path
from typing import Any, Dict, Optional

import torch

# Handle both relative and absolute imports
try:
    from ..base import BaseModule
    from ...config import WHISPER_MODEL_DIR
except ImportError:
    from modules.base import BaseModule
    from config import WHISPER_MODEL_DIR

# Add the runtime directories to path for imports
# Path: whisper_module.py -> voice -> modules -> backend -> ask-anything-app -> multimodal (project)
MULTIMODAL_DIR = Path(__file__).parent.parent.parent.parent.parent
sys.path.insert(0, str(MULTIMODAL_DIR / "voice-runtime"))


class WhisperModule(BaseModule):
    """Speech-to-text module using Whisper model."""

    def __init__(self):
        super().__init__()
        self._runner = None
        self._load_audio = None

    def load(
        self,
        model_dir: Optional[str] = None,
    ) -> None:
        """Load Whisper model.

        Args:
            model_dir: Path to the model directory containing model.pte and tokenizer
        """
        if self._loaded:
            print("Whisper model already loaded")
            return

        model_dir = Path(model_dir or WHISPER_MODEL_DIR)

        print(f"Loading Whisper model from {model_dir}...")
        load_start = time.time()

        # Import the runtime module
        from whisper_runtime_inference import WhisperRuntimeRunner, load_audio

        self._runner = WhisperRuntimeRunner(
            model_path=str(model_dir / "model.pte"),
            preprocessor_path=str(model_dir / "whisper_preprocessor.pte"),
            tokenizer_path=str(model_dir),
        )
        self._load_audio = load_audio

        load_time = time.time() - load_start
        print(f"Whisper model loaded in {load_time:.2f}s")
        self._loaded = True

    def unload(self) -> None:
        """Unload the model and free resources."""
        self._runner = None
        self._load_audio = None
        self._loaded = False
        print("Whisper model unloaded")

    def infer(
        self,
        audio_bytes: Optional[bytes] = None,
        audio_tensor: Optional[torch.Tensor] = None,
        max_new_tokens: int = 448,
    ) -> str:
        """Transcribe audio to text.

        Args:
            audio_bytes: Raw audio bytes (WAV format)
            audio_tensor: Pre-loaded audio tensor
            max_new_tokens: Maximum tokens to generate

        Returns:
            Transcribed text
        """
        if not self._loaded or self._runner is None:
            raise RuntimeError("Whisper model not loaded. Call load() first.")

        # Convert bytes to tensor if needed
        if audio_bytes is not None and audio_tensor is None:
            # Write to temp file and load with proper resampling
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=True) as f:
                f.write(audio_bytes)
                f.flush()
                audio_tensor = self._load_audio(f.name)

        if audio_tensor is None:
            raise ValueError("Either audio_bytes or audio_tensor must be provided")

        # Run transcription
        transcription = self._runner.transcribe(
            audio=audio_tensor,
            max_new_tokens=max_new_tokens,
            echo=False,  # Disable console output in server mode
        )

        return transcription

    def get_status(self) -> Dict[str, Any]:
        """Get detailed module status."""
        status = super().get_status()
        if self._runner:
            status["max_seq_len"] = getattr(self._runner, "max_seq_len", None)
            status["eos_token_id"] = getattr(self._runner, "eos_token_id", None)
        return status
