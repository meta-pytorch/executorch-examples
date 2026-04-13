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
    @Environment(ModelDownloader.self) private var downloader
    @Environment(Preferences.self) private var preferences
    var isDropTargeted: Bool = false

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "waveform")
                .font(.system(size: 56))
                .foregroundStyle(.secondary)

            Text("ExecuWhisper")
                .font(.title.bold())

            Text("On-device speech recognition powered by ExecuTorch")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            Spacer().frame(height: 8)

            modelSection

            if store.isModelReady {
                preloadSection
            }

            if store.isModelReady {
                Button {
                    Task { await store.startRecording() }
                } label: {
                    Label("Start Recording", systemImage: "mic.fill")
                        .frame(minWidth: 180)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .keyboardShortcut("R", modifiers: [.command, .shift])

                Button {
                    store.importAudioFileWithPanel()
                } label: {
                    Label("Import Audio...", systemImage: "square.and.arrow.down")
                        .frame(minWidth: 180)
                }
                .buttonStyle(.bordered)
                .controlSize(.large)

                dropHint
            }

            Text(preferences.modelDirectory)
                .font(.caption.monospaced())
                .foregroundStyle(.tertiary)
                .lineLimit(2)
                .multilineTextAlignment(.center)

            shortcutHints
        }
        .padding(40)
        .frame(maxWidth: 520)
        .background(backgroundStyle, in: RoundedRectangle(cornerRadius: 18))
        .overlay {
            RoundedRectangle(cornerRadius: 18)
                .strokeBorder(
                    isDropTargeted ? Color.accentColor : Color.secondary.opacity(0.16),
                    style: StrokeStyle(lineWidth: isDropTargeted ? 2 : 1, dash: isDropTargeted ? [10, 6] : [])
                )
        }
    }

    @ViewBuilder
    private var modelSection: some View {
        if store.healthResult?.runnerAvailable == false {
            VStack(spacing: 10) {
                Label("Helper setup required", systemImage: "wrench.and.screwdriver")
                    .font(.headline)
                Text("Build `parakeet_helper` or choose an existing binary in Settings before recording.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .padding()
            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 10))
        } else if downloader.isDownloading || store.modelState == .downloading {
            VStack(spacing: 12) {
                ProgressView(value: max(downloader.overallProgress, 0.02))
                    .frame(minWidth: 220)
                Text(downloader.statusMessage.isEmpty ? "Downloading model..." : downloader.statusMessage)
                    .font(.callout)
                if !downloader.currentFileName.isEmpty {
                    Text(downloader.currentFileName)
                        .font(.caption.monospaced())
                        .foregroundStyle(.secondary)
                }
            }
            .padding()
            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 10))
        } else if store.isModelReady {
            HStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(.green)
                Text("Model files ready")
                    .foregroundStyle(.secondary)
            }
            .font(.callout)
        } else if store.modelState == .checking {
            VStack(spacing: 12) {
                ProgressView()
                    .controlSize(.regular)
                Text("Checking model...")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
            .padding()
            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 10))
        } else if store.healthResult?.shouldOfferModelDownload == true {
            Button {
                Task { await store.downloadModel() }
            } label: {
                Label("Download Model", systemImage: "arrow.down.circle")
                    .frame(minWidth: 180)
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
        } else {
            VStack(spacing: 10) {
                Label("Model setup incomplete", systemImage: "exclamationmark.triangle")
                    .font(.headline)
                Text(store.statusMessage)
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
            .padding()
            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 10))
        }
    }

    @ViewBuilder
    private var preloadSection: some View {
        switch store.helperState {
        case .unloaded:
            VStack(spacing: 12) {
                Text("Preload the helper to reduce stop-to-text latency.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)

                Button {
                    Task { await store.preloadModel() }
                } label: {
                    Label("Preload Model", systemImage: "bolt.fill")
                        .frame(minWidth: 180)
                }
                .buttonStyle(.bordered)
            }
            .padding()
            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 10))

        case .loading:
            VStack(spacing: 12) {
                ProgressView()
                    .controlSize(.regular)
                Text(store.helperStatusMessage.isEmpty ? "Warming model..." : store.helperStatusMessage)
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
            .padding()
            .frame(minWidth: 220)
            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 10))

        case .warm:
            HStack(spacing: 8) {
                Image(systemName: "bolt.circle.fill")
                    .foregroundStyle(.green)
                Text("Model preloaded")
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
                .help("Unload the helper to free resources")
            }
            .font(.callout)

        case .failed:
            VStack(spacing: 12) {
                Label("Warmup failed", systemImage: "exclamationmark.triangle")
                    .foregroundStyle(.orange)
                Text(store.helperStatusMessage.isEmpty ? "The helper could not preload." : store.helperStatusMessage)
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                Button("Retry Preload") {
                    Task { await store.preloadModel() }
                }
                .buttonStyle(.bordered)
            }
            .padding()
            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 10))
        }
    }

    private var shortcutHints: some View {
        HStack(spacing: 16) {
            shortcutBadge("⌘⇧R", label: "Record / Stop")
            shortcutBadge("⌘⇧C", label: "Copy")
            shortcutBadge(preferences.dictationShortcut.displayString, label: "Dictation")
        }
        .padding(.top, 8)
    }

    private var dropHint: some View {
        VStack(spacing: 8) {
            Text(isDropTargeted ? "Drop audio to transcribe" : "Drop a WAV or MP3 file here to transcribe it")
                .font(.callout.weight(isDropTargeted ? .semibold : .regular))
                .foregroundStyle(isDropTargeted ? Color.accentColor : Color.secondary)
                .multilineTextAlignment(.center)
            Text("Imported transcripts are saved to History using the filename as the title.")
                .font(.caption)
                .foregroundStyle(.tertiary)
                .multilineTextAlignment(.center)
        }
        .padding(.top, 4)
    }

    private var backgroundStyle: some ShapeStyle {
        isDropTargeted ? AnyShapeStyle(.background.secondary) : AnyShapeStyle(.clear)
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
