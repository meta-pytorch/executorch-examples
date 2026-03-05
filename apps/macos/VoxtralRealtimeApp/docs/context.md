# Project Context

Living document of architectural decisions, constraints, and accumulated knowledge. Read this before starting any new task.

---

## Architecture overview

```
VoxtralRealtimeApp (@main)
├── TranscriptStore (@Observable, @MainActor) — single source of truth
│   ├── SessionState: idle → loading → transcribing ⇆ paused → idle
│   ├── ModelState: unloaded → loading → ready
│   └── RunnerBridge (actor) — owns the runner subprocess
│         ├── Process (voxtral_realtime_runner C++ binary)
│         │   ├── stdin  ← raw 16kHz mono f32le PCM bytes
│         │   ├── stdout → transcript tokens (parsed line-by-line)
│         │   └── stderr → status messages ("Loading model...", etc.)
│         └── AudioEngine (actor)
│               └── AVAudioEngine → AVAudioConverter (resample to 16kHz mono) → pipe to stdin
├── DictationManager (@Observable, @MainActor)
│   ├── Global hotkey (Carbon RegisterEventHotKey, Ctrl+Space)
│   ├── DictationPanel (NSPanel, non-activating, floating, all spaces)
│   └── Paste via CGEvent (simulated Cmd+V to frontmost app)
└── Views (SwiftUI, NavigationSplitView)
    ├── Sidebar: SidebarView (session list, search, context menus)
    ├── Detail: TranscriptView | WelcomeView | SetupGuideView
    ├── Toolbar: RecordingControls
    ├── Overlay: ErrorBannerView
    └── Settings: SettingsView (2 tabs: General, Dictation)
```

### Data flow

1. User taps Record → `TranscriptStore.startTranscription()`
2. Store calls `runner.launchRunner()` if not already running → spawns `voxtral_realtime_runner` process
3. `launchRunner()` returns a `Streams` struct with 5 `AsyncStream`s (tokens, errors, audioLevel, status, modelState)
4. Store spawns a `TaskGroup` with 5 concurrent consumers for each stream
5. Store calls `runner.startAudioCapture()` → `AudioEngine.startCapture(writingTo: stdinFileHandle)`
6. AVAudioEngine tap callback: converts to 16kHz mono f32le → computes RMS via vDSP → writes bytes to stdin pipe
7. Runner process reads stdin, runs inference, writes tokens to stdout
8. RunnerBridge reads stdout on `DispatchQueue.global`, yields tokens via `AsyncStream`
9. Store appends tokens to `liveTranscript` → SwiftUI auto-updates
10. Stop → audio capture stops, stdin pipe stays open, runner stays alive (model remains loaded)
11. End session → creates `Session` value, saves to JSON, resets state

### Concurrency model

- `AudioEngine` and `RunnerBridge` are **custom actors** isolating I/O from the main thread
- Audio writes go directly from the AVAudioEngine tap to the stdin `FileHandle` — no extra actor hop
- Cross-isolation communication uses **5 `AsyncStream`s**: tokens, errors, audioLevel, status, modelState
- All UI state is `@MainActor`-isolated `@Observable` classes
- No `Task.detached` — structured child tasks via `TaskGroup`

### Dependency injection

`VoxtralRealtimeApp.init()` builds the object graph:
```
Preferences → TranscriptStore(preferences:) → DictationManager(store:, preferences:)
```
All three are injected into the SwiftUI environment via `.environment()`. Views access them with `@Environment(TranscriptStore.self)`.

---

## Decisions

