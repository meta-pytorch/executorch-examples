/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

struct TranscriptView: View {
    let text: String
    let isLive: Bool
    var isRecording: Bool = false
    var isTranscribing: Bool = false
    var audioLevel: Float = 0
    var statusMessage: String = ""

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if text.isEmpty && isLive {
                        livePlaceholder
                    } else {
                        Text(text)
                            .font(.body)
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding()
                    }

                    Color.clear
                        .frame(height: 1)
                        .id("bottom")
                }
            }
            .onChange(of: text) {
                withAnimation(.easeOut(duration: 0.15)) {
                    proxy.scrollTo("bottom", anchor: .bottom)
                }
            }
            .onAppear {
                proxy.scrollTo("bottom", anchor: .bottom)
            }
        }
        .overlay(alignment: .bottom) {
            if isLive {
                statusIndicator
                    .padding(.bottom, 12)
            }
        }
    }

    private var livePlaceholder: some View {
        VStack(spacing: 16) {
            Spacer()

            if isRecording {
                AudioLevelView(level: audioLevel)
            } else {
                ProgressView()
                    .controlSize(.large)
            }

            Text(isRecording ? "Recording..." : "Transcribing...")
                .font(.title3)
                .foregroundStyle(.secondary)

            if !statusMessage.isEmpty {
                Text(statusMessage)
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
    }

    private var statusIndicator: some View {
        HStack(spacing: 8) {
            if isRecording {
                AudioLevelView(level: audioLevel, barCount: 12)
                Text("Recording")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else if isTranscribing {
                ProgressView()
                    .controlSize(.small)
                Text(statusMessage.isEmpty ? "Transcribing" : statusMessage)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(.ultraThinMaterial, in: Capsule())
    }
}
