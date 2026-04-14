// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Diagnostics;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;
using ParakeetApp.Models;

namespace ParakeetApp.Services;

public class TranscriptionResult
{
    public string Text { get; set; } = "";
    public List<TimestampSegment> Timestamps { get; set; } = new();
    public double TranscriptionTimeSeconds { get; set; }
}

public class RunnerBridge
{
    public event Action<string>? StatusReceived;
    public event Action<string>? ErrorOccurred;

    private static readonly Regex AnsiRegex = new(@"\x1B\[[0-9;]*m", RegexOptions.Compiled);

    public async Task<TranscriptionResult> TranscribeAsync(
        string runnerPath,
        string modelPath,
        string tokenizerPath,
        string dataPath,
        string audioPath,
        CancellationToken ct = default)
    {
        var runnerDir = Path.GetDirectoryName(runnerPath) ?? "";
        var args = BuildArguments(modelPath, tokenizerPath, dataPath, audioPath);
        AppLogger.Log("Runner", $"Launching: {runnerPath}");
        AppLogger.Log("Runner", $"Args: {args}");

        var startInfo = new ProcessStartInfo
        {
            FileName = runnerPath,
            Arguments = args,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };

        var extraPaths = new List<string> { runnerDir };
        extraPaths.AddRange(FindCudaPaths());
        var existingPath = Environment.GetEnvironmentVariable("PATH") ?? "";
        startInfo.Environment["PATH"] = string.Join(";", extraPaths) + ";" + existingPath;
        AppLogger.Log("Runner", $"Extra PATH dirs: {string.Join("; ", extraPaths)}");

        var proc = new Process { StartInfo = startInfo, EnableRaisingEvents = true };
        var stdout = new StringBuilder();
        var stderr = new StringBuilder();
        var sw = Stopwatch.StartNew();

        try
        {
            proc.Start();
            AppLogger.Log("Runner", $"Runner started (PID: {proc.Id})");

            var stdoutTask = Task.Run(async () =>
            {
                var reader = proc.StandardOutput;
                string? line;
                while ((line = await reader.ReadLineAsync(ct)) != null)
                {
                    var clean = AnsiRegex.Replace(line, "");
                    stdout.AppendLine(clean);
                    AppLogger.Log("Stdout", clean);
                }
            }, ct);

            var stderrTask = Task.Run(async () =>
            {
                var reader = proc.StandardError;
                string? line;
                while ((line = await reader.ReadLineAsync(ct)) != null)
                {
                    var clean = AnsiRegex.Replace(line, "");
                    stderr.AppendLine(clean);
                    AppLogger.Log("Stderr", clean);
                    ReportStatus(clean);
                }
            }, ct);

            await Task.WhenAll(stdoutTask, stderrTask);
            await proc.WaitForExitAsync(ct);
            sw.Stop();

            var exitCode = proc.ExitCode;
            AppLogger.Log("Runner", $"Runner exited with code {exitCode} (0x{exitCode:X8}) in {sw.Elapsed.TotalSeconds:F1}s");

            if (exitCode != 0)
            {
                var errorMsg = $"Runner failed with exit code {exitCode}";
                ErrorOccurred?.Invoke(errorMsg);
                throw new InvalidOperationException(errorMsg);
            }

            return ParseOutput(stdout.ToString(), sw.Elapsed.TotalSeconds);
        }
        catch (OperationCanceledException)
        {
            if (!proc.HasExited)
            {
                proc.Kill();
                AppLogger.Log("Runner", "Runner killed (cancelled)");
            }
            throw;
        }
        finally
        {
            proc.Dispose();
        }
    }

    private static string BuildArguments(
        string modelPath, string tokenizerPath,
        string dataPath, string audioPath)
    {
        var args = $"--model_path \"{modelPath}\" " +
                   $"--tokenizer_path \"{tokenizerPath}\"";

        if (!string.IsNullOrEmpty(dataPath))
            args += $" --data_path \"{dataPath}\"";

        args += $" --audio_path \"{audioPath}\"";
        args += " --timestamps segment";
        return args;
    }

    private static TranscriptionResult ParseOutput(string output, double elapsedSeconds)
    {
        var result = new TranscriptionResult
        {
            TranscriptionTimeSeconds = elapsedSeconds
        };

        var lines = output.Split('\n', StringSplitOptions.None);
        var textBuilder = new StringBuilder();
        bool inTimestamps = false;

        foreach (var rawLine in lines)
        {
            var line = rawLine.Trim();

            if (line.StartsWith("Transcribed text:"))
            {
                var text = line["Transcribed text:".Length..].Trim();
                if (text.StartsWith("\"") && text.EndsWith("\""))
                    text = text[1..^1];
                textBuilder.AppendLine(text);
                continue;
            }

            var tsMatch = Regex.Match(line, @"^\[(\d+\.?\d*)\s*-\s*(\d+\.?\d*)\]\s*(.+)$");
            if (tsMatch.Success)
            {
                inTimestamps = true;
                result.Timestamps.Add(new TimestampSegment
                {
                    Start = double.Parse(tsMatch.Groups[1].Value),
                    End = double.Parse(tsMatch.Groups[2].Value),
                    Text = tsMatch.Groups[3].Value.Trim()
                });
                continue;
            }

            if (line.Contains("---") || line.Contains("Start") || string.IsNullOrWhiteSpace(line))
                continue;

            if (!inTimestamps && !string.IsNullOrWhiteSpace(line))
                textBuilder.AppendLine(line);
        }

        result.Text = textBuilder.ToString().Trim();

        if (string.IsNullOrEmpty(result.Text) && result.Timestamps.Count > 0)
        {
            result.Text = string.Join(" ", result.Timestamps.Select(t => t.Text));
        }

        return result;
    }

    private void ReportStatus(string line)
    {
        if (line.Contains("Loading model"))
            StatusReceived?.Invoke("Loading model...");
        else if (line.Contains("Loading tokenizer"))
            StatusReceived?.Invoke("Loading tokenizer...");
        else if (line.Contains("Running encoder"))
            StatusReceived?.Invoke("Running encoder...");
        else if (line.Contains("Running decoder") || line.Contains("Running joiner"))
            StatusReceived?.Invoke("Decoding...");
        else if (line.Contains("Transcription complete") || line.Contains("Transcribed text"))
            StatusReceived?.Invoke("Transcription complete");
    }

    private static List<string> FindCudaPaths()
    {
        var paths = new List<string>();

        var cudaPath = Environment.GetEnvironmentVariable("CUDA_PATH");
        if (!string.IsNullOrEmpty(cudaPath))
        {
            var bin = Path.Combine(cudaPath, "bin");
            if (Directory.Exists(bin)) paths.Add(bin);
        }

        var toolkitRoot = @"C:\Program Files\NVIDIA GPU Computing Toolkit\CUDA";
        if (Directory.Exists(toolkitRoot))
        {
            foreach (var dir in Directory.GetDirectories(toolkitRoot)
                         .OrderByDescending(d => d))
            {
                var bin = Path.Combine(dir, "bin");
                if (Directory.Exists(bin) && !paths.Contains(bin))
                    paths.Add(bin);
            }
        }

        var condaPrefix = Environment.GetEnvironmentVariable("CONDA_PREFIX");
        if (!string.IsNullOrEmpty(condaPrefix))
        {
            var libBin = Path.Combine(condaPrefix, "Library", "bin");
            if (Directory.Exists(libBin)) paths.Add(libBin);
            var bin = Path.Combine(condaPrefix, "bin");
            if (Directory.Exists(bin)) paths.Add(bin);
        }

        return paths;
    }
}
