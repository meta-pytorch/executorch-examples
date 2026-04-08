// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Diagnostics;
using System.IO;
using System.Text.RegularExpressions;
using VoxtralRealtime.Models;

namespace VoxtralRealtime.Services;

public class RunnerBridge : IDisposable
{
    private Process? _process;
    // Hold strong reference to StandardInput to prevent GC from closing the pipe
    private StreamWriter? _stdinWriter;
    private Stream? _stdinStream;
    private AudioCaptureService? _audioCapture;
    private readonly object _lock = new();
    private bool _disposed;

    public event Action<string>? TokenReceived;
    public event Action<string>? StatusReceived;
    public event Action<string>? ErrorOccurred;
    public event Action<ModelState>? ModelStateChanged;
    public event Action<float>? AudioLevelChanged;

    public ModelState CurrentModelState { get; private set; } = ModelState.Unloaded;
    public bool IsRunnerAlive
    {
        get
        {
            lock (_lock)
            {
                return _process is { HasExited: false };
            }
        }
    }

    private static readonly Regex AnsiRegex = new(@"\x1B\[[0-9;]*m", RegexOptions.Compiled);

    public void LaunchRunner(
        string runnerPath,
        string modelPath,
        string tokenizerPath,
        string preprocessorPath,
        string dataPath)
    {
        lock (_lock)
        {
            if (IsRunnerAlive) Stop();
        }

        var runnerDir = Path.GetDirectoryName(runnerPath) ?? "";
        var args = BuildArguments(modelPath, tokenizerPath, preprocessorPath, dataPath);
        AppLogger.Log("Runner", $"Launching: {runnerPath}");
        AppLogger.Log("Runner", $"Args: {args}");

        var startInfo = new ProcessStartInfo
        {
            FileName = runnerPath,
            Arguments = args,
            RedirectStandardInput = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };

        // Set PATH to include runner directory and CUDA toolkit
        var extraPaths = new List<string> { runnerDir };
        extraPaths.AddRange(FindCudaPaths());
        var existingPath = Environment.GetEnvironmentVariable("PATH") ?? "";
        startInfo.Environment["PATH"] = string.Join(";", extraPaths) + ";" + existingPath;
        AppLogger.Log("Runner", $"Extra PATH dirs: {string.Join("; ", extraPaths)}");

        var proc = new Process { StartInfo = startInfo, EnableRaisingEvents = true };

        proc.Exited += (_, _) =>
        {
            var exitCode = proc.ExitCode;
            AppLogger.Log("Runner", $"Runner EXITED with code {exitCode} (0x{exitCode:X8})");
            if (exitCode != 0 && exitCode != 2)
            {
                ErrorOccurred?.Invoke($"Runner crashed with exit code {exitCode}");
            }
            CurrentModelState = ModelState.Unloaded;
            ModelStateChanged?.Invoke(ModelState.Unloaded);
        };

        lock (_lock) { _process = proc; }

        CurrentModelState = ModelState.Loading;
        ModelStateChanged?.Invoke(ModelState.Loading);
        StatusReceived?.Invoke("Launching runner...");

        try
        {
            proc.Start();
            AppLogger.Log("Runner", $"Runner started (PID: {proc.Id})");

            // CRITICAL: Hold a strong reference to StandardInput (StreamWriter)
            // to prevent GC from finalizing it and closing the underlying pipe.
            // Also grab the raw BaseStream for binary audio writes.
            _stdinWriter = proc.StandardInput;
            _stdinWriter.AutoFlush = false;
            _stdinStream = _stdinWriter.BaseStream;
            AppLogger.Log("Runner", "Stdin pipe acquired");

            // Pre-warm audio device while model loads (takes ~7s, overlaps with model load)
            if (_audioCapture == null)
            {
                _audioCapture = new AudioCaptureService();
                _audioCapture.LevelChanged += level => AudioLevelChanged?.Invoke(level);
                Task.Run(() =>
                {
                    try
                    {
                        _audioCapture.InitAndStartDevice();
                        AppLogger.Log("Runner", "Audio device pre-warmed");
                    }
                    catch (Exception ex)
                    {
                        AppLogger.Log("Runner", $"Audio pre-warm failed: {ex.Message}");
                    }
                });
            }
        }
        catch (Exception ex)
        {
            AppLogger.Log("Runner", $"Failed to launch: {ex.Message}");
            ErrorOccurred?.Invoke($"Failed to launch runner: {ex.Message}");
            CurrentModelState = ModelState.Unloaded;
            ModelStateChanged?.Invoke(ModelState.Unloaded);
            return;
        }

        Task.Run(() => ReadStdout(proc));
        Task.Run(() => ReadStderr(proc));
    }

    private static string BuildArguments(
        string modelPath, string tokenizerPath,
        string preprocessorPath, string dataPath)
    {
        var args = $"--model_path \"{modelPath}\" " +
                   $"--tokenizer_path \"{tokenizerPath}\" " +
                   $"--preprocessor_path \"{preprocessorPath}\"";

        if (!string.IsNullOrEmpty(dataPath))
        {
            args += $" --data_path \"{dataPath}\"";
        }

        args += " --mic";
        return args;
    }

