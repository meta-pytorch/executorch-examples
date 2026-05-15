/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import AppKit
import SwiftUI

final class DictationPanel: NSPanel {
    init(contentView: some View) {
        super.init(
            contentRect: NSRect(x: 0, y: 0, width: 320, height: 140),
            styleMask: [.nonactivatingPanel, .fullSizeContentView, .borderless],
            backing: .buffered,
            defer: true
        )

        level = .floating
        isOpaque = false
        backgroundColor = .clear
        hasShadow = true
        isMovableByWindowBackground = true
        collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        animationBehavior = .utilityWindow

        let hosting = NSHostingView(rootView: contentView)
        hosting.frame = contentRect(forFrameRect: frame)
        self.contentView = hosting
    }

    func showCentered(on preferredScreen: NSScreen? = nil) {
        guard let screen = preferredScreen ?? NSScreen.main else { return }
        let screenFrame = screen.visibleFrame
        let x = screenFrame.midX - frame.width / 2
        let y = screenFrame.midY - frame.height / 2 + 100
        setFrameOrigin(NSPoint(x: x, y: y))
        orderFrontRegardless()
    }

    func dismiss() {
        orderOut(nil)
    }
}
