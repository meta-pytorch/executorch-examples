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
using ParakeetApp.Models;
using ParakeetApp.Services;

namespace ParakeetApp.ViewModels;

public partial class TranscriptStoreViewModel : ObservableObject
{
    private readonly RunnerBridge _runner = new();
    private readonly SettingsViewModel _settings;
    private readonly ModelDownloadService _downloadService = new();
    private AudioRecordService? _audioRecorder;
    private CancellationTokenSource? _downloadCts;
    private CancellationTokenSource? _transcriptionCts;
    private DateTime? _recordingStartTime;
    private string? _currentWavPath;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsRecording))]
    [NotifyPropertyChangedFor(nameof(IsTranscribing))]
    [NotifyPropertyChangedFor(nameof(IsIdle))]
    private TranscriptionState _transcriptionState = TranscriptionState.Idle;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsModelReady))]
    [NotifyPropertyChangedFor(nameof(IsDownloading))]
    private ModelState _modelState = Models.ModelState.Unloaded;

    [ObservableProperty] private string _liveTranscript = "";
    [ObservableProperty] private float _audioLevel;
    [ObservableProperty] private string _statusMessage = "";
    [ObservableProperty] private string? _currentError;
    [ObservableProperty] private HealthCheckResult? _healthResult;
    [ObservableProperty] private ObservableCollection<Session> _sessions = new();
    [ObservableProperty] private Guid? _selectedSessionId;
    [ObservableProperty] private double _downloadProgress;

    public bool IsRecording => TranscriptionState == TranscriptionState.Recording;
    public bool IsTranscribing => TranscriptionState == TranscriptionState.Transcribing;
    public bool IsIdle => TranscriptionState == TranscriptionState.Idle;
    public bool IsModelReady => ModelState == Models.ModelState.Ready;
    public bool IsDownloading => ModelState == Models.ModelState.Downloading;

    public TranscriptStoreViewModel(SettingsViewModel settings)
    {
        _settings = settings;
        LoadSessions();
        WireRunnerEvents();
    }

    private void WireRunnerEvents()
    {
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
                TranscriptionState = TranscriptionState.Idle;
            });
        };
    }

    // MARK: - Model lifecycle

    [RelayCommand]
    public async Task EnsureModelsReady()
    {
        if (ModelState == Models.ModelState.Ready) return;
        if (ModelState == Models.ModelState.Downloading) return;

        RunHealthCheck();

        if (HealthResult?.RunnerAvailable != true)
        {
            CurrentError = "Runner binary not found. Build from source or install the app.";
            return;
        }

        if (HealthResult?.FilesReady != true)
        {
            var modelsDir = SettingsViewModel.ModelsDir;
            var missing = _downloadService.GetMissingFiles(modelsDir);

            if (missing.Count > 0)
            {
                ModelState = Models.ModelState.Downloading;
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
                    ModelState = Models.ModelState.Unloaded;
                    StatusMessage = "Download cancelled";
                    return;
                }
                catch (Exception ex)
                {
                    ModelState = Models.ModelState.Unloaded;
                    CurrentError = $"Download failed: {ex.Message}";
                    return;
                }
                finally
                {
                    _downloadService.ProgressChanged -= OnDownloadProgress;
                }

                _settings.ModelPath = Path.Combine(modelsDir, "model.pte");
                _settings.TokenizerPath = Path.Combine(modelsDir, "tokenizer.model");
                _settings.DataPath = Path.Combine(modelsDir, "aoti_cuda_blob.ptd");

                RunHealthCheck();
                if (HealthResult?.FilesReady != true)
                {
                    ModelState = Models.ModelState.Unloaded;
                    CurrentError = "Download completed but files could not be verified.";
                    return;
                }
            }
        }

        ModelState = Models.ModelState.Ready;
        StatusMessage = "Ready to record";
    }

    // MARK: - Record and Transcribe

    [RelayCommand]
    public async Task StartRecording()
    {
        if (TranscriptionState != TranscriptionState.Idle) return;

        if (ModelState != Models.ModelState.Ready)
        {
            await EnsureModelsReady();
            if (ModelState != Models.ModelState.Ready) return;
        }

        CurrentError = null;
        LiveTranscript = "";

        var tempDir = Path.Combine(PersistenceService.AppDataDir, "recordings");
        Directory.CreateDirectory(tempDir);
        _currentWavPath = Path.Combine(tempDir, $"recording_{DateTime.Now:yyyyMMdd_HHmmss}.wav");

        _audioRecorder = new AudioRecordService();
        _audioRecorder.LevelChanged += level =>
        {
            Application.Current.Dispatcher.BeginInvoke(() =>
            {
                AudioLevel = level;
            });
        };

        try
        {
            _audioRecorder.StartRecording(_currentWavPath);
            _recordingStartTime = DateTime.Now;
            TranscriptionState = TranscriptionState.Recording;
            StatusMessage = "Recording...";
        }
        catch (Exception ex)
        {
            CurrentError = $"Failed to start recording: {ex.Message}";
            _audioRecorder.Dispose();
            _audioRecorder = null;
        }
    }

    [RelayCommand]
    public async Task StopRecordingAndTranscribe()
    {
        if (TranscriptionState != TranscriptionState.Recording) return;

        double audioDuration = _audioRecorder?.StopRecording() ?? 0;
        AudioLevel = 0;
        _audioRecorder?.Dispose();
        _audioRecorder = null;

        if (string.IsNullOrEmpty(_currentWavPath) || !File.Exists(_currentWavPath))
        {
            CurrentError = "Recording file not found.";
            TranscriptionState = TranscriptionState.Idle;
            return;
        }

        if (audioDuration < 0.5)
        {
            StatusMessage = "Recording too short (< 0.5s)";
            TranscriptionState = TranscriptionState.Idle;
            try { File.Delete(_currentWavPath); } catch { }
            return;
        }

        TranscriptionState = TranscriptionState.Transcribing;
        StatusMessage = "Transcribing...";

        _transcriptionCts?.Cancel();
        _transcriptionCts = new CancellationTokenSource();

        try
        {
            var result = await _runner.TranscribeAsync(
                _settings.RunnerPath,
                _settings.ModelPath,
                _settings.TokenizerPath,
                _settings.DataPath,
                _currentWavPath,
                _transcriptionCts.Token);

            LiveTranscript = result.Text;

            var session = new Session
            {
                Date = _recordingStartTime ?? DateTime.Now,
                Transcript = result.Text,
                Duration = result.TranscriptionTimeSeconds,
                AudioDuration = audioDuration,
                Timestamps = result.Timestamps,
                WavPath = _currentWavPath
            };

            Sessions.Insert(0, session);
            SelectedSessionId = session.Id;
            SaveSessions();

            StatusMessage = $"Done ({result.TranscriptionTimeSeconds:F1}s)";
        }
        catch (OperationCanceledException)
        {
            StatusMessage = "Transcription cancelled";
        }
        catch (Exception ex)
        {
            CurrentError = $"Transcription failed: {ex.Message}";
        }
        finally
        {
            TranscriptionState = TranscriptionState.Idle;
            _recordingStartTime = null;
        }
    }

    [RelayCommand]
    public void CancelTranscription()
    {
        _transcriptionCts?.Cancel();
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
            FileName = $"parakeet-{session.Date:yyyy-MM-ddTHH-mm-ss}",
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
                audioDuration = session.AudioDuration,
                transcriptionTime = session.Duration,
                timestamps = session.Timestamps
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
            _settings.DataPath);
    }

    [RelayCommand]
    public void CancelDownload()
    {
        _downloadCts?.Cancel();
    }

    // MARK: - Private

    private void OnDownloadProgress(DownloadProgress p)
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

    private static string FormatSrt(Session session)
    {
        if (session.Timestamps.Count == 0)
        {
            var duration = TimeSpan.FromSeconds(session.AudioDuration);
            return $"1\n00:00:00,000 --> {duration:hh\\:mm\\:ss\\,fff}\n{session.Transcript}\n";
        }

        var sb = new System.Text.StringBuilder();
        for (int i = 0; i < session.Timestamps.Count; i++)
        {
            var ts = session.Timestamps[i];
            var start = TimeSpan.FromSeconds(ts.Start);
            var end = TimeSpan.FromSeconds(ts.End);
            sb.AppendLine($"{i + 1}");
            sb.AppendLine($"{start:hh\\:mm\\:ss\\,fff} --> {end:hh\\:mm\\:ss\\,fff}");
            sb.AppendLine(ts.Text);
            sb.AppendLine();
        }
        return sb.ToString();
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
        _audioRecorder?.Dispose();
        _downloadCts?.Cancel();
        _transcriptionCts?.Cancel();
    }
}
