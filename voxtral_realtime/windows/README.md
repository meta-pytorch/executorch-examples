# Voxtral Realtime - Windows CUDA

Real-time speech transcription desktop app powered by ExecuTorch with CUDA acceleration.

This is the Windows equivalent of the [macOS Voxtral Realtime app](../macos/).

## Quick Start (Pre-built Release)

Download `VoxtralRealtime.exe` from the [Releases](https://github.com/meta-pytorch/executorch-examples/releases) page and run it directly. No installation required.

You also need:
- The `voxtral_realtime_runner.exe` (built from ExecuTorch with CUDA support)
- Model files from HuggingFace (see [Model Files](#model-files) below)

## Prerequisites

- Windows 10/11 with NVIDIA GPU (CUDA-capable)
- CUDA Toolkit installed (auto-detected from `C:\Program Files\NVIDIA GPU Computing Toolkit\CUDA\`)
- [.NET 8.0 SDK](https://dotnet.microsoft.com/download/dotnet/8.0) (only for building from source)

## Model Files

Download from HuggingFace:

```powershell
pip install huggingface_hub
huggingface-cli download younghan-meta/Voxtral-Mini-4B-Realtime-2602-ExecuTorch-CUDA --local-dir voxtral_rt_exports
```

This downloads: `model.pte`, `preprocessor.pte`, `aoti_cuda_blob.ptd`

You also need the tokenizer from the base model:

```powershell
huggingface-cli download mistralai/Voxtral-Mini-4B-Realtime-2602 tekken.json --local-dir voxtral_tokenizer
```

## Building the Runner

Build the `voxtral_realtime_runner.exe` from the ExecuTorch repo:

```bash
cd executorch
cmake --preset voxtral-realtime-cuda
cmake --build --preset voxtral-realtime-cuda
```

The runner will be at `cmake-out/examples/models/voxtral_realtime/Release/voxtral_realtime_runner.exe`.

## Build from Source

```powershell
# Install .NET SDK if not already installed
winget install Microsoft.DotNet.SDK.8

# Build
cd VoxtralRealtime
dotnet restore
dotnet build --configuration Release

# Run
dotnet run --project VoxtralRealtime --configuration Release
```

## Publish Standalone Executable

Create a single self-contained exe (no .NET runtime required on target machine):

```powershell
cd VoxtralRealtime
dotnet publish VoxtralRealtime --configuration Release --runtime win-x64 --self-contained true /p:PublishSingleFile=true /p:IncludeNativeLibrariesForSelfExtract=true /p:DebugType=none -o publish
```

The output `publish\VoxtralRealtime.exe` can be distributed and run on any Windows x64 machine.

## Configuration

On first launch, the app auto-loads the model from default paths. All paths are configurable in Settings:

| File | Default Path |
|------|-------------|
| Runner | `cmake-out\examples\models\voxtral_realtime\Release\voxtral_realtime_runner.exe` |
| Model | `voxtral_rt_exports_wsl\model.pte` |
| Preprocessor | `voxtral_rt_exports_wsl\preprocessor.pte` |
| CUDA blob | `voxtral_rt_exports_wsl\aoti_cuda_blob.ptd` |
| Tokenizer | `tekken.json` |

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