| # | Decision | Rationale | Date | Status |
|---|---|---|---|---|
| 1 | Shell out to `voxtral_realtime_runner` via `Process` instead of linking ExecuTorch C++ | Simpler integration, runner already works as CLI, avoids complex C++/Swift bridging | Pre-project | active |
| 2 | JSON file persistence instead of SwiftData | Simpler, no migration headaches, sufficient for session list. File at `~/Library/Application Support/VoxtralRealtime/sessions.json` | Pre-project | active |
| 3 | `@Observable` + `@MainActor` pattern for state (not Combine/ObservableObject) | Modern Swift concurrency, less boilerplate, requires macOS 14+ | Pre-project | active |
| 4 | Custom actors for AudioEngine and RunnerBridge (not classes with locks) | Swift actor isolation provides thread safety without manual locking | Pre-project | active |
| 5 | 5 separate AsyncStreams from RunnerBridge (not a single multiplexed stream) | Each stream has different consumer behavior; keeps token stream unpolluted by errors/status | Pre-project | active |
| 6 | Carbon API for global hotkey (not modern alternative) | No modern macOS API exists for global hotkeys; Carbon `RegisterEventHotKey` is the standard approach | Pre-project | active |
| 7 | `NSPanel` (non-activating) for dictation overlay | Must not steal focus from the target app during dictation | Pre-project | active |
| 8 | Audio pipeline writes directly to FileHandle (no intermediate buffer) | Minimizes latency; 80ms PCM chunks go straight to runner stdin | Pre-project | active |
| 9 | Model preloading — runner stays alive between sessions | Avoids 30s+ model reload on each transcription start | Pre-project | active |
| 10 | XcodeGen (`project.yml`) for project generation | Keeps project config in version-controlled YAML, avoids Xcode project merge conflicts | Pre-project | active |
| 11 | Bundled runner + libomp + models via build script | Post-compile script in `project.yml` copies runner binary, patches `install_name_tool` for libomp, copies model files into .app Resources | Pre-project | active |
| 12 | Bundle models in .app via build script, DMG ships self-contained | Non-technical users should not need to download models separately | Pre-project | active |
| 13 | Preferences backed by UserDefaults with `didSet` writes | Simple, no framework dependency, auto-persists | Pre-project | active |
| 14 | Distribute via GitHub Releases DMG (not git-bundled models) | Model files are ~6.2 GB, too large for git. Developer builds DMG with models bundled, uploads to Releases. End users download DMG. | 2026-03-05 | active |
| 15 | `create_dmg.sh` validates bundled files before creating DMG | Prevents shipping an incomplete DMG missing model weights | 2026-03-05 | active |
| 16 | `scripts/build.sh` automates full pipeline | One command: check prereqs → xcodegen → xcodebuild → create DMG. Supports `--download-models` flag. | 2026-03-05 | active |

<!-- Status: active | superseded | revisiting -->

---

## Constraints

- Building requires `conda activate et-metal` — a dedicated conda env with ExecuTorch installed from a fresh `git clone` to `~/executorch` (Metal/MPS backend). Runner build, model download, and CLI testing all depend on it.
- Runner binary is a pre-built C++ executable (`voxtral_realtime_runner`) from ExecuTorch
- Audio format: 16kHz mono f32le PCM piped to runner's stdin (no WAV header, raw bytes)
- Model artifacts are ~6.2 GB total — not in git; developer downloads from HF, build script bundles into .app, DMG ships self-contained
- Must use Hardened Runtime + sandbox for notarization (entitlements file currently empty)
- macOS 14+ minimum (for `@Observable`, modern SwiftUI APIs)
- Apple Silicon only (Metal backend requires M1+)
- Runner expects exactly these CLI args: `--model_path`, `--tokenizer_path`, `--preprocessor_path`, `--mic`
- Runner's stdout protocol: emits `"Listening"` once model is ready, then transcript tokens; `PyTorchObserver` stats lines must be filtered
- Runner's stderr protocol: emits status strings like "Loading model", "Loading tokenizer", "Warming up", "Warmup complete"
- System-wide dictation requires Accessibility permission (for CGEvent paste simulation)
- Global hotkey uses Carbon API — cannot be changed to a modern API (none exists)
- No third-party dependencies — pure Apple frameworks (AVFoundation, CoreAudio, Accelerate, Carbon)
- `libomp` must be installed via Homebrew and bundled in the .app for the runner to work

---

## Known issues

| Issue | Impact | Workaround | Status |
|---|---|---|---|
| Spin-wait polling for model load state | `ensureRunnerLaunched()` uses `while modelState == .loading { Task.sleep(100ms) }` — wasteful polling loop | Works but not ideal; could use `AsyncStream` signal or continuation | open |
| `DictationManager.cleanup()` uses `MainActor.assumeIsolated` in nonisolated context | Fragile pattern — crashes if called off main actor | Currently safe because only called from `deinit` which runs on main actor | open |
| Entitlements file is empty | App won't pass notarization; sandbox + audio input entitlements needed | Only affects distribution, not development builds | open |
| No error recovery for pipe write failures | AudioEngine logs first 3 write errors then suppresses; relies on runner termination handler | Runner crash eventually triggers error flow | open |
| AccentColor in assets has no custom color values | Uses system default accent color | Functional, just not branded | open |
| Accessibility permission prompt timing | `AXIsProcessTrustedWithOptions` prompt fires on startup even if user doesn't need dictation | Could defer to first dictation use | open |
| Debug builds invalidate Accessibility trust | Each Xcode rebuild changes binary signature; macOS requires re-granting Accessibility permission | Use Release build for testing dictation, or remove/re-add in System Settings | mitigated |

<!-- Status: open | mitigated | resolved -->

---

## What works

Things confirmed working — don't re-investigate these:

