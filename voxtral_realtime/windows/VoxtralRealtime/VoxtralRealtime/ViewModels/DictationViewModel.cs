// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using VoxtralRealtime.Models;
using VoxtralRealtime.Services;

namespace VoxtralRealtime.ViewModels;

public partial class DictationViewModel : ObservableObject
{
    [ObservableProperty] private DictationState _state = DictationState.Idle;

    private readonly TranscriptStoreViewModel _store;
    private readonly SettingsViewModel _settings;
    private readonly GlobalHotkeyService _hotkeyService;
    private CancellationTokenSource? _silenceMonitorCts;
    private IntPtr _targetWindowHandle;
    private DateTime _dictationStartTime;
    private volatile float _peakAudioLevel; // peak level since last poll

    public TranscriptStoreViewModel Store => _store;

    public event Action? DictationStarted;
    public event Action? DictationStopped;

    public DictationViewModel(
        TranscriptStoreViewModel store,
        SettingsViewModel settings,
        GlobalHotkeyService hotkeyService)
    {
        _store = store;
        _settings = settings;
        _hotkeyService = hotkeyService;
        _hotkeyService.HotkeyPressed += OnHotkeyPressed;
    }

    private void OnHotkeyPressed()
    {
        Application.Current.Dispatcher.BeginInvoke(Toggle);
    }

    public async void Toggle()
    {
        if (State == DictationState.Idle)
        {
            await StartListening();
        }
        else if (State == DictationState.Listening)
        {
            await StopAndPaste();
        }
    }

    private async Task StartListening()
    {
        _targetWindowHandle = ClipboardPasteService.CaptureTargetWindow();
        _dictationStartTime = DateTime.Now;
        _peakAudioLevel = 0;

        // Subscribe to raw audio level (fires on audio thread, no dispatch delay)
        _store.RawAudioLevelChanged += OnRawAudioLevel;

        State = DictationState.Loading;
        DictationStarted?.Invoke();

        await _store.StartDictation();

        if (!_store.IsDictating)
        {
            _store.RawAudioLevelChanged -= OnRawAudioLevel;
            State = DictationState.Idle;
            DictationStopped?.Invoke();
            return;
        }

        State = DictationState.Listening;
        StartSilenceMonitor();
    }

    private void OnRawAudioLevel(float level)
    {
        // Track peak level - the monitor resets this each poll.
        // This ensures we don't miss speech between polls.
        if (level > _peakAudioLevel)
            _peakAudioLevel = level;
    }

    private async Task StopAndPaste()
    {
        _silenceMonitorCts?.Cancel();
        _store.RawAudioLevelChanged -= OnRawAudioLevel;
        State = DictationState.Processing;

        var rawText = _store.StopDictation();
        var duration = (DateTime.Now - _dictationStartTime).TotalSeconds;

        DictationStopped?.Invoke();

        if (!string.IsNullOrWhiteSpace(rawText))
        {
            var processed = _store.ProcessDictationText(rawText);
            _store.SaveDictationSession(processed, duration);

            await Task.Run(() =>
            {
                ClipboardPasteService.CopyAndPaste(processed.OutputText, _targetWindowHandle);
            });
        }

        State = DictationState.Idle;
    }

    private void StartSilenceMonitor()
    {
        _silenceMonitorCts?.Cancel();
        _silenceMonitorCts = new CancellationTokenSource();
        var ct = _silenceMonitorCts.Token;

        Task.Run(async () =>
        {
            // Track whether we've seen any speech at all. Don't auto-stop
            // until the user has actually spoken something.
            bool heardSpeech = false;
            int consecutiveSilentPolls = 0;
            int pollIntervalMs = 250;

            // Use a longer timeout for dictation: 3 seconds of continuous
            // silence after speech ends. Brief pauses between words/phrases
            // (typically < 1s) won't trigger this.
            double silenceTimeout = Math.Max(_settings.SilenceTimeoutSeconds, 2.0);
            int requiredSilentPolls = (int)(silenceTimeout * 1000 / pollIntervalMs);

            // Don't start monitoring until at least 2 seconds into dictation
            // to avoid false triggers from mic initialization noise.
            await Task.Delay(2000, ct);

            while (!ct.IsCancellationRequested)
            {
                await Task.Delay(pollIntervalMs, ct);

                // Read peak level since last poll, then reset
                float peak = _peakAudioLevel;
                _peakAudioLevel = 0;

                float threshold = (float)_settings.SilenceThreshold;
                bool isSilent = peak < threshold;

                if (consecutiveSilentPolls % 8 == 0)
                {
                    AppLogger.Log("Dictation",
                        $"peak={peak:F4} threshold={threshold:F4} silent={isSilent} heardSpeech={heardSpeech} silentPolls={consecutiveSilentPolls}/{requiredSilentPolls}");
                }

                if (!isSilent)
                {
                    heardSpeech = true;
                    consecutiveSilentPolls = 0;
                }
                else
                {
                    consecutiveSilentPolls++;

                    // Only auto-stop if: we heard speech, then silence for
                    // the full timeout, and there's text to paste.
                    if (heardSpeech &&
                        consecutiveSilentPolls >= requiredSilentPolls &&
                        !string.IsNullOrEmpty(_store.DictationText))
                    {
                        AppLogger.Log("Dictation",
                            $"Auto-stop: {silenceTimeout}s silence after speech");
                        _ = Application.Current.Dispatcher.BeginInvoke(() => StopAndPaste());
                        break;
                    }
                }
            }
        }, ct);
    }

    public void RegisterHotkey(Window window)
    {
        _hotkeyService.Register(window);
    }

    public void Cleanup()
    {
        _silenceMonitorCts?.Cancel();
        _hotkeyService.Dispose();
    }
}
