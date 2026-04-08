// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Text.Json.Serialization;

namespace VoxtralRealtime.Models;

public class Snippet
{
    [JsonPropertyName("id")]
    public Guid Id { get; set; } = Guid.NewGuid();

    [JsonPropertyName("name")]
    public string Name { get; set; } = "";

    [JsonPropertyName("trigger")]
    public string Trigger { get; set; } = "";

    [JsonPropertyName("content")]
    public string Content { get; set; } = "";

    [JsonPropertyName("isEnabled")]
    public bool IsEnabled { get; set; } = true;

    [JsonPropertyName("notes")]
    public string? Notes { get; set; }

    [JsonPropertyName("lastUsedAt")]
    public DateTime? LastUsedAt { get; set; }

    public Snippet Clone() => new()
    {
        Id = Id,
        Name = Name,
        Trigger = Trigger,
        Content = Content,
        IsEnabled = IsEnabled,
        Notes = Notes,
        LastUsedAt = LastUsedAt
    };
}
