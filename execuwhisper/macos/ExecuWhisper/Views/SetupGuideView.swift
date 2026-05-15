/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

struct SetupGuideView: View {
    @Environment(TranscriptStore.self) private var store
    @Environment(Preferences.self) private var preferences
    @Environment(ModelDownloader.self) private var downloader

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "arrow.down.circle")
                .font(.system(size: 48))
                .foregroundStyle(Color.accentColor)

            Text("Preparing ExecuWhisper")
                .font(.title2.bold())

            if let result = store.healthResult {
                VStack(alignment: .leading, spacing: 12) {
                    checkRow("Helper binary", ok: result.runnerAvailable)
                    checkRow("model.pte", ok: result.modelAvailable)
                    checkRow("tokenizer.model", ok: result.tokenizerAvailable)
                }
                .padding()
                .background(.background.secondary, in: RoundedRectangle(cornerRadius: 8))
            }

            if downloader.isDownloading {
                VStack(spacing: 10) {
                    ProgressView(value: max(downloader.overallProgress, 0.02))
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
            } else if store.healthResult?.shouldOfferModelDownload == true {
                Text("The app downloads the Parakeet ASR model and LFM2.5 formatter artifacts into Application Support the first time it launches.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)

                Button("Retry Download") {
                    Task { await store.downloadModel() }
                }
                .buttonStyle(.borderedProminent)
            } else {
                Text("The Parakeet helper is missing or invalid. Build it locally or choose a valid binary path in Settings before retrying.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            developerInstructions

            Button("Recheck") {
                Task { await store.runHealthCheck() }
            }
            .buttonStyle(.bordered)
        }
        .padding(40)
        .frame(maxWidth: 560)
    }

    private func checkRow(_ label: String, ok: Bool) -> some View {
        HStack {
            Image(systemName: ok ? "checkmark.circle.fill" : "xmark.circle.fill")
                .foregroundStyle(ok ? .green : .red)
            Text(label)
            Spacer()
        }
    }

    private var developerInstructions: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Developer notes")
                .font(.headline)

            Text("""
            cd ~/executorch
            gh pr checkout https://github.com/pytorch/executorch/pull/18861
            conda activate et-metal
            make parakeet-metal
            conda activate et-mlx
            make lfm_2_5_formatter-mlx
            """)
            .font(.system(.caption, design: .monospaced))
            .textSelection(.enabled)
            .padding(8)
            .background(.background.tertiary, in: RoundedRectangle(cornerRadius: 4))

            Text("Helper path:")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(preferences.runnerPath)
                .font(.system(.caption, design: .monospaced))
                .textSelection(.enabled)

            Text("Download location:")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(preferences.modelDirectory)
                .font(.system(.caption, design: .monospaced))
                .textSelection(.enabled)

            Text("Formatter location:")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(preferences.formatterModelDirectory)
                .font(.system(.caption, design: .monospaced))
                .textSelection(.enabled)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
