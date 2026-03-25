/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

struct WakeSettingsView: View {
    @Environment(Preferences.self) private var preferences
    @Environment(TranscriptStore.self) private var store

    var body: some View {
        @Bindable var prefs = preferences

        Form {
            Section {
                HStack(spacing: 12) {
                    Image(systemName: prefs.enableSileroVAD ? "ear.and.waveform" : "ear.badge.waveform")
                        .font(.title)
                        .foregroundStyle(prefs.enableSileroVAD ? .green : .secondary)
                        .frame(width: 40)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(prefs.enableSileroVAD ? "Wake is active" : "Wake is off")
                            .font(.headline)
                        Text(prefs.enableSileroVAD
                             ? "Say \"Hey \(prefs.wakeKeyword)\" to start dictation"
                             : "Enable to start dictation hands-free")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Toggle("", isOn: $prefs.enableSileroVAD)
                        .labelsHidden()
                        .toggleStyle(.switch)
                }
                .padding(.vertical, 4)

                Text("⌃⇧W to toggle")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }

            Section("Wake Phrase") {
                Toggle("Require wake phrase", isOn: $prefs.enableWakePhrase)
                    .disabled(!prefs.enableSileroVAD)

                LabeledContent("Keyword") {
                    HStack(spacing: 4) {
                        Text("Hey")
                            .foregroundStyle(.secondary)
                        TextField("torch", text: $prefs.wakeKeyword)
                            .textFieldStyle(.roundedBorder)
                            .frame(width: 140)
                    }
                }

                LabeledContent("Check window") {
                    VStack(alignment: .trailing, spacing: 4) {
                        Slider(value: $prefs.wakeCheckSeconds, in: 1.0...4.0, step: 0.5)
                            .frame(width: 200)
                        Text(String(format: "%.1f s", prefs.wakeCheckSeconds))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .monospacedDigit()
                    }
                }
            }

            Section("Detection") {
                LabeledContent("Speech threshold") {
                    VStack(alignment: .trailing, spacing: 4) {
                        Slider(value: $prefs.vadThreshold, in: 0.3...0.9, step: 0.05)
                            .frame(width: 200)
                        Text(String(format: "%.2f probability", prefs.vadThreshold))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .monospacedDigit()
                    }
                }

                LabeledContent("Hangover") {
                    VStack(alignment: .trailing, spacing: 4) {
                        Slider(value: $prefs.vadHangoverMilliseconds, in: 160...800, step: 40)
                            .frame(width: 200)
                        Text("\(Int(prefs.vadHangoverMilliseconds)) ms")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .monospacedDigit()
                    }
                }
            }

            Section("Paths") {
                LabeledContent("VAD runner") {
                    HStack {
                        TextField("silero_vad_stream_runner", text: $prefs.vadRunnerPath)
                            .textFieldStyle(.roundedBorder)
                        browseButton(for: $prefs.vadRunnerPath)
                    }
                }

                LabeledContent("VAD model") {
                    HStack {
                        TextField("silero_vad.pte", text: $prefs.vadModelPath)
                            .textFieldStyle(.roundedBorder)
                        browseButton(for: $prefs.vadModelPath)
                    }
                }
            }

            if let wakeState = store.wakeState as WakeState? {
                Section("Status") {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(statusColor(for: wakeState))
                            .frame(width: 8, height: 8)
                        Text(statusLabel(for: wakeState))
                            .font(.callout)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .formStyle(.grouped)
    }

    private func browseButton(for binding: Binding<String>) -> some View {
        Button("Browse...") {
            let panel = NSOpenPanel()
            panel.canChooseFiles = true
            panel.canChooseDirectories = false
            panel.allowsMultipleSelection = false
            if panel.runModal() == .OK, let url = panel.url {
                binding.wrappedValue = url.path(percentEncoded: false)
            }
        }
        .controlSize(.small)
    }

    private func statusColor(for state: WakeState) -> Color {
        switch state {
        case .disabled: .secondary
        case .listening: .green
        case .speechDetected: .orange
        case .checkingPhrase: .yellow
        case .active: .blue
        }
    }

    private func statusLabel(for state: WakeState) -> String {
        switch state {
        case .disabled: "Disabled"
        case .listening: "Listening for speech..."
        case .speechDetected: "Speech detected"
        case .checkingPhrase: "Checking wake phrase..."
        case .active: "Active dictation"
        }
    }
}
