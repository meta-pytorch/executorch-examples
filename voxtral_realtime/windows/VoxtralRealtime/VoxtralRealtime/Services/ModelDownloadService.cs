// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.IO;
using System.Net.Http;

namespace VoxtralRealtime.Services;

public record DownloadProgress(
    string FileName,
    long BytesDownloaded,
    long TotalBytes,
    int FileIndex,
    int TotalFiles);

public class ModelDownloadService
{
    private static readonly HttpClient Http = new()
    {
        Timeout = TimeSpan.FromHours(2)
    };

    public static readonly string DefaultCacheDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "VoxtralRealtime", "models");

    private static readonly (string RepoId, string FileName)[] ModelFiles =
    {
        ("younghan-meta/Voxtral-Mini-4B-Realtime-2602-ExecuTorch-CUDA-Windows", "model.pte"),
        ("younghan-meta/Voxtral-Mini-4B-Realtime-2602-ExecuTorch-CUDA-Windows", "preprocessor.pte"),
        ("younghan-meta/Voxtral-Mini-4B-Realtime-2602-ExecuTorch-CUDA-Windows", "aoti_cuda_blob.ptd"),
        ("mistralai/Voxtral-Mini-4B-Realtime-2602", "tekken.json"),
    };

    public event Action<DownloadProgress>? ProgressChanged;

    public List<string> GetMissingFiles(string cacheDir)
    {
        var missing = new List<string>();
        foreach (var (_, fileName) in ModelFiles)
        {
            var path = Path.Combine(cacheDir, fileName);
            if (!File.Exists(path))
                missing.Add(fileName);
        }
        return missing;
    }

    public async Task DownloadAllAsync(string cacheDir, CancellationToken ct = default)
    {
        Directory.CreateDirectory(cacheDir);

        int fileIndex = 0;
        int totalFiles = ModelFiles.Length;

        foreach (var (repoId, fileName) in ModelFiles)
        {
            fileIndex++;
            var destPath = Path.Combine(cacheDir, fileName);

            if (File.Exists(destPath))
            {
                ProgressChanged?.Invoke(new DownloadProgress(
                    fileName, 1, 1, fileIndex, totalFiles));
                continue;
            }

            var url = $"https://huggingface.co/{repoId}/resolve/main/{fileName}";
            await DownloadFileAsync(url, destPath, fileName, fileIndex, totalFiles, ct);
        }
    }

    private async Task DownloadFileAsync(
        string url, string destPath, string fileName,
        int fileIndex, int totalFiles, CancellationToken ct)
    {
        var tmpPath = destPath + ".tmp";

        try
        {
            using var response = await Http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, ct);
            response.EnsureSuccessStatusCode();

            var totalBytes = response.Content.Headers.ContentLength ?? -1;

            await using var stream = await response.Content.ReadAsStreamAsync(ct);
            await using var fileStream = new FileStream(tmpPath, FileMode.Create, FileAccess.Write, FileShare.None, 81920);

            var buffer = new byte[81920];
            long bytesRead = 0;
            int read;

            while ((read = await stream.ReadAsync(buffer, ct)) > 0)
            {
                await fileStream.WriteAsync(buffer.AsMemory(0, read), ct);
                bytesRead += read;

                ProgressChanged?.Invoke(new DownloadProgress(
                    fileName, bytesRead, totalBytes, fileIndex, totalFiles));
            }

            await fileStream.FlushAsync(ct);
        }
        catch
        {
            try { File.Delete(tmpPath); } catch { }
            throw;
        }

        File.Move(tmpPath, destPath, overwrite: true);
    }

    public static string FormatBytes(long bytes)
    {
        if (bytes < 0) return "?";
        if (bytes < 1024) return $"{bytes} B";
        if (bytes < 1024 * 1024) return $"{bytes / 1024.0:F0} KB";
        if (bytes < 1024L * 1024 * 1024) return $"{bytes / (1024.0 * 1024):F1} MB";
        return $"{bytes / (1024.0 * 1024 * 1024):F2} GB";
    }
}
