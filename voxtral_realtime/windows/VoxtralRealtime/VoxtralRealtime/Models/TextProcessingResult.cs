// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

namespace VoxtralRealtime.Models;

public record TextProcessingResult(
    string RawText,
    string OutputText,
    List<string> Tags,
    List<Guid> UsedSnippetIds,
    bool SkippedSnippetExpansion = false)
{
    public bool Transformed => RawText != OutputText;
}
