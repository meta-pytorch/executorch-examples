/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

struct DictationOverlayView: View {
    @Environment(TranscriptStore.self) private var store
    @Environment(DictationManager.self) private var dictationManager

    var body: some View {
        VStack(spacing: 10) {
            AudioLevelView(level: store.audioLevel, barCount: 20)
                .frame(height: 36)

            Text("\(dictationManager.hotKeyDisplayText) to finish")
                .font(.caption2)
                .foregroundStyle(.tertiary)
        }
        .padding(16)
        .frame(width: 300)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(.quaternary, lineWidth: 0.5)
        )
    }
}
