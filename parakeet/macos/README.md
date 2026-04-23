# ExecuWhisper

`ExecuWhisper` is a native macOS app for on-device speech transcription with the Parakeet TDT model on ExecuTorch + Metal. It keeps the app workflow intentionally simple:

- record audio from the microphone
- stop recording
- keep `parakeet_helper` warm and send the captured PCM directly for transcription
- save manual recording transcripts to local history

Unlike `VoxtralRealtime`, this app still does **not** do live token streaming, wake-word detection, or `silero_vad`. System dictation is available in a batch-compatible form: the default shortcut is `Ctrl+Space`, users can customize it in Settings, and the overlay pastes the final transcript when recording stops.

## Features

- Record-then-transcribe flow with local microphone capture
- Auto-detected microphone selection for both manual recording and system dictation
- Batch-compatible system dictation with a customizable global shortcut and floating overlay
- First-launch model download from `younghan-meta/Parakeet-TDT-ExecuTorch-Metal`
- Searchable session history with rename, pinning, and recency grouping
- Text replacements for product names, acronyms, and domain terms
- Snippets for exact-match dictated templates
- Session export to `.txt`, `.json`, and `.srt`
- Lightweight DMG packaging by default, with optional bundled-model builds

## Requirements

- macOS 14.0+
- Apple Silicon
- Xcode 16+
- Conda
- `xcodegen`
- `libomp`

Install the host tools:

```bash
brew install xcodegen libomp
```

## Usage

### First launch

The default app build is intentionally small. On first launch, `ExecuWhisper` downloads:

- `model.pte`
- `tokenizer.model`

into:

```text
~/Library/Application Support/ExecuWhisper/models
```

Session history is stored at:

```text
~/Library/Application Support/ExecuWhisper/sessions.json
```

Replacements are stored at:

```text
~/Library/Application Support/ExecuWhisper/replacements.json
```

### Keyboard shortcuts

| Shortcut | Action |
|---|---|
| `Cmd+Shift+R` | Start recording / stop and transcribe |
| `Cmd+Shift+C` | Copy the current transcript |
| `Ctrl+Space` | Toggle system dictation by default; change it in Settings |

## Build From Source

### 1. Activate the Metal environment

```bash
conda create -n et-metal python=3.12 -y
conda activate et-metal
```

### 2. Build the Parakeet helper

```bash
cd ~/executorch
make parakeet-metal
```

The helper is expected at:

```text
~/executorch/cmake-out/examples/models/parakeet/parakeet_helper
```

### 3. Build the macOS app

```bash
cd /Users/younghan/project/executorch-examples/parakeet/macos
./scripts/build.sh
```

That produces:

```text
./build/Build/Products/Release/ExecuWhisper.app
```

### Optional: download or bundle models during the build

Download model artifacts into `MODEL_DIR` before building:

```bash
./scripts/build.sh --download-models
```

Build a self-contained `.app` that already includes `model.pte` and `tokenizer.model`:

```bash
./scripts/build.sh --bundle-models
```

You can override the default paths with:

```bash
export EXECUTORCH_PATH="$HOME/executorch"
export MODEL_DIR="$HOME/parakeet_metal"
```

## Create A DMG

After building the app:

```bash
./scripts/create_dmg.sh \
  "./build/Build/Products/Release/ExecuWhisper.app" \
  "./ExecuWhisper.dmg"
```

Behavior:

- If the app bundle contains only the helper and runtime libraries, the DMG stays lightweight and the app downloads the model on first launch.
- If the app bundle already contains `model.pte` and `tokenizer.model`, the script validates both files and creates a bundled-model DMG.

## Run Tests

```bash
xcodegen generate
xcodebuild \
  -project ExecuWhisper.xcodeproj \
  -scheme ExecuWhisper \
  -derivedDataPath build \
  -destination "platform=macOS" \
  test
```

Current regression coverage includes:

- helper reuse and restart behavior in the warm bridge
- direct PCM handoff from the recorder into the helper
- preload and unload state handling for the helper lifecycle
- session compatibility for older `sessions.json` payloads
- replacement pipeline behavior
- session history grouping and pinning logic
- export rendering and file writing

## Manual Latency Gate

Use the helper benchmark to compare the first cold request against a second warm
request on the same helper process:

```bash
python3 ./scripts/benchmark_helper.py \
  --helper "$HOME/executorch/cmake-out/examples/models/parakeet/parakeet_helper" \
  --model "$HOME/parakeet_metal/model.pte" \
  --tokenizer "$HOME/parakeet_metal/tokenizer.model" \
  --audio /path/to/16khz_mono_float32.wav
```

Notes:

- The script exits non-zero if the warm request is not faster than the cold request.
- Pass `--min-speedup-ratio 0.2` to require at least a 20% warmup win.
- If you do not have a sample WAV handy, omit `--audio` and the script will use a generated synthetic clip.

## Troubleshooting

- `Parakeet helper not found`: run `conda activate et-metal && cd ~/executorch && make parakeet-metal`
- `libomp.dylib not found`: run `brew install libomp`
- Model download fails on first launch: check network access and verify the Hugging Face repo is reachable from your machine
- DMG script says bundled-model files are missing: rebuild with `./scripts/build.sh --bundle-models`, or create a lightweight DMG instead
- Existing history is visible even if model assets are currently missing: use the `Home` page to repair downloads while keeping old transcripts accessible from the sidebar
- To reset macOS Accessibility and Microphone permissions for the app during testing:

```bash
tccutil reset Accessibility org.pytorch.executorch.ExecuWhisper
tccutil reset Microphone org.pytorch.executorch.ExecuWhisper
```
