# Voxtral Realtime

A native macOS showcase app for [Voxtral-Mini-4B-Realtime](https://huggingface.co/mistral-labs/Voxtral-Mini-4B-Realtime-2602-ExecuTorch) — Mistral's on-device real-time speech transcription model. All inference runs locally on Apple Silicon via [ExecuTorch](https://github.com/pytorch/executorch) with Metal acceleration. No cloud, no network required.

## Features

- **Live transcription** — real-time token streaming with audio waveform visualization
- **System-wide dictation** — press `Ctrl+Space` in any app to transcribe speech and auto-paste the result
- **Model preloading** — load the model once, transcribe instantly across sessions
- **Pause / resume** — pause and resume within the same session without losing context
- **Session history** — searchable history with rename, copy, and delete
- **Silence detection** — dictation auto-stops after 2 seconds of silence
- **Self-contained DMG** — runner binary, model weights, and runtime libraries all bundled

## Download

**End users**: download the latest DMG from [GitHub Releases](https://github.com/seyeong-han/executorch-examples/releases). The DMG is self-contained — runner binary, model weights (~6.2 GB), and runtime libraries are all bundled. No terminal, no Python, no model downloads required.

### Requirements

- macOS 14.0+ (Sonoma)
- Apple Silicon (M1/M2/M3/M4)
- ~7 GB disk space

## Usage

### In-app transcription

1. Click **Load Model** on the welcome screen (takes ~30s on first load)
2. Click **Start Transcription** (or `Cmd+Shift+R`)
3. Speak — text appears in real time
4. **Pause** (`Cmd+.`) / **Resume** (`Cmd+Shift+R`) within the same session
5. **Done** (`Cmd+Return`) to save the session to history

### System-wide dictation

1. Make sure the model is loaded
2. Focus any text field in any app (Notes, Slack, browser, etc.)
3. Press **`Ctrl+Space`** — a floating overlay appears with a waveform
4. Speak — live transcribed text appears in the overlay
5. Press **`Ctrl+Space`** again to stop, or wait for 2 seconds of silence
6. The transcribed text is automatically pasted into the focused text field

### Keyboard shortcuts

| Shortcut | Action |
|---|---|
| `Cmd+Shift+R` | Start / Resume transcription |
| `Cmd+.` | Pause transcription |
| `Cmd+Return` | End session and save |
| `Cmd+Shift+C` | Copy transcript |
| `Cmd+Shift+U` | Unload model |
| `Ctrl+Space` | Toggle system-wide dictation |
| `Cmd+,` | Settings |

---

## Build from Source

Model files (~6.2 GB) are not included in the git repo. Developers must download them before building. The build script bundles everything into the `.app` so the resulting DMG is self-contained.

### Quick build (one command)

If you already have ExecuTorch built and model files downloaded:

```bash
cd apps/macos/VoxtralRealtimeApp
./scripts/build.sh
```

Or to download models and build in one step:

```bash
./scripts/build.sh --download-models
```

This validates all prerequisites, builds the app, and creates a DMG with all models bundled.

### Step-by-step setup

#### Prerequisites

- macOS 14.0+ (Sonoma)
- Apple Silicon (M1/M2/M3/M4)
- Xcode 16+
- [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`)

#### 1. Install ExecuTorch with Metal backend

```bash
export EXECUTORCH_PATH="$HOME/executorch"
git clone https://github.com/pytorch/executorch/ ${EXECUTORCH_PATH}
cd ${EXECUTORCH_PATH}
EXECUTORCH_BUILD_KERNELS_TORCHAO=1 TORCHAO_BUILD_EXPERIMENTAL_MPS=1 ./install_executorch.sh
```

> We recommend installing in a new conda or venv environment. If you run into any installation problems, open an issue or have a look at the official [Voxtral Realtime installation guide](https://github.com/pytorch/executorch/tree/main/examples/models/voxtral_realtime).

#### 2. Build the voxtral realtime runner

```bash
cd ${EXECUTORCH_PATH}
make voxtral_realtime-metal
```

The runner binary will be at:
```
${EXECUTORCH_PATH}/cmake-out/examples/models/voxtral_realtime/voxtral_realtime_runner
```

#### 3. Install runtime dependencies

```bash
brew install libomp
export DYLD_LIBRARY_PATH=/usr/lib:$(brew --prefix libomp)/lib
```

Also install `sounddevice` for the CLI mic streaming script:
```bash
pip install sounddevice
```

#### 4. Download model artifacts

```bash
pip install huggingface_hub
export LOCAL_FOLDER="$HOME/voxtral_realtime_quant_metal"
hf download mistralai/Voxtral-Mini-4B-Realtime-2602-Executorch --local-dir ${LOCAL_FOLDER}
```

This downloads three files (~6.2 GB total):
- `model-metal-int4.pte` — 4-bit quantized model (Metal)
- `preprocessor.pte` — audio-to-mel spectrogram
- `tekken.json` — tokenizer

HuggingFace repo: [`mistralai/Voxtral-Mini-4B-Realtime-2602-ExecuTorch`](https://huggingface.co/mistral-labs/Voxtral-Mini-4B-Realtime-2602-ExecuTorch)

#### 5. Test with CLI (optional)

Verify the runner works before building the app:

```bash
export CMAKE_RUNNER="${EXECUTORCH_PATH}/cmake-out/examples/models/voxtral_realtime/voxtral_realtime_runner"
export LOCAL_FOLDER="$HOME/voxtral_realtime_quant_metal"

cd ${LOCAL_FOLDER} && chmod +x stream_audio.py
./stream_audio.py | \
   ${CMAKE_RUNNER} \
    --model_path ./model-metal-int4.pte \
    --tokenizer_path ./tekken.json \
    --preprocessor_path ./preprocessor.pte \
    --mic
```

#### 6. Build the app

```bash
cd apps/macos/VoxtralRealtimeApp
xcodegen generate
open VoxtralRealtime.xcodeproj
```

Build and run from Xcode (`Cmd+R`). The post-compile build script automatically bundles the runner binary, `libomp.dylib`, and all model files into the `.app/Contents/Resources/`.

#### 7. Create a DMG

```bash
./scripts/create_dmg.sh "./build/Build/Products/Release/Voxtral Realtime.app" "./VoxtralRealtime.dmg"
```

The script validates that all required files (runner, libomp, model weights, tokenizer) are present in the `.app` bundle before creating the DMG. If anything is missing, it will tell you exactly what's needed.

The resulting DMG is self-contained — end users just drag to Applications and run.

---

## Architecture

```
VoxtralRealtimeApp
├── TranscriptStore (@Observable, @MainActor)
│   ├── SessionState: idle → loading → transcribing ⇆ paused → idle
│   ├── ModelState: unloaded → loading → ready
│   └── RunnerBridge (actor)
│         ├── Process (voxtral_realtime_runner)
│         │   ├── stdin  ← raw 16kHz mono f32le PCM
│         │   ├── stdout → transcript tokens
│         │   └── stderr → status messages
│         └── AudioEngine (actor)
│               └── AVAudioEngine → format conversion → pipe
├── DictationManager (@Observable, @MainActor)
│   ├── Global hotkey (Carbon RegisterEventHotKey)
│   ├── DictationPanel (NSPanel, non-activating, floating)
│   └── Paste via CGEvent (Cmd+V to frontmost app)
└── Views (SwiftUI)
```

## Troubleshooting

### Auto-paste doesn't work in dictation mode

The app needs **Accessibility** permission to simulate `Cmd+V` in other apps.

1. Open **System Settings → Privacy & Security → Accessibility**
2. Click the **+** button and add `Voxtral Realtime.app`
3. If already listed, toggle it **off and back on**
4. **Quit and relaunch** the app — macOS caches the trust state at process launch

When running Debug builds from Xcode, each rebuild produces a new binary signature. macOS tracks Accessibility trust per binary identity, so you may need to re-grant permission after rebuilding. To avoid this:
- Remove the old entry from Accessibility settings before re-adding
- Or run the Release build for testing dictation

Even if Accessibility isn't granted, the transcribed text is always copied to the clipboard — you can paste manually with `Cmd+V`.

### Model fails to load / runner crashes

Check Console.app (filter by `VoxtralRealtime`) for diagnostics:

- `"Runner stderr: ..."` — shows the runner's internal loading messages
- `"Runner exited with code N"` — non-zero exit indicates a crash

Common causes:
- **Missing model files** — verify `~/voxtral_realtime_quant_metal/` contains all three files (or check app bundle Resources)
- **libomp not found** — run `brew install libomp` and rebuild
- **Runner not built** — run `make voxtral_realtime-metal` in the ExecuTorch directory

### Microphone permission denied

The app requests microphone access on first use. If denied:

1. Open **System Settings → Privacy & Security → Microphone**
2. Enable `Voxtral Realtime`
3. Relaunch the app

### No transcription output (waveform animates but no text)

This means audio is captured but the runner isn't producing tokens. Check:

1. Console.app for `"Audio written: N bytes"` — confirms data flows to the runner
2. Console.app for `"Pipe write failed"` — indicates broken pipe
3. The runner may need a few seconds of speech before producing the first token

## License

Apache 2.0