- Full transcription pipeline: mic → AVAudioEngine → resample to 16kHz mono f32le → pipe to runner stdin → tokens from stdout → live UI
- Model preloading: runner launches once, stays alive across sessions (avoids ~30s reload)
- Pause / resume: stops audio capture but keeps runner process alive
- Session persistence: JSON save/load at `~/Library/Application Support/VoxtralRealtime/sessions.json`
- Session management: create, rename, delete, search, copy transcript
- System-wide dictation: Ctrl+Space → floating panel → auto-paste via CGEvent Cmd+V
- Silence detection in dictation mode: polls audioLevel every 250ms, auto-stops after configured timeout
- Audio level visualization: RMS computed via vDSP, animated waveform bars with color transitions
- Health check: validates runner binary, model files, mic permission on startup
- DMG creation: `scripts/create_dmg.sh` with drag-to-Applications layout
- Build script: bundles runner binary, libomp.dylib, model files into .app Resources via post-compile script
- Full build pipeline: `scripts/build.sh` validates prereqs, builds app, creates DMG in one command
- DMG validation: `create_dmg.sh` refuses to create DMG if runner/models/libomp are missing from .app bundle
- Runner stdout parsing: detects "Listening" for model ready, filters PyTorchObserver stats, strips ANSI escapes
- Runner stderr parsing: extracts status messages for UI (Loading model, Loading tokenizer, Warming up, etc.)
- Error flow: runner crash → RunnerError → ErrorBannerView with dismiss
- Settings window: runner path, model directory, silence threshold/timeout (2 tabs)
- Keyboard shortcuts: Cmd+Shift+R (start/resume), Cmd+. (pause), Cmd+Return (end), Cmd+Shift+C (copy), Cmd+Shift+U (unload), Ctrl+Space (dictation)

---

## What doesn't work

Approaches tried and abandoned — don't retry without new information:

- (none yet — no failed approaches to record)

---

## Feature status

| # | Feature | Status | Notes |
|---|---|---|---|
| 1 | Record / Stop | done | Toggle via toolbar + shortcuts |
| 2 | Model loading status | done | ProgressView + stderr-parsed status messages |
| 3 | Live scrolling transcript | done | Auto-scroll with ScrollViewReader |
| 4 | Copy transcript | done | Button + Cmd+Shift+C |
| 5 | Keyboard shortcuts | done | 6 shortcuts registered |
| 6 | Session history sidebar | done | List with selection, live row |
| 7 | Persist sessions | done | JSON file (not SwiftData) |
| 8 | Search sessions | done | `.searchable` in sidebar |
| 9 | Delete / rename sessions | done | Context menu + rename sheet |
| 10 | Export session (.txt/.srt/.json) | not started | — |
| 11 | Menu bar | partial | Transcription + Dictation menus; missing View, Export |
| 12 | Toolbar | done | Pause/Resume, End, Unload |
| 13 | Resizable window | done | Default 900×600 |
| 14 | Fullscreen / Split View | not started | No special handling |
| 15 | Settings window | done | 2 tabs (General, Dictation) |
| 16 | Audio input device picker | not started | `Preferences.audioDeviceID` exists but no UI |
| 17 | Audio level indicator | done | Animated waveform bars |
| 18 | Silence detection | done | Dictation mode only |
| 19 | Liquid Glass style | not started | Plan says macOS 26+ glassEffect |
| 20 | Light / dark mode | implicit | SwiftUI default behavior |
| 21 | Animations | done | Scroll, waveform, error banner transitions |
| 22 | Accessibility (VoiceOver) | not started | No accessibilityLabel/Hint on controls |
| 23 | Bundled runner binary | done | Build script in project.yml |
| 24 | First-run model download | not started | Manual download only; setup guide shows instructions |
| 25 | Developer ID signing + notarization | not started | Entitlements empty |
| 26 | DMG installer | done | `scripts/create_dmg.sh` |
| 27 | Sparkle auto-update | not started | Stretch goal |
| 28 | System-wide dictation | done | Ctrl+Space, floating panel, auto-paste |
| 29 | Pause / Resume | done | Audio stops, runner stays alive |
| 30 | Model preloading | done | Load once, transcribe instantly |

---

## Code patterns and conventions

When adding new features, follow these existing patterns:

- **State**: Add new state properties to `TranscriptStore` as `@Observable` properties. Views read directly, no bindings needed for read-only.
- **Two-way bindings in views**: Use `@Bindable var store = store` inside `body` to create bindings from `@Environment`.
- **New services**: Create as a Swift `actor` if it does I/O. Communicate with `TranscriptStore` via `AsyncStream`.
- **Views**: All views get state from `@Environment(TranscriptStore.self)` and/or `@Environment(Preferences.self)`.
- **Errors**: Add new cases to `RunnerError` enum. Set `store.currentError` to display in `ErrorBannerView`.
- **Logging**: Use `os.Logger(subsystem: "com.younghan.VoxtralRealtime", category: "YourCategory")`.
- **File references**: The planned `BundleResources.swift` was never created. Path resolution lives in `Preferences.init()` which auto-detects bundled vs. filesystem paths.
- **Keyboard shortcuts**: Register in `VoxtralRealtimeApp.swift` menu commands section.
- **Settings**: Add UI to `SettingsView.swift`, backing property to `Preferences.swift` with `UserDefaults` persistence.
- **No third-party deps**: Keep it pure Apple frameworks. If you need a dep, document it as a decision.

