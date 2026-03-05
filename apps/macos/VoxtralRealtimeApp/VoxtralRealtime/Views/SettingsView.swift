import SwiftUI

struct SettingsView: View {
    @Environment(Preferences.self) private var preferences

    var body: some View {
        @Bindable var prefs = preferences

        TabView {
            Form {
                Section("Runner") {
                    LabeledContent("Binary path") {
                        HStack {
                            TextField("Path to voxtral_realtime_runner", text: $prefs.runnerPath)
                                .textFieldStyle(.roundedBorder)
                            browseButton(for: $prefs.runnerPath)
                        }
                    }
                }

                Section("Model") {
                    LabeledContent("Model directory") {
                        HStack {
                            TextField("Path to model artifacts", text: $prefs.modelDirectory)
                                .textFieldStyle(.roundedBorder)
                            browseButton(for: $prefs.modelDirectory, directory: true)
                        }
                    }

                    LabeledContent("Files") {
                        VStack(alignment: .leading, spacing: 4) {
                            fileStatus("model-metal-int4.pte", path: preferences.modelPath)
                            fileStatus("tekken.json", path: preferences.tokenizerPath)
                            fileStatus("preprocessor.pte", path: preferences.preprocessorPath)
                        }
                    }
                }
            }
            .formStyle(.grouped)
            .padding()
            .tabItem { Label("General", systemImage: "gear") }

            Form {
                Section("Silence Detection") {
                    LabeledContent("Silence threshold") {
                        VStack(alignment: .trailing, spacing: 4) {
                            Slider(value: $prefs.silenceThreshold, in: 0.005...0.1, step: 0.005)
                                .frame(width: 200)
                            Text(String(format: "%.3f RMS", preferences.silenceThreshold))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .monospacedDigit()
                        }
                    }

                    LabeledContent("Auto-stop delay") {
                        VStack(alignment: .trailing, spacing: 4) {
                            Slider(value: $prefs.silenceTimeout, in: 0.5...5.0, step: 0.5)
                                .frame(width: 200)
                            Text(String(format: "%.1fs after silence", preferences.silenceTimeout))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .monospacedDigit()
                        }
                    }

                    Text("Lower threshold = more sensitive (stops on softer sounds). Higher = only stops in true silence. Adjust based on your environment.")
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                }

                Section("Shortcut") {
                    LabeledContent("Dictation hotkey") {
                        Text("⌃Space")
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(.quaternary, in: RoundedRectangle(cornerRadius: 4))
                    }
                }
            }
            .formStyle(.grouped)
            .padding()
            .tabItem { Label("Dictation", systemImage: "mic.badge.plus") }
        }
        .frame(width: 500, height: 320)
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
