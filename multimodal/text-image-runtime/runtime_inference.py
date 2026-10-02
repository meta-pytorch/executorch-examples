#!/usr/bin/env python3
"""
Gemma3 Multimodal (Vision-Language) inference using ExecutorTorch Runtime directly.

This script demonstrates how to run Gemma3 multimodal inference using the low-level
ExecutorTorch portable_lib API for vision-language tasks.

Example usage:
    python runtime_inference.py --image_path example.jpg --prompt "What is in this image?"
    python runtime_inference.py --image_path example.jpg --prompt "How many people are in this image?"

Requirements:
    - ExecuTorch with Python bindings installed
    - transformers
    - Pillow for image loading
"""

import argparse
import sys
import time
from pathlib import Path
from typing import List, Optional

import torch
from PIL import Image
from transformers import AutoProcessor, AutoTokenizer

# Load required operator libraries for quantized ops
# These must be imported BEFORE loading the model to register the operators.
# IMPORTANT: Import order matters! We follow the same order as optimum-executorch:
# 1. torch.ao.quantization decomposed lib
# 2. portable_lib (loads ExecuTorch runtime)
# 3. executorch.kernels.quantized
# 4. LLM custom ops (custom_sdpa specifically)
print("Loading operator libraries...")

try:
    # This registers the quantized decomposed ops
    from torch.ao.quantization.fx._decomposed import quantized_decomposed_lib  # noqa: F401

    print("  ✓ Loaded torch quantized decomposed lib")
except Exception as e:
    print(f"  ✗ Failed to load torch quantized decomposed lib: {e}")

# Import portable_lib BEFORE custom_ops (same order as optimum-executorch)
from executorch.extension.pybindings.portable_lib import _load_for_executorch

print("  ✓ Loaded portable_lib")

try:
    from executorch.kernels import quantized  # noqa: F401

    print("  ✓ Loaded executorch quantized kernels")
except Exception as e:
    print(f"  ✗ Failed to load executorch quantized kernels: {e}")

try:
    # This loads libcustom_ops_aot_lib.dylib which registers llama::update_cache and llama::custom_sdpa
    # IMPORTANT: Import custom_sdpa directly (not just the module) to ensure the library is loaded
    from executorch.extension.llm.custom_ops.custom_ops import custom_sdpa  # noqa: F401

    print("  ✓ Loaded LLM custom ops (custom_sdpa, update_cache)")
except Exception as e:
    print(f"  ⚠ LLM custom ops not loaded (may not be needed): {e}")


def preprocess_image_manual(
    image: Image.Image,
    target_size: int = 896,
    image_mean: List[float] = [0.5, 0.5, 0.5],
    image_std: List[float] = [0.5, 0.5, 0.5],
) -> torch.Tensor:
    """Preprocess image manually without using transformers processor.

    Args:
        image: PIL Image
        target_size: Target size for resizing (896 for Gemma3)
        image_mean: Mean values for normalization
        image_std: Std values for normalization

    Returns:
        Preprocessed image tensor of shape [1, 3, H, W]
    """
    # Resize to target size
    image = image.convert("RGB")
    image = image.resize((target_size, target_size), Image.Resampling.BILINEAR)

    # Convert to tensor and normalize
    import numpy as np

    img_array = np.array(image).astype(np.float32) / 255.0  # Scale to [0, 1]

    # Normalize: (x - mean) / std
    for c in range(3):
        img_array[:, :, c] = (img_array[:, :, c] - image_mean[c]) / image_std[c]

    # Convert from HWC to CHW and add batch dimension
    img_tensor = torch.from_numpy(img_array).permute(2, 0, 1).unsqueeze(0)

    return img_tensor


