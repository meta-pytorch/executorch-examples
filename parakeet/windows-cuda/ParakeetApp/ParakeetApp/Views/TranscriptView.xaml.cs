// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using ParakeetApp.Models;

namespace ParakeetApp.Views;

public partial class TranscriptView : UserControl
{
    private bool _isTranscribing;
    private Session? _session;

    public TranscriptView()
    {
        InitializeComponent();
        App.Store.PropertyChanged += OnStorePropertyChanged;
    }

    public void ShowTranscribing()
    {
        _isTranscribing = true;
        _session = null;
        TranscribingPlaceholder.Visibility = Visibility.Visible;
        SessionHeader.Visibility = Visibility.Collapsed;
        TranscriptText.Text = "";
        TimestampsSection.Visibility = Visibility.Collapsed;
        CopyBtn.Visibility = Visibility.Collapsed;
        UpdateTranscribingStatus();
    }

    public void ShowSession(Session session)
    {
        _isTranscribing = false;
        _session = session;

        TranscribingPlaceholder.Visibility = Visibility.Collapsed;
        SessionHeader.Visibility = Visibility.Visible;
        SessionTitle.Text = session.DisplayTitle;
        SessionMeta.Text = $"{session.Date:MMM d, yyyy h:mm tt}  •  Audio: {session.FormattedDuration}  •  Transcribed in {session.Duration:F1}s";

        TranscriptText.Text = session.Transcript;
        CopyBtn.Visibility = string.IsNullOrEmpty(session.Transcript)
            ? Visibility.Collapsed : Visibility.Visible;

        if (session.Timestamps.Count > 0)
        {
            TimestampsSection.Visibility = Visibility.Visible;
            TimestampsList.ItemsSource = session.Timestamps;
        }
        else
        {
            TimestampsSection.Visibility = Visibility.Collapsed;
        }
    }

    private void OnStorePropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (!_isTranscribing) return;

        if (e.PropertyName is nameof(App.Store.StatusMessage) or
            nameof(App.Store.TranscriptionState) or
            nameof(App.Store.LiveTranscript))
        {
            Dispatcher.BeginInvoke(UpdateTranscribingStatus);
        }
    }

    private void UpdateTranscribingStatus()
    {
        if (!_isTranscribing) return;

        TranscribingStatusText.Text = string.IsNullOrEmpty(App.Store.StatusMessage)
            ? "Transcribing..." : App.Store.StatusMessage;

        if (!App.Store.IsTranscribing && !string.IsNullOrEmpty(App.Store.LiveTranscript))
        {
            TranscribingPlaceholder.Visibility = Visibility.Collapsed;
            TranscriptText.Text = App.Store.LiveTranscript;
            CopyBtn.Visibility = Visibility.Visible;
        }
    }

    private void OnCopyAll(object sender, RoutedEventArgs e)
    {
        var text = _session?.Transcript ?? App.Store.LiveTranscript;
        if (!string.IsNullOrEmpty(text))
            Clipboard.SetText(text);
    }

    private void OnCancelTranscription(object sender, RoutedEventArgs e)
    {
        App.Store.CancelTranscription();
    }
}
