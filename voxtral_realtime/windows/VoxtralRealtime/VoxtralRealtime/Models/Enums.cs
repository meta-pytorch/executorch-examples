// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

namespace VoxtralRealtime.Models;

public enum SessionState
{
    Idle,
    Loading,
    Transcribing,
    Paused
}

public enum ModelState
{
    Unloaded,
    Downloading,
    Loading,
    Ready
}

public enum SessionSource
{
    Transcription,
    Dictation
}

public enum DictationState
{
    Idle,
    Loading,
    Listening,
    Processing
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
    Replacements,
    Snippets,
    Settings
}