class Gemma3RuntimeRunner:
    """Run Gemma3 multimodal inference using ExecutorTorch portable_lib directly."""

    # Default HuggingFace model ID for tokenizer/processor
    DEFAULT_HF_MODEL_ID = "google/gemma-3-4b-it"

    def __init__(self, model_path: str, processor_path: str, hf_model_id: Optional[str] = None):
        # Load model using _load_for_executorch first to get metadata
        print(f"Loading model from {model_path}...")
        load_start = time.time()
        self.model = _load_for_executorch(model_path)
        load_time = time.time() - load_start
        print(f"  Model loaded in {load_time:.2f}s")

        # Load metadata first (needed for vision_token_id)
        self._load_metadata()

        # Load processor/tokenizer
        # Try local path first, then fallback to HuggingFace model ID
        hf_model_id = hf_model_id or self.DEFAULT_HF_MODEL_ID
        print(f"Loading processor...")

        try:
            self.processor = AutoProcessor.from_pretrained(processor_path, trust_remote_code=True)
            self.tokenizer = self.processor.tokenizer
            print(f"  ✓ Loaded processor from {processor_path}")
        except Exception as e1:
            print(f"  ⚠ Local processor not found: {e1}")
            try:
                self.processor = AutoProcessor.from_pretrained(hf_model_id, trust_remote_code=True)
                self.tokenizer = self.processor.tokenizer
                print(f"  ✓ Loaded processor from HuggingFace: {hf_model_id}")
            except Exception as e2:
                print(f"  ⚠ HuggingFace processor not found: {e2}")
                # Final fallback to just tokenizer
                try:
                    self.tokenizer = AutoTokenizer.from_pretrained(hf_model_id, trust_remote_code=True)
                    self.processor = None
                    print(f"  ✓ Loaded tokenizer from HuggingFace: {hf_model_id}")
                except Exception as e3:
                    raise RuntimeError(
                        f"Could not load processor or tokenizer. Tried:\n"
                        f"  1. {processor_path}: {e1}\n"
                        f"  2. {hf_model_id}: {e2}\n"
                        f"  3. Tokenizer only from {hf_model_id}: {e3}"
                    )

    def _load_metadata(self):
        """Load model metadata from auxiliary methods."""
        method_names = self.model.method_names()
        print(f"Available methods: {method_names}")

        # Get key metadata
        self.use_kv_cache = self._get_metadata("use_kv_cache", True)
        self.max_seq_len = self._get_metadata("get_max_seq_len", 2048)
        self.eos_token_id = self._get_metadata("get_eos_id", 1)
        self.bos_token_id = self._get_metadata("get_bos_id", 2)
        self.vision_token_id = self._get_metadata("vision_token_id", 262144)
        self.image_seq_length = self._get_metadata("image_seq_length", 256)

        # Image preprocessing metadata
        self.image_size = self._get_metadata("size", [896, 896])
        self.image_mean = self._get_metadata("image_mean", [0.5, 0.5, 0.5])
        self.image_std = self._get_metadata("image_std", [0.5, 0.5, 0.5])

        # Stop tokens - include both EOS and <end_of_turn> for chat format
        # <end_of_turn> token ID is 106 for Gemma3
        self.stop_token_ids = {self.eos_token_id, 106}

        print(f"Model metadata:")
        print(f"  max_seq_len: {self.max_seq_len}")
        print(f"  eos_token_id: {self.eos_token_id}")
        print(f"  bos_token_id: {self.bos_token_id}")
        print(f"  vision_token_id: {self.vision_token_id}")
        print(f"  image_seq_length: {self.image_seq_length}")
        print(f"  image_size: {self.image_size}")
        print(f"  stop_token_ids: {self.stop_token_ids}")

    def _get_metadata(self, method_name: str, default):
        """Get metadata from model method."""
        if method_name in self.model.method_names():
            try:
                result = self.model.run_method(method_name)
                return result[0] if len(result) == 1 else result
            except Exception:
                return default
        return default

    def preprocess_image(self, image: Image.Image) -> torch.Tensor:
        """Preprocess image for vision encoder.

        Args:
            image: PIL Image

        Returns:
            Preprocessed image tensor
        """
        if self.processor is not None and hasattr(self.processor, 'image_processor'):
            # Use the transformers image processor
            inputs = self.processor.image_processor(images=image, return_tensors="pt")
            return inputs["pixel_values"]
        else:
            # Manual preprocessing
            target_size = self.image_size[0] if isinstance(self.image_size, list) else self.image_size
            return preprocess_image_manual(
                image,
                target_size=target_size,
                image_mean=self.image_mean,
                image_std=self.image_std,
            )

    def format_prompt(self, prompt: str, has_image: bool = True) -> str:
        """Format prompt with Gemma3's chat template.

        Args:
            prompt: User's text prompt
            has_image: Whether an image is included

        Returns:
            Formatted prompt string
        """
        if has_image:
            # Gemma3 format with image placeholder
            # Use a placeholder that we'll expand later with vision tokens
            formatted = f"<start_of_turn>user\n<image>{prompt}<end_of_turn>\n<start_of_turn>model\n"
        else:
            formatted = f"<start_of_turn>user\n{prompt}<end_of_turn>\n<start_of_turn>model\n"
        return formatted

    def build_input_ids_with_image(self, prompt: str) -> torch.Tensor:
        """Build input_ids with vision token placeholders expanded.

        For Gemma3, we need to:
        1. Tokenize the prompt with the processor (produces <start_of_image> marker)
        2. Find the <start_of_image> marker position
        3. Replace it with `image_seq_length` vision tokens

        Args:
            prompt: User's text prompt

        Returns:
            input_ids tensor with vision token placeholders
        """
        # Use processor if available (handles chat template correctly)
        if self.processor is not None:
            # Create a conversation format for the processor
            messages = [
                {
                    "role": "user",
                    "content": [
                        {"type": "image"},
                        {"type": "text", "text": prompt},
                    ],
                }
            ]
            text = self.processor.apply_chat_template(messages, add_generation_prompt=True, tokenize=False)
            # Tokenize the text
            input_ids = self.tokenizer.encode(text, return_tensors="pt")

            # Find the <start_of_image> token (ID 255999) and replace with vision tokens
            start_of_image_id = self.tokenizer.convert_tokens_to_ids("<start_of_image>")
            if start_of_image_id is not None:
                # Find position of <start_of_image>
                input_ids_list = input_ids[0].tolist()
                try:
                    img_pos = input_ids_list.index(start_of_image_id)
                    # Replace <start_of_image> with image_seq_length vision tokens
                    new_ids = (
                        input_ids_list[:img_pos]
                        + [self.vision_token_id] * self.image_seq_length
                        + input_ids_list[img_pos + 1:]
                    )
                    return torch.tensor([new_ids], dtype=torch.long)
                except ValueError:
                    # <start_of_image> not found, return as-is
                    pass

            return input_ids

        # Manual construction if processor not available
        # Format the prompt
        formatted = self.format_prompt(prompt, has_image=True)

        # Tokenize parts before and after image placeholder
        parts = formatted.split("<image>")
        if len(parts) != 2:
            # No image placeholder, just tokenize directly
            return self.tokenizer.encode(formatted, return_tensors="pt")

        before_image = parts[0]
        after_image = parts[1]

        # Tokenize each part (without special tokens to avoid duplicate BOS)
        before_tokens = self.tokenizer.encode(before_image, add_special_tokens=True)
        after_tokens = self.tokenizer.encode(after_image, add_special_tokens=False)

        # Create vision token placeholders
        vision_tokens = [self.vision_token_id] * self.image_seq_length

        # Combine: before + vision_tokens + after
        all_tokens = before_tokens + vision_tokens + after_tokens

        return torch.tensor([all_tokens], dtype=torch.long)

    def generate(
        self,
        prompt: str,
        image: Optional[Image.Image] = None,
        max_new_tokens: int = 100,
        temperature: float = 0.0,
        echo: bool = True,
    ) -> str:
        """Generate text from prompt and optional image.

        Args:
            prompt: Text prompt
            image: Optional PIL Image for vision-language tasks
            max_new_tokens: Maximum number of tokens to generate
            temperature: Sampling temperature (0 = greedy)
            echo: Whether to print tokens as they are generated

        Returns:
            Generated text
        """
        # Build input_ids with proper vision token handling
        if image is not None:
            input_ids = self.build_input_ids_with_image(prompt)
        else:
            formatted_prompt = self.format_prompt(prompt, has_image=False)
            input_ids = self.tokenizer.encode(formatted_prompt, return_tensors="pt")

        prompt_len = input_ids.shape[1]
        print(f"Prompt tokens: {prompt_len}")

        # Preprocess image if provided
        pixel_values = None
        if image is not None:
            print("Preprocessing image...")
            preprocess_start = time.time()
            pixel_values = self.preprocess_image(image)
            preprocess_time = time.time() - preprocess_start
            print(f"  Image shape: {pixel_values.shape}")
            print(f"  Preprocessing time: {preprocess_time:.2f}s")

        generated_tokens = []

        # Prefill phase
        print("Running prefill (encoder + first decoder pass)...")
        prefill_start = time.time()

        # Get token embeddings
        token_embeddings = self.model.run_method("token_embedding", (input_ids,))[0]

        # If we have an image, run vision encoder and merge embeddings
        if pixel_values is not None:
            print("  Running vision encoder...")
            vision_start = time.time()
            vision_embeddings = self.model.run_method("vision_encoder", (pixel_values,))[0]
            vision_time = time.time() - vision_start
            print(f"  Vision encoder output shape: {vision_embeddings.shape}")
            print(f"  Vision encoder time: {vision_time:.2f}s")

            # Find vision token positions and replace with vision embeddings
            # Vision embeddings shape: [1, num_patches, hidden_dim] or [num_patches, hidden_dim]
            vision_token_mask = input_ids == self.vision_token_id
            num_vision_tokens = vision_token_mask.sum().item()
            print(f"  Vision token positions: {num_vision_tokens} tokens")

            if num_vision_tokens > 0:
                # Reshape vision embeddings to match the number of vision tokens
                vision_emb_flat = vision_embeddings.reshape(-1, vision_embeddings.shape[-1])
                # Only use as many embeddings as we have vision tokens
                vision_emb_to_use = vision_emb_flat[:num_vision_tokens]
                token_embeddings[vision_token_mask] = vision_emb_to_use

        # Run text decoder for prefill
        cache_position = torch.arange(prompt_len, dtype=torch.long)
        try:
            logits = self.model.run_method("text_decoder", (token_embeddings, cache_position))[0]
        except Exception as e:
            print(f"\nError during prefill: {e}")
            raise

        prefill_time = time.time() - prefill_start
        print(f"  Prefill time: {prefill_time:.2f}s")

        # Sample first token
        if temperature > 0:
            probs = torch.softmax(logits[0, -1, :] / temperature, dim=-1)
            next_token = torch.multinomial(probs, num_samples=1).item()
        else:
            next_token = torch.argmax(logits[0, -1, :], dim=-1).item()

        generated_tokens.append(next_token)

        if echo:
            token_text = self.tokenizer.decode([next_token])
            print(token_text, end="", flush=True)

        # Check if first token is a stop token
        if next_token in self.stop_token_ids:
            if echo:
                print()  # Newline after generation
            decode_time = 0
        else:
            # Decode phase - generate tokens one at a time
            print("\nDecoding...", end="" if echo else "\n")
            decode_start = time.time()
            pos = prompt_len

            while len(generated_tokens) < max_new_tokens:
                # Check for stop tokens (EOS or <end_of_turn>)
                if next_token in self.stop_token_ids:
                    break

                # Get embedding for current token
                token_tensor = torch.tensor([[next_token]], dtype=torch.long)
                token_emb = self.model.run_method("token_embedding", (token_tensor,))[0]

                # Run decoder
                pos_tensor = torch.tensor([pos], dtype=torch.long)
                try:
                    logits = self.model.run_method("text_decoder", (token_emb, pos_tensor))[0]
                except Exception as e:
                    print(f"\nError during decode step {pos}: {e}")
                    break

                # Sample next token
                if temperature > 0:
                    probs = torch.softmax(logits[0, -1, :] / temperature, dim=-1)
                    next_token = torch.multinomial(probs, num_samples=1).item()
                else:
                    next_token = torch.argmax(logits[0, -1, :], dim=-1).item()

                generated_tokens.append(next_token)
                pos += 1

                if echo:
                    token_text = self.tokenizer.decode([next_token])
                    print(token_text, end="", flush=True)

            decode_time = time.time() - decode_start
            if echo:
                print()  # Newline after generation

        # Print stats
        num_tokens = len(generated_tokens)
        print("-" * 50)
        print(f"Generated tokens: {num_tokens}")
        print(f"Prefill: {prefill_time:.2f}s")
        if decode_time > 0 and num_tokens > 1:
            print(f"Decode: {decode_time:.2f}s ({(num_tokens - 1) / decode_time:.1f} tok/s)")
        print(f"Total: {prefill_time + decode_time:.2f}s")

        # Decode all tokens to text
        generated_text = self.tokenizer.decode(generated_tokens, skip_special_tokens=True)
        return generated_text


