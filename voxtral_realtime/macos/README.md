# Voxtral Realtime

A native macOS showcase app for [Voxtral-Mini-4B-Realtime](https://huggingface.co/mistral-labs/Voxtral-Mini-4B-Realtime-2602-ExecuTorch) — Mistral's on-device real-time speech transcription model. All inference runs locally on Apple Silicon via [ExecuTorch](https://github.com/pytorch/executorch) with Metal acceleration. No cloud, no network required.

https://github.com/user-attachments/assets/6d6089fc-5feb-458b-a60b-08379855976a

## Features

- **Live transcription** — real-time token streaming with audio waveform visualization
- **System-wide dictation** — press `Ctrl+Space` in any app to transcribe speech and auto-paste the result
- **"Hey torch" voice wake** — hands-free dictation via Silero VAD speech detection and wake phrase matching
- **Text replacements** — auto-correct names, acronyms, and domain terms after transcription
- **Snippets** — say a trigger phrase (e.g., "email signature") to paste pre-written templates
- **Model preloading** — load the model once, transcribe instantly across sessions
- **Pause / resume** — pause and resume within the same session without losing context
- **Session history** — searchable history with pinning, recency grouping, rename, copy, and multi-format export (.txt, .json, .srt)
- **Silence detection** — dictation auto-stops after configurable silence timeout
- **Sidebar navigation** — Home, Replacements, Snippets, Wake, and Settings pages
- **Self-contained DMG** — runner binary, model weights, and runtime libraries all bundled


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
5. Press **`Ctrl+Space`** again to stop, or wait for silence auto-stop
6. The transcribed text is automatically pasted into the focused text field

### Voice wake ("Hey torch")

1. Open the **Wake** page in the sidebar and enable it (or press `Ctrl+Shift+W`)
2. Say **"Hey torch"** — Silero VAD detects your speech, Voxtral checks for the wake keyword
3. If matched, the dictation panel appears and you can start speaking
4. Dictation auto-pastes when you stop speaking, then wake listening resumes

The wake keyword is configurable in the Wake settings (default: "torch"). The app accumulates the full speech segment before checking, so you can say "hey torch" naturally as one phrase.

### Replacements

Add text replacements in the **Replacements** page. Each entry has a trigger and replacement — when the trigger appears in transcribed text, it's automatically replaced.

Examples:
- `mtia` → `MTIA`
- `executorch` → `ExecuTorch`
- `execute torch` → `ExecuTorch`

Supports case-preserving matching and word boundary options.

### Snippets

Add voice-triggered templates in the **Snippets** page. When your entire dictation matches a snippet trigger, the template content is pasted instead.

Example: say **"email signature"** to paste:
```
Best,
Younghan
```

### Keyboard shortcuts

| Shortcut | Action |
|---|---|
| `Cmd+Shift+R` | Start / Resume transcription |
| `Cmd+.` | Pause transcription |
| `Cmd+Return` | End session and save |
| `Cmd+Shift+C` | Copy transcript |
| `Cmd+Shift+U` | Unload model |
| `Ctrl+Space` | Toggle system-wide dictation |
| `Ctrl+Shift+W` | Toggle voice wake on/off |

---

## Build from Source

Model files (~6.2 GB) are not included in the git repo. The entire build chain — ExecuTorch installation, runner compilation, model downloading — runs inside a conda environment with Metal (MPS) backend support. The build script bundles everything into the `.app` so the resulting DMG is self-contained.

### Quick build

If you already have the conda env set up, ExecuTorch built, and models downloaded:

```bash
conda create -n et-metal python=3.12 -y
conda activate et-metal
cd voxtral_realtime/macos
./scripts/build.sh
```

Or to download models and build in one step:

```bash
conda create -n et-metal python=3.12 -y
conda activate et-metal
./scripts/build.sh --download-models
```

The script checks that you're in a conda env, validates all prerequisites, builds the app, and creates a DMG with all models bundled. Run `./scripts/build.sh --help` for all options.

### Full setup (from scratch)

#### Prerequisites

- macOS 14.0+ (Sonoma)
- Apple Silicon (M1/M2/M3/M4)
- Xcode 16+
- [Conda](https://docs.conda.io/en/latest/miniconda.html) (Miniconda or Anaconda)

```bash
brew install xcodegen libomp
```

#### 1. Create and activate the conda environment

All subsequent steps must run inside this environment. The conda env isolates the Python packages and C++ build artifacts that ExecuTorch needs.

```bash
conda create -n et-metal python=3.10 -y
conda activate et-metal
```

> You must run `conda activate et-metal` in every new terminal session before building or running the runner.

#### 2. Install ExecuTorch with Metal backend

```bash
export EXECUTORCH_PATH="$HOME/executorch"
git clone https://github.com/pytorch/executorch/ ${EXECUTORCH_PATH}
cd ${EXECUTORCH_PATH}
EXECUTORCH_BUILD_KERNELS_TORCHAO=1 TORCHAO_BUILD_EXPERIMENTAL_MPS=1 ./install_executorch.sh
```

This installs ExecuTorch with the Metal (MPS) backend enabled, which is required for Apple Silicon GPU acceleration. If you run into installation problems, see the official [Voxtral Realtime installation guide](https://github.com/pytorch/executorch/tree/main/examples/models/voxtral_realtime).

#### 3. Build the voxtral realtime runner

```bash
cd ${EXECUTORCH_PATH}
make voxtral_realtime-metal
```

The runner binary will be at:
```
${EXECUTORCH_PATH}/cmake-out/examples/models/voxtral_realtime/voxtral_realtime_runner
```

#### 4. Build the Silero VAD stream runner (for voice wake)

```bash
cd ${EXECUTORCH_PATH}
make silero-vad-cpu
```

This builds `silero_vad_stream_runner` at:
```
${EXECUTORCH_PATH}/cmake-out/examples/models/silero_vad/silero_vad_stream_runner
```

#### 5. Install Python packages

```bash
pip install huggingface_hub sounddevice
```

- `huggingface_hub` — to download model artifacts from HuggingFace
- `sounddevice` — for the CLI mic streaming test script

#### 6. Download model artifacts

```bash
export LOCAL_FOLDER="$HOME/voxtral_realtime_quant_metal"
hf download mistralai/Voxtral-Mini-4B-Realtime-2602-Executorch --local-dir ${LOCAL_FOLDER}
```

This downloads three files (~6.2 GB total):
- `model-metal-int4.pte` — 4-bit quantized model (Metal)
- `preprocessor.pte` — audio-to-mel spectrogram
- `tekken.json` — tokenizer

HuggingFace repo: [`mistralai/Voxtral-Mini-4B-Realtime-2602-ExecuTorch`](https://huggingface.co/mistral-labs/Voxtral-Mini-4B-Realtime-2602-ExecuTorch)

Download the Silero VAD model:

```bash
hf download younghan-meta/Silero-VAD-ExecuTorch-XNNPACK --local-dir ~/silero_vad_xnnpack
```

#### 7. Test with CLI (optional)

Verify the runner works before building the app:

```bash
export DYLD_LIBRARY_PATH=/usr/lib:$(brew --prefix libomp)/lib
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

#### 8. Build the app and create DMG

```bash
cd voxtral_realtime/macos
./scripts/build.sh
```

Or build manually:

```bash
xcodegen generate
xcodebuild -project VoxtralRealtime.xcodeproj -scheme VoxtralRealtime -configuration Release -derivedDataPath build build
./scripts/create_dmg.sh "./build/Build/Products/Release/Voxtral Realtime.app" "./VoxtralRealtime.dmg"
```

The post-compile build script automatically bundles the runner binary, `libomp.dylib`, and all model files into `.app/Contents/Resources/`. The `create_dmg.sh` script validates that all required files are present before creating the DMG.

The resulting DMG is self-contained — end users just drag to Applications and run.

---

## Architecture

```
VoxtralRealtimeApp
├── TranscriptStore (@Observable, @MainActor)
│   ├── SessionState: idle → loading → transcribing ⇆ paused → idle
│   ├── ModelState: unloaded → loading → ready
│   ├── TextPipeline: replacements → snippets → style (no-op)
│   └── RunnerBridge (actor)
│         ├── Process (voxtral_realtime_runner)
│         │   ├── stdin  ← raw 16kHz mono f32le PCM
│         │   ├── stdout → transcript tokens
│         │   └── stderr → status messages
│         └── AudioEngine (actor)
│               └── AVAudioEngine → format conversion → pipe
├── DictationManager (@Observable, @MainActor)
│   ├── Global hotkey (Carbon RegisterEventHotKey)
│   ├── VadService (actor)
│   │     ├── Process (silero_vad_stream_runner)
│   │     │   ├── stdin  ← 16kHz mono f32le PCM
│   │     │   └── stdout → PROB <time> <probability>
│   │     └── AVAudioEngine → speech segment accumulation
│   ├── Wake flow: VAD speech-end → Voxtral phrase check → active
│   ├── DictationPanel (NSPanel, non-activating, floating)
│   └── Paste via CGEvent (Cmd+V to frontmost app)
├── ReplacementStore / SnippetStore (JSON, Application Support)
└── Views (SwiftUI, sidebar navigation)
      ├── Home (welcome, transcription, session detail)
      ├── Replacements (add/edit/delete trigger→replacement)
      ├── Snippets (add/edit/delete voice-triggered templates)
      ├── Wake (VAD toggle, keyword, detection tuning, status)
      └── Settings (runner paths, model files, silence, shortcuts)
```

## Troubleshooting

### Auto-paste doesn't work in dictation mode

The app needs **Accessibility** permission to simulate `Cmd+V` in other apps.

1. Open **System Settings → Privacy & Security → Accessibility**
2. Click the **+** button and add `Voxtral Realtime.app`
3. If already listed, toggle it **off and back on**
4. **Quit and relaunch** the app — macOS caches the trust state at process launch

After every fresh build, reset Accessibility permissions:

```bash
tccutil reset Accessibility org.pytorch.executorch.VoxtralRealtime
```

Then relaunch and re-grant when prompted.

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
3. **Quit and relaunch** the app — macOS caches permission grants per process lifetime

### Microphone producing silence

If the Wake status shows "Disabled" with a silence error, macOS is delivering zero-audio to the app. This can happen after fresh builds or permission changes.

1. Toggle the app's microphone permission **off and on** in System Settings
2. Quit and relaunch the app
3. If running from Cursor's terminal, try the native macOS Terminal instead

### Voice wake not triggering

- Check the **Wake** page — status should show "Listening for speech..."
- Ensure `silero_vad_stream_runner` and `silero_vad.pte` paths are correct
- Try increasing the **Check window** slider (default 4s) — Voxtral needs 1-2s to produce the first token
- Speak clearly and wait for a brief pause after "hey torch" so VAD detects the speech segment end

### Permission prompts don't appear (stale TCC entries)

If you've built or installed the app multiple times (Debug builds, Release builds, DMG installs), macOS may have accumulated multiple permission entries for the same bundle ID. Reset them to get a clean slate:

```bash
tccutil reset Microphone org.pytorch.executorch.VoxtralRealtime
tccutil reset Accessibility org.pytorch.executorch.VoxtralRealtime
```

Then quit and relaunch the app. You should see fresh permission prompts.

### No transcription output (waveform animates but no text)

This means audio is captured but the runner isn't producing tokens. Check:

1. Console.app for `"Audio written: N bytes"` — confirms data flows to the runner
2. Console.app for `"Pipe write failed"` — indicates broken pipe
3. The runner may need a few seconds of speech before producing the first token
