// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Text.Json.Serialization;

namespace VoxtralRealtime.Models;

public class ReplacementEntry
{
    [JsonPropertyName("id")]
    public Guid Id { get; set; } = Guid.NewGuid();

    [JsonPropertyName("trigger")]
    public string Trigger { get; set; } = "";

    [JsonPropertyName("replacement")]
    public string Replacement { get; set; } = "";

    [JsonPropertyName("isEnabled")]
    public bool IsEnabled { get; set; } = true;

    [JsonPropertyName("isCaseSensitive")]
    public bool IsCaseSensitive { get; set; }

    [JsonPropertyName("requiresWordBoundary")]
    public bool RequiresWordBoundary { get; set; } = true;

    [JsonPropertyName("notes")]
    public string? Notes { get; set; }

    public ReplacementEntry Clone() => new()
    {
        Id = Id,
        Trigger = Trigger,
        Replacement = Replacement,
        IsEnabled = IsEnabled,
        IsCaseSensitive = IsCaseSensitive,
        RequiresWordBoundary = RequiresWordBoundary,
        Notes = Notes
    };
}
