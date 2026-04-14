// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using VoxtralRealtime.Models;

namespace VoxtralRealtime.Views;

public partial class RecordingControlsBar : UserControl
{
    public RecordingControlsBar()
    {
        InitializeComponent();
        App.Store.PropertyChanged += OnStoreChanged;
        Loaded += (_, _) => UpdateState();
    }

    private void OnStoreChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(App.Store.SessionState) or
            nameof(App.Store.ModelState))
        {
            Dispatcher.BeginInvoke(UpdateState);
        }
    }

    private void UpdateState()
    {
        var state = App.Store.SessionState;
        var modelState = App.Store.ModelState;
        bool hasSession = App.Store.HasActiveSession;

        // Hide controls during download
        if (modelState == ModelState.Downloading)
        {
            LoadingIndicator.Visibility = Visibility.Collapsed;
            MainBtn.Visibility = Visibility.Collapsed;
            DoneBtn.Visibility = Visibility.Collapsed;
            UnloadBtn.Visibility = Visibility.Collapsed;
            return;
        }

        // Main button state
        LoadingIndicator.Visibility = state == SessionState.Loading
            ? Visibility.Visible : Visibility.Collapsed;
        MainBtn.Visibility = state != SessionState.Loading
            ? Visibility.Visible : Visibility.Collapsed;

        switch (state)
        {
            case SessionState.Idle:
                MainIcon.Text = "\U0001F3A4";
                MainLabel.Text = "Transcribe";
                MainIcon.Foreground = new SolidColorBrush(Color.FromRgb(0x1C, 0x1C, 0x1E));
                break;
            case SessionState.Transcribing:
                MainIcon.Text = "\u23F8";
                MainLabel.Text = "Pause";
                MainIcon.Foreground = new SolidColorBrush(Color.FromRgb(0xFF, 0x95, 0x00));
                break;
            case SessionState.Paused:
                MainIcon.Text = "\u25B6";
                MainLabel.Text = "Resume";
                MainIcon.Foreground = new SolidColorBrush(Color.FromRgb(0x1C, 0x1C, 0x1E));
                break;
        }

        // Done button
        DoneBtn.Visibility = hasSession ? Visibility.Visible : Visibility.Collapsed;

        // Unload button
        UnloadBtn.Visibility = modelState == ModelState.Ready && !hasSession
            ? Visibility.Visible : Visibility.Collapsed;
    }

    private async void OnMainAction(object sender, RoutedEventArgs e)
    {
        switch (App.Store.SessionState)
        {
            case SessionState.Idle:
                await App.Store.StartTranscription();
                break;
            case SessionState.Transcribing:
                App.Store.PauseTranscription();
                break;
            case SessionState.Paused:
                await App.Store.ResumeTranscription();
                break;
        }
    }

    private void OnDone(object sender, RoutedEventArgs e) => App.Store.EndSession();
    private void OnUnload(object sender, RoutedEventArgs e) => App.Store.UnloadModel();
}
