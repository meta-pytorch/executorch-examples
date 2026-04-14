// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Collections.ObjectModel;
using System.IO;
using System.Text.Json;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Win32;
using VoxtralRealtime.Models;
using VoxtralRealtime.Services;

namespace VoxtralRealtime.ViewModels;

public partial class TranscriptStoreViewModel : ObservableObject
{
    private readonly RunnerBridge _runner = new();
    private readonly SettingsViewModel _settings;
    private readonly TextPipeline _textPipeline;
    private readonly ModelDownloadService _downloadService = new();
    private CancellationTokenSource? _downloadCts;
    private DateTime? _startDate;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasActiveSession))]
    [NotifyPropertyChangedFor(nameof(IsTranscribing))]
    [NotifyPropertyChangedFor(nameof(IsPaused))]
    [NotifyPropertyChangedFor(nameof(IsLoading))]
    private SessionState _sessionState = SessionState.Idle;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsModelReady))]
    [NotifyPropertyChangedFor(nameof(IsDownloading))]
    private ModelState _modelState = ModelState.Unloaded;

    [ObservableProperty] private string _liveTranscript = "";
    [ObservableProperty] private float _audioLevel;
    [ObservableProperty] private string _statusMessage = "";
    [ObservableProperty] private string? _currentError;
    [ObservableProperty] private HealthCheckResult? _healthResult;
    [ObservableProperty] private ObservableCollection<Session> _sessions = new();
    [ObservableProperty] private Guid? _selectedSessionId;
    [ObservableProperty] private string _dictationText = "";
    [ObservableProperty] private bool _isDictating;
    [ObservableProperty] private double _downloadProgress;

    public bool HasActiveSession => SessionState != SessionState.Idle;
    public bool IsTranscribing => SessionState == SessionState.Transcribing;
    public bool IsPaused => SessionState == SessionState.Paused;
    public bool IsLoading => SessionState == SessionState.Loading;
    public bool IsModelReady => ModelState == ModelState.Ready;
    public bool IsDownloading => ModelState == ModelState.Downloading;

    // Raw audio level event - fires on audio thread without dispatch delay.
    // Used by DictationViewModel for accurate silence detection.
    public event Action<float>? RawAudioLevelChanged;

    public TranscriptStoreViewModel(SettingsViewModel settings, TextPipeline textPipeline)
    {
        _settings = settings;
        _textPipeline = textPipeline;
        LoadSessions();
        WireRunnerEvents();
    }

    private void WireRunnerEvents()
    {
        _runner.TokenReceived += token =>
        {
            Application.Current.Dispatcher.BeginInvoke(() =>
            {
                if (IsDictating)
                    DictationText += token;
                else if (HasActiveSession)
                    LiveTranscript += token;
            });
        };

        _runner.StatusReceived += status =>
        {
            Application.Current.Dispatcher.BeginInvoke(() =>
            {
                StatusMessage = status;
            });
        };

        _runner.ErrorOccurred += error =>
        {
            Application.Current.Dispatcher.BeginInvoke(() =>
            {
                CurrentError = error;
                if (IsTranscribing)
                    PauseTranscription();
            });
        };

        _runner.ModelStateChanged += state =>
        {
            Application.Current.Dispatcher.BeginInvoke(() =>
            {
                ModelState = state;
                if (state == ModelState.Ready &&
                    (StatusMessage.Contains("Loading") || StatusMessage == "Warming up..."))
                {
                    StatusMessage = "Model ready";
                }
            });
        };

        _runner.AudioLevelChanged += level =>
        {
            // Fire raw event immediately on audio thread (for silence detection)
            RawAudioLevelChanged?.Invoke(level);

            // Then dispatch to UI thread for visual updates
            Application.Current.Dispatcher.BeginInvoke(() =>
            {
                AudioLevel = level;
            });
        };
    }

    // MARK: - Model lifecycle

    [RelayCommand]
    public async Task PreloadModel()
    {
        if (ModelState != ModelState.Unloaded) return;
        await EnsureRunnerLaunched();
    }

    [RelayCommand]
    public void UnloadModel()
    {
        CancelDownload();
        if (HasActiveSession) EndSession();
        _runner.Stop();
        ModelState = ModelState.Unloaded;
        StatusMessage = "";
    }

    // MARK: - Transcription

    [RelayCommand]
    public async Task StartTranscription()
    {
        if (SessionState != SessionState.Idle) return;

        if (ModelState != ModelState.Ready)
        {
            await EnsureRunnerLaunched();
            SessionState = SessionState.Loading;
            while (ModelState == ModelState.Loading)
            {
                await Task.Delay(100);
            }
            if (ModelState != ModelState.Ready)
            {
                SessionState = SessionState.Idle;
                return;
            }
        }

        SessionState = SessionState.Transcribing;
        LiveTranscript = "";
        _startDate = DateTime.Now;
        CurrentError = null;
        StatusMessage = "Starting microphone...";

        try
        {
            await Task.Run(() => _runner.StartAudioCapture());
            StatusMessage = "Transcribing";
        }
        catch (Exception ex)
        {
            CurrentError = ex.Message;
            SessionState = SessionState.Idle;
        }
    }

    [RelayCommand]
    public async Task ResumeTranscription()
    {
        if (SessionState != SessionState.Paused) return;

        if (ModelState != ModelState.Ready)
        {
            await EnsureRunnerLaunched();
            while (ModelState == ModelState.Loading)
            {
                await Task.Delay(100);
            }
            if (ModelState != ModelState.Ready) return;
        }

        SessionState = SessionState.Transcribing;
        StatusMessage = "Starting microphone...";

        try
        {
            await Task.Run(() => _runner.StartAudioCapture());
            StatusMessage = "Transcribing";
        }
        catch (Exception ex)
        {
            CurrentError = ex.Message;
            SessionState = SessionState.Paused;
        }
    }

    [RelayCommand]
    public void PauseTranscription()
    {
        if (SessionState != SessionState.Transcribing && SessionState != SessionState.Loading) return;
        _runner.StopAudioCapture();
        AudioLevel = 0;
        StatusMessage = "Paused";
        SessionState = SessionState.Paused;
    }

    [RelayCommand]
    public void EndSession()
    {
        if (SessionState == SessionState.Transcribing)
        {
            _runner.StopAudioCapture();
        }

        var duration = _startDate.HasValue
            ? (DateTime.Now - _startDate.Value).TotalSeconds
            : 0;

        if (!string.IsNullOrEmpty(LiveTranscript))
        {
            var processed = _textPipeline.Process(LiveTranscript, TextContext.SessionSave);
            var session = new Session
            {
                Date = _startDate ?? DateTime.Now,
                Transcript = processed.OutputText,
                Duration = duration,
                Source = SessionSource.Transcription,
                RawTranscript = processed.Transformed ? processed.RawText : null,
                Tags = processed.Tags,
                UsedSnippetIds = processed.UsedSnippetIds
            };
            Sessions.Insert(0, session);
            SelectedSessionId = session.Id;
            SaveSessions();
        }

        LiveTranscript = "";
        AudioLevel = 0;
        StatusMessage = IsModelReady ? "Model ready" : "";
        SessionState = SessionState.Idle;
        _startDate = null;
    }

    [RelayCommand]
    public void TogglePauseResume()
    {
        switch (SessionState)
        {
            case SessionState.Idle:
                _ = StartTranscription();
                break;
            case SessionState.Loading:
            case SessionState.Transcribing:
                PauseTranscription();
                break;
            case SessionState.Paused:
                _ = ResumeTranscription();
                break;
        }
    }

    // MARK: - Session management

    public void DeleteSession(Session session)
    {
        var item = Sessions.FirstOrDefault(s => s.Id == session.Id);
        if (item != null)
        {
            Sessions.Remove(item);
            if (SelectedSessionId == session.Id)
                SelectedSessionId = Sessions.FirstOrDefault()?.Id;
            SaveSessions();
        }
    }

    public void RenameSession(Session session, string newTitle)
    {
        var item = Sessions.FirstOrDefault(s => s.Id == session.Id);
        if (item != null)
        {
            item.Title = newTitle;
            SaveSessions();
            OnPropertyChanged(nameof(Sessions));
        }
    }

    public void TogglePinned(Session session)
    {
        var item = Sessions.FirstOrDefault(s => s.Id == session.Id);
        if (item != null)
        {
            item.Pinned = !item.Pinned;
            SaveSessions();
            OnPropertyChanged(nameof(Sessions));
        }
    }

    public void ExportSession(Session session, SessionExportFormat format)
    {
        var dialog = new SaveFileDialog
        {
            FileName = $"voxtral-{session.Source.ToString().ToLower()}-{session.Date:yyyy-MM-ddTHH-mm-ss}",
            Filter = format switch
            {
                SessionExportFormat.Txt => "Text files (*.txt)|*.txt",
                SessionExportFormat.Json => "JSON files (*.json)|*.json",
                SessionExportFormat.Srt => "SRT files (*.srt)|*.srt",
                _ => "All files (*.*)|*.*"
            }
        };

        if (dialog.ShowDialog() != true) return;

        var content = format switch
        {
            SessionExportFormat.Txt => session.Transcript,
            SessionExportFormat.Json => JsonSerializer.Serialize(new
            {
                id = session.Id,
                date = session.Date.ToString("o"),
                transcript = session.Transcript,
                duration = session.Duration,
                source = session.Source.ToString().ToLower()
            }, new JsonSerializerOptions { WriteIndented = true }),
            SessionExportFormat.Srt => FormatSrt(session),
            _ => session.Transcript
        };

        File.WriteAllText(dialog.FileName, content);
    }

    public Session? GetSelectedSession()
    {
        return SelectedSessionId.HasValue
            ? Sessions.FirstOrDefault(s => s.Id == SelectedSessionId.Value)
            : null;
    }

    // MARK: - Dictation support

    public async Task StartDictation(float[]? initialSamples = null)
    {
        if (IsDictating) return;

        if (ModelState != ModelState.Ready)
        {
            await EnsureRunnerLaunched();
            while (ModelState == ModelState.Loading)
            {
                await Task.Delay(100);
            }
            if (ModelState != ModelState.Ready) return;
        }

        IsDictating = true;
        DictationText = "";
        AudioLevel = 0;

        try
        {
            if (initialSamples != null && initialSamples.Length > 0)
            {
                _runner.PrimeAudioSamples(initialSamples);
            }
            await Task.Run(() => _runner.StartAudioCapture());
        }
        catch
        {
            IsDictating = false;
        }
    }

    public string StopDictation()
    {
        if (!IsDictating) return "";
        _runner.StopAudioCapture();
        IsDictating = false;
        AudioLevel = 0;
        var result = DictationText;
        DictationText = "";
        return result;
    }

    public void SaveDictationSession(TextProcessingResult result, double duration)
    {
        // Dictation results are pasted directly, not saved to history
    }

    public TextProcessingResult ProcessDictationText(string rawText)
    {
        return _textPipeline.Process(rawText, TextContext.Dictation);
    }

    [RelayCommand]
    public void ClearError()
    {
        CurrentError = null;
    }

    // MARK: - Health check

    public void RunHealthCheck()
    {
        HealthResult = HealthCheckService.Run(
            _settings.RunnerPath,
            _settings.ModelPath,
            _settings.TokenizerPath,
            _settings.PreprocessorPath,
            _settings.DataPath);
    }

    // MARK: - Private

    private async Task EnsureRunnerLaunched()
    {
        if (_runner.IsRunnerAlive) return;

        RunHealthCheck();

        // Runner binary must exist — it can't be downloaded
        if (HealthResult?.RunnerAvailable != true)
        {
            CurrentError = "Runner binary not found. Build from source or install the app.";
            return;
        }

        // If model files are missing, attempt download
        if (HealthResult?.FilesReady != true)
        {
            var modelsDir = SettingsViewModel.ModelsDir;
            var missing = _downloadService.GetMissingFiles(modelsDir);

            if (missing.Count > 0)
            {
                ModelState = ModelState.Downloading;
                DownloadProgress = 0;
                StatusMessage = "Preparing download...";
                CurrentError = null;

                _downloadCts?.Cancel();
                _downloadCts = new CancellationTokenSource();
                var ct = _downloadCts.Token;

                _downloadService.ProgressChanged += OnDownloadProgress;
                try
                {
                    await _downloadService.DownloadAllAsync(modelsDir, ct);
                }
                catch (OperationCanceledException)
                {
                    ModelState = ModelState.Unloaded;
                    StatusMessage = "Download cancelled";
                    return;
                }
                catch (Exception ex)
                {
                    ModelState = ModelState.Unloaded;
                    CurrentError = $"Download failed: {ex.Message}";
                    return;
                }
                finally
                {
                    _downloadService.ProgressChanged -= OnDownloadProgress;
                }

                // Update settings to point to downloaded files
                _settings.ModelPath = Path.Combine(modelsDir, "model.pte");
                _settings.PreprocessorPath = Path.Combine(modelsDir, "preprocessor.pte");
                _settings.DataPath = Path.Combine(modelsDir, "aoti_cuda_blob.ptd");
                _settings.TokenizerPath = Path.Combine(modelsDir, "tekken.json");

                // Re-run health check with updated paths
                RunHealthCheck();
                if (HealthResult?.FilesReady != true)
                {
                    ModelState = ModelState.Unloaded;
                    CurrentError = "Download completed but files could not be verified.";
                    return;
                }
            }
        }

        _runner.LaunchRunner(
            _settings.RunnerPath,
            _settings.ModelPath,
            _settings.TokenizerPath,
            _settings.PreprocessorPath,
            _settings.DataPath);

        // Set ModelState synchronously so callers' while loops work correctly.
        // The runner also fires ModelStateChanged(Loading) via BeginInvoke,
        // but that hasn't been processed yet when this method returns.
        ModelState = ModelState.Loading;
    }

    private void OnDownloadProgress(Services.DownloadProgress p)
    {
        Application.Current.Dispatcher.BeginInvoke(() =>
        {
            var pct = p.TotalBytes > 0
                ? (double)p.BytesDownloaded / p.TotalBytes * 100
                : 0;
            DownloadProgress = pct;
            StatusMessage = $"Downloading {p.FileName} ({ModelDownloadService.FormatBytes(p.BytesDownloaded)} / {ModelDownloadService.FormatBytes(p.TotalBytes)}) — file {p.FileIndex}/{p.TotalFiles}";
        });
    }

    [RelayCommand]
    public void CancelDownload()
    {
        _downloadCts?.Cancel();
    }

    private static string FormatSrt(Session session)
    {
        var duration = TimeSpan.FromSeconds(session.Duration);
        return $"1\n00:00:00,000 --> {duration:hh\\:mm\\:ss\\,fff}\n{session.Transcript}\n";
    }

    private void SaveSessions()
    {
        PersistenceService.Save(PersistenceService.SessionsPath, Sessions.ToList());
    }

    private void LoadSessions()
    {
        var data = PersistenceService.Load<List<Session>>(PersistenceService.SessionsPath);
        if (data != null)
        {
            Sessions = new ObservableCollection<Session>(data);
        }
    }

    public void Shutdown()
    {
        _runner.Dispose();
    }
}
