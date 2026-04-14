# Parakeet Speech-to-Text - Windows CUDA

Desktop speech-to-text app powered by NVIDIA Parakeet TDT 0.6B with ExecuTorch CUDA acceleration.

Record audio, then transcribe it on-device with segment-level timestamps.

## Quick Start

1. Download `ParakeetApp-Setup.exe` from the [Releases](https://github.com/meta-pytorch/executorch-examples/releases) page and run the installer
2. Launch from the Start Menu or desktop shortcut
3. Click **"Download Model"** — the app will automatically download the required model files from HuggingFace on first launch
4. Once ready, click **"Record"** to capture audio, then **"Stop & Transcribe"**

### Requirements

- Windows 10/11 with NVIDIA GPU (CUDA-capable)
- CUDA Toolkit installed (auto-detected from `C:\Program Files\NVIDIA GPU Computing Toolkit\CUDA\`)

## Features

- **Record & Transcribe** — Record audio from your microphone, then transcribe with Parakeet TDT 0.6B
- **Segment Timestamps** — View transcription results with segment-level timing information
- **Session History** — Save, rename, pin, delete, and export sessions (TXT/JSON/SRT)
- **Audio Level Visualization** — Real-time waveform display during recording
- **Auto Model Download** — Model weights downloaded from HuggingFace on first launch with progress tracking
- **SRT Export** — Export sessions with timestamps in SubRip format

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl+Shift+R | Start recording |
| Ctrl+Enter | Stop & Transcribe |

## Build from Source

### Prerequisites

- [.NET 8.0 SDK](https://dotnet.microsoft.com/download/dotnet/8.0)
- Pre-built `parakeet_runner.exe` (see [Building the Runner](#building-the-runner))

### Building the Runner

Build the `parakeet_runner.exe` from the [ExecuTorch](https://github.com/pytorch/executorch) repo:

```bash
cd executorch
cmake --preset parakeet-tdt-cuda
cmake --build --preset parakeet-tdt-cuda
```

The runner will be at `cmake-out/examples/models/parakeet_tdt/Release/parakeet_runner.exe`.

### Build and Run

```powershell
cd ParakeetApp
dotnet restore
dotnet build --configuration Release
dotnet run --project ParakeetApp --configuration Release
```

Model files will be auto-downloaded on first launch to the `models/` directory next to the executable. To download them manually instead:

```powershell
pip install huggingface_hub
huggingface-cli download younghan-meta/Parakeet-TDT-ExecuTorch-CUDA-Windows-Quantized --local-dir models
```

### Publish Standalone Executable

Create a single self-contained exe (no .NET runtime required on target machine):

```powershell
cd ParakeetApp
dotnet publish ParakeetApp --configuration Release --runtime win-x64 --self-contained true /p:PublishSingleFile=true /p:IncludeNativeLibrariesForSelfExtract=true /p:DebugType=none -o publish
```

### Create Installer

Builds an installer that bundles the app and runner. Model weights are downloaded automatically on first launch.

```powershell
# 1. Install Inno Setup (one-time)
winget install JRSoftware.InnoSetup

# 2. Publish the app
cd ParakeetApp
dotnet publish ParakeetApp --configuration Release --runtime win-x64 --self-contained true /p:PublishSingleFile=true /p:IncludeNativeLibrariesForSelfExtract=true /p:DebugType=none -o publish

# 3. Build the installer (set EXECUTORCH_ROOT to your ExecuTorch repo path)
cd ..
$env:EXECUTORCH_ROOT = "C:\path\to\executorch"
& "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe" installer.iss
```

The output `installer-output\ParakeetApp-Setup.exe` includes:
- App executable (self-contained, no .NET runtime needed)
- `parakeet_runner.exe` + CUDA shims
- Start Menu and optional desktop shortcuts
- Clean uninstall via Windows Settings

Model weights (`model.pte`, `tokenizer.model`, `aoti_cuda_blob.ptd`) are **not bundled** — they are downloaded from HuggingFace on first launch, keeping the installer small.

## Architecture

The app uses a record-then-transcribe workflow:

```
Microphone (WASAPI) -> Record 16kHz mono 16-bit PCM WAV -> parakeet_runner.exe --audio_path -> Parse stdout -> WPF UI
```

Unlike the Voxtral Realtime app which streams audio in real-time, Parakeet uses batch transcription:
1. **Record** — WASAPI captures audio, resamples to 16kHz mono, writes WAV file
2. **Transcribe** — Launches `parakeet_runner.exe` with `--audio_path`, captures stdout/stderr
3. **Parse** — Extracts transcribed text and segment timestamps from runner output
4. **Display** — Shows results with timestamps, saves to session history

### Model Files

| File | Description |
|------|-------------|
| `model.pte` | ExecuTorch-compiled Parakeet TDT 0.6B model |
| `tokenizer.model` | SentencePiece tokenizer |
| `aoti_cuda_blob.ptd` | CUDA AOT-compiled kernels |

### Runner CLI

```
parakeet_runner.exe --model_path X --tokenizer_path X --data_path X --audio_path X --timestamps segment
```

Output format:
```
Transcribed text: "The transcribed text appears here"

[0.00 - 2.40] The transcribed
[2.40 - 4.80] text appears here
```
