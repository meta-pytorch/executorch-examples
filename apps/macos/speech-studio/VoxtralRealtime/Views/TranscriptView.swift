import SwiftUI

struct TranscriptView: View {
    let text: String
    let isLive: Bool
    var isPaused: Bool = false
    var audioLevel: Float = 0
    var statusMessage: String = ""

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if text.isEmpty && isLive {
                        listeningPlaceholder
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
                copyButton
                    .padding(8)
            }
        }
        .overlay(alignment: .bottom) {
            if isLive || isPaused {
                statusIndicator
                    .padding(.bottom, 12)
            }
        }
    }

    private var listeningPlaceholder: some View {
        VStack(spacing: 16) {
            Spacer()
            AudioLevelView(level: audioLevel)
            Text("Listening...")
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

    private var statusIndicator: some View {
        HStack(spacing: 8) {
            if isPaused {
                Image(systemName: "pause.fill")
                    .font(.caption2)
                    .foregroundStyle(.orange)
                Text("Paused")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                AudioLevelView(level: audioLevel, barCount: 12)
                Text("Transcribing")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(.ultraThinMaterial, in: Capsule())
    }
}
