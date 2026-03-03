import SwiftUI

struct DictationOverlayView: View {
    var store: TranscriptStore

    private var lineCount: Int {
        let text = store.dictationText
        guard !text.isEmpty else { return 0 }
        let width: CGFloat = 268 // 300 - 32 padding
        let font = NSFont.preferredFont(forTextStyle: .callout)
        let attrs: [NSAttributedString.Key: Any] = [.font: font]
        let size = (text as NSString).boundingRect(
            with: CGSize(width: width, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: attrs
        )
        return max(1, Int(ceil(size.height / font.pointSize)))
    }

    private var textHeight: CGFloat {
        let lines = lineCount
        if lines <= 2 { return 40 }
        return min(CGFloat(lines) * 18, 200)
    }

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
                    .frame(height: textHeight)
                    .onChange(of: store.dictationText) {
                        withAnimation(.easeOut(duration: 0.15)) {
                            proxy.scrollTo("end", anchor: .bottom)
                        }
                    }
                }
            }

            Text("⌃Space to finish")
                .font(.caption2)
                .foregroundStyle(.tertiary)
        }
        .padding(16)
        .frame(width: 300)
        .animation(.easeInOut(duration: 0.2), value: textHeight)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(.quaternary, lineWidth: 0.5)
        )
    }
}
