// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.IO;
using System.Text.Json;

namespace VoxtralRealtime.Services;

public static class PersistenceService
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public static string AppDataDir { get; } = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "VoxtralRealtime");

    public static string SessionsPath => Path.Combine(AppDataDir, "sessions.json");
    public static string ReplacementsPath => Path.Combine(AppDataDir, "replacements.json");
    public static string SnippetsPath => Path.Combine(AppDataDir, "snippets.json");
    public static string SettingsPath => Path.Combine(AppDataDir, "settings.json");

    public static void Save<T>(string path, T data)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var json = JsonSerializer.Serialize(data, JsonOptions);
        File.WriteAllText(path, json);
    }

    public static T? Load<T>(string path)
    {
        if (!File.Exists(path)) return default;
        try
        {
            var json = File.ReadAllText(path);
            return JsonSerializer.Deserialize<T>(json, JsonOptions);
        }
        catch
        {
            return default;
        }
    }
}
