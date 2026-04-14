// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using ParakeetApp.Models;

namespace ParakeetApp.Views;

public partial class SidebarView : UserControl
{
    public SidebarView()
    {
        InitializeComponent();
        Loaded += (_, _) => { RefreshSessions(); UpdateRecordingRow(); };
        App.Store.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName is nameof(App.Store.Sessions))
                Dispatcher.BeginInvoke(() => RefreshSessions());

            if (e.PropertyName is nameof(App.Store.TranscriptionState) or
                nameof(App.Store.StatusMessage))
                Dispatcher.BeginInvoke(UpdateRecordingRow);
        };
    }

    private MainWindow MainWin => (MainWindow)Window.GetWindow(this)!;

    private void RefreshSessions(string? filter = null)
    {
        var sessions = App.Store.Sessions.AsEnumerable();
        if (!string.IsNullOrWhiteSpace(filter))
        {
            sessions = sessions.Where(s =>
                s.DisplayTitle.Contains(filter, StringComparison.OrdinalIgnoreCase) ||
                s.Transcript.Contains(filter, StringComparison.OrdinalIgnoreCase));
        }

        SessionsList.ItemsSource = sessions
            .OrderByDescending(s => s.Pinned)
            .ThenByDescending(s => s.Date)
            .ToList();
    }

    private void UpdateRecordingRow()
    {
        bool isRecording = App.Store.IsRecording;
        bool isTranscribing = App.Store.IsTranscribing;

        RecordingRow.Visibility = (isRecording || isTranscribing)
            ? Visibility.Visible : Visibility.Collapsed;

        if (isRecording)
        {
            RecordingRow.Background = new System.Windows.Media.SolidColorBrush(
                System.Windows.Media.Color.FromArgb(0x14, 0xFF, 0x3B, 0x30));
            RecordDot.Fill = new System.Windows.Media.SolidColorBrush(
                System.Windows.Media.Color.FromRgb(0xFF, 0x3B, 0x30));
            RecordingTitle.Text = "Recording...";
            RecordingStatus.Text = "";
        }
        else if (isTranscribing)
        {
            RecordingRow.Background = new System.Windows.Media.SolidColorBrush(
                System.Windows.Media.Color.FromArgb(0x14, 0x10, 0xA3, 0x7F));
            RecordDot.Fill = new System.Windows.Media.SolidColorBrush(
                System.Windows.Media.Color.FromRgb(0x10, 0xA3, 0x7F));
            RecordingTitle.Text = "Transcribing...";
            RecordingStatus.Text = App.Store.StatusMessage;
        }
    }

    private void OnSearchChanged(object sender, TextChangedEventArgs e)
    {
        RefreshSessions(SearchBox.Text);
    }

    private void OnSessionSelected(object sender, SelectionChangedEventArgs e)
    {
        if (SessionsList.SelectedItem is Session session)
        {
            MainWin.NavigateToSession(session.Id);
        }
    }

    private void OnHomeClick(object sender, RoutedEventArgs e) => MainWin.NavigateTo(SidebarPage.Home);
    private void OnSettingsClick(object sender, RoutedEventArgs e) => MainWin.NavigateTo(SidebarPage.Settings);

    private Session? SelectedSession => SessionsList.SelectedItem as Session;

    private void OnTogglePin(object sender, RoutedEventArgs e)
    {
        if (SelectedSession is { } s) { App.Store.TogglePinned(s); RefreshSessions(); }
    }

    private void OnRename(object sender, RoutedEventArgs e)
    {
        if (SelectedSession is not { } s) return;
        var dialog = new RenameDialog(s.Title ?? s.DisplayTitle)
        {
            Owner = Window.GetWindow(this)
        };
        if (dialog.ShowDialog() == true && !string.IsNullOrEmpty(dialog.ResultTitle))
        {
            App.Store.RenameSession(s, dialog.ResultTitle);
            RefreshSessions();
        }
    }

    private void OnCopyTranscript(object sender, RoutedEventArgs e)
    {
        if (SelectedSession is { } s && !string.IsNullOrEmpty(s.Transcript))
            Clipboard.SetText(s.Transcript);
    }

    private void OnExportTxt(object sender, RoutedEventArgs e)
    {
        if (SelectedSession is { } s) App.Store.ExportSession(s, SessionExportFormat.Txt);
    }

    private void OnExportJson(object sender, RoutedEventArgs e)
    {
        if (SelectedSession is { } s) App.Store.ExportSession(s, SessionExportFormat.Json);
    }

    private void OnExportSrt(object sender, RoutedEventArgs e)
    {
        if (SelectedSession is { } s) App.Store.ExportSession(s, SessionExportFormat.Srt);
    }

    private void OnDelete(object sender, RoutedEventArgs e)
    {
        if (SelectedSession is not { } s) return;
        var result = MessageBox.Show(
            $"Delete session \"{s.DisplayTitle}\"?",
            "Delete Session",
            MessageBoxButton.YesNo,
            MessageBoxImage.Warning);
        if (result == MessageBoxResult.Yes)
        {
            App.Store.DeleteSession(s);
            RefreshSessions();
        }
    }

    private void OnRecordingClick(object sender, RoutedEventArgs e)
    {
        MainWin.NavigateTo(SidebarPage.Home);
    }
}
