// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Runtime.InteropServices;
using System.Windows;
using VoxtralRealtime.Services;
using VoxtralRealtime.ViewModels;

namespace VoxtralRealtime;

public partial class App : Application
{
    [DllImport("kernel32.dll")]
    private static extern bool AttachConsole(int dwProcessId);
    private const int ATTACH_PARENT_PROCESS = -1;
    public static SettingsViewModel Settings { get; private set; } = null!;
    public static ReplacementStoreViewModel ReplacementStore { get; private set; } = null!;
    public static SnippetStoreViewModel SnippetStore { get; private set; } = null!;
    public static TextPipeline TextPipeline { get; private set; } = null!;
    public static TranscriptStoreViewModel Store { get; private set; } = null!;
    public static DictationViewModel Dictation { get; private set; } = null!;
    public static GlobalHotkeyService HotkeyService { get; private set; } = null!;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // Attach to parent console so Console.WriteLine shows in the terminal
        AttachConsole(ATTACH_PARENT_PROCESS);
        AppLogger.Log("App", "Voxtral Realtime starting up");

        Settings = new SettingsViewModel();
        ReplacementStore = new ReplacementStoreViewModel();
        SnippetStore = new SnippetStoreViewModel();
        TextPipeline = new TextPipeline(ReplacementStore, SnippetStore);
        Store = new TranscriptStoreViewModel(Settings, TextPipeline);
        HotkeyService = new GlobalHotkeyService();
        Dictation = new DictationViewModel(Store, Settings, HotkeyService);
    }

    protected override void OnExit(ExitEventArgs e)
    {
        Dictation.Cleanup();
        Store.Shutdown();
        base.OnExit(e);
    }
}
