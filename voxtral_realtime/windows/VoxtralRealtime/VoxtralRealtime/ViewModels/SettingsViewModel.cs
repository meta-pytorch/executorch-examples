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
    private static readonly string DefaultRunnerPath = Path.Combine(
        @"C:\Users\younghan\project\executorch",
        @"cmake-out\examples\models\voxtral_realtime\Release\voxtral_realtime_runner.exe");

    private static readonly string DefaultModelPath = Path.Combine(
        @"C:\Users\younghan\project\executorch",
        @"voxtral_rt_exports_wsl\model.pte");

    private static readonly string DefaultPreprocessorPath = Path.Combine(
        @"C:\Users\younghan\project\executorch",
        @"voxtral_rt_exports_wsl\preprocessor.pte");

    private static readonly string DefaultDataPath = Path.Combine(
        @"C:\Users\younghan\project\executorch",
        @"voxtral_rt_exports_wsl\aoti_cuda_blob.ptd");

    private static readonly string DefaultTokenizerPath =
        @"C:\Users\younghan\models\Voxtral-Mini-4B-Realtime-2602\tekken.json";

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

        RunnerPath = data.RunnerPath ?? DefaultRunnerPath;
        ModelPath = data.ModelPath ?? DefaultModelPath;
        TokenizerPath = data.TokenizerPath ?? DefaultTokenizerPath;
        PreprocessorPath = data.PreprocessorPath ?? DefaultPreprocessorPath;
        DataPath = data.DataPath ?? DefaultDataPath;
        SilenceThreshold = data.SilenceThreshold;
        SilenceTimeoutSeconds = data.SilenceTimeoutSeconds;
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
