"""Gemma3 vision-language module using ExecutorTorch runtime."""
import base64
import io
import sys
import time
from pathlib import Path
from typing import Any, Dict, Optional

from PIL import Image

# Handle both relative and absolute imports
try:
    from ..base import BaseModule
    from ...config import GEMMA3_MODEL_PATH, GEMMA3_PROCESSOR_PATH, GEMMA3_HF_MODEL_ID
except ImportError:
    from modules.base import BaseModule
    from config import GEMMA3_MODEL_PATH, GEMMA3_PROCESSOR_PATH, GEMMA3_HF_MODEL_ID

# Add the runtime directories to path for imports
# Path: gemma3_module.py -> multimodal -> modules -> backend -> ask-anything-app -> multimodal (project)
MULTIMODAL_DIR = Path(__file__).parent.parent.parent.parent.parent
sys.path.insert(0, str(MULTIMODAL_DIR / "text-image-runtime"))


class Gemma3Module(BaseModule):
    """Vision-language module using Gemma3 4B model."""

    def __init__(self):
        super().__init__()
        self._runner = None

    def load(
        self,
        model_path: Optional[str] = None,
        processor_path: Optional[str] = None,
        hf_model_id: Optional[str] = None,
    ) -> None:
        """Load Gemma3 model.

        Args:
            model_path: Path to the .pte model file
            processor_path: Path to processor/tokenizer directory
            hf_model_id: HuggingFace model ID for tokenizer fallback
        """
        if self._loaded:
            print("Gemma3 model already loaded")
            return

        # Use defaults if not provided
        model_path = model_path or GEMMA3_MODEL_PATH
        processor_path = processor_path or GEMMA3_PROCESSOR_PATH
        hf_model_id = hf_model_id or GEMMA3_HF_MODEL_ID

        print(f"Loading Gemma3 model from {model_path}...")
        load_start = time.time()

        # Import the runtime module
        from runtime_inference import Gemma3RuntimeRunner

        self._runner = Gemma3RuntimeRunner(
            model_path=model_path,
            processor_path=processor_path,
            hf_model_id=hf_model_id,
        )

        load_time = time.time() - load_start
        print(f"Gemma3 model loaded in {load_time:.2f}s")
        self._loaded = True

    def unload(self) -> None:
        """Unload the model and free resources."""
        self._runner = None
        self._loaded = False
        print("Gemma3 model unloaded")

    def infer(
        self,
        prompt: str,
        image: Optional[Image.Image] = None,
        image_base64: Optional[str] = None,
        max_new_tokens: int = 256,
        temperature: float = 0.7,
    ) -> str:
        """Run vision-language inference.

        Args:
            prompt: Text prompt for the model
            image: Optional PIL Image
            image_base64: Optional base64-encoded image string
            max_new_tokens: Maximum tokens to generate
            temperature: Sampling temperature (0 = greedy)

        Returns:
            Generated text response
        """
        if not self._loaded or self._runner is None:
            raise RuntimeError("Gemma3 model not loaded. Call load() first.")

        # Decode base64 image if provided
        if image_base64 and image is None:
            try:
                image_data = base64.b64decode(image_base64)
                image = Image.open(io.BytesIO(image_data))
            except Exception as e:
                print(f"Warning: Failed to decode base64 image: {e}")
                image = None

        # Run inference
        response = self._runner.generate(
            prompt=prompt,
            image=image,
            max_new_tokens=max_new_tokens,
            temperature=temperature,
            echo=False,  # Disable console output in server mode
        )

        return response

    def get_status(self) -> Dict[str, Any]:
        """Get detailed module status."""
        status = super().get_status()
        if self._runner:
            status["max_seq_len"] = getattr(self._runner, "max_seq_len", None)
            status["vision_token_id"] = getattr(self._runner, "vision_token_id", None)
        return status
