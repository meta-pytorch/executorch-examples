// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Text.Json.Serialization;

namespace ParakeetApp.Models;

public class TimestampSegment
{
    [JsonPropertyName("start")]
    public double Start { get; set; }

    [JsonPropertyName("end")]
    public double End { get; set; }

    [JsonPropertyName("text")]
    public string Text { get; set; } = "";
}

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

    [JsonPropertyName("audioDuration")]
    public double AudioDuration { get; set; }

    [JsonPropertyName("timestamps")]
    public List<TimestampSegment> Timestamps { get; set; } = new();

    [JsonPropertyName("pinned")]
    public bool Pinned { get; set; }

    [JsonPropertyName("wavPath")]
    public string? WavPath { get; set; }

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

    [JsonIgnore]
    public string FormattedDuration
    {
        get
        {
            var ts = TimeSpan.FromSeconds(AudioDuration);
            return ts.TotalMinutes >= 1
                ? $"{(int)ts.TotalMinutes}:{ts.Seconds:D2}"
                : $"{ts.Seconds}s";
        }
    }
}
