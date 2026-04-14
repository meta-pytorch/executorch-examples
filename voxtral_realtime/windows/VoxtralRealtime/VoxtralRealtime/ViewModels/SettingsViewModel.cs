// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.IO;
using System.Text.Json.Serialization;
using CommunityToolkit.Mvvm.ComponentModel;
using VoxtralRealtime.Services;

namespace VoxtralRealtime.ViewModels;

public partial class SettingsViewModel : ObservableObject
{
    private static readonly string AppDir = AppDomain.CurrentDomain.BaseDirectory;

    // Models directory: always next to the exe (install dir or dev dir).
    // Models are either bundled by the installer or auto-downloaded on first launch.
    public static readonly string ModelsDir = Path.Combine(AppDir, "models");

    private static readonly string DefaultRunnerPath =
        Path.Combine(AppDir, "runner", "voxtral_realtime_runner.exe");
    private static readonly string DefaultModelPath =
        Path.Combine(ModelsDir, "model.pte");
    private static readonly string DefaultPreprocessorPath =
        Path.Combine(ModelsDir, "preprocessor.pte");
    private static readonly string DefaultDataPath =
        Path.Combine(ModelsDir, "aoti_cuda_blob.ptd");
    private static readonly string DefaultTokenizerPath =
        Path.Combine(ModelsDir, "tekken.json");

    [ObservableProperty] private string _runnerPath = DefaultRunnerPath;
    [ObservableProperty] private string _modelPath = DefaultModelPath;
    [ObservableProperty] private string _tokenizerPath = DefaultTokenizerPath;
    [ObservableProperty] private string _preprocessorPath = DefaultPreprocessorPath;
    [ObservableProperty] private string _dataPath = DefaultDataPath;
    [ObservableProperty] private double _silenceThreshold = 0.008;
    [ObservableProperty] private double _silenceTimeoutSeconds = 2.0;

    public SettingsViewModel()
    {
        Load();
    }

    partial void OnRunnerPathChanged(string value) => Save();
    partial void OnModelPathChanged(string value) => Save();
    partial void OnTokenizerPathChanged(string value) => Save();
    partial void OnPreprocessorPathChanged(string value) => Save();
    partial void OnDataPathChanged(string value) => Save();
    partial void OnSilenceThresholdChanged(double value) => Save();
    partial void OnSilenceTimeoutSecondsChanged(double value) => Save();

    private void Save()
    {
        PersistenceService.Save(PersistenceService.SettingsPath, new SettingsData
        {
            RunnerPath = RunnerPath,
            ModelPath = ModelPath,
            TokenizerPath = TokenizerPath,
            PreprocessorPath = PreprocessorPath,
            DataPath = DataPath,
            SilenceThreshold = SilenceThreshold,
            SilenceTimeoutSeconds = SilenceTimeoutSeconds
        });
    }

    private void Load()
    {
        var data = PersistenceService.Load<SettingsData>(PersistenceService.SettingsPath);
        if (data == null) return;

        // Only restore saved paths if the files actually exist;
        // otherwise fall back to defaults (triggers auto-download).
        RunnerPath = FileOrDefault(data.RunnerPath, DefaultRunnerPath);
        ModelPath = FileOrDefault(data.ModelPath, DefaultModelPath);
        TokenizerPath = FileOrDefault(data.TokenizerPath, DefaultTokenizerPath);
        PreprocessorPath = FileOrDefault(data.PreprocessorPath, DefaultPreprocessorPath);
        DataPath = FileOrDefault(data.DataPath, DefaultDataPath);
        SilenceThreshold = data.SilenceThreshold;
        SilenceTimeoutSeconds = data.SilenceTimeoutSeconds;
    }

    private static string FileOrDefault(string? saved, string fallback)
    {
        if (!string.IsNullOrEmpty(saved) && File.Exists(saved)) return saved;
        return fallback;
    }

    private class SettingsData
    {
        [JsonPropertyName("runnerPath")] public string? RunnerPath { get; set; }
        [JsonPropertyName("modelPath")] public string? ModelPath { get; set; }
        [JsonPropertyName("tokenizerPath")] public string? TokenizerPath { get; set; }
        [JsonPropertyName("preprocessorPath")] public string? PreprocessorPath { get; set; }
        [JsonPropertyName("dataPath")] public string? DataPath { get; set; }
        [JsonPropertyName("silenceThreshold")] public double SilenceThreshold { get; set; } = 0.008;
        [JsonPropertyName("silenceTimeoutSeconds")] public double SilenceTimeoutSeconds { get; set; } = 2.0;
    }
}
