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
struct PersistenceRegressionTests {
    @Test
    func unreadableReplacementFileIsNotOverwritten() throws {
        let sandbox = makeSandbox()
        let fileURL = sandbox.appendingPathComponent("replacements.json")
        try Data("not-json".utf8).write(to: fileURL, options: .atomic)

        let store = ReplacementStore(fileURL: fileURL)

        #expect(!store.entries.isEmpty)
        let contents = try String(contentsOf: fileURL)
        #expect(contents == "not-json")
    }

    @Test
    func staleSavedRunnerPathFallsBackToBundledRunnerPath() {
        let resolved = Preferences.resolveRunnerPath(
            savedRunnerPath: "/tmp/custom-runner",
            savedRunnerExists: false,
            bundledRunnerPath: "/tmp/bundled-runner",
            bundledRunnerExists: true,
            buildRunnerPath: "/tmp/build-runner"
        )

        #expect(resolved == "/tmp/bundled-runner")
    }

    @Test
    func validSavedRunnerPathStillBeatsBundledRunnerPath() {
        let resolved = Preferences.resolveRunnerPath(
            savedRunnerPath: "/tmp/custom-runner",
            savedRunnerExists: true,
            bundledRunnerPath: "/tmp/bundled-runner",
            bundledRunnerExists: true,
            buildRunnerPath: "/tmp/build-runner"
        )

        #expect(resolved == "/tmp/custom-runner")
    }

    @Test
    func staleSavedModelDirectoryFallsBackToDownloadedModelDirectory() {
        let resolved = Preferences.resolveModelDirectory(
            savedModelDirectory: "/tmp/stale-models",
            bundledModelDirectory: nil,
            downloadedModelDirectory: "/tmp/downloaded-models"
        ) { candidate in
            candidate == "/tmp/downloaded-models"
        }

        #expect(resolved == "/tmp/downloaded-models")
    }

    @Test
    func savedMicrophoneSelectionPersistsAcrossPreferencesReload() {
        let suiteName = "PersistenceRegressionTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let preferences = Preferences(defaults: defaults)
        preferences.selectedMicrophoneID = "usb-mic"

        let reloaded = Preferences(defaults: defaults)

        #expect(reloaded.selectedMicrophoneID == "usb-mic")
    }

    @Test
    func healthCheckGuidanceDistinguishesRunnerSetupFromModelDownload() {
        let runnerMissing = HealthCheck.Result(
            runnerAvailable: false,
            modelAvailable: false,
            tokenizerAvailable: false,
            micPermission: .authorized
        )
        let modelMissing = HealthCheck.Result(
            runnerAvailable: true,
            modelAvailable: false,
            tokenizerAvailable: false,
            micPermission: .authorized
        )

        #expect(runnerMissing.setupStatusMessage == "Helper setup required")
        #expect(runnerMissing.missingFiles == ["parakeet_helper", "model.pte", "tokenizer.model"])
        #expect(!runnerMissing.shouldOfferModelDownload)
        #expect(modelMissing.setupStatusMessage == "Model download required")
        #expect(modelMissing.shouldOfferModelDownload)
    }

    private func makeSandbox() -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }
}
