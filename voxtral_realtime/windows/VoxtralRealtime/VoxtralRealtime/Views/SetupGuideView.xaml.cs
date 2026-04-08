// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace VoxtralRealtime.Views;

public partial class SetupGuideView : UserControl
{
    public SetupGuideView()
    {
        InitializeComponent();
        App.Store.PropertyChanged += OnStorePropertyChanged;
        Loaded += (_, _) => UpdateChecks();
    }

    private void OnStorePropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(App.Store.HealthResult))
            Dispatcher.BeginInvoke(UpdateChecks);
    }

    private void UpdateChecks()
    {
        var result = App.Store.HealthResult;
        if (result == null) return;

        SetIcon(RunnerIcon, result.RunnerAvailable);
        SetIcon(ModelIcon, result.ModelAvailable);
        SetIcon(PreprocessorIcon, result.PreprocessorAvailable);
        SetIcon(TokenizerIcon, result.TokenizerAvailable);
        SetIcon(DataIcon, result.DataPathAvailable);
        SetIcon(MicIcon, result.MicrophoneAvailable);
    }

    private static void SetIcon(TextBlock icon, bool available)
    {
        icon.Text = available ? "\u2713" : "\u2717";
        icon.Foreground = available
            ? new SolidColorBrush(Color.FromRgb(16, 124, 16))
            : new SolidColorBrush(Color.FromRgb(209, 52, 56));
    }

    private void OnOpenSettings(object sender, RoutedEventArgs e)
    {
        ((MainWindow)Window.GetWindow(this)!).NavigateTo(Models.SidebarPage.Settings);
    }

    private void OnRefresh(object sender, RoutedEventArgs e)
    {
        App.Store.RunHealthCheck();
    }
}
