// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Runtime.InteropServices;
using System.Windows;
using ParakeetApp.Services;
using ParakeetApp.ViewModels;

namespace ParakeetApp;

public partial class App : Application
{
    [DllImport("kernel32.dll")]
    private static extern bool AttachConsole(int dwProcessId);
    private const int ATTACH_PARENT_PROCESS = -1;

    public static SettingsViewModel Settings { get; private set; } = null!;
    public static TranscriptStoreViewModel Store { get; private set; } = null!;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        AttachConsole(ATTACH_PARENT_PROCESS);
        AppLogger.Log("App", "Parakeet App starting up");

        Settings = new SettingsViewModel();
        Store = new TranscriptStoreViewModel(Settings);
    }

    protected override void OnExit(ExitEventArgs e)
    {
        Store.Shutdown();
        base.OnExit(e);
    }
}
