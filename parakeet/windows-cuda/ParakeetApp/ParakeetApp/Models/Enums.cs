// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

namespace ParakeetApp.Models;

public enum ModelState
{
    Unloaded,
    Downloading,
    Ready
}

public enum TranscriptionState
{
    Idle,
    Recording,
    Transcribing
}

public enum SessionExportFormat
{
    Txt,
    Json,
    Srt
}

public enum SidebarPage
{
    Home,
    Settings
}
