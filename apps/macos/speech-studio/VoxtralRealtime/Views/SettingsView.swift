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
        }
        .frame(width: 500, height: 300)
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
