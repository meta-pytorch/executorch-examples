// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Collections.Concurrent;
using System.IO;
using NAudio.CoreAudioApi;
using NAudio.Wave;

namespace VoxtralRealtime.Services;

public class AudioCaptureService : IDisposable
{
    private WasapiCapture? _capture;
    private Stream? _outputStream;
    private bool _disposed;
    private volatile bool _piping; // true = write audio to pipe, false = discard
    private int _writeErrorCount;
    private long _totalBytesWritten;

    // Resampling state
    private int _nativeSampleRate;
    private int _nativeChannels;
    private int _nativeBytesPerSample;
    private bool _nativeIsFloat;

    private readonly ConcurrentQueue<byte[]> _writeQueue = new();
    private readonly ManualResetEventSlim _dataAvailable = new(false);
    private CancellationTokenSource? _writerCts;
    private Thread? _writerThread;

    public event Action<float>? LevelChanged;

    /// <summary>
    /// Initialize and start the audio device. Call once during model loading.
    /// Audio is captured but discarded until StartPiping() is called.
    /// </summary>
    public void InitAndStartDevice()
    {
        _capture = new WasapiCapture(WasapiCapture.GetDefaultCaptureDevice(), false, 80);

        var fmt = _capture.WaveFormat;
        _nativeSampleRate = fmt.SampleRate;
        _nativeChannels = fmt.Channels;
        _nativeBytesPerSample = fmt.BitsPerSample / 8;
        _nativeIsFloat = fmt.Encoding == WaveFormatEncoding.IeeeFloat;

        _capture.DataAvailable += OnDataAvailable;

        AppLogger.Log("Audio", $"WASAPI initialized: {_nativeSampleRate}Hz, {fmt.BitsPerSample}bit, {_nativeChannels}ch");

        _capture.StartRecording();
        AppLogger.Log("Audio", "Recording started (pre-warm, muted)");
    }

    /// <summary>
    /// Start piping captured audio to the runner's stdin.
    /// </summary>
    public void StartPiping(Stream outputStream)
    {
        _outputStream = outputStream;
        _writeErrorCount = 0;

        if (_writerThread == null || !_writerThread.IsAlive)
        {
            _writerCts = new CancellationTokenSource();
            _writerThread = new Thread(WriterLoop)
            {
                Name = "AudioPipeWriter",
                IsBackground = true
            };
            _writerThread.Start();
        }

        _piping = true;
        AppLogger.Log("Audio", "Piping enabled - audio flowing to runner");
    }

    /// <summary>
    /// Stop piping audio (mute). Device stays alive for instant resume.
    /// </summary>
    public void StopPiping()
    {
        _piping = false;
        AppLogger.Log("Audio", "Piping disabled (device still running)");
    }

    private void OnDataAvailable(object? sender, WaveInEventArgs e)
    {
        if (e.BytesRecorded == 0) return;

        // Always convert and compute level (for UI responsiveness)
        var resampled = ConvertTo16kMonoFloat(e.Buffer, e.BytesRecorded);
        if (resampled.Length == 0) return;

        ComputeAndReportLevel(resampled);

        // Only enqueue for writing when piping is enabled
        if (_piping)
        {
            _writeQueue.Enqueue(resampled);
            _dataAvailable.Set();
        }
    }

    private byte[] ConvertTo16kMonoFloat(byte[] buffer, int bytesRecorded)
    {
        int frameSize = _nativeChannels * _nativeBytesPerSample;
        int frameCount = bytesRecorded / frameSize;
        if (frameCount == 0) return Array.Empty<byte>();

        // Extract mono float samples from native format (take first channel)
        var monoSamples = new float[frameCount];
        for (int i = 0; i < frameCount; i++)
        {
            int offset = i * frameSize;
            float sample;

            if (_nativeIsFloat && _nativeBytesPerSample == 4)
            {
                sample = BitConverter.ToSingle(buffer, offset);
            }
            else if (_nativeBytesPerSample == 2)
            {
                sample = BitConverter.ToInt16(buffer, offset) / 32768f;
            }
            else if (_nativeBytesPerSample == 3)
            {
                int val = buffer[offset] | (buffer[offset + 1] << 8) | (buffer[offset + 2] << 16);
                if ((val & 0x800000) != 0) val |= unchecked((int)0xFF000000);
                sample = val / 8388608f;
            }
            else
            {
                sample = 0;
            }

            monoSamples[i] = sample;
        }

        // Resample from native rate to 16kHz using linear interpolation
        double ratio = (double)_nativeSampleRate / 16000.0;
        int outputSamples = (int)(frameCount / ratio);
        if (outputSamples == 0) return Array.Empty<byte>();

        var result = new byte[outputSamples * 4];
        for (int i = 0; i < outputSamples; i++)
        {
            double srcPos = i * ratio;
            int srcIdx = (int)srcPos;
            double frac = srcPos - srcIdx;

            float s0 = monoSamples[Math.Min(srcIdx, frameCount - 1)];
            float s1 = monoSamples[Math.Min(srcIdx + 1, frameCount - 1)];
            float interpolated = (float)(s0 + (s1 - s0) * frac);

            BitConverter.TryWriteBytes(result.AsSpan(i * 4), interpolated);
        }

        return result;
    }

    private void WriterLoop()
    {
        var ct = _writerCts!.Token;
        try
        {
            while (!ct.IsCancellationRequested)
            {
                _dataAvailable.Wait(ct);
                _dataAvailable.Reset();

                while (_writeQueue.TryDequeue(out var data))
                {
                    try
                    {
                        _outputStream?.Write(data, 0, data.Length);
                        _totalBytesWritten += data.Length;

                        if (_totalBytesWritten % 64000 < data.Length)
                            AppLogger.Log("Audio", $"Written {_totalBytesWritten} bytes ({_totalBytesWritten / 64000}s @ 16kHz)");
                    }
                    catch (IOException ex)
                    {
                        _writeErrorCount++;
                        if (_writeErrorCount <= 3)
                            AppLogger.Log("Audio", $"Pipe write failed (#{_writeErrorCount}): {ex.Message}");
                        if (_writeErrorCount > 10) return;
                    }
                }
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex)
        {
            AppLogger.Log("Audio", $"Writer loop error: {ex.Message}");
        }
    }

    private void ComputeAndReportLevel(byte[] buffer)
    {
        int sampleCount = buffer.Length / 4;
        if (sampleCount == 0) return;

        float sumSquares = 0;
        for (int i = 0; i < sampleCount; i++)
        {
            float sample = BitConverter.ToSingle(buffer, i * 4);
            sumSquares += sample * sample;
        }
        float rms = MathF.Sqrt(sumSquares / sampleCount);
        LevelChanged?.Invoke(rms);
    }

    public void Stop()
    {
        _piping = false;
        _writerCts?.Cancel();
        _dataAvailable.Set();
        _writerThread?.Join(2000);
        _writerThread = null;
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        Stop();
        try { _capture?.StopRecording(); } catch { }
        _capture?.Dispose();
        _writerCts?.Dispose();
        _dataAvailable.Dispose();
        _capture = null;
        _outputStream = null;
    }
}
