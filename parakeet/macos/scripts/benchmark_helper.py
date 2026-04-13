#!/usr/bin/env python3
#
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.
#

import argparse
import json
import math
import os
import struct
import subprocess
import sys
import time
import uuid
from typing import Tuple


PROTOCOL_VERSION = 1
DEFAULT_SAMPLE_RATE = 16_000


def parse_args() -> argparse.Namespace:
    home = os.path.expanduser("~")
    default_helper = os.path.join(home, "executorch", "cmake-out", "examples", "models", "parakeet", "parakeet_helper")
    default_model = os.path.join(home, "parakeet_metal", "model.pte")
    default_tokenizer = os.path.join(home, "parakeet_metal", "tokenizer.model")

    parser = argparse.ArgumentParser(description="Benchmark cold vs warm Parakeet helper latency.")
    parser.add_argument("--helper", default=default_helper, help="Path to parakeet_helper")
    parser.add_argument("--model", default=default_model, help="Path to model.pte")
    parser.add_argument("--tokenizer", default=default_tokenizer, help="Path to tokenizer.model")
    parser.add_argument(
        "--audio",
        help="Optional path to a 16kHz mono float32 WAV file. If omitted, a synthetic clip is generated.",
    )
    parser.add_argument(
        "--synthetic-duration-s",
        type=float,
        default=2.5,
        help="Duration of the generated synthetic clip when --audio is omitted.",
    )
    parser.add_argument(
        "--min-speedup-ratio",
        type=float,
        default=0.0,
        help="Optional minimum fractional warm-speedup required. 0.2 means warm must be at least 20%% faster.",
    )
    return parser.parse_args()


