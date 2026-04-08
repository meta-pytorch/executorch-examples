// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;

namespace VoxtralRealtime.Views;

public partial class DictationWindow : Window
{
    [DllImport("user32.dll")]
    private static extern int GetWindowLong(IntPtr hWnd, int nIndex);

    [DllImport("user32.dll")]
    private static extern int SetWindowLong(IntPtr hWnd, int nIndex, int dwNewLong);

    private const int GWL_EXSTYLE = -20;
    private const int WS_EX_NOACTIVATE = 0x08000000;
    private const int WS_EX_TOOLWINDOW = 0x00000080;

    public DictationWindow()
    {
        InitializeComponent();
        SourceInitialized += OnSourceInitialized;
        App.Store.PropertyChanged += OnStoreChanged;

        // Position slightly above center
        Loaded += (_, _) =>
        {
            var screen = SystemParameters.WorkArea;
            Left = (screen.Width - Width) / 2;
            Top = screen.Height * 0.35;
        };
    }

    private void OnSourceInitialized(object? sender, EventArgs e)
    {
        var handle = new WindowInteropHelper(this).Handle;
        var exStyle = GetWindowLong(handle, GWL_EXSTYLE);
        SetWindowLong(handle, GWL_EXSTYLE, exStyle | WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW);
    }

    private void OnStoreChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(App.Store.DictationText))
        {
            Dispatcher.BeginInvoke(() =>
            {
                var text = App.Store.DictationText;
                DictationText.Text = string.IsNullOrEmpty(text) ? "Listening..." : text;
            });
        }
        else if (e.PropertyName == nameof(App.Store.AudioLevel))
        {
            Dispatcher.BeginInvoke(() =>
            {
                DictationLevel.Level = App.Store.AudioLevel;
            });
        }
    }

    protected override void OnClosing(CancelEventArgs e)
    {
        App.Store.PropertyChanged -= OnStoreChanged;
        base.OnClosing(e);
    }
}
