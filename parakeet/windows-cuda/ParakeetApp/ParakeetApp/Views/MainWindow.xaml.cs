// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using ParakeetApp.Models;

namespace ParakeetApp.Views;

public partial class MainWindow : Window
{
    private SidebarPage _currentPage = SidebarPage.Home;

    private readonly WelcomeView _welcomeView = new();
    private readonly TranscriptView _transcriptView = new();
    private readonly SettingsView _settingsView = new();

    public MainWindow()
    {
        InitializeComponent();
        DataContext = App.Store;

        Loaded += OnLoaded;
        App.Store.PropertyChanged += OnStorePropertyChanged;

        UpdateDetailContent();
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        App.Store.RunHealthCheck();

        if (App.Store.ModelState == ModelState.Unloaded)
        {
            _ = App.Store.EnsureModelsReady();
        }
    }

    private void OnStorePropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(App.Store.TranscriptionState) or
            nameof(App.Store.ModelState) or
            nameof(App.Store.HealthResult) or
            nameof(App.Store.SelectedSessionId))
        {
            Dispatcher.BeginInvoke(UpdateDetailContent);
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
            SidebarPage.Settings => _settingsView,
            _ => GetHomeView()
        };

        DetailContent.Content = view;
    }

    private UserControl GetHomeView()
    {
        if (App.Store.SelectedSessionId.HasValue && App.Store.IsIdle)
        {
            var session = App.Store.GetSelectedSession();
            if (session != null)
            {
                _transcriptView.ShowSession(session);
                return _transcriptView;
            }
        }

        if (App.Store.IsTranscribing)
        {
            _transcriptView.ShowTranscribing();
            return _transcriptView;
        }

        return _welcomeView;
    }
}
