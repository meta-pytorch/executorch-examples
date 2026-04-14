// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using ParakeetApp.Models;

namespace ParakeetApp.Views;

public partial class RecordingBar : UserControl
{
    public RecordingBar()
    {
        InitializeComponent();
        App.Store.PropertyChanged += OnStoreChanged;
        Loaded += (_, _) => UpdateState();
    }

    private void OnStoreChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(App.Store.TranscriptionState) or
            nameof(App.Store.ModelState) or
            nameof(App.Store.AudioLevel))
        {
            Dispatcher.BeginInvoke(UpdateState);
        }
    }

    private void UpdateState()
    {
        var state = App.Store.TranscriptionState;
        var modelState = App.Store.ModelState;

        if (modelState == ModelState.Downloading)
        {
            RecordBtn.Visibility = Visibility.Collapsed;
            TranscribingIndicator.Visibility = Visibility.Collapsed;
            AudioLevel.Visibility = Visibility.Collapsed;
            StopBtn.Visibility = Visibility.Collapsed;
            return;
        }

        switch (state)
        {
            case TranscriptionState.Idle:
                RecordBtn.Visibility = modelState == ModelState.Ready
                    ? Visibility.Visible : Visibility.Collapsed;
                RecordIcon.Text = "\u23FA";
                RecordIcon.Foreground = new SolidColorBrush(Color.FromRgb(0xFF, 0x3B, 0x30));
                RecordLabel.Text = "Record";
                TranscribingIndicator.Visibility = Visibility.Collapsed;
                AudioLevel.Visibility = Visibility.Collapsed;
                StopBtn.Visibility = Visibility.Collapsed;
                break;

            case TranscriptionState.Recording:
                RecordBtn.Visibility = Visibility.Collapsed;
                TranscribingIndicator.Visibility = Visibility.Collapsed;
                AudioLevel.Visibility = Visibility.Visible;
                AudioLevel.Level = App.Store.AudioLevel;
                StopBtn.Visibility = Visibility.Visible;
                break;

            case TranscriptionState.Transcribing:
                RecordBtn.Visibility = Visibility.Collapsed;
                TranscribingIndicator.Visibility = Visibility.Visible;
                TranscribingLabel.Text = string.IsNullOrEmpty(App.Store.StatusMessage)
                    ? "Transcribing..." : App.Store.StatusMessage;
                AudioLevel.Visibility = Visibility.Collapsed;
                StopBtn.Visibility = Visibility.Collapsed;
                break;
        }
    }

    private async void OnRecordAction(object sender, RoutedEventArgs e)
    {
        await App.Store.StartRecording();
    }

    private async void OnStop(object sender, RoutedEventArgs e)
    {
        await App.Store.StopRecordingAndTranscribe();
    }
}
