<p align="center">
  <img src="docs/logo.png" alt="ExecuWhisper Logo" width="128" />
</p>

# <p align="center">ExecuWhisper</p>

<p align="center"><strong>ExecuWhisper is your free, open-source alternative to Wispr Flow and SuperWhisper.</strong></p>

If you don't want to pay $12–$15 / month for Wispr Flow or SuperWhisper but want a much simpler version that does what most people seem to need — press a hotkey, dictate, get clean punctuated text pasted into the focused app — ExecuWhisper does the basics well. It does **not** offer every feature those products ship (no cloud sync, no team collab, no agentic prompt-injection of "rules"); it covers the core dictation loop.

Wispr Flow and SuperWhisper are great products and this is definitely not a 1:1 clone. ExecuWhisper is a much simpler take, just the basics for folks who want control, **don't want their voice or transcripts leaving the laptop**, and don't want to pay. If you need all the fancy features, your best bet is to support those teams. But if you just want something free (no gotchas), open, and **fully on-device**, this project does the job!

**100% free** for both **personal** and **commercial** use under the BSD-3-Clause license that covers the rest of [`meta-pytorch/executorch-examples`](https://github.com/meta-pytorch/executorch-examples). Use it, modify it, distribute it — and if you feel like it, shout out the project (or open a PR) 🙂.

It runs **fully on-device** using [ExecuTorch](https://github.com/pytorch/executorch). Speech-to-text via NVIDIA's Parakeet-TDT (Metal backend); a fine-tuned LiquidAI LFM2.5-350M cleans up disfluencies, casing, and punctuation (MLX delegate). No cloud, no API keys, no telemetry — the only network calls are the first-launch model downloads from the Hugging Face Hub.

> **Status:** v0.1.0 — initial open-source release. Apple Silicon, macOS 14+. The three required ExecuTorch helper PRs are still in upstream review (see [Build From Source](#build-from-source)). Prebuilt arm64 helpers are attached to the [GitHub Release](#install-prebuilt) so you don't have to build them yourself.

## Demo

https://github.com/user-attachments/assets/b0557b3d-fd2e-4c8d-9ca1-dd9a35f6736c

(Recording outline lives in [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md).)

## Architecture

<p align="center">
  <img src="docs/architecture.png" alt="ExecuWhisper architecture diagram" />
</p>

At a glance: microphone → `AudioRecorder` → `parakeet_helper` (Metal, [pytorch/executorch#18861](https://github.com/pytorch/executorch/pull/18861)) → 30-word chunker → `lfm25_formatter_helper` (MLX, [pytorch/executorch#19562](https://github.com/pytorch/executorch/pull/19562); export from [#19195](https://github.com/pytorch/executorch/pull/19195)) → validator + replacements → `ExecuWhisper Paste Helper` (`LSBackgroundOnly`; CGEvent ⌘V) → focused text field in any app.

## Features

- **Fully on-device** — no cloud, no API keys, no telemetry. The only network traffic is the first-launch model download from the Hugging Face Hub.
- **System-wide dictation** — press `Ctrl+Space` (configurable) in any app; a floating overlay shows the live capture. Final text is auto-pasted into the focused text field.
- **Smart reformatting** — the LFM2.5-350M formatter cleans up disfluencies, restores casing/punctuation, and infers list structure. Trained specifically to refuse to "answer" the dictation, even when the user's voice ends in a question.
- **Long input chunking** — transcripts longer than ~30 words are formatted in chunks; the validator falls back to raw text on any chunk the model garbles, so you never lose words.
- **Replacements & snippets** — auto-correct names/acronyms; trigger templates with a phrase.
- **Searchable session history** — rename, pin, group by recency. Export to `.txt`, `.json`, `.srt`.
- **Open helpers** — `parakeet_helper` and `lfm25_formatter_helper` speak a tiny JSON-line protocol over stdin/stdout. Easy to script with the included `scripts/probe_formatter.py`.

<a id="install-prebuilt"></a>
## Install (prebuilt path)

For users who want to skip building three upstream ExecuTorch PRs:

1. Download the prebuilt helpers tarball from the [GitHub Release](https://github.com/seyeong-han/executorch-examples/releases) attached to this PR (`helpers-arm64-darwin.tar.gz`).
2. Extract into your ExecuTorch checkout:
   ```bash
   mkdir -p ~/executorch/cmake-out/examples/models/llama
   mkdir -p ~/executorch/cmake-out/examples/models/parakeet
   tar -xzf helpers-arm64-darwin.tar.gz -C ~/executorch/cmake-out/examples/models/
   ```
3. Install libomp (LLVM OpenMP runtime — not redistributed in the release):
   ```bash
   brew install libomp
   ```
4. Set your Apple Developer team and build:
   ```bash
   brew install xcodegen
   cd execuwhisper/macos
   DEVELOPMENT_TEAM=YOUR_TEAM_ID xcodegen generate
   DEVELOPMENT_TEAM=YOUR_TEAM_ID ./scripts/build.sh
   ```

The prebuilt helpers are ad-hoc-signed, so first launch will trigger a Gatekeeper warning. Right-click the `.app` → **Open**, or run:
```bash
xattr -d com.apple.quarantine /Applications/ExecuWhisper.app
```

> **Note on entitlements.** The helpers ship pre-signed with the hardened runtime + `disable-library-validation` + `allow-dyld-environment-variables` entitlements. If you re-sign them yourself, preserve those entitlements (see `scripts/sign_release.sh`) so the helpers can load `libomp.dylib` from `/opt/homebrew/opt/libomp`.

## Build From Source

For users who want everything compiled from source.

### Prerequisites

- macOS 14.0+ (Sonoma or newer)
- Apple Silicon (M1/M2/M3/M4)
- Xcode 16+
- [Conda](https://docs.conda.io/en/latest/miniconda.html) (Miniconda or Anaconda)
- An Apple Developer team identifier (free or paid). Set it via the `DEVELOPMENT_TEAM` environment variable; the project does **not** hard-code one.

```bash
brew install xcodegen libomp
```

### Upstream ExecuTorch PRs (in review)

The helper binaries this app embeds live in three PRs against `pytorch/executorch` that have not yet landed on `main`. Until they merge, check them out locally:

| PR | What it adds | Where it lands |
|---|---|---|
| [pytorch/executorch#18861](https://github.com/pytorch/executorch/pull/18861) | `parakeet_helper` (ASR runtime, Metal) + `make parakeet-metal` | `cmake-out/examples/models/parakeet/parakeet_helper` |
| [pytorch/executorch#19195](https://github.com/pytorch/executorch/pull/19195) | LFM2.5 MLX export pipeline + `lfm2_5_350m` model class + `lfm_2_5-mlx` Makefile target | export-time only |
| [pytorch/executorch#19562](https://github.com/pytorch/executorch/pull/19562) | `lfm25_formatter_helper` (formatter runtime, MLX) + `make lfm_2_5_formatter-mlx` | `cmake-out/examples/models/llama/lfm25_formatter_helper` |

Apply all three before building:

```bash
git clone https://github.com/pytorch/executorch ~/executorch
cd ~/executorch
gh pr checkout 18861 -b parakeet
gh pr checkout 19195 -b lfm25-export
gh pr checkout 19562 -b lfm25-helper
# Resolve any conflicts manually if the three branches don't merge cleanly.
```

If `gh pr checkout` is unavailable, the equivalent is `git fetch origin pull/<n>/head:<branch>` followed by a merge or cherry-pick.

### 1. Set up the conda environments

The Parakeet helper builds in `et-metal`; the LFM2.5 formatter helper builds in `et-mlx`. Two separate envs are needed because the MLX runtime ships its own pinned `numpy` / `torch` that conflicts with the Metal backend's deps.

```bash
conda create -n et-metal python=3.12 -y
conda activate et-metal
pip install -r scripts/requirements_et-metal.txt
pip install -e ~/executorch  # editable install of the local checkout

conda create -n et-mlx python=3.12 -y
conda activate et-mlx
pip install -r scripts/requirements_et-mlx.txt
pip install -e ~/executorch
```

The `requirements_*.txt` files are exact pin captures from a known-good Mac. Treat them as a starting point — you may need to relax pins if upstream packages move.

### 2. Build the Parakeet helper (Metal)

```bash
cd ~/executorch
conda activate et-metal
make parakeet-metal
```

Produces `~/executorch/cmake-out/examples/models/parakeet/parakeet_helper`.

### 3. Build the LFM2.5 formatter helper (MLX)

```bash
cd ~/executorch
conda activate et-mlx
make lfm_2_5_formatter-mlx
```

Produces `~/executorch/cmake-out/examples/models/llama/lfm25_formatter_helper` and `~/executorch/cmake-out/examples/models/llama/mlx.metallib`.

### 4. Build the macOS app

```bash
cd execuwhisper/macos
brew install xcodegen
DEVELOPMENT_TEAM=YOUR_TEAM_ID xcodegen generate
DEVELOPMENT_TEAM=YOUR_TEAM_ID ./scripts/build.sh
```

Produces `./build/Build/Products/Release/ExecuWhisper.app`.

The post-compile script copies `parakeet_helper`, `lfm25_formatter_helper`, `mlx.metallib`, and `libomp.dylib` into `Contents/Resources/`. To embed the model artifacts into the bundle (otherwise downloaded on first launch):

```bash
DEVELOPMENT_TEAM=YOUR_TEAM_ID ./scripts/build.sh --download-models --bundle-models
```

If your `~/executorch` checkout lives elsewhere, override paths via env:

```bash
export EXECUTORCH_PATH="$HOME/path/to/executorch"
export PARAKEET_HELPER_PATH="$EXECUTORCH_PATH/cmake-out/examples/models/parakeet/parakeet_helper"
export FORMATTER_HELPER_PATH="$EXECUTORCH_PATH/cmake-out/examples/models/llama/lfm25_formatter_helper"
export FORMATTER_METALLIB_PATH="$EXECUTORCH_PATH/cmake-out/examples/models/llama/mlx.metallib"
```

## Run

On first launch, ExecuWhisper downloads ~1.3 GB of model artifacts:

| File | Source | Size |
|---|---|---|
| `model.pte` | [`younghan-meta/Parakeet-TDT-ExecuTorch-Metal`](https://huggingface.co/younghan-meta/Parakeet-TDT-ExecuTorch-Metal) | ~822 MB |
| `tokenizer.model` | same | ~360 KB |
| `lfm2_5_350m_mlx_4w.pte` | [`younghan-meta/LFM2.5-350M-ExecuWhisper-Formatter`](https://huggingface.co/younghan-meta/LFM2.5-350M-ExecuWhisper-Formatter) | ~468 MB |
| `tokenizer.json` | same | ~4.5 MB |
| `tokenizer_config.json` | same | <1 KB |

Files land in `~/Library/Application Support/ExecuWhisper/models/`. Re-download by deleting that directory.

Grant permissions when prompted:

- **Microphone** — for `ExecuWhisper.app` (audio capture).
- **Accessibility** — for `ExecuWhisper Paste Helper.app` (so it can simulate `Cmd+V` into other apps). The helper is installed under `Contents/Resources/`; macOS treats it as its own bundle for permission purposes.

### Keyboard shortcuts

| Shortcut | Action |
|---|---|
| `Cmd+Shift+R` | Start recording / stop and transcribe |
| `Cmd+Shift+C` | Copy the current transcript |
| `Ctrl+Space` | Toggle system-wide dictation overlay (configurable in Settings) |

### Run tests

```bash
cd execuwhisper/macos
DEVELOPMENT_TEAM=YOUR_TEAM_ID xcodegen generate
xcodebuild \
  -project ExecuWhisper.xcodeproj \
  -scheme ExecuWhisper \
  -derivedDataPath build \
  -destination "platform=macOS" \
  test
```

## Models

| Repo | Purpose | Base model |
|---|---|---|
| [`younghan-meta/Parakeet-TDT-ExecuTorch-Metal`](https://huggingface.co/younghan-meta/Parakeet-TDT-ExecuTorch-Metal) | ASR runtime artifacts (Metal-quantized `.pte`) | [`nvidia/parakeet-tdt-0.6b-v2`](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2) |
| [`younghan-meta/LFM2.5-350M-ExecuWhisper-Formatter`](https://huggingface.co/younghan-meta/LFM2.5-350M-ExecuWhisper-Formatter) | Formatter runtime artifacts (MLX-quantized `.pte`) + fp32 fine-tune + tokenizer + eval results | [`LiquidAI/LFM2.5-350M`](https://huggingface.co/LiquidAI/LFM2.5-350M) |

The formatter model card includes a Python `Quick Start` for using the `.pte` outside the app, AMI eval numbers (forbidden 0.030 / coverage 0.874), and a re-export guide for users who want to swap in a different quantization.

### Fine-tune your own formatter

To retrain or adapt the formatter to a new domain (e.g. medical, legal, multilingual dictation), follow the **[Unsloth LFM2.5 fine-tuning tutorial](https://unsloth.ai/docs/models/tutorials/lfm2.5)**. The tutorial covers SFT + LoRA, hyperparameter selection, and export to GGUF/MLX. After training, use the `lfm_2_5-mlx` export pipeline ([pytorch/executorch#19195](https://github.com/pytorch/executorch/pull/19195)) to produce a `.pte` that drops into ExecuWhisper.

## Status & Known Issues

- macOS-only; no iOS/Linux/Windows app yet.
- The three upstream ExecuTorch PRs (#18861, #19195, #19562) are in review. Until they land, follow the [Build From Source](#build-from-source) workflow or use the prebuilt helpers from the GitHub Release.
- The formatter occasionally over-summarizes self-corrections ("actually, no — make it tomorrow") and may drop the closing name in email-style sign-offs. See the model card for full eval numbers and limitations.
- Long inputs are chunked at a 30-word boundary on word splits; very short follow-up clauses can land in their own chunk and get capitalized as if starting a new sentence. A smarter chunker is on the follow-up list.
- No telemetry. The app makes one network call: first-launch model download from `huggingface.co`. After that, all inference is local.

## Acknowledgements

- **NVIDIA** — [Parakeet-TDT](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2) ASR model.
- **LiquidAI** — [LFM2.5-350M](https://huggingface.co/LiquidAI/LFM2.5-350M) base model and the LFM architecture.
- **Apple MLX team** — [`mlx`](https://github.com/ml-explore/mlx) + the MLX delegate inside ExecuTorch.
- **PyTorch / ExecuTorch team** — runtime, Metal backend, MLX delegate.
- **LLVM project** — [OpenMP runtime](https://openmp.llvm.org/) (`libomp`), required by the Parakeet helper at runtime.
- **Unsloth** — fine-tuning framework used to produce the v3 formatter model.
- **AMI Meeting Corpus** ([Univ. Edinburgh](https://groups.inf.ed.ac.uk/ami/corpus/)) — eval and a portion of the formatter's training data.
- The [`voxtral_realtime/macos`](../../voxtral_realtime/macos) example, which served as the structural template for this directory.

See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for upstream license details.

## License

This source code is licensed under the BSD-3-Clause license; see the repo-root [`LICENSE`](../../LICENSE) file. Per-component licenses for upstream models and runtime libraries are in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Citation

If you use ExecuWhisper or the formatter model in academic or industry work, please cite:

```bibtex
@software{execuwhisper2026,
  title = {ExecuWhisper: On-device dictation for macOS with ExecuTorch},
  author = {Han, Younghan and contributors},
  year = {2026},
  url = {https://github.com/meta-pytorch/executorch-examples/tree/main/execuwhisper/macos},
  note = {Apple Silicon, Parakeet-TDT (Metal) + LFM2.5-350M (MLX)}
}

@software{execuwhisper_formatter2026,
  title = {LFM2.5-350M ExecuWhisper Formatter},
  author = {Han, Younghan},
  year = {2026},
  url = {https://huggingface.co/younghan-meta/LFM2.5-350M-ExecuWhisper-Formatter},
  note = {Fine-tuned dictation cleaner; trained on AMI corpus subset + synthetic dictation pairs}
}
```
