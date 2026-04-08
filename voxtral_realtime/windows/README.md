# Voxtral Realtime - Windows CUDA

Real-time speech transcription desktop app powered by ExecuTorch with CUDA acceleration.

This is the Windows equivalent of the [macOS Voxtral Realtime app](../macos/).

## Quick Start

Download `VoxtralRealtime-Setup.exe` from the [Releases](https://github.com/meta-pytorch/executorch-examples/releases) page and run the installer. Everything is bundled -- the app, runner, model weights, and tokenizer. No additional downloads required.

After install, launch from the Start Menu or desktop shortcut and click "Start Transcription".

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
- Model files from HuggingFace (see [Model Files](#model-files))

### Model Files

```powershell
pip install huggingface_hub
huggingface-cli download younghan-meta/Voxtral-Mini-4B-Realtime-2602-ExecuTorch-CUDA-Windows --local-dir voxtral_rt_exports
huggingface-cli download mistralai/Voxtral-Mini-4B-Realtime-2602 tekken.json --local-dir voxtral_tokenizer
```

### Building the Runner

```bash
cd executorch
cmake --preset voxtral-realtime-cuda
cmake --build --preset voxtral-realtime-cuda
```

### Build and Run

```powershell
cd VoxtralRealtime
dotnet restore
dotnet build --configuration Release
dotnet run --project VoxtralRealtime --configuration Release
```

### Publish Standalone Executable

```powershell
cd VoxtralRealtime
dotnet publish VoxtralRealtime --configuration Release --runtime win-x64 --self-contained true /p:PublishSingleFile=true /p:IncludeNativeLibrariesForSelfExtract=true /p:DebugType=none -o publish
```

### Create Installer

Builds a self-contained installer that bundles the app, runner, model weights, and tokenizer:

```powershell
# 1. Install Inno Setup (one-time)
winget install JRSoftware.InnoSetup

# 2. Publish the app
cd VoxtralRealtime
dotnet publish VoxtralRealtime --configuration Release --runtime win-x64 --self-contained true /p:PublishSingleFile=true /p:IncludeNativeLibrariesForSelfExtract=true /p:DebugType=none -o publish

# 3. Build the installer
cd ..
ISCC installer.iss
```

The output `installer-output\VoxtralRealtime-Setup.exe` includes:
- App executable (self-contained, no .NET runtime needed)
- `voxtral_realtime_runner.exe` + `aoti_cuda_shims.dll`
- Model weights (`model.pte`, `preprocessor.pte`, `aoti_cuda_blob.ptd`)
- Tokenizer (`tekken.json`)
- Start Menu and optional desktop shortcuts
- Clean uninstall via Windows Settings

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
