#!/usr/bin/env python3
"""
Qwen3-0.6B inference using ExecutorTorch Runtime via optimum-executorch.

This script runs inference on a Qwen3 model exported with ExecutorTorch XNNPACK backend.
The model was exported with --use_custom_sdpa --use_custom_kv_cache flags.

Usage:
    python qwen_inference.py --prompt "Hello, how are you?"
    python qwen_inference.py --prompt "What is the capital of France?" --max_new_tokens 50
"""

import argparse
from pathlib import Path

from optimum.executorch import ExecuTorchModelForCausalLM
from transformers import AutoTokenizer


def format_chat_prompt(
    prompt: str,
    system_prompt: str = "You are a helpful assistant.",
    enable_thinking: bool = False,
) -> str:
    """Format prompt using Qwen3's chat template.

    Args:
        prompt: The user's message
        system_prompt: System instructions for the assistant
        enable_thinking: If False, disable thinking mode to get direct answers
    """
    base = f"<|im_start|>system\n{system_prompt}<|im_end|>\n<|im_start|>user\n{prompt}<|im_end|>\n<|im_start|>assistant\n"
    if not enable_thinking:
        # Add empty thinking block to disable thinking mode
        base += "<think>\n\n</think>\n\n"
    return base


def main():
    parser = argparse.ArgumentParser(description="Run Qwen3 inference with ExecutorTorch")

    parser.add_argument(
        "--model_dir",
        type=str,
        default="models/Qwen3-0.6B-ExecuTorch-XNNPACK",
        help="Path to the model directory containing model.pte and tokenizer files",
    )
    parser.add_argument(
        "--prompt",
        type=str,
        default="Simply put, the theory of relativity states that",
        help="Input prompt for generation",
    )
    parser.add_argument(
        "--max_seq_len",
        type=int,
        default=128,
        help="Maximum sequence length (prompt + generated tokens)",
    )
    parser.add_argument(
        "--echo",
        action="store_true",
        help="Include the prompt in the output",
    )
    parser.add_argument(
        "--chat",
        action="store_true",
        help="Use chat template formatting",
    )
    parser.add_argument(
        "--thinking",
        action="store_true",
        help="Enable thinking mode (shows reasoning before answer)",
    )

    args = parser.parse_args()

    # Resolve paths relative to script directory
    script_dir = Path(__file__).parent
    model_dir = script_dir / args.model_dir

    print(f"Loading model from {model_dir}...")

    # Load tokenizer from the model directory
    tokenizer = AutoTokenizer.from_pretrained(str(model_dir))

    # Load the ExecuTorch model using optimum-executorch
    model = ExecuTorchModelForCausalLM.from_pretrained(str(model_dir))

    # Format prompt if using chat mode
    prompt = args.prompt
    if args.chat:
        prompt = format_chat_prompt(prompt, enable_thinking=args.thinking)
        print("Using chat template" + (" with thinking mode" if args.thinking else ""))

    print(f"\nPrompt: {prompt}")
    print("-" * 50)

    # Generate text
    generated_text = model.text_generation(
        tokenizer=tokenizer,
        prompt=prompt,
        max_seq_len=args.max_seq_len,
        echo=args.echo,
    )

    print(f"\nGenerated text:\n{generated_text}")
    print("-" * 50)
    print("Done!")


if __name__ == "__main__":
    main()
