# Voxtral Realtime — macOS App

A native macOS app that runs Mistral's Voxtral-Mini-4B-Realtime on-device for real-time voice transcription. All inference runs locally via ExecuTorch — no cloud, no network required.

## Goal

Wrap the ExecuTorch `voxtral_realtime_runner` C++ binary in a polished SwiftUI macOS app with mic capture, live transcript display, session management, and DMG distribution.

---

## Inference Runtime

The app shells out to the pre-built runner binary. Audio is captured natively (AVFoundation / CoreAudio) and piped as 16kHz mono f32le PCM to stdin.

```bash
./stream_audio.py | \
  voxtral_realtime_runner \
    --model_path ./model-metal-int4.pte \
    --tokenizer_path ./tekken.json \
    --preprocessor_path ./preprocessor.pte \
    --mic
```

Runner source: `/Users/younghan/project/executorch/examples/models/voxtral_realtime`

### Model artifacts

Pre-quantized 4-bit Metal checkpoint from HuggingFace — no manual export needed.

| Artifact | Purpose | Local path |
|---|---|---|
| `model-metal-int4.pte` | 4-bit quantized streaming encoder + decoder (Metal) | `~/voxtral_realtime_quant_metal/model-metal-int4.pte` |
| `preprocessor.pte` | Audio-to-mel spectrogram | `~/voxtral_realtime_quant_metal/preprocessor.pte` |
| `tekken.json` | Tokenizer | `~/voxtral_realtime_quant_metal/tekken.json` |
| `stream_audio.py` | Mic capture script (16kHz mono f32le PCM to stdout) | `~/voxtral_realtime_quant_metal/stream_audio.py` |

