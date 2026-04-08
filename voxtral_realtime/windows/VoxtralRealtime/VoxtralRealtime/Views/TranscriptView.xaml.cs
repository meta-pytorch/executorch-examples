// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using VoxtralRealtime.Models;

namespace VoxtralRealtime.Views;

public partial class TranscriptView : UserControl
{
    private bool _isLive;
    private Session? _session;

    public TranscriptView()
    {
        InitializeComponent();
        App.Store.PropertyChanged += OnStorePropertyChanged;
    }

    public void ShowLive()
    {
        _isLive = true;
        _session = null;
        UpdateDisplay();
    }

    public void ShowSession(Session session)
    {
        _isLive = false;
        _session = session;
        TranscriptText.Text = session.Transcript;
        ListeningPlaceholder.Visibility = Visibility.Collapsed;
        StatusCapsule.Visibility = Visibility.Collapsed;
        CopyBtn.Visibility = string.IsNullOrEmpty(session.Transcript)
            ? Visibility.Collapsed : Visibility.Visible;
    }

    private void OnStorePropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (!_isLive) return;

        if (e.PropertyName == nameof(App.Store.LiveTranscript))
            Dispatcher.BeginInvoke(UpdateDisplay);
        else if (e.PropertyName == nameof(App.Store.AudioLevel))
            Dispatcher.BeginInvoke(() =>
            {
                PlaceholderLevel.Level = App.Store.AudioLevel;
                StatusLevel.Level = App.Store.AudioLevel;
            });
        else if (e.PropertyName is nameof(App.Store.SessionState) or nameof(App.Store.StatusMessage))
            Dispatcher.BeginInvoke(UpdateDisplay);
    }

    private void UpdateDisplay()
    {
        if (!_isLive) return;

        var text = App.Store.LiveTranscript;
        TranscriptText.Text = text;

        bool isEmpty = string.IsNullOrEmpty(text);
        bool isTranscribing = App.Store.IsTranscribing;
        bool isPaused = App.Store.IsPaused;
        bool hasSession = App.Store.HasActiveSession;

        // Listening placeholder
        ListeningPlaceholder.Visibility = isEmpty && isTranscribing
            ? Visibility.Visible : Visibility.Collapsed;

        // Copy button
        CopyBtn.Visibility = !isEmpty ? Visibility.Visible : Visibility.Collapsed;

        // Status capsule at bottom
        StatusCapsule.Visibility = (isTranscribing || isPaused)
            ? Visibility.Visible : Visibility.Collapsed;

        if (isPaused)
        {
            PausedIcon.Visibility = Visibility.Visible;
            StatusLevel.Visibility = Visibility.Collapsed;
            StatusText.Text = "Paused";
        }
        else if (isTranscribing)
        {
            PausedIcon.Visibility = Visibility.Collapsed;
            StatusLevel.Visibility = Visibility.Visible;
            StatusText.Text = "Transcribing";
        }

        if (isTranscribing && !isEmpty)
            TranscriptScroller.ScrollToEnd();
    }

    private void OnCopyAll(object sender, RoutedEventArgs e)
    {
        var text = _isLive ? App.Store.LiveTranscript : _session?.Transcript;
        if (!string.IsNullOrEmpty(text))
            Clipboard.SetText(text);
    }
}
