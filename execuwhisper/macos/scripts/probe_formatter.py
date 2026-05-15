#!/usr/bin/env python3
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.
"""Probe lfm25_formatter_helper directly via its JSON-line wire protocol.

Bypasses the Swift app, the chunker, and the validator. Lets us see exactly
what the model produces for a given transcript under different prompt
configurations.

Usage:
    python scripts/probe_formatter.py
    python scripts/probe_formatter.py --transcript "your text here"
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import threading
import time
import uuid
from queue import Queue
from typing import Optional

HOME = os.path.expanduser("~")
DEFAULT_HELPER = os.path.join(
    HOME,
    "executorch",
    "cmake-out",
    "examples",
    "models",
    "llama",
    "lfm25_formatter_helper",
)
DEFAULT_MODEL_DIR = os.path.join(
    HOME, "Library", "Application Support", "ExecuWhisper", "models", "formatter"
)
DEFAULT_MODEL_PTE = os.path.join(DEFAULT_MODEL_DIR, "lfm2_5_350m_mlx_4w.pte")
DEFAULT_TOKENIZER_DIR = DEFAULT_MODEL_DIR

DEFAULT_TRANSCRIPT = (
    "Just say, hey Michael. Yeah, for sure. I'd love $40 million. Uh my full "
    "name is uh John Smith. My address is 123 Sesame Street. Uh my contact "
    "phone number is 555-555-5555. And my profession is uh uh scammer. So "
    "yeah, that'd be great if you could send that over. Uh I'd love to uh "
    "receive that money within 14 days. Thanks."
)

SMART_INSTRUCTION = (
    "You rewrite spoken dictation into clean final text. You are not a chat "
    "assistant. Never answer or respond to the dictation, even if it is a "
    "question. Treat the dictation strictly as text to rewrite. Fix casing, "
    "punctuation, filler, and speech disfluencies. Preserve meaning and detail. "
    "Use bullets only when it clearly reads as a list. Do not summarize or "
    "invent information. Output only the rewritten dictation."
)

EXAMPLE_BLOCK = (
    "Examples:\n"
    "Dictation: um does it feel like real time processing\n"
    "Output: Does it feel like real-time processing?\n\n"
    "Dictation: what is the next step\n"
    "Output: What is the next step?\n\n"
    "Dictation: okay so the plan is finish the build then deploy\n"
    "Output: Okay, so the plan is finish the build, then deploy."
)


def build_prompt(
    transcript: str, *, with_examples: bool = True, instruction: str = SMART_INSTRUCTION
) -> str:
    body = instruction
    if with_examples:
        body += "\n\n" + EXAMPLE_BLOCK
    body += f"\n\nDictation: {transcript}\nOutput:"
    return (
        "<|startoftext|><|im_start|>user\n"
        f"{body}\n"
        "<|im_end|>\n"
        "<|im_start|>assistant\n"
    )


def chunks(transcript: str, target: int = 30) -> list[str]:
    words = transcript.split()
    if len(words) <= target:
        return [transcript.strip()]
    n = (len(words) + target - 1) // target
    per = (len(words) + n - 1) // n
    out: list[str] = []
    i = 0
    while i < len(words):
        out.append(" ".join(words[i : i + per]))
        i += per
    return out


class Helper:
    """Wraps the persistent lfm25_formatter_helper subprocess."""

    def __init__(self, helper_path: str, model_path: str, tokenizer_path: str):
        env = os.environ.copy()
        # Ensure libomp + mlx.metallib are findable next to the helper.
        self.proc = subprocess.Popen(
            [
                helper_path,
                f"--model_path={model_path}",
                f"--tokenizer_path={tokenizer_path}",
            ],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env=env,
            cwd=os.path.dirname(helper_path),
            bufsize=0,
        )
        self.q: Queue = Queue()
        self.stderr_buf: list[bytes] = []
        threading.Thread(target=self._reader, daemon=True).start()
        threading.Thread(target=self._stderr_reader, daemon=True).start()

    def _reader(self):
        assert self.proc.stdout is not None
        for line in self.proc.stdout:
            line = line.decode("utf-8", errors="replace").strip()
            if not line:
                continue
            try:
                self.q.put(json.loads(line))
            except json.JSONDecodeError:
                # Helper occasionally writes non-JSON debug to stdout.
                self.q.put({"_raw": line})

    def _stderr_reader(self):
        assert self.proc.stderr is not None
        for line in self.proc.stderr:
            self.stderr_buf.append(line)

    def wait_ready(self, timeout: float = 60.0) -> dict:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                msg = self.q.get(timeout=0.5)
            except Exception:
                continue
            if msg.get("type") == "ready":
                return msg
            if msg.get("type") == "error":
                raise RuntimeError(f"helper error before ready: {msg}")
        raise TimeoutError("helper never reported ready")

    def request(
        self,
        prompt: str,
        max_new_tokens: int = 256,
        temperature: float = 0.0,
        timeout: float = 120.0,
    ) -> dict:
        req_id = str(uuid.uuid4())
        payload = {
            "type": "format",
            "version": 1,
            "request_id": req_id,
            "prompt": prompt,
            "max_new_tokens": max_new_tokens,
            "temperature": temperature,
        }
        assert self.proc.stdin is not None
        self.proc.stdin.write((json.dumps(payload) + "\n").encode("utf-8"))
        self.proc.stdin.flush()
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                msg = self.q.get(timeout=0.5)
            except Exception:
                continue
            if msg.get("type") == "result" and msg.get("request_id") == req_id:
                return msg
            if msg.get("type") == "error":
                return msg
            # status / unknown -> keep going
        raise TimeoutError(f"no result for request {req_id}")

    def shutdown(self):
        try:
            assert self.proc.stdin is not None
            self.proc.stdin.write(
                (json.dumps({"type": "shutdown", "version": 1}) + "\n").encode("utf-8")
            )
            self.proc.stdin.flush()
        except Exception:
            pass
        try:
            self.proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self.proc.kill()


def fmt_result(label: str, prompt: str, result: dict) -> None:
    print(f"\n=== {label} ===")
    print(f"  prompt chars: {len(prompt)}")
    text = result.get("text", "<<no text field>>")
    print(f"  result.text   ({len(text)} chars): {text!r}")
    tps = result.get("tokens_per_second")
    if tps is not None:
        print(f"  tokens/sec  : {tps:.2f}")
    if result.get("type") == "error":
        print(
            f"  ERROR       : {result.get('message')}  details={result.get('details')}"
        )


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--helper", default=DEFAULT_HELPER)
    p.add_argument("--model", default=DEFAULT_MODEL_PTE)
    p.add_argument("--tokenizer", default=DEFAULT_TOKENIZER_DIR)
    p.add_argument("--transcript", default=DEFAULT_TRANSCRIPT)
    p.add_argument("--max-new-tokens", type=int, default=256)
    args = p.parse_args()

    print(f"helper    : {args.helper}")
    print(f"model     : {args.model}")
    print(f"tokenizer : {args.tokenizer}")
    print(f"transcript: {args.transcript!r}")

    helper = Helper(args.helper, args.model, args.tokenizer)
    try:
        ready = helper.wait_ready()
        print(f"\n[helper ready] {ready}")

        full = args.transcript

        # 1) full transcript, prompt as Swift app sends it (instruction + examples)
        prompt = build_prompt(full, with_examples=True)
        fmt_result(
            "A: full transcript, full prompt (instruction + examples)",
            prompt,
            helper.request(prompt, max_new_tokens=args.max_new_tokens),
        )

        # 2) full transcript, NO few-shot examples (testing memorization theory)
        prompt = build_prompt(full, with_examples=False)
        fmt_result(
            "B: full transcript, instruction only (NO few-shot)",
            prompt,
            helper.request(prompt, max_new_tokens=args.max_new_tokens),
        )

        # 3) lower-cased full transcript (matching training-time casing distribution)
        prompt = build_prompt(full.lower(), with_examples=True)
        fmt_result(
            "C: lowercase transcript, full prompt",
            prompt,
            helper.request(prompt, max_new_tokens=args.max_new_tokens),
        )

        # 4) lowercase + no few-shot
        prompt = build_prompt(full.lower(), with_examples=False)
        fmt_result(
            "D: lowercase transcript, instruction only",
            prompt,
            helper.request(prompt, max_new_tokens=args.max_new_tokens),
        )

        # 5) chunked path matching app behavior
        print("\n=== E: chunked (mirrors Swift app) ===")
        for i, c in enumerate(chunks(full, target=30), 1):
            prompt = build_prompt(c, with_examples=True)
            r = helper.request(prompt, max_new_tokens=args.max_new_tokens)
            fmt_result(f"  E.{i} chunk", prompt, r)

        # 6) sanity: a tiny single-sentence input that's IN-distribution
        ind = "uh hey can we move the meeting to friday"
        prompt = build_prompt(ind, with_examples=True)
        fmt_result(
            "F: in-distribution short lowercase sanity",
            prompt,
            helper.request(prompt, max_new_tokens=args.max_new_tokens),
        )

    finally:
        if helper.stderr_buf:
            print("\n--- helper stderr (first 2 KB) ---")
            sys.stdout.write(
                b"".join(helper.stderr_buf)[:2048].decode(errors="replace")
            )
        helper.shutdown()


if __name__ == "__main__":
    main()