    private void ReadStdout(Process proc)
    {
        var stream = proc.StandardOutput;
        bool sawListening = false;
        var buffer = new char[4096];
        var lineBuffer = new System.Text.StringBuilder();

        try
        {
            while (true)
            {
                int charsRead = stream.Read(buffer, 0, buffer.Length);
                if (charsRead == 0) break;

                var text = new string(buffer, 0, charsRead);
                AppLogger.Log("Stdout", $"({charsRead} chars): {text[..Math.Min(300, text.Length)]}");

                if (!sawListening)
                {
                    lineBuffer.Append(text);
                    var accumulated = lineBuffer.ToString();
                    if (accumulated.Contains("Listening"))
                    {
                        sawListening = true;
                        CurrentModelState = ModelState.Ready;
                        ModelStateChanged?.Invoke(ModelState.Ready);
                        AppLogger.Log("Runner", "Model ready - saw 'Listening'");

                        var remainder = accumulated
                            .Replace("Listening (Ctrl+C to stop)...", "")
                            .Trim();
                        if (!string.IsNullOrEmpty(remainder))
                        {
                            var cleaned = AnsiRegex.Replace(remainder, "");
                            if (!string.IsNullOrEmpty(cleaned))
                                TokenReceived?.Invoke(cleaned);
                        }
                        lineBuffer.Clear();
                    }
                    continue;
                }

                if (text.Contains("PyTorchObserver"))
                {
                    var parts = text.Split('\n');
                    var nonStats = string.Join("", parts.Where(p => !p.Contains("PyTorchObserver")));
                    if (!string.IsNullOrWhiteSpace(nonStats))
                    {
                        var cleaned = AnsiRegex.Replace(nonStats, "");
                        if (!string.IsNullOrEmpty(cleaned))
                            TokenReceived?.Invoke(cleaned);
                    }
                    continue;
                }

                var cleanedText = AnsiRegex.Replace(text, "");
                if (!string.IsNullOrEmpty(cleanedText))
                {
                    TokenReceived?.Invoke(cleanedText);
                }
            }
        }
        catch (Exception ex)
        {
            AppLogger.Log("Stdout", $"Reader error: {ex.Message}");
        }
        AppLogger.Log("Stdout", "Reader thread exiting");
    }

    private void ReadStderr(Process proc)
    {
        var reader = proc.StandardError;
        try
        {
            while (!proc.HasExited || !reader.EndOfStream)
            {
                var line = reader.ReadLine();
                if (line == null) break;

                AppLogger.Log("Stderr", line);

                if (line.Contains("Loading model"))
                    StatusReceived?.Invoke("Loading model...");
                else if (line.Contains("Loading tokenizer"))
                    StatusReceived?.Invoke("Loading tokenizer...");
                else if (line.Contains("Loading preprocessor"))
                    StatusReceived?.Invoke("Loading preprocessor...");
                else if (line.Contains("Warming up"))
                    StatusReceived?.Invoke("Warming up...");
                else if (line.Contains("Warmup complete"))
                    StatusReceived?.Invoke("Model ready");
            }
        }
        catch (Exception ex)
        {
            AppLogger.Log("Stderr", $"Reader error: {ex.Message}");
        }
        AppLogger.Log("Stderr", "Reader thread exiting");
    }

    public void StartAudioCapture()
    {
        Process? proc;
        lock (_lock) { proc = _process; }

        if (proc == null || proc.HasExited)
        {
            AppLogger.Log("Runner", "StartAudioCapture FAILED: runner not alive");
            throw new InvalidOperationException("Runner is not running");
        }

        if (_stdinStream == null || !_stdinStream.CanWrite)
        {
            AppLogger.Log("Runner", $"StartAudioCapture FAILED: stdin null={_stdinStream == null}, canWrite={_stdinStream?.CanWrite}");
            throw new InvalidOperationException("Runner stdin not available");
        }

        AppLogger.Log("Runner", $"Starting audio capture, runner PID={proc.Id}");

        if (_audioCapture == null)
        {
            // Audio wasn't pre-warmed, initialize now
            _audioCapture = new AudioCaptureService();
            _audioCapture.LevelChanged += level => AudioLevelChanged?.Invoke(level);
            _audioCapture.InitAndStartDevice();
        }

        _audioCapture.StartPiping(_stdinStream);
        AppLogger.Log("Runner", "Audio piping enabled (instant)");
    }

    public void PrimeAudioSamples(float[] samples)
    {
        if (samples.Length == 0) return;
        if (_stdinStream == null)
            throw new InvalidOperationException("Runner stdin not available");

        var bytes = new byte[samples.Length * sizeof(float)];
        Buffer.BlockCopy(samples, 0, bytes, 0, bytes.Length);
        _stdinStream.Write(bytes, 0, bytes.Length);
    }

    public void StopAudioCapture()
    {
        AppLogger.Log("Runner", "Stopping audio capture");
        _audioCapture?.StopPiping();
        AudioLevelChanged?.Invoke(0);
    }

    public void Stop()
    {
        AppLogger.Log("Runner", "Stop() called");
        _audioCapture?.Dispose();
        _audioCapture = null;
        AudioLevelChanged?.Invoke(0);

        Process? proc;
        lock (_lock)
        {
            proc = _process;
            _process = null;
        }

        // Close stdin to signal runner to exit
        try
        {
            _stdinWriter?.Close();
            AppLogger.Log("Runner", "Stdin closed");
        }
        catch { }
        _stdinWriter = null;
        _stdinStream = null;

        if (proc == null) return;

        try
        {
            if (!proc.HasExited)
            {
                proc.WaitForExit(3000);
                if (!proc.HasExited)
                {
                    proc.Kill();
                    AppLogger.Log("Runner", "Runner killed");
                }
            }
        }
        catch { }

        proc.Dispose();
        CurrentModelState = ModelState.Unloaded;
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        Stop();
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
