// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.IO;

namespace VoxtralRealtime.Services;

public static class AppLogger
{
    private static readonly string LogPath = Path.Combine(
        PersistenceService.AppDataDir, "voxtral_realtime.log");

    private static readonly object Lock = new();

    static AppLogger()
    {
        Directory.CreateDirectory(PersistenceService.AppDataDir);
        // Clear log on startup
        File.WriteAllText(LogPath, $"=== Voxtral Realtime started at {DateTime.Now:O} ===\n");
    }

    public static void Log(string category, string message)
    {
        var line = $"[{DateTime.Now:HH:mm:ss.fff}] [{category}] {message}";
        Console.WriteLine(line);
        lock (Lock)
        {
            try { File.AppendAllText(LogPath, line + "\n"); } catch { }
        }
    }
}
