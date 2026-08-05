#!/usr/bin/env python3
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

"""Download and verify the pinned OpenMMLab RTMW ONNX archive."""

from __future__ import annotations

import argparse
import shutil
import urllib.request
import zipfile
from pathlib import Path

from rtmw_export import (
    RTMW_ARCHIVE_SHA256,
    RTMW_ARCHIVE_URL,
    RTMW_ONNX_SHA256,
    sha256_file,
)


ARCHIVE_NAME = "rtmw-dw-x-l_simcc-cocktail14_270e-384x288_20231122.zip"
ONNX_NAME = "end2end.onnx"


def download(url: str, destination: Path) -> None:
    partial = destination.with_suffix(destination.suffix + ".part")
    request = urllib.request.Request(url, headers={"User-Agent": "ExecuTorch-RTMW-export/1"})
    with urllib.request.urlopen(request) as response, partial.open("wb") as output:
        shutil.copyfileobj(response, output, length=1024 * 1024)
    partial.replace(destination)


def verified_archive(output_directory: Path) -> Path:
    output_directory.mkdir(parents=True, exist_ok=True)
    archive = output_directory / ARCHIVE_NAME
    if not archive.is_file() or sha256_file(archive) != RTMW_ARCHIVE_SHA256:
        download(RTMW_ARCHIVE_URL, archive)
    actual = sha256_file(archive)
    if actual != RTMW_ARCHIVE_SHA256:
        raise ValueError(
            f"Archive SHA-256 mismatch: expected {RTMW_ARCHIVE_SHA256}, got {actual}"
        )
    return archive


def extract_onnx(archive: Path, output_directory: Path) -> Path:
    destination = output_directory / ONNX_NAME
    partial = destination.with_suffix(destination.suffix + ".part")
    with zipfile.ZipFile(archive) as source:
        member = source.getinfo(ONNX_NAME)
        with source.open(member) as model, partial.open("wb") as output:
            shutil.copyfileobj(model, output, length=1024 * 1024)
    actual = sha256_file(partial)
    if actual != RTMW_ONNX_SHA256:
        partial.unlink(missing_ok=True)
        raise ValueError(
            f"ONNX SHA-256 mismatch: expected {RTMW_ONNX_SHA256}, got {actual}"
        )
    partial.replace(destination)
    return destination


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path(__file__).resolve().parent / "artifacts",
        help="Download/extraction directory (default: tools/rtmw/artifacts)",
    )
    parser.add_argument(
        "--archive-only",
        action="store_true",
        help="Verify the archive without extracting end2end.onnx",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    archive = verified_archive(args.output_dir)
    print(f"verified archive: {archive} ({RTMW_ARCHIVE_SHA256})")
    if not args.archive_only:
        onnx_path = extract_onnx(archive, args.output_dir)
        print(f"verified ONNX: {onnx_path} ({RTMW_ONNX_SHA256})")


if __name__ == "__main__":
    main()
