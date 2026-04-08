// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.ComponentModel;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Microsoft.Win32;

namespace VoxtralRealtime.Views;

public partial class SettingsView : UserControl
{
    public SettingsView()
    {
        InitializeComponent();
        DataContext = App.Settings;
        App.Settings.PropertyChanged += OnSettingsChanged;
        Loaded += (_, _) => UpdateFileStatus();
    }

    private void OnSettingsChanged(object? sender, PropertyChangedEventArgs e) => UpdateFileStatus();

    private void UpdateFileStatus()
    {
        SetStatus(RunnerStatus, File.Exists(App.Settings.RunnerPath));
        SetStatus(ModelStatus, File.Exists(App.Settings.ModelPath));
        SetStatus(TokenizerStatus, File.Exists(App.Settings.TokenizerPath));
        SetStatus(PreprocessorStatus, File.Exists(App.Settings.PreprocessorPath));
        SetStatus(DataStatus, File.Exists(App.Settings.DataPath));
    }

    private static void SetStatus(TextBlock tb, bool exists)
    {
        tb.Text = exists ? "\u2713" : "\u2717";
        tb.Foreground = exists
            ? new SolidColorBrush(Color.FromRgb(16, 124, 16))
            : new SolidColorBrush(Color.FromRgb(209, 52, 56));
    }

    private void OnBrowseRunner(object sender, RoutedEventArgs e) =>
        BrowseFile("Executable files (*.exe)|*.exe", path => App.Settings.RunnerPath = path);
    private void OnBrowseModel(object sender, RoutedEventArgs e) =>
        BrowseFile("PTE files (*.pte)|*.pte|All files (*.*)|*.*", path => App.Settings.ModelPath = path);
    private void OnBrowseTokenizer(object sender, RoutedEventArgs e) =>
        BrowseFile("JSON files (*.json)|*.json|All files (*.*)|*.*", path => App.Settings.TokenizerPath = path);
    private void OnBrowsePreprocessor(object sender, RoutedEventArgs e) =>
        BrowseFile("PTE files (*.pte)|*.pte|All files (*.*)|*.*", path => App.Settings.PreprocessorPath = path);
    private void OnBrowseData(object sender, RoutedEventArgs e) =>
        BrowseFile("PTD files (*.ptd)|*.ptd|All files (*.*)|*.*", path => App.Settings.DataPath = path);

    private static void BrowseFile(string filter, Action<string> setter)
    {
        var dialog = new OpenFileDialog { Filter = filter };
        if (dialog.ShowDialog() == true)
        {
            setter(dialog.FileName);
        }
    }
}
