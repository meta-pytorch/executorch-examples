# Add Voxtral Realtime Windows WPF Application

## Summary

Adds a Windows WPF (.NET 8) desktop application for Voxtral Realtime — a real-time speech-to-text transcription tool powered by ExecuTorch. This is the Windows counterpart to the existing macOS SwiftUI application.

## Key Features

- **Real-time transcription** — Live speech-to-text using the Voxtral model running locally via ExecuTorch
- **Dictation mode** — Speak and auto-paste transcribed text into any target application via clipboard
- **Silence detection** — Peak-based audio level monitoring with configurable silence threshold and timeout for auto-stop
- **Text processing pipeline** — Post-processing with configurable text replacements and snippet expansion
- **Session history** — Persistent transcript storage with search and browsing
- **Global hotkey** — System-wide keyboard shortcut to start/stop transcription
- **MVVM architecture** — Clean separation using `CommunityToolkit.Mvvm` with `ObservableObject` view models

## Architecture

```
VoxtralRealtime/
├── Models/           # Data models (Session, Snippet, ReplacementEntry, Enums)
├── Converters/       # WPF value converters
├── Services/         # Core services
│   ├── RunnerBridge.cs         # ExecuTorch model bridge
│   ├── AudioCaptureService.cs  # NAudio-based mic capture
│   ├── ClipboardPasteService.cs # Win32 clipboard + paste
│   ├── GlobalHotkeyService.cs  # System-wide hotkey registration
│   ├── TextPipeline.cs         # Post-processing pipeline
│   ├── PersistenceService.cs   # JSON file storage
│   └── AppLogger.cs            # File-based logging
├── ViewModels/       # MVVM view models
│   ├── TranscriptStoreViewModel.cs  # Central state management
│   ├── DictationViewModel.cs        # Dictation flow + silence monitor
│   ├── SettingsViewModel.cs         # App configuration
│   ├── ReplacementStoreViewModel.cs # Text replacements
│   └── SnippetStoreViewModel.cs     # Text snippets
├── Views/            # WPF XAML views
│   ├── MainWindow              # Shell with sidebar + detail layout
│   ├── WelcomeView             # Home/landing page
│   ├── TranscriptView          # Live transcript display
│   ├── SidebarView             # Navigation sidebar
│   ├── RecordingControlsBar    # Toolbar (transcribe/pause/done)
│   ├── DictationWindow         # Floating dictation overlay
│   ├── AudioLevelControl       # Real-time audio level meter
│   ├── SettingsView            # Configuration UI
│   └── *ManagementViews        # Replacement & snippet editors
└── Resources/        # Styles and assets
```

## Also Included

- `.gitignore` updated with .NET/C# build artifact rules (`bin/`, `obj/`, `publish/`, etc.)
- `build.bat` for release builds
- `upload_models.py` helper for model distribution
- `README.md` with setup and build instructions

## Test Plan

- Built and tested manually on Windows 10/11 with .NET 8
- Verified real-time transcription, dictation auto-paste, silence detection, and session persistence
