/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Carbon.HIToolbox
import Foundation
import Testing

@MainActor
struct DictationManagerTests {
    @Test
    func dictationStartSetsListeningState() async {
        let manager = DictationManager.preview()

        await manager.beginPreviewDictation()

        #expect(manager.state == .listening)
        #expect(manager.overlayStatusText.isEmpty)
    }

    @Test
    func beginPreviewTranscriptionSetsTranscribingState() async {
        let manager = DictationManager.preview()

        await manager.beginPreviewTranscription()

        #expect(manager.state == .transcribing)
        #expect(manager.overlayStatusText.isEmpty)
    }

    @Test
    func finishPreviewDictationReturnsToIdle() async {
        let manager = DictationManager.preview()
        await manager.beginPreviewDictation()

        await manager.finishPreviewDictation()

        #expect(manager.state == .idle)
    }

    @Test
    func silenceTimeoutSchedulesStopRequestAsynchronously() async {
        var didRequestStop = false
        let manager = DictationManager.preview {
            didRequestStop = true
        }
        await manager.beginPreviewDictation()

        manager.triggerSilenceTimeoutForTesting()
        await Task.yield()

        #expect(didRequestStop)
    }

    @Test
    func hotKeyStatusUsesConfiguredShortcutDisplay() {
        let suiteName = "DictationManagerTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let preferences = Preferences(defaults: defaults)
        preferences.dictationShortcut = DictationShortcut(
            keyCode: UInt32(kVK_ANSI_D),
            carbonModifiers: UInt32(controlKey | shiftKey),
            keyDisplay: "D"
        )

        let manager = DictationManager(preferences: preferences)

        #expect(manager.hotKeyStatusText == "⌃⇧D ready")
    }
}
