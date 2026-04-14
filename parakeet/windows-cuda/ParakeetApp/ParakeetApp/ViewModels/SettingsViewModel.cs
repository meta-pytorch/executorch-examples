// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.IO;
using System.Text.Json.Serialization;
using CommunityToolkit.Mvvm.ComponentModel;
using ParakeetApp.Services;

namespace ParakeetApp.ViewModels;

public partial class SettingsViewModel : ObservableObject
{
    private static readonly string AppDir = AppDomain.CurrentDomain.BaseDirectory;

    public static readonly string ModelsDir = Path.Combine(AppDir, "models");

    private static readonly string DefaultRunnerPath =
        Path.Combine(AppDir, "runner", "parakeet_runner.exe");
    private static readonly string DefaultModelPath =
        Path.Combine(ModelsDir, "model.pte");
    private static readonly string DefaultTokenizerPath =
        Path.Combine(ModelsDir, "tokenizer.model");
    private static readonly string DefaultDataPath =
        Path.Combine(ModelsDir, "aoti_cuda_blob.ptd");

    [ObservableProperty] private string _runnerPath = DefaultRunnerPath;
    [ObservableProperty] private string _modelPath = DefaultModelPath;
    [ObservableProperty] private string _tokenizerPath = DefaultTokenizerPath;
    [ObservableProperty] private string _dataPath = DefaultDataPath;

    public SettingsViewModel()
    {
        Load();
    }

    partial void OnRunnerPathChanged(string value) => Save();
    partial void OnModelPathChanged(string value) => Save();
    partial void OnTokenizerPathChanged(string value) => Save();
    partial void OnDataPathChanged(string value) => Save();

    private void Save()
    {
        PersistenceService.Save(PersistenceService.SettingsPath, new SettingsData
        {
            RunnerPath = RunnerPath,
            ModelPath = ModelPath,
            TokenizerPath = TokenizerPath,
            DataPath = DataPath
        });
    }

    private void Load()
    {
        var data = PersistenceService.Load<SettingsData>(PersistenceService.SettingsPath);
        if (data == null) return;

        RunnerPath = FileOrDefault(data.RunnerPath, DefaultRunnerPath);
        ModelPath = FileOrDefault(data.ModelPath, DefaultModelPath);
        TokenizerPath = FileOrDefault(data.TokenizerPath, DefaultTokenizerPath);
        DataPath = FileOrDefault(data.DataPath, DefaultDataPath);
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
        [JsonPropertyName("dataPath")] public string? DataPath { get; set; }
    }
}