def main():
    parser = argparse.ArgumentParser(
        description="Run Gemma3 multimodal inference with ExecutorTorch Runtime (low-level API)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
    # Describe an image
    python runtime_inference.py --image_path example.jpg --prompt "What is in this image?"

    # Count objects
    python runtime_inference.py --image_path example.jpg --prompt "How many people are in this image?"

    # Text-only (no image)
    python runtime_inference.py --prompt "What is the capital of France?"
        """,
    )
    parser.add_argument(
        "--model_dir",
        type=str,
        default="gemma3",
        help="Path to the model directory containing .pte file and tokenizer",
    )
    parser.add_argument(
        "--model_file",
        type=str,
        default="GEMMA3_4B_XNNPACK_INT8_INT4.pte",
        help="Name of the .pte model file",
    )
    parser.add_argument(
        "--image_path",
        type=str,
        default=None,
        help="Path to the input image file (optional for text-only)",
    )
    parser.add_argument(
        "--prompt",
        type=str,
        default="What is in this image?",
        help="Text prompt for the model",
    )
    parser.add_argument(
        "--max_new_tokens",
        type=int,
        default=100,
        help="Maximum number of tokens to generate",
    )
    parser.add_argument(
        "--temperature",
        type=float,
        default=0.0,
        help="Sampling temperature (0 = greedy decoding)",
    )

    args = parser.parse_args()

    # Resolve paths relative to script directory
    script_dir = Path(__file__).parent
    model_dir = script_dir / args.model_dir
    model_path = str(model_dir / args.model_file)

    print("=" * 60)
    print("Gemma3 Multimodal Runtime Inference (low-level API)")
    print("=" * 60)

    # Load image if provided
    image = None
    if args.image_path:
        image_path = Path(args.image_path)
        if not image_path.is_absolute():
            image_path = script_dir / image_path

        print(f"Loading image from {image_path}...")
        image = Image.open(image_path)
        print(f"  Image size: {image.size}")

    # Create runner
    runner = Gemma3RuntimeRunner(model_path, str(model_dir))

    print()
    print("-" * 50)
    print(f"Prompt: {args.prompt}")
    print("-" * 50)
    print("Response: ", end="", flush=True)

    # Generate
    response = runner.generate(
        prompt=args.prompt,
        image=image,
        max_new_tokens=args.max_new_tokens,
        temperature=args.temperature,
        echo=True,
    )

    print()
    print("=" * 60)
    print("Final Response:")
    print("=" * 60)
    print(response)
    print()
    print("Done!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