---

## Key file paths

### Source code

| What | Path (relative to `apps/macos/VoxtralRealtimeApp/`) |
|---|---|
| App entry point | `VoxtralRealtime/VoxtralRealtimeApp.swift` |
| Central state machine | `VoxtralRealtime/Models/TranscriptStore.swift` |
| Session model | `VoxtralRealtime/Models/Session.swift` |
| User preferences | `VoxtralRealtime/Models/Preferences.swift` |
| Audio capture actor | `VoxtralRealtime/Services/AudioEngine.swift` |
| Runner process actor | `VoxtralRealtime/Services/RunnerBridge.swift` |
| Global dictation manager | `VoxtralRealtime/Services/DictationManager.swift` |
| Startup validation | `VoxtralRealtime/Services/HealthCheck.swift` |
| Error types | `VoxtralRealtime/Utilities/RunnerError.swift` |
| Root view (NavigationSplitView) | `VoxtralRealtime/Views/ContentView.swift` |
| Session list | `VoxtralRealtime/Views/SidebarView.swift` |
| Live transcript display | `VoxtralRealtime/Views/TranscriptView.swift` |
| Toolbar buttons | `VoxtralRealtime/Views/RecordingControls.swift` |
| Waveform visualization | `VoxtralRealtime/Views/AudioLevelView.swift` |
| Preferences window | `VoxtralRealtime/Views/SettingsView.swift` |
| First-run guide | `VoxtralRealtime/Views/SetupGuideView.swift` |
| Landing page | `VoxtralRealtime/Views/WelcomeView.swift` |
| Error banner overlay | `VoxtralRealtime/Views/ErrorBannerView.swift` |
| Dictation HUD content | `VoxtralRealtime/Views/DictationOverlayView.swift` |
| Floating NSPanel | `VoxtralRealtime/Views/DictationPanel.swift` |

### Build configuration

| What | Path |
|---|---|
| XcodeGen spec | `project.yml` |
| Entitlements | `VoxtralRealtime/VoxtralRealtime.entitlements` |
| Info.plist | `VoxtralRealtime/Info.plist` |
| App icon assets | `VoxtralRealtime/Resources/Assets.xcassets/` |
| DMG build script | `scripts/create_dmg.sh` |
| Full build pipeline | `scripts/build.sh` |

### External dependencies

| What | Path |
|---|---|
| Plan | `docs/plan.md` |
| Progress log | `docs/progress.md` |
| Runner source | `~/executorch/examples/models/voxtral_realtime` |
| Runner binary | `~/executorch/cmake-out/examples/models/voxtral_realtime/voxtral_realtime_runner` |
| Model dir (HF) | `~/voxtral_realtime_quant_metal` |
| Model (Metal int4) | `~/voxtral_realtime_quant_metal/model-metal-int4.pte` |
| Preprocessor | `~/voxtral_realtime_quant_metal/preprocessor.pte` |
| Tokenizer | `~/voxtral_realtime_quant_metal/tekken.json` |
| Mic streamer (CLI test) | `~/voxtral_realtime_quant_metal/stream_audio.py` |
| Session data | `~/Library/Application Support/VoxtralRealtime/sessions.json` |

### Runtime requirements

| Requirement | How to install |
|---|---|
| libomp | `brew install libomp` |
| ExecuTorch (Metal) | `conda activate et-metal && cd ~/executorch && EXECUTORCH_BUILD_KERNELS_TORCHAO=1 TORCHAO_BUILD_EXPERIMENTAL_MPS=1 ./install_executorch.sh` |
| Runner binary | `conda activate et-metal && cd ~/executorch && make voxtral_realtime-metal` |
| Model artifacts | `hf download mistralai/Voxtral-Mini-4B-Realtime-2602-Executorch --local-dir ~/voxtral_realtime_quant_metal` |
| XcodeGen | `brew install xcodegen` |

---

## Open questions

- [ ] Use `Process` to shell out to the runner, or link ExecuTorch C++ as a Swift package? (Currently using Process)
- [ ] Metal vs XNNPACK as default — benchmark both on M1/M2/M3 before deciding.
- [ ] Minimum macOS version: 14 (Sonoma, current) vs 15 (Sequoia) for latest SwiftUI APIs?
