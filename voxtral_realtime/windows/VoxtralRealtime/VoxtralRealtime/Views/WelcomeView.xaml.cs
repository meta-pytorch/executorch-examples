// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using VoxtralRealtime.Models;

namespace VoxtralRealtime.Views;

public partial class WelcomeView : UserControl
{
    public WelcomeView()
    {
        InitializeComponent();
        DataContext = App.Store;
        App.Store.PropertyChanged += OnStoreChanged;
        Loaded += (_, _) => UpdateModelState();

        // Show static waveform bars on the icon
        WaveIcon.Level = 0.3f;
    }

    private void OnStoreChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(App.Store.ModelState) or
            nameof(App.Store.StatusMessage) or
            nameof(App.Store.SessionState))
        {
            Dispatcher.BeginInvoke(UpdateModelState);
        }
    }

    private void UpdateModelState()
    {
        var modelState = App.Store.ModelState;

        // Model section visibility
        LoadModelBtn.Visibility = modelState == ModelState.Unloaded
            ? Visibility.Visible : Visibility.Collapsed;
        LoadingPanel.Visibility = modelState == ModelState.Loading
            ? Visibility.Visible : Visibility.Collapsed;
        ReadyPanel.Visibility = modelState == ModelState.Ready
            ? Visibility.Visible : Visibility.Collapsed;

        // Start button only when ready
        StartBtn.Visibility = modelState == ModelState.Ready
            ? Visibility.Visible : Visibility.Collapsed;

        // Loading status text
        if (modelState == ModelState.Loading)
        {
            LoadingStatusText.Text = string.IsNullOrEmpty(App.Store.StatusMessage)
                ? "Loading model..." : App.Store.StatusMessage;
        }
    }

    private async void OnStartTranscription(object sender, RoutedEventArgs e)
    {
        await App.Store.StartTranscription();
    }

    private void OnLoadModel(object sender, RoutedEventArgs e)
    {
        App.Store.PreloadModel();
    }

    private void OnUnloadModel(object sender, RoutedEventArgs e)
    {
        App.Store.UnloadModel();
    }
}
