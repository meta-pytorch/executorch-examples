# Voxtral Realtime - Windows CUDA

Real-time speech transcription desktop app powered by ExecuTorch with CUDA acceleration.

https://github.com/user-attachments/assets/9288b923-24e6-4d73-9298-80b325633c2c



This is the Windows equivalent of the [macOS Voxtral Realtime app](../macos/).

## Quick Start

1. Download `VoxtralRealtime-Setup.exe` from the [Releases](https://github.com/meta-pytorch/executorch-examples/releases) page and run the installer
2. Launch from the Start Menu or desktop shortcut
3. Click **"Load Model"** — the app will automatically download the required model files (~5.2 GB) from HuggingFace on first launch
4. Once loaded, click **"Start Transcription"**

### Requirements

- Windows 10/11 with NVIDIA GPU (CUDA-capable)
- CUDA Toolkit installed (auto-detected from `C:\Program Files\NVIDIA GPU Computing Toolkit\CUDA\`)

## Features

- **Live Transcription** - Start/Pause/Resume with streaming text output
- **Session Management** - Save, rename, pin, delete, and export sessions (TXT/JSON/SRT)
- **Text Replacements** - Auto-correct transcription (e.g., "executorch" -> "ExecuTorch")
- **Text Snippets** - Voice-triggered templates for common text blocks
- **Dictation Mode** - Ctrl+Space global hotkey, floating overlay, auto-paste to any app, auto-stop on 2s silence
- **Audio Level Visualization** - Real-time waveform display
- **Auto Model Download** - Model weights are downloaded from HuggingFace on first launch with progress tracking

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl+Shift+R | Start / Resume transcription |
| Ctrl+. | Pause transcription |
| Ctrl+Enter | End session |
| Ctrl+Space | Toggle dictation mode |

## Build from Source

For developers who want to build the app themselves.

### Prerequisites

- [.NET 8.0 SDK](https://dotnet.microsoft.com/download/dotnet/8.0)
- Pre-built `voxtral_realtime_runner.exe` (see [Building the Runner](#building-the-runner))

### Building the Runner

Build the `voxtral_realtime_runner.exe` from the [ExecuTorch](https://github.com/pytorch/executorch) repo:

```bash
cd executorch
cmake --preset voxtral-realtime-cuda
cmake --build --preset voxtral-realtime-cuda
```

The runner will be at `cmake-out/examples/models/voxtral_realtime/Release/voxtral_realtime_runner.exe`.

### Build and Run

```powershell
cd VoxtralRealtime
dotnet restore
dotnet build --configuration Release
dotnet run --project VoxtralRealtime --configuration Release
```

Model files will be auto-downloaded on first launch to the `models/` directory next to the executable. To download them manually instead:

```powershell
pip install huggingface_hub
huggingface-cli download younghan-meta/Voxtral-Mini-4B-Realtime-2602-ExecuTorch-CUDA-Windows --local-dir models
huggingface-cli download mistralai/Voxtral-Mini-4B-Realtime-2602 tekken.json --local-dir models
```

### Publish Standalone Executable

Create a single self-contained exe (no .NET runtime required on target machine):

```powershell
cd VoxtralRealtime
dotnet publish VoxtralRealtime --configuration Release --runtime win-x64 --self-contained true /p:PublishSingleFile=true /p:IncludeNativeLibrariesForSelfExtract=true /p:DebugType=none -o publish
```

### Create Installer

Builds an installer that bundles the app and runner. Model weights are downloaded automatically on first launch.

```powershell
# 1. Install Inno Setup (one-time)
winget install JRSoftware.InnoSetup

# 2. Publish the app
cd VoxtralRealtime
dotnet publish VoxtralRealtime --configuration Release --runtime win-x64 --self-contained true /p:PublishSingleFile=true /p:IncludeNativeLibrariesForSelfExtract=true /p:DebugType=none -o publish

# 3. Build the installer (set EXECUTORCH_ROOT to your ExecuTorch repo path)
cd ..
$env:EXECUTORCH_ROOT = "C:\path\to\executorch"
& "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe" installer.iss
```

The output `installer-output\VoxtralRealtime-Setup.exe` includes:
- App executable (self-contained, no .NET runtime needed)
- `voxtral_realtime_runner.exe` + `aoti_cuda_shims.dll`
- Start Menu and optional desktop shortcuts
- Clean uninstall via Windows Settings

Model weights (`model.pte`, `preprocessor.pte`, `aoti_cuda_blob.ptd`, `tekken.json`) are **not bundled** — they are downloaded from HuggingFace on first launch, keeping the installer small (~49 MB).

## Architecture

The app wraps the `voxtral_realtime_runner.exe` C++ binary via stdin/stdout pipes:

```
Microphone (WASAPI 48kHz) -> Resample to 16kHz mono f32le -> stdin pipe -> runner.exe -> stdout tokens -> WPF UI
```

Platform-specific adaptations from the macOS app:
- **Audio**: WASAPI (shared mode) replaces AVAudioEngine, with software resampling from native rate to 16kHz
- **Hotkey**: Win32 `RegisterHotKey` replaces Carbon `RegisterEventHotKey`
- **UI**: WPF (.NET 8) with MVVM replaces SwiftUI
- **Backend**: `--data_path` for CUDA blob (macOS uses Metal backend)
- **Stdin**: Binary mode (`_setmode`) required on Windows to prevent 0x1A byte (Ctrl+Z) from being interpreted as EOF
