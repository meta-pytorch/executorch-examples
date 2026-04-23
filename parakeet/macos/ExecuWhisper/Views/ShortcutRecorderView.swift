/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import AppKit
import Carbon.HIToolbox
import SwiftUI

struct ShortcutRecorderView: View {
    @Binding var shortcut: DictationShortcut
    let onChange: () -> Void

    @State private var isRecording = false
    @State private var keyMonitor: Any?

    var body: some View {
        HStack(spacing: 8) {
            Button(isRecording ? "Type shortcut" : shortcut.displayString) {
                if isRecording {
                    stopRecording()
                } else {
                    beginRecording()
                }
            }
            .buttonStyle(.bordered)
            .font(.system(.body, design: .monospaced))
            .frame(minWidth: 140)

            Button("Reset") {
                shortcut = .controlSpace
                onChange()
            }
            .controlSize(.small)

            if isRecording {
                Text("Press Esc to cancel")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .onDisappear {
            stopRecording()
        }
    }

    private func beginRecording() {
        guard !isRecording else { return }
        isRecording = true
        keyMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
            handleKeyDown(event)
            return nil
        }
    }

    private func stopRecording() {
        isRecording = false
        if let keyMonitor {
            NSEvent.removeMonitor(keyMonitor)
            self.keyMonitor = nil
        }
    }

    private func handleKeyDown(_ event: NSEvent) {
        if event.keyCode == UInt16(kVK_Escape) {
            stopRecording()
            return
        }

        guard let recordedShortcut = DictationShortcut(event: event) else {
            NSSound.beep()
            return
        }

        shortcut = recordedShortcut
        onChange()
        stopRecording()
    }
}
