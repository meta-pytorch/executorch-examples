// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using VoxtralRealtime.Models;

namespace VoxtralRealtime.Views;

public partial class MainWindow : Window
{
    private SidebarPage _currentPage = SidebarPage.Home;

    // Cached views
    private readonly WelcomeView _welcomeView = new();
    private readonly SetupGuideView _setupGuideView = new();
    private readonly TranscriptView _transcriptView = new();
    private readonly SettingsView _settingsView = new();
    private readonly ReplacementManagementView _replacementView = new();
    private readonly SnippetManagementView _snippetView = new();

    private DictationWindow? _dictationWindow;

    public MainWindow()
    {
        InitializeComponent();
        DataContext = App.Store;

        Loaded += OnLoaded;
        Closing += OnClosing;

        App.Store.PropertyChanged += OnStorePropertyChanged;
        App.Dictation.DictationStarted += OnDictationStarted;
        App.Dictation.DictationStopped += OnDictationStopped;

        UpdateDetailContent();
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        App.Dictation.RegisterHotkey(this);
        App.Store.RunHealthCheck();

        // Auto-load model on startup
        if (App.Store.ModelState == Models.ModelState.Unloaded)
        {
            App.Store.PreloadModel();
        }
    }

    private void OnClosing(object? sender, CancelEventArgs e)
    {
        _dictationWindow?.Close();
    }

    private void OnStorePropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(App.Store.SessionState) or
            nameof(App.Store.ModelState) or
            nameof(App.Store.HealthResult))
        {
            UpdateDetailContent();
        }
    }

    public void NavigateTo(SidebarPage page)
    {
        _currentPage = page;
        if (page == SidebarPage.Home)
            App.Store.SelectedSessionId = null;
        UpdateDetailContent();
    }

    public void NavigateToSession(Guid sessionId)
    {
        App.Store.SelectedSessionId = sessionId;
        _currentPage = SidebarPage.Home;
        UpdateDetailContent();
    }

    private void UpdateDetailContent()
    {
        UserControl view = _currentPage switch
        {
            SidebarPage.Replacements => _replacementView,
            SidebarPage.Snippets => _snippetView,
            SidebarPage.Settings => _settingsView,
            _ => GetHomeView()
        };

        DetailContent.Content = view;
    }

    private UserControl GetHomeView()
    {
        // If viewing a specific session
        if (App.Store.SelectedSessionId.HasValue && !App.Store.HasActiveSession)
        {
            var session = App.Store.GetSelectedSession();
            if (session != null)
            {
                _transcriptView.ShowSession(session);
                return _transcriptView;
            }
        }

        // If health check not good and no active session
        if (App.Store.HealthResult?.AllGood == false &&
            !App.Store.HasActiveSession &&
            App.Store.ModelState == ModelState.Unloaded)
        {
            return _setupGuideView;
        }

        // If transcribing
        if (App.Store.HasActiveSession)
        {
            _transcriptView.ShowLive();
            return _transcriptView;
        }

        return _welcomeView;
    }

    private void OnDictationStarted()
    {
        Dispatcher.BeginInvoke(() =>
        {
            _dictationWindow?.Close();
            _dictationWindow = new DictationWindow();
            _dictationWindow.Show();
        });
    }

    private void OnDictationStopped()
    {
        Dispatcher.BeginInvoke(() =>
        {
            _dictationWindow?.Close();
            _dictationWindow = null;
        });
    }
}
