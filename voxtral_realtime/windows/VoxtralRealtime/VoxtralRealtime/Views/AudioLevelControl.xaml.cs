// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shapes;

namespace VoxtralRealtime.Views;

public partial class AudioLevelControl : UserControl
{
    private const int BarCount = 20;
    private const double BarGap = 2;
    private readonly Rectangle[] _bars;
    private readonly Random _rng = new();
    private float _level;

    public float Level
    {
        get => _level;
        set { _level = value; UpdateBars(); }
    }

    public AudioLevelControl()
    {
        InitializeComponent();

        _bars = new Rectangle[BarCount];
        for (int i = 0; i < BarCount; i++)
        {
            _bars[i] = new Rectangle
            {
                Fill = new SolidColorBrush(Color.FromRgb(0x48, 0x48, 0x4A)),
                RadiusX = 2,
                RadiusY = 2
            };
            BarCanvas.Children.Add(_bars[i]);
        }

        SizeChanged += (_, _) => UpdateBars();
    }

    private void UpdateBars()
    {
        double canvasWidth = ActualWidth;
        double canvasHeight = ActualHeight;
        if (canvasWidth <= 0 || canvasHeight <= 0) return;

        double barWidth = (canvasWidth - (BarCount - 1) * BarGap) / BarCount;
        if (barWidth < 1) barWidth = 1;

        float normalizedLevel = Math.Min(_level * 10f, 1f); // Amplify for visibility

        for (int i = 0; i < BarCount; i++)
        {
            // Envelope: taller in center, shorter at edges
            double center = BarCount / 2.0;
            double distFromCenter = Math.Abs(i - center) / center;
            double envelope = 1.0 - distFromCenter * 0.6;

            // Random variation
            double variation = 0.5 + _rng.NextDouble() * 0.5;

            double height = canvasHeight * normalizedLevel * envelope * variation;
            height = Math.Max(height, 2); // minimum bar height

            _bars[i].Width = barWidth;
            _bars[i].Height = height;
            Canvas.SetLeft(_bars[i], i * (barWidth + BarGap));
            Canvas.SetTop(_bars[i], (canvasHeight - height) / 2);

            // Monochrome dark gray like macOS waveform
            _bars[i].Fill = new SolidColorBrush(Color.FromRgb(0x48, 0x48, 0x4A));
        }
    }
}
