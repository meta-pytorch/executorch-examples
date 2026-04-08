// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Text.Json.Serialization;

namespace VoxtralRealtime.Models;

public class Session
{
    [JsonPropertyName("id")]
    public Guid Id { get; set; } = Guid.NewGuid();

    [JsonPropertyName("date")]
    public DateTime Date { get; set; } = DateTime.Now;

    [JsonPropertyName("title")]
    public string? Title { get; set; }

    [JsonPropertyName("transcript")]
    public string Transcript { get; set; } = "";

    [JsonPropertyName("duration")]
    public double Duration { get; set; }

    [JsonPropertyName("source")]
    [JsonConverter(typeof(JsonStringEnumConverter))]
    public SessionSource Source { get; set; } = SessionSource.Transcription;

    [JsonPropertyName("rawTranscript")]
    public string? RawTranscript { get; set; }

    [JsonPropertyName("tags")]
    public List<string> Tags { get; set; } = new();

    [JsonPropertyName("wakeTriggered")]
    public bool WakeTriggered { get; set; }

    [JsonPropertyName("pinned")]
    public bool Pinned { get; set; }

    [JsonPropertyName("usedSnippetIds")]
    public List<Guid> UsedSnippetIds { get; set; } = new();

    [JsonIgnore]
    public string DisplayTitle => !string.IsNullOrEmpty(Title)
        ? Title
        : Date.ToString("MMM d, yyyy h:mm tt");

    [JsonIgnore]
    public string PreviewText
    {
        get
        {
            var text = Transcript.Trim();
            return text.Length > 100 ? text[..100] + "..." : text;
        }
    }
}
