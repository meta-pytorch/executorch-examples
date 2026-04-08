// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;

namespace VoxtralRealtime.Views;

public partial class ErrorBannerControl : UserControl
{
    public ErrorBannerControl()
    {
        InitializeComponent();
        App.Store.PropertyChanged += OnStoreChanged;
    }

    private void OnStoreChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(App.Store.CurrentError))
        {
            Dispatcher.BeginInvoke(() =>
            {
                var error = App.Store.CurrentError;
                BannerBorder.Visibility = error != null ? Visibility.Visible : Visibility.Collapsed;
                ErrorText.Text = error ?? "";
            });
        }
    }

    private void OnDismiss(object sender, RoutedEventArgs e)
    {
        App.Store.ClearError();
    }
}
