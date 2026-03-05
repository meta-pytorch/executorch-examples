/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

struct WelcomeView: View {
    @Environment(TranscriptStore.self) private var store

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "waveform")
                .font(.system(size: 56))
                .foregroundStyle(.secondary)

            Text("Voxtral Realtime")
                .font(.title.bold())

            Text("On-device speech transcription powered by ExecuTorch")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            Spacer().frame(height: 8)

            modelSection

            if store.isModelReady {
                Button {
                    Task { await store.startTranscription() }
                } label: {
                    Label("Start Transcription", systemImage: "mic.fill")
                        .frame(minWidth: 180)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .keyboardShortcut("R", modifiers: [.command, .shift])
            }

            shortcutHints
        }
        .padding(40)
        .frame(maxWidth: 480)
    }

    @ViewBuilder
    private var modelSection: some View {
        switch store.modelState {
        case .unloaded:
            Button {
                Task { await store.preloadModel() }
            } label: {
                Label("Load Model", systemImage: "arrow.down.circle")
                    .frame(minWidth: 180)
            }
            .buttonStyle(.bordered)
            .controlSize(.large)

        case .loading:
            VStack(spacing: 12) {
                ProgressView()
                    .controlSize(.regular)
                Text(store.statusMessage)
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
            .padding()
            .frame(minWidth: 220)
            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 10))

        case .ready:
            HStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(.green)
                Text("Model loaded")
                    .foregroundStyle(.secondary)
                Button {
                    Task { await store.unloadModel() }
                } label: {
                    Label("Unload", systemImage: "xmark.circle")
                        .labelStyle(.iconOnly)
                        .font(.callout)
                }
                .buttonStyle(.plain)
                .foregroundStyle(.secondary)
                .help("Unload model to free resources")
            }
            .font(.callout)
        }
    }

    private var shortcutHints: some View {
        HStack(spacing: 16) {
            shortcutBadge("⌘⇧R", label: "Transcribe")
            shortcutBadge("⌘.", label: "Pause")
            shortcutBadge("⌘↩", label: "Done")
        }
        .padding(.top, 8)
    }

    private func shortcutBadge(_ shortcut: String, label: String) -> some View {
        VStack(spacing: 4) {
            Text(shortcut)
                .font(.caption.monospaced())
                .padding(.horizontal, 6)
                .padding(.vertical, 3)
                .background(.quaternary, in: RoundedRectangle(cornerRadius: 4))
            Text(label)
                .font(.caption2)
                .foregroundStyle(.tertiary)
        }
    }
}
