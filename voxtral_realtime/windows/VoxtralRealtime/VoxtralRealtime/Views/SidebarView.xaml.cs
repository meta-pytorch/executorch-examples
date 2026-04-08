// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using VoxtralRealtime.Models;

namespace VoxtralRealtime.Views;

public partial class SidebarView : UserControl
{
    public SidebarView()
    {
        InitializeComponent();
        Loaded += (_, _) => { RefreshSessions(); UpdateLiveRow(); };
        App.Store.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName is nameof(App.Store.Sessions))
                Dispatcher.BeginInvoke(() => RefreshSessions());

            if (e.PropertyName is nameof(App.Store.SessionState) or
                nameof(App.Store.LiveTranscript) or
                nameof(App.Store.AudioLevel))
                Dispatcher.BeginInvoke(UpdateLiveRow);
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

    private void UpdateLiveRow()
    {
        bool hasSession = App.Store.HasActiveSession;
        bool isPaused = App.Store.IsPaused;
        bool isTranscribing = App.Store.IsTranscribing;

        LiveSessionRow.Visibility = hasSession ? Visibility.Visible : Visibility.Collapsed;

        if (!hasSession) return;

        // Background color: blue tint when transcribing, orange tint when paused
        LiveSessionRow.Background = isPaused
            ? new System.Windows.Media.SolidColorBrush(
                System.Windows.Media.Color.FromArgb(0x14, 0xFF, 0x95, 0x00))
            : new System.Windows.Media.SolidColorBrush(
                System.Windows.Media.Color.FromArgb(0x14, 0x00, 0x7A, 0xFF));

        // Audio level vs pause icon
        LiveAudioLevel.Visibility = isTranscribing ? Visibility.Visible : Visibility.Collapsed;
        LivePauseIcon.Visibility = isPaused ? Visibility.Visible : Visibility.Collapsed;
        if (isTranscribing) LiveAudioLevel.Level = App.Store.AudioLevel;

        // Title and preview
        LiveTitle.Text = isPaused ? "Paused" : "Transcribing...";
        var preview = App.Store.LiveTranscript;
        LivePreview.Text = preview.Length > 60 ? preview[..60] : preview;
        LivePreview.Visibility = string.IsNullOrEmpty(preview)
            ? Visibility.Collapsed : Visibility.Visible;
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
    private void OnReplacementsClick(object sender, RoutedEventArgs e) => MainWin.NavigateTo(SidebarPage.Replacements);
    private void OnSnippetsClick(object sender, RoutedEventArgs e) => MainWin.NavigateTo(SidebarPage.Snippets);
    private void OnSettingsClick(object sender, RoutedEventArgs e) => MainWin.NavigateTo(SidebarPage.Settings);

    private Session? SelectedSession => SessionsList.SelectedItem as Session;

    private void OnTogglePin(object sender, RoutedEventArgs e)
    {
        if (SelectedSession is { } s) { App.Store.TogglePinned(s); RefreshSessions(); }
    }

    private void OnRename(object sender, RoutedEventArgs e)
    {
        if (SelectedSession is not { } s) return;
        var dialog = new EditReplacementDialog { Title = "Rename Session" };
        dialog.TriggerBox.Text = s.Title ?? s.DisplayTitle;
        dialog.ReplacementBox.Visibility = Visibility.Collapsed;
        dialog.ReplacementLabel.Visibility = Visibility.Collapsed;
        dialog.TriggerLabel.Content = "Title:";
        dialog.Owner = Window.GetWindow(this);
        if (dialog.ShowDialog() == true)
        {
            App.Store.RenameSession(s, dialog.TriggerBox.Text);
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

    private void OnLiveSessionClick(object sender, RoutedEventArgs e)
    {
        MainWin.NavigateTo(SidebarPage.Home);
    }
}
