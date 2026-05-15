/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

struct SettingsView: View {
    @Environment(Preferences.self) private var preferences
    @Environment(DictationManager.self) private var dictationManager
    var usesFixedWindowSize = false

    var body: some View {
        @Bindable var prefs = preferences
        let availableMicrophones = AudioRecorder.availableInputDevices()
        let resolvedMicrophone = AudioRecorder.resolvePreferredMicrophone(
            selectedMicrophoneID: prefs.selectedMicrophoneID,
            availableDevices: availableMicrophones
        )

        Form {
            Section("Helper") {
                LabeledContent("Binary path") {
                    HStack {
                        TextField("Path to parakeet_helper", text: $prefs.runnerPath)
                            .textFieldStyle(.roundedBorder)
                        browseButton(for: $prefs.runnerPath)
                    }
                }
            }

            Section("Model Files") {
                LabeledContent("Model directory") {
                    HStack {
                        TextField("Path to downloaded model files", text: $prefs.modelDirectory)
                            .textFieldStyle(.roundedBorder)
                        browseButton(for: $prefs.modelDirectory, directory: true)
                    }
                }

                LabeledContent("Files") {
                    VStack(alignment: .leading, spacing: 4) {
                        fileStatus("model.pte", path: prefs.modelPath)
                        fileStatus("tokenizer.model", path: prefs.tokenizerPath)
                    }
                }
            }

            Section("Smart Formatting") {
                Toggle("Smart formatting with LFM2.5", isOn: $prefs.enableSmartFormatting)

                Text("Uses one smart prompt for final paste-ready text: clean up dictation, preserve meaning, and use bullets only when the transcript clearly reads as a list.")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                LabeledContent("Formatter helper") {
                    HStack {
                        TextField("Path to lfm25_formatter_helper", text: $prefs.formatterRunnerPath)
                            .textFieldStyle(.roundedBorder)
                        browseButton(for: $prefs.formatterRunnerPath)
                    }
                }

                LabeledContent("Formatter model directory") {
                    HStack {
                        TextField("Path to LFM2.5 formatter files", text: $prefs.formatterModelDirectory)
                            .textFieldStyle(.roundedBorder)
                        browseButton(for: $prefs.formatterModelDirectory, directory: true)
                    }
                }

                LabeledContent("Formatter files") {
                    VStack(alignment: .leading, spacing: 4) {
                        fileStatus("lfm2_5_350m_mlx_4w.pte", path: prefs.formatterModelPath)
                        fileStatus("tokenizer.json", path: prefs.formatterTokenizerPath)
                        fileStatus("tokenizer_config.json", path: prefs.formatterTokenizerConfigPath)
                    }
                }

                Text("If formatter assets are unavailable, ExecuWhisper falls back to replacement-only transcript text.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section("Audio Input") {
                LabeledContent("Microphone") {
                    Picker("Microphone", selection: $prefs.selectedMicrophoneID) {
                        Text("Auto (System Default)").tag("")
                        ForEach(availableMicrophones) { microphone in
                            Text(microphone.displayName).tag(microphone.id)
                        }
                    }
                    .labelsHidden()
                    .frame(width: 260)
                    .disabled(availableMicrophones.isEmpty)
                }

                LabeledContent("Status") {
                    Group {
                        if availableMicrophones.isEmpty {
                            Text("No audio inputs detected")
                                .foregroundStyle(.orange)
                        } else if prefs.selectedMicrophoneID.isEmpty {
                            Text("Using \(resolvedMicrophone?.device.displayName ?? "system default microphone")")
                                .foregroundStyle(.secondary)
                        } else if let resolvedMicrophone, resolvedMicrophone.usedFallback {
                            Text("Saved mic unavailable; using \(resolvedMicrophone.device.displayName)")
                                .foregroundStyle(.orange)
                        } else if let resolvedMicrophone {
                            Text("Using \(resolvedMicrophone.device.name)")
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                Text("Applies to manual recording and system dictation.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section("System Dictation") {
                Toggle("Enable dictation hotkey", isOn: $prefs.enableGlobalHotkey)

                LabeledContent("Shortcut") {
                    ShortcutRecorderView(shortcut: $prefs.dictationShortcut) {
                        dictationManager.refreshHotKeyRegistration()
                    }
                }

                LabeledContent("Hotkey status") {
                    VStack(alignment: .trailing, spacing: 4) {
                        Text(dictationManager.hotKeyStatusText)
                            .foregroundStyle(dictationManager.hotKeyRegistrationError == nil ? Color.secondary : Color.orange)
                        if dictationManager.hotKeyRegistrationError != nil {
                            Text("Common conflicts: macOS input source switcher uses Ctrl-Space; Spotlight uses Cmd-Space.")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.trailing)
                        }
                    }
                }

                LabeledContent("Accessibility") {
                    VStack(alignment: .trailing, spacing: 6) {
                        Text(DictationManager.checkAccessibility() ? "Enabled" : "Required for auto-paste")
                            .foregroundStyle(DictationManager.checkAccessibility() ? Color.secondary : Color.orange)
                        Text("Grant \(PasteController.helperIdentifier)")
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                        if !DictationManager.checkAccessibility() {
                            Button("Request Access") {
                                dictationManager.promptForAccessibilityAccess()
                            }
                            .controlSize(.small)
                        }
                    }
                }

                LabeledContent("Silence threshold") {
                    VStack(alignment: .trailing, spacing: 4) {
                        Slider(value: $prefs.silenceThreshold, in: 0.005...0.1, step: 0.005)
                            .frame(width: 200)
                        Text(String(format: "%.3f RMS", prefs.silenceThreshold))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .monospacedDigit()
                    }
                }

                LabeledContent("Auto-stop delay") {
                    VStack(alignment: .trailing, spacing: 4) {
                        Slider(value: $prefs.silenceTimeout, in: 0.5...5.0, step: 0.25)
                            .frame(width: 200)
                        Text(String(format: "%.2fs after silence", prefs.silenceTimeout))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .monospacedDigit()
                    }
                }
            }

            Section("Defaults") {
                Text("ExecuWhisper downloads the Hugging Face model into Application Support on first launch.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(preferences.downloadedModelDirectoryURL.path(percentEncoded: false))
                    .font(.system(.caption, design: .monospaced))
                    .textSelection(.enabled)
            }
        }
        .formStyle(.grouped)
        .padding()
        .frame(
            width: usesFixedWindowSize ? 640 : nil,
            height: usesFixedWindowSize ? 680 : nil
        )
        .onChange(of: prefs.enableGlobalHotkey) { _, _ in
            dictationManager.refreshHotKeyRegistration()
        }
    }

    private func browseButton(for binding: Binding<String>, directory: Bool = false) -> some View {
        Button("Browse...") {
            let panel = NSOpenPanel()
            panel.canChooseFiles = !directory
            panel.canChooseDirectories = directory
            panel.allowsMultipleSelection = false
            if panel.runModal() == .OK, let url = panel.url {
                binding.wrappedValue = url.path(percentEncoded: false)
            }
        }
        .controlSize(.small)
    }

    private func fileStatus(_ name: String, path: String) -> some View {
        let exists = FileManager.default.fileExists(atPath: path)
        return HStack(spacing: 4) {
            Image(systemName: exists ? "checkmark.circle.fill" : "xmark.circle.fill")
                .foregroundStyle(exists ? .green : .red)
                .font(.caption)
            Text(name)
                .font(.caption)
        }
    }
}
