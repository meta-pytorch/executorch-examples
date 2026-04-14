// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.IO;
using NAudio.Wave;

namespace ParakeetApp.Services;

public record HealthCheckResult(
    bool RunnerAvailable,
    bool ModelAvailable,
    bool TokenizerAvailable,
    bool DataPathAvailable,
    bool MicrophoneAvailable)
{
    public bool FilesReady => RunnerAvailable && ModelAvailable
        && TokenizerAvailable && DataPathAvailable;
    public bool AllGood => FilesReady && MicrophoneAvailable;

    public List<string> MissingFiles
    {
        get
        {
            var missing = new List<string>();
            if (!RunnerAvailable) missing.Add("Runner binary");
            if (!ModelAvailable) missing.Add("Model file (model.pte)");
            if (!TokenizerAvailable) missing.Add("Tokenizer (tokenizer.model)");
            if (!DataPathAvailable) missing.Add("CUDA data blob (aoti_cuda_blob.ptd)");
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
            TokenizerAvailable: File.Exists(tokenizerPath),
            DataPathAvailable: File.Exists(dataPath),
            MicrophoneAvailable: micAvailable
        );
    }
}
