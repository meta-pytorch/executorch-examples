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

    private var isBundledApp: Bool {
        Bundle.main.resourcePath.map {
            FileManager.default.fileExists(atPath: "\($0)/voxtral_realtime_runner")
        } ?? false
    }

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 48))
                .foregroundStyle(.orange)

            Text("Setup Required")
                .font(.title2.bold())

            if let result = store.healthResult {
                VStack(alignment: .leading, spacing: 12) {
                    checkRow("Runner binary", ok: result.runnerAvailable)
                    checkRow("Model file", ok: result.modelAvailable)
                    checkRow("Preprocessor", ok: result.preprocessorAvailable)
                    checkRow("Tokenizer", ok: result.tokenizerAvailable)
                    checkRow("Microphone access", ok: result.micPermission == .authorized)
                }
                .padding()
                .background(.background.secondary, in: RoundedRectangle(cornerRadius: 8))

                if !result.missingFiles.isEmpty {
                    if isBundledApp {
                        bundledInstructions
                    } else {
                        developerInstructions
                    }
                }

                if result.micPermission == .notDetermined {
                    Button("Grant Microphone Access") {
                        Task {
                            _ = await HealthCheck.requestMicrophoneAccess()
                            await store.runHealthCheck()
                        }
                    }
                    .buttonStyle(.borderedProminent)
                } else if result.micPermission == .denied {
                    Text("Open System Settings → Privacy & Security → Microphone to grant access.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Button("Recheck") {
                Task { await store.runHealthCheck() }
            }
            .buttonStyle(.bordered)
        }
        .padding(40)
        .frame(maxWidth: 500)
    }

    private func checkRow(_ label: String, ok: Bool) -> some View {
        HStack {
            Image(systemName: ok ? "checkmark.circle.fill" : "xmark.circle.fill")
                .foregroundStyle(ok ? .green : .red)
            Text(label)
            Spacer()
        }
    }

    private var bundledInstructions: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("App installation may be incomplete")
                .font(.headline)

            Text("Model files are missing from the app bundle. Please download a complete DMG from the project releases or rebuild the app from source.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var developerInstructions: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Download model files:")
                .font(.headline)

            Text("""
            hf download mistralai/Voxtral-Mini-4B-Realtime-2602-Executorch \\
              --local-dir ~/voxtral_realtime_quant_metal
            """)
            .font(.system(.caption, design: .monospaced))
            .textSelection(.enabled)
            .padding(8)
            .background(.background.tertiary, in: RoundedRectangle(cornerRadius: 4))

            Text("Then configure paths in Settings (⌘,).")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}