HF repo: [`mistralai/Voxtral-Mini-4B-Realtime-2602-Executorch`](https://huggingface.co/mistral-labs/Voxtral-Mini-4B-Realtime-2602-Executorch)

#### Download models

```bash
export LOCAL_FOLDER="$HOME/voxtral_realtime_quant_metal"
hf download mistralai/Voxtral-Mini-4B-Realtime-2602-Executorch --local-dir ${LOCAL_FOLDER}
```

### Build the runner

```bash
export EXECUTORCH_PATH="$HOME/executorch"

# Install ExecuTorch with Metal backend
cd ${EXECUTORCH_PATH} && \
  EXECUTORCH_BUILD_KERNELS_TORCHAO=1 \
  TORCHAO_BUILD_EXPERIMENTAL_MPS=1 \
  ./install_executorch.sh

# Build the voxtral realtime runner
cd ${EXECUTORCH_PATH} && make voxtral_realtime-metal
```

The runner binary lands at:
```
${EXECUTORCH_PATH}/cmake-out/examples/models/voxtral_realtime/voxtral_realtime_runner
```

#### Runtime dependencies

```bash
brew install libomp
export DYLD_LIBRARY_PATH=/usr/lib:$(brew --prefix libomp)/lib
pip install sounddevice   # in the executorch conda env
```

### Run (CLI, for testing)

```bash
export EXECUTORCH_PATH="$HOME/executorch"
export CMAKE_RUNNER="${EXECUTORCH_PATH}/cmake-out/examples/models/voxtral_realtime/voxtral_realtime_runner"
export LOCAL_FOLDER="$HOME/voxtral_realtime_quant_metal"

cd ${LOCAL_FOLDER} && chmod +x stream_audio.py &&
./stream_audio.py | \
   ${CMAKE_RUNNER} \
    --model_path ./model-metal-int4.pte \
    --tokenizer_path ./tekken.json \
    --preprocessor_path ./preprocessor.pte \
    --mic
```

### Backend options

| Backend | Hardware | Quantization | Notes |
|---|---|---|---|
| Metal | Apple GPU | int4 (`model-metal-int4.pte`) | Pre-quantized from HF, recommended for M-series |
| XNNPACK | CPU | `8da4w` | Requires manual export |

Default: Metal on Apple Silicon (uses the HF pre-quantized checkpoint).

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                    VoxtralApp                        │
│                  (SwiftUI App)                       │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ Sidebar  │  │ Transcript   │  │  Toolbar /   │  │
│  │ Sessions │  │ DetailView   │  │  StatusBar   │  │
│  └────┬─────┘  └──────┬───────┘  └──────┬───────┘  │
│       │               │                 │           │
│       └───────────────┼─────────────────┘           │
│                       │                             │
│              ┌────────▼────────┐                    │
│              │ TranscriptStore │  @Observable        │
│              │  (shared state) │  @MainActor         │
│              └────────┬────────┘                    │
│                       │                             │
│         ┌─────────────┼─────────────┐               │
│         ▼                           ▼               │
│  ┌─────────────┐          ┌─────────────────┐       │
│  │ AudioEngine │          │  RunnerBridge   │       │
│  │   (actor)   │──PCM──▶  │    (actor)      │       │
│  │ CoreAudio   │  pipe    │ Process + stdin  │       │
│  └─────────────┘          │ stdout parsing   │       │
│                           └─────────────────┘       │
└─────────────────────────────────────────────────────┘
```

### Key types

| Type | Isolation | Responsibility |
|---|---|---|
| `VoxtralApp` | @MainActor | App entry, scene, menu commands |
| `TranscriptStore` | @MainActor, @Observable | Sessions list, active transcript text, recording state |
| `AudioEngine` | actor | CoreAudio capture → 16kHz mono f32le PCM stream |
| `RunnerBridge` | actor | Spawn `voxtral_realtime_runner` as `Process`, pipe PCM to stdin, parse stdout/stderr |
| `HealthCheck` | nonisolated | Startup validation: binary exists, model files present, mic permission |
| `Session` | value type (Sendable) | Immutable snapshot: id, date, transcript text, duration |
| `RunnerError` | value type (Sendable) | Error cases: binary not found, model missing, crash, permission denied |
| `Preferences` | @MainActor, @Observable | Model paths, backend selection, audio device |

### Data flow

1. User taps Record (or Cmd+Shift+R).
2. `TranscriptStore` tells `RunnerBridge` to start a session.
3. `RunnerBridge` spawns the runner process, obtaining a `FileHandle` to its stdin pipe.
4. `RunnerBridge` tells `AudioEngine` to start capture, passing the stdin `FileHandle` directly.
5. `AudioEngine` opens a CoreAudio input unit, writes 80ms PCM chunks directly to the stdin pipe (no intermediate actor hop).
6. `RunnerBridge` reads the runner's stdout line-by-line, parses transcript tokens.
7. New tokens flow back to `TranscriptStore` via `AsyncStream<String>`.
8. `TranscriptStore` appends to the live transcript; SwiftUI updates the view.
9. User taps Stop (or Cmd+.). `TranscriptStore` tells `RunnerBridge` to stop.
10. `RunnerBridge` tells `AudioEngine` to stop capture, then closes stdin. Runner flushes remaining text before exiting.

### Error flow

1. `RunnerBridge` reads stderr asynchronously alongside stdout.
2. If the runner process exits with non-zero status or emits to stderr, `RunnerBridge` yields an error through a separate `AsyncStream<RunnerError>`.
3. `TranscriptStore` receives the error, sets a published `currentError` property, and stops recording.
4. The UI displays an inline error banner (not a modal alert) with a retry action.

Common failure modes:
- **Runner binary not found** — caught at startup by `HealthCheck`, surfaces a setup guide.
- **Model files missing/corrupt** — runner exits immediately with stderr message.
- **Runner crash mid-session** — `Process.terminationHandler` fires, `RunnerBridge` yields error, UI shows "transcription interrupted" with the partial transcript preserved.
- **Microphone permission denied** — `AudioEngine` checks `AVCaptureDevice.authorizationStatus` before opening; surfaces a permission prompt.

### Concurrency design

- `AudioEngine` and `RunnerBridge` are custom actors to isolate audio I/O and process management from the UI.
- `AudioEngine` writes PCM chunks directly to the runner's stdin `FileHandle` — avoids an extra actor hop per 80ms chunk. The `FileHandle` is safe to write from a single actor.
- Transcript tokens flow via `AsyncStream<String>` — structured concurrency, no callbacks.
- Errors flow via a separate `AsyncStream<RunnerError>` to avoid polluting the token stream.
- All UI state lives in `@MainActor`-isolated `@Observable` classes.
- No `Task.detached` unless profiling shows a need. Prefer structured child tasks.

---

## Features

### Core (MVP)

1. **Record / Stop** — single toggle; mic capture starts/stops.
2. **Model loading status** — show progress while runner initializes (loading .pte files).
3. **Live scrolling transcript** — text appears token-by-token in real time, auto-scrolls to bottom.
4. **Copy transcript** — select text or one-click "Copy All" button.
5. **Keyboard shortcuts** — Cmd+Shift+R record, Cmd+. stop, Cmd+C copy selection, Cmd+Shift+C copy all. (Cmd+R avoided — conflicts with system "show ruler" in text views.)

### Session management

6. **Session history sidebar** — each recording saved as a session with timestamp and transcript.
7. **Persist sessions** — SwiftData (macOS 14+) for free search, sorting, and migration; JSON file fallback if targeting older OS.
8. **Search sessions** — Cmd+F to filter past transcripts by keyword.
9. **Delete / rename sessions** — right-click context menu.
10. **Export session** — save as `.txt`, `.srt` (subtitles), or `.json` with timestamps.

### macOS integration

11. **Menu bar** — App, Edit (Undo/Redo/Copy/Paste/Select All), View (toggle sidebar), Transcription (Record/Stop/Export), Window, Help.
12. **Toolbar** — record button (red dot when active), export, settings gear.
13. **Resizable window** — min 500x400, remembers position/size across launches.
14. **Fullscreen / Split View** support.
15. **Settings window** (Cmd+,) — model paths, audio input device picker, backend (Metal / XNNPACK), appearance.

### Audio

16. **Audio input device selection** — pick from available mics in Settings.
17. **Audio level indicator** — subtle waveform or level meter while recording.
18. **Silence detection** — visual indicator when no speech detected (dims the record button).

### Polish

19. **Liquid Glass style** — translucent window chrome, glass-effect toolbar and sidebar, matching macOS 26+ aesthetics with fallback for older OS.
20. **Light / dark mode** — respect system appearance.
21. **Animations** — smooth transcript scroll, record button pulse, fade-in for new tokens.
22. **Accessibility** — VoiceOver labels on all controls, Dynamic Type support, keyboard-navigable.

### Distribution

23. **Bundled runner binary** — `voxtral_realtime_runner` embedded in the .app bundle.
24. **First-run model download** — guide user to download model artifacts (or auto-download from HuggingFace with progress).
25. **Developer ID signed + notarized** — passes Gatekeeper on first launch.
26. **DMG installer** — drag-to-Applications layout.
27. **Sparkle auto-update** (stretch goal) — check for updates on launch.

---

## Style

- Liquid Glass translucent window (macOS 26+ `glassEffect`, `.ultraThinMaterial` fallback)
- Light, airy palette — white/transparent surfaces, subtle gray borders
- SF Symbols for all icons (mic.fill, stop.fill, doc.on.doc, gear)
- Monospaced or serif font option for transcript text (user preference)
- Minimal chrome — content-first, toolbar auto-hides in fullscreen

---

## Entitlements & permissions

| Entitlement | Required | Reason |
|---|---|---|
| `com.apple.security.device.audio-input` | Yes | Microphone access |
| `com.apple.security.app-sandbox` | Yes (for notarization) | App Store / Gatekeeper requirement |
| `com.apple.security.files.user-selected.read-write` | Yes | User picks model file location |
| `com.apple.security.network.client` | Optional | Only if auto-downloading models from HuggingFace |
| Hardened Runtime | Yes | Required for notarization |

The app must include an `NSMicrophoneUsageDescription` string in Info.plist explaining why mic access is needed.

---

## Startup health check

On first launch (and on every launch before enabling Record):

1. **Runner binary** — verify `voxtral_realtime_runner` exists in the app bundle and is executable.
2. **Model files** — verify `model.pte`, `preprocessor.pte`, and `tekken.json` exist at the configured paths.
3. **Microphone permission** — check `AVCaptureDevice.authorizationStatus(for: .audio)`.

If any check fails, show a setup guide view instead of the main UI (not a blocking modal — the user can still browse past sessions).

---

## File structure (planned)

```
VoxtralRealtime/
├── VoxtralRealtimeApp.swift         # @main, scenes, menu commands
├── Models/
│   ├── Session.swift                # Session value type
│   ├── TranscriptStore.swift        # @Observable shared state
│   └── Preferences.swift            # User settings
├── Services/
│   ├── AudioEngine.swift            # CoreAudio capture actor
│   ├── RunnerBridge.swift           # Process management actor
│   └── HealthCheck.swift            # Startup validation (binary, models, mic)
├── Utilities/
│   ├── BundleResources.swift        # Path resolution for bundled runner/models
│   └── RunnerError.swift            # Error types for runner failures
├── Views/
│   ├── ContentView.swift            # NavigationSplitView root
│   ├── SidebarView.swift            # Session list
│   ├── TranscriptView.swift         # Live transcript detail
│   ├── RecordingControls.swift      # Toolbar controls
│   ├── AudioLevelView.swift         # Waveform / level meter
│   ├── SetupGuideView.swift         # First-run / missing model guide
│   ├── ErrorBannerView.swift        # Inline error display
│   └── SettingsView.swift           # Preferences window
├── Resources/
│   ├── Assets.xcassets
│   └── Runner/                      # Bundled voxtral_realtime_runner
├── VoxtralRealtime.entitlements
└── Info.plist
```

---

## Open questions

- [x] ~~Bundle model artifacts in the .app (large ~2-4GB) or download on first run?~~ → Download from HF on first run. Pre-quantized checkpoint at `mistralai/Voxtral-Mini-4B-Realtime-2602-Executorch`.
- [ ] Use `Process` to shell out to the runner, or link ExecuTorch C++ as a Swift package?
- [ ] Metal vs XNNPACK as default — benchmark both on M1/M2/M3 before deciding.
- [ ] Minimum macOS version: 14 (Sonoma) for `@Observable`, or 15 (Sequoia) for latest SwiftUI APIs?