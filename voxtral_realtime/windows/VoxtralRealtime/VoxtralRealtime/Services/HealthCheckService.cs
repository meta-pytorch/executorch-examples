// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.IO;
using NAudio.Wave;

namespace VoxtralRealtime.Services;

public record HealthCheckResult(
    bool RunnerAvailable,
    bool ModelAvailable,
    bool PreprocessorAvailable,
    bool TokenizerAvailable,
    bool DataPathAvailable,
    bool MicrophoneAvailable)
{
    public bool FilesReady => RunnerAvailable && ModelAvailable
        && PreprocessorAvailable && TokenizerAvailable && DataPathAvailable;
    public bool AllGood => FilesReady && MicrophoneAvailable;

    public List<string> MissingFiles
    {
        get
        {
            var missing = new List<string>();
            if (!RunnerAvailable) missing.Add("Runner binary");
            if (!ModelAvailable) missing.Add("Model file");
            if (!PreprocessorAvailable) missing.Add("Preprocessor");
            if (!TokenizerAvailable) missing.Add("Tokenizer");
            if (!DataPathAvailable) missing.Add("CUDA data blob");
            return missing;
        }
    }
}

public static class HealthCheckService
{
    public static HealthCheckResult Run(
        string runnerPath,
        string modelPath,
        string tokenizerPath,
        string preprocessorPath,
        string dataPath)
    {
        bool micAvailable;
        try
        {
            micAvailable = WaveInEvent.DeviceCount > 0;
        }
        catch
        {
            micAvailable = false;
        }

        return new HealthCheckResult(
            RunnerAvailable: File.Exists(runnerPath),
            ModelAvailable: File.Exists(modelPath),
            PreprocessorAvailable: File.Exists(preprocessorPath),
            TokenizerAvailable: File.Exists(tokenizerPath),
            DataPathAvailable: File.Exists(dataPath),
            MicrophoneAvailable: micAvailable
        );
    }
}
