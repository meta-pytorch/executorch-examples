import SwiftUI

struct DictationOverlayView: View {
    var store: TranscriptStore

    var body: some View {
        VStack(spacing: 10) {
            AudioLevelView(level: store.audioLevel, barCount: 20)
                .frame(height: 36)

            if store.dictationText.isEmpty {
                Text("Listening...")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        Text(store.dictationText)
                            .font(.callout)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Color.clear.frame(height: 1).id("end")
                    }
                    .frame(maxHeight: 60)
                    .onChange(of: store.dictationText) {
                        proxy.scrollTo("end", anchor: .bottom)
                    }
                }
            }

            Text("⌃Space to finish")
                .font(.caption2)
                .foregroundStyle(.tertiary)
        }
        .padding(16)
        .frame(width: 300)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(.quaternary, lineWidth: 0.5)
        )
    }
}
