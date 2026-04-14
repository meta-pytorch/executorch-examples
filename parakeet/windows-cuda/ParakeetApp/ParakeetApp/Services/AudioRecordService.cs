// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.IO;
using NAudio.CoreAudioApi;
using NAudio.Wave;

namespace ParakeetApp.Services;

public class AudioRecordService : IDisposable
{
    private WasapiCapture? _capture;
    private WaveFileWriter? _writer;
    private bool _disposed;
    private bool _recording;
    private readonly WaveFormat _targetFormat = new(16000, 16, 1);

    private int _nativeSampleRate;
    private int _nativeChannels;
    private int _nativeBytesPerSample;
    private bool _nativeIsFloat;

    public event Action<float>? LevelChanged;

    public string? CurrentFilePath { get; private set; }

    public void StartRecording(string outputPath)
    {
        if (_recording) throw new InvalidOperationException("Already recording");

        CurrentFilePath = outputPath;
        Directory.CreateDirectory(Path.GetDirectoryName(outputPath)!);

        _capture = new WasapiCapture(WasapiCapture.GetDefaultCaptureDevice(), false, 80);

        var fmt = _capture.WaveFormat;
        _nativeSampleRate = fmt.SampleRate;
        _nativeChannels = fmt.Channels;
        _nativeBytesPerSample = fmt.BitsPerSample / 8;
        _nativeIsFloat = fmt.Encoding == WaveFormatEncoding.IeeeFloat;

        AppLogger.Log("Audio", $"WASAPI: {_nativeSampleRate}Hz, {fmt.BitsPerSample}bit, {_nativeChannels}ch");

        _writer = new WaveFileWriter(outputPath, _targetFormat);
        _capture.DataAvailable += OnDataAvailable;

        _capture.StartRecording();
        _recording = true;
        AppLogger.Log("Audio", $"Recording started -> {outputPath}");
    }

    public double StopRecording()
    {
        if (!_recording) return 0;
        _recording = false;

        try { _capture?.StopRecording(); } catch { }
        _capture?.Dispose();
        _capture = null;

        double duration = 0;
        if (_writer != null)
        {
            duration = _writer.TotalTime.TotalSeconds;
            _writer.Flush();
            _writer.Dispose();
            _writer = null;
        }

        LevelChanged?.Invoke(0);
        AppLogger.Log("Audio", $"Recording stopped. Duration: {duration:F1}s, File: {CurrentFilePath}");
        return duration;
    }

    private void OnDataAvailable(object? sender, WaveInEventArgs e)
    {
        if (e.BytesRecorded == 0 || !_recording) return;

        var samples16k = ConvertTo16kMono16bit(e.Buffer, e.BytesRecorded);
        if (samples16k.Length == 0) return;

        ComputeAndReportLevel(e.Buffer, e.BytesRecorded);

        try
        {
            _writer?.Write(samples16k, 0, samples16k.Length);
        }
        catch (Exception ex)
        {
            AppLogger.Log("Audio", $"Write error: {ex.Message}");
        }
    }

    private byte[] ConvertTo16kMono16bit(byte[] buffer, int bytesRecorded)
    {
        int frameSize = _nativeChannels * _nativeBytesPerSample;
        int frameCount = bytesRecorded / frameSize;
        if (frameCount == 0) return Array.Empty<byte>();

        var monoSamples = new float[frameCount];
        for (int i = 0; i < frameCount; i++)
        {
            int offset = i * frameSize;
            float sample;

            if (_nativeIsFloat && _nativeBytesPerSample == 4)
                sample = BitConverter.ToSingle(buffer, offset);
            else if (_nativeBytesPerSample == 2)
                sample = BitConverter.ToInt16(buffer, offset) / 32768f;
            else if (_nativeBytesPerSample == 3)
            {
                int val = buffer[offset] | (buffer[offset + 1] << 8) | (buffer[offset + 2] << 16);
                if ((val & 0x800000) != 0) val |= unchecked((int)0xFF000000);
                sample = val / 8388608f;
            }
            else
                sample = 0;

            monoSamples[i] = sample;
        }

        double ratio = (double)_nativeSampleRate / 16000.0;
        int outputSamples = (int)(frameCount / ratio);
        if (outputSamples == 0) return Array.Empty<byte>();

        var result = new byte[outputSamples * 2];
        for (int i = 0; i < outputSamples; i++)
        {
            double srcPos = i * ratio;
            int srcIdx = (int)srcPos;
            double frac = srcPos - srcIdx;

            float s0 = monoSamples[Math.Min(srcIdx, frameCount - 1)];
            float s1 = monoSamples[Math.Min(srcIdx + 1, frameCount - 1)];
            float interpolated = (float)(s0 + (s1 - s0) * frac);

            short pcm = (short)Math.Clamp(interpolated * 32768f, short.MinValue, short.MaxValue);
            BitConverter.TryWriteBytes(result.AsSpan(i * 2), pcm);
        }

        return result;
    }

    private void ComputeAndReportLevel(byte[] buffer, int bytesRecorded)
    {
        int frameSize = _nativeChannels * _nativeBytesPerSample;
        int frameCount = bytesRecorded / frameSize;
        if (frameCount == 0) return;

        float sumSquares = 0;
        for (int i = 0; i < frameCount; i++)
        {
            int offset = i * frameSize;
            float sample;
            if (_nativeIsFloat && _nativeBytesPerSample == 4)
                sample = BitConverter.ToSingle(buffer, offset);
            else if (_nativeBytesPerSample == 2)
                sample = BitConverter.ToInt16(buffer, offset) / 32768f;
            else
                sample = 0;
            sumSquares += sample * sample;
        }

        float rms = MathF.Sqrt(sumSquares / frameCount);
        LevelChanged?.Invoke(rms);
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        if (_recording) StopRecording();
        _capture?.Dispose();
        _writer?.Dispose();
    }
}
