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
    var onExport: ((SessionExportFormat) -> Void)? = nil

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
        .overlay(alignment: .topTrailing) {
            if !text.isEmpty {
                HStack(spacing: 8) {
                    if let onExport {
                        exportButton(onExport)
                    }
                    copyButton
                }
                    .padding(8)
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

    private var copyButton: some View {
        Button {
            NSPasteboard.general.clearContents()
            NSPasteboard.general.setString(text, forType: .string)
        } label: {
            Label("Copy All", systemImage: "doc.on.doc")
                .font(.caption)
        }
        .buttonStyle(.bordered)
        .controlSize(.small)
        .keyboardShortcut("C", modifiers: [.command, .shift])
    }

    private func exportButton(_ action: @escaping (SessionExportFormat) -> Void) -> some View {
        Menu {
            ForEach(SessionExportFormat.allCases, id: \.rawValue) { format in
                Button(format.title) {
                    action(format)
                }
            }
        } label: {
            Label("Export", systemImage: "square.and.arrow.down")
                .font(.caption)
        }
        .menuStyle(.borderlessButton)
        .fixedSize()
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
