// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using ParakeetApp.Models;

namespace ParakeetApp.Views;

public partial class WelcomeView : UserControl
{
    public WelcomeView()
    {
        InitializeComponent();
        DataContext = App.Store;
        App.Store.PropertyChanged += OnStoreChanged;
        Loaded += (_, _) => UpdateModelState();

        WaveIcon.Level = 0.3f;
    }

    private void OnStoreChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(App.Store.ModelState) or
            nameof(App.Store.StatusMessage) or
            nameof(App.Store.TranscriptionState) or
            nameof(App.Store.DownloadProgress))
        {
            Dispatcher.BeginInvoke(UpdateModelState);
        }
    }

    private void UpdateModelState()
    {
        var modelState = App.Store.ModelState;

        DownloadModelBtn.Visibility = modelState == ModelState.Unloaded
            ? Visibility.Visible : Visibility.Collapsed;
        DownloadingPanel.Visibility = modelState == ModelState.Downloading
            ? Visibility.Visible : Visibility.Collapsed;
        ReadyPanel.Visibility = modelState == ModelState.Ready
            ? Visibility.Visible : Visibility.Collapsed;

        RecordBtn.Visibility = modelState == ModelState.Ready && App.Store.IsIdle
            ? Visibility.Visible : Visibility.Collapsed;

        if (modelState == ModelState.Downloading)
        {
            DownloadProgressBar.Value = App.Store.DownloadProgress;
            DownloadStatusText.Text = string.IsNullOrEmpty(App.Store.StatusMessage)
                ? "Preparing download..." : App.Store.StatusMessage;
        }
    }

    private async void OnStartRecording(object sender, RoutedEventArgs e)
    {
        await App.Store.StartRecording();
    }

    private async void OnDownloadModel(object sender, RoutedEventArgs e)
    {
        await App.Store.EnsureModelsReady();
    }

    private void OnCancelDownload(object sender, RoutedEventArgs e)
    {
        App.Store.CancelDownload();
    }
}
