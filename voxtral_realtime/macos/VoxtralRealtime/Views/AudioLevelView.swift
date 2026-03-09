/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

struct AudioLevelView: View {
    let level: Float
    let barCount: Int

    @State private var barHeights: [CGFloat]

    init(level: Float, barCount: Int = 24) {
        self.level = level
        self.barCount = barCount
        _barHeights = State(initialValue: Array(repeating: 0.08, count: barCount))
    }

    var body: some View {
        HStack(spacing: 2) {
            ForEach(0..<barCount, id: \.self) { i in
                RoundedRectangle(cornerRadius: 1.5)
                    .fill(barColor(for: barHeights[i]))
                    .frame(width: 3, height: max(3, barHeights[i] * 32))
            }
        }
        .frame(height: 36)
        .onChange(of: level) {
            updateBars()
        }
    }

    private func updateBars() {
        let normalized = CGFloat(min(level * 8, 1.0))
        withAnimation(.easeOut(duration: 0.08)) {
            for i in 0..<barCount {
                let distance = abs(CGFloat(i) - CGFloat(barCount) / 2) / CGFloat(barCount / 2)
                let randomVariation = CGFloat.random(in: 0.6...1.0)
                let envelope = 1.0 - (distance * 0.7)
                barHeights[i] = max(0.08, normalized * envelope * randomVariation)
            }
        }
    }

    private func barColor(for height: CGFloat) -> Color {
        if height > 0.7 { return .orange }
        if height > 0.15 { return .accentColor }
        return .secondary.opacity(0.4)
    }
}