def load_float32_mono_wav(path: str) -> bytes:
    with open(path, "rb") as handle:
        data = handle.read()

    if data[0:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise ValueError(f"{path} is not a RIFF/WAVE file")

    offset = 12
    fmt_chunk = None
    pcm_chunk = None
    while offset + 8 <= len(data):
        chunk_id = data[offset:offset + 4]
        chunk_size = struct.unpack_from("<I", data, offset + 4)[0]
        chunk_start = offset + 8
        chunk_end = chunk_start + chunk_size
        if chunk_end > len(data):
            raise ValueError(f"{path} has a truncated {chunk_id.decode('ascii', 'ignore')} chunk")

        if chunk_id == b"fmt ":
            fmt_chunk = data[chunk_start:chunk_end]
        elif chunk_id == b"data":
            pcm_chunk = data[chunk_start:chunk_end]

        offset = chunk_end + (chunk_size % 2)

    if fmt_chunk is None or pcm_chunk is None:
        raise ValueError(f"{path} is missing fmt or data chunks")

    audio_format, channels, sample_rate = struct.unpack_from("<HHI", fmt_chunk, 0)
    bits_per_sample = struct.unpack_from("<H", fmt_chunk, 14)[0]

    if audio_format != 3:
        raise ValueError(f"{path} must use IEEE float32 WAV encoding (format=3)")
    if channels != 1:
        raise ValueError(f"{path} must be mono (got {channels} channels)")
    if sample_rate != DEFAULT_SAMPLE_RATE:
        raise ValueError(f"{path} must be {DEFAULT_SAMPLE_RATE}Hz (got {sample_rate}Hz)")
    if bits_per_sample != 32:
        raise ValueError(f"{path} must be float32 (got {bits_per_sample} bits per sample)")

    return pcm_chunk


def generate_synthetic_pcm(duration_s: float) -> bytes:
    sample_count = max(1, int(duration_s * DEFAULT_SAMPLE_RATE))
    amplitude = 0.08
    frequency_hz = 220.0
    samples = bytearray()
    for index in range(sample_count):
        sample = amplitude * math.sin(2.0 * math.pi * frequency_hz * (index / DEFAULT_SAMPLE_RATE))
        samples.extend(struct.pack("<f", sample))
    return bytes(samples)


def start_helper(helper_path: str, model_path: str, tokenizer_path: str) -> subprocess.Popen[bytes]:
    return subprocess.Popen(
        [helper_path, "--model_path", model_path, "--tokenizer_path", tokenizer_path],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        bufsize=0,
    )


def read_json_line(stream) -> dict:
    line = stream.readline()
    if not line:
        raise RuntimeError("Helper closed stdout unexpectedly")
    try:
        return json.loads(line.decode("utf-8"))
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Helper emitted invalid JSON: {line!r}") from exc


def wait_for_ready(process: subprocess.Popen[bytes]) -> None:
    message = read_json_line(process.stdout)
    if message.get("type") != "ready":
        raise RuntimeError(f"Expected ready message, got: {message}")


def run_request(process: subprocess.Popen[bytes], pcm_data: bytes) -> Tuple[float, dict]:
    request_id = uuid.uuid4().hex
    header = {
        "type": "transcribe",
        "version": PROTOCOL_VERSION,
        "request_id": request_id,
        "audio": {
            "encoding": "f32le",
            "sample_rate": DEFAULT_SAMPLE_RATE,
            "channel_count": 1,
            "payload_byte_count": len(pcm_data),
        },
        "enable_runtime_profile": True,
    }

    start = time.perf_counter()
    process.stdin.write(json.dumps(header).encode("utf-8") + b"\n")
    process.stdin.write(pcm_data)
    process.stdin.flush()

    while True:
        message = read_json_line(process.stdout)
        message_type = message.get("type")
        if message_type == "status":
            continue
        if message_type == "result" and message.get("request_id") == request_id:
            elapsed_ms = (time.perf_counter() - start) * 1000.0
            return elapsed_ms, message
        if message_type == "error":
            raise RuntimeError(message.get("details") or message.get("message") or "Helper returned an error")


def shutdown_helper(process: subprocess.Popen[bytes]) -> None:
    if process.stdin is not None:
        try:
            process.stdin.write(json.dumps({"type": "shutdown", "version": PROTOCOL_VERSION}).encode("utf-8") + b"\n")
            process.stdin.flush()
        except BrokenPipeError:
            pass

    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.terminate()
        process.wait(timeout=5)


def main() -> int:
    args = parse_args()

    if not os.path.isfile(args.helper):
        raise FileNotFoundError(f"Helper not found: {args.helper}")
    if not os.path.isfile(args.model):
        raise FileNotFoundError(f"Model not found: {args.model}")
    if not os.path.isfile(args.tokenizer):
        raise FileNotFoundError(f"Tokenizer not found: {args.tokenizer}")

    if args.audio:
        pcm_data = load_float32_mono_wav(args.audio)
        audio_description = args.audio
    else:
        pcm_data = generate_synthetic_pcm(args.synthetic_duration_s)
        audio_description = f"synthetic {args.synthetic_duration_s:.2f}s tone"

    process = start_helper(args.helper, args.model, args.tokenizer)
    stderr_output = b""
    try:
        wait_for_ready(process)
        cold_ms, cold_result = run_request(process, pcm_data)
        warm_ms, warm_result = run_request(process, pcm_data)
    finally:
        shutdown_helper(process)
        if process.stderr is not None:
            try:
                stderr_output = process.stderr.read()
            except Exception:
                stderr_output = b""

    speedup = 0.0
    if cold_ms > 0:
        speedup = max(0.0, (cold_ms - warm_ms) / cold_ms)

    print(f"Audio source: {audio_description}")
    print(f"Cold request: {cold_ms:.1f} ms")
    print(f"Warm request: {warm_ms:.1f} ms")
    print(f"Warm speedup: {speedup * 100.0:.1f}%")

    cold_profile = cold_result.get("runtime_profile")
    warm_profile = warm_result.get("runtime_profile")
    if cold_profile:
        print(f"Cold runtime profile: {cold_profile}")
    if warm_profile:
        print(f"Warm runtime profile: {warm_profile}")

    if stderr_output.strip():
        print("\nHelper stderr:")
        print(stderr_output.decode("utf-8", errors="replace"))

    if warm_ms >= cold_ms:
        print("\nFAIL: warm request was not faster than cold request.", file=sys.stderr)
        return 1
    if speedup < args.min_speedup_ratio:
        print(
            f"\nFAIL: warm speedup {speedup * 100.0:.1f}% is below the required {(args.min_speedup_ratio * 100.0):.1f}%.",
            file=sys.stderr,
        )
        return 1

    print("\nPASS: warm request beat the cold request.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
