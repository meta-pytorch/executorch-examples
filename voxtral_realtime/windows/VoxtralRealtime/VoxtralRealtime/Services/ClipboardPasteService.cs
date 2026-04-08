// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Runtime.InteropServices;
using System.Windows;

namespace VoxtralRealtime.Services;

public static class ClipboardPasteService
{
    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);

    private const byte VK_CONTROL = 0x11;
    private const byte VK_V = 0x56;
    private const uint KEYEVENTF_KEYUP = 0x0002;

    public static IntPtr CaptureTargetWindow()
    {
        return GetForegroundWindow();
    }

    public static void CopyAndPaste(string text, IntPtr targetWindow)
    {
        if (string.IsNullOrEmpty(text)) return;

        // Set clipboard on UI thread
        Application.Current.Dispatcher.Invoke(() =>
        {
            Clipboard.SetText(text);
        });

        // Restore target window focus
        if (targetWindow != IntPtr.Zero)
        {
            SetForegroundWindow(targetWindow);
        }

        // Brief delay for focus to settle
        Thread.Sleep(150);

        // Simulate Ctrl+V via keybd_event
        keybd_event(VK_CONTROL, 0, 0, UIntPtr.Zero);
        keybd_event(VK_V, 0, 0, UIntPtr.Zero);
        keybd_event(VK_V, 0, KEYEVENTF_KEYUP, UIntPtr.Zero);
        keybd_event(VK_CONTROL, 0, KEYEVENTF_KEYUP, UIntPtr.Zero);
    }
}
