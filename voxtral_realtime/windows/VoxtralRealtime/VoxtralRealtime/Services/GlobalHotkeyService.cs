// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;

namespace VoxtralRealtime.Services;

public class GlobalHotkeyService : IDisposable
{
    [DllImport("user32.dll")]
    private static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);

    [DllImport("user32.dll")]
    private static extern bool UnregisterHotKey(IntPtr hWnd, int id);

    private const int HOTKEY_ID = 9001;
    private const uint MOD_CONTROL = 0x0002;
    private const uint VK_SPACE = 0x20;
    private const int WM_HOTKEY = 0x0312;

    private HwndSource? _source;
    private IntPtr _windowHandle;
    private bool _registered;

    public event Action? HotkeyPressed;

    public bool Register(Window window)
    {
        var helper = new WindowInteropHelper(window);
        _windowHandle = helper.Handle;

        if (_windowHandle == IntPtr.Zero) return false;

        _source = HwndSource.FromHwnd(_windowHandle);
        _source?.AddHook(HwndHook);

        _registered = RegisterHotKey(_windowHandle, HOTKEY_ID, MOD_CONTROL, VK_SPACE);
        return _registered;
    }

    private IntPtr HwndHook(IntPtr hwnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        if (msg == WM_HOTKEY && wParam.ToInt32() == HOTKEY_ID)
        {
            HotkeyPressed?.Invoke();
            handled = true;
        }
        return IntPtr.Zero;
    }

    public void Unregister()
    {
        if (_registered && _windowHandle != IntPtr.Zero)
        {
            UnregisterHotKey(_windowHandle, HOTKEY_ID);
            _registered = false;
        }
        _source?.RemoveHook(HwndHook);
        _source = null;
    }

    public void Dispose()
    {
        Unregister();
    }
}
