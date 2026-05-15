/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation
import Testing

@MainActor
struct PreferencesFormattingTests {
    @Test
    func formatterDefaultsUseSmartFormattingAndDownloadedModelDirectory() {
        let suiteName = "formatter-defaults-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)

        let preferences = Preferences(defaults: defaults)

        #expect(preferences.enableSmartFormatting)
        #expect(preferences.formatterModelDirectory == preferences.downloadedFormatterModelDirectoryURL.path(percentEncoded: false))
        #expect(preferences.formatterModelPath.hasSuffix("lfm2_5_350m_mlx_4w.pte"))
        #expect(preferences.formatterTokenizerPath.hasSuffix("tokenizer.json"))
        #expect(preferences.formatterTokenizerConfigPath.hasSuffix("tokenizer_config.json"))
    }

    @Test
    func formatterSettingsPersistAcrossPreferencesInstances() {
        let suiteName = "formatter-persistence-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)

        let first = Preferences(defaults: defaults)
        first.enableSmartFormatting = false
        first.formatterRunnerPath = "/tmp/lfm25_formatter_helper"
        let formatterDirectory = URL(fileURLWithPath: "/tmp/lfm25-\(UUID().uuidString)", isDirectory: true)
        try? FileManager.default.createDirectory(at: formatterDirectory, withIntermediateDirectories: true)
        try? Data("pte".utf8).write(to: formatterDirectory.appendingPathComponent("lfm2_5_350m_mlx_4w.pte"))
        try? Data("tokenizer".utf8).write(to: formatterDirectory.appendingPathComponent("tokenizer.json"))
        try? Data("config".utf8).write(to: formatterDirectory.appendingPathComponent("tokenizer_config.json"))
        first.formatterModelDirectory = formatterDirectory.path(percentEncoded: false)

        let second = Preferences(defaults: defaults)

        #expect(!second.enableSmartFormatting)
        #expect(second.formatterRunnerPath == "/tmp/lfm25_formatter_helper")
        #expect(second.formatterModelDirectory == formatterDirectory.path(percentEncoded: false))
    }

}
