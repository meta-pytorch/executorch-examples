/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation
import Observation

@MainActor @Observable
final class Preferences {
    @ObservationIgnored private let defaults: UserDefaults

    var enableGlobalHotkey: Bool = true {
        didSet { defaults.set(enableGlobalHotkey, forKey: "enableGlobalHotkey") }
    }

    var dictationShortcut: DictationShortcut = .controlSpace {
        didSet { Self.persist(dictationShortcut: dictationShortcut, in: defaults) }
    }

    var selectedMicrophoneID: String = "" {
        didSet { defaults.set(selectedMicrophoneID, forKey: "selectedMicrophoneID") }
    }

    var silenceThreshold: Double = 0.02 {
        didSet { defaults.set(silenceThreshold, forKey: "silenceThreshold") }
    }

    var silenceTimeout: Double = 1.5 {
        didSet { defaults.set(silenceTimeout, forKey: "silenceTimeout") }
    }

    var runnerPath: String = "" {
        didSet { defaults.set(runnerPath, forKey: "runnerPath") }
    }

    var modelDirectory: String = "" {
        didSet {
            defaults.set(modelDirectory, forKey: "modelDirectory")
            try? FileManager.default.createDirectory(
                at: modelDirectoryURL,
                withIntermediateDirectories: true
            )
        }
    }

    var modelPath: String { modelDirectoryURL.appendingPathComponent("model.pte").path(percentEncoded: false) }
    var tokenizerPath: String { modelDirectoryURL.appendingPathComponent("tokenizer.model").path(percentEncoded: false) }

    var modelDirectoryURL: URL { URL(fileURLWithPath: modelDirectory, isDirectory: true) }

    var bundledRunnerPath: String {
        let resources = Bundle.main.resourcePath ?? ""
        return URL(fileURLWithPath: resources).appendingPathComponent("parakeet_helper").path(percentEncoded: false)
    }

    var bundledLibompPath: String {
        let resources = Bundle.main.resourcePath ?? ""
        return URL(fileURLWithPath: resources).appendingPathComponent("libomp.dylib").path(percentEncoded: false)
    }

    var bundledModelDirectoryURL: URL? {
        guard let resources = Bundle.main.resourcePath else { return nil }
        let directoryURL = URL(fileURLWithPath: resources, isDirectory: true)
        let modelURL = directoryURL.appendingPathComponent("model.pte")
        let tokenizerURL = directoryURL.appendingPathComponent("tokenizer.model")
        if FileManager.default.fileExists(atPath: modelURL.path(percentEncoded: false))
            && FileManager.default.fileExists(atPath: tokenizerURL.path(percentEncoded: false)) {
            return directoryURL
        }
        return nil
    }

    var downloadedModelDirectoryURL: URL {
        PersistencePaths.modelsDirectoryURL
    }

    static func resolveRunnerPath(
        savedRunnerPath: String?,
        savedRunnerExists: Bool,
        bundledRunnerPath: String,
        bundledRunnerExists: Bool,
        buildRunnerPath: String
    ) -> String {
        if let savedRunnerPath, !savedRunnerPath.isEmpty, savedRunnerExists {
            return savedRunnerPath
        }
        if bundledRunnerExists {
            return bundledRunnerPath
        }
        if let savedRunnerPath, !savedRunnerPath.isEmpty {
            return savedRunnerPath
        }
        return buildRunnerPath
    }

    static func modelDirectoryCandidates(
        savedModelDirectory: String?,
        bundledModelDirectory: String?,
        downloadedModelDirectory: String
    ) -> [String] {
        var candidates: [String] = []
        for candidate in [savedModelDirectory, bundledModelDirectory, downloadedModelDirectory] {
            guard let candidate, !candidate.isEmpty, !candidates.contains(candidate) else { continue }
            candidates.append(candidate)
        }
        return candidates
    }

    static func resolveModelDirectory(
        savedModelDirectory: String?,
        bundledModelDirectory: String?,
        downloadedModelDirectory: String,
        hasUsableModelFiles: (String) -> Bool
    ) -> String {
        let candidates = modelDirectoryCandidates(
            savedModelDirectory: savedModelDirectory,
            bundledModelDirectory: bundledModelDirectory,
            downloadedModelDirectory: downloadedModelDirectory
        )

        if let resolved = candidates.first(where: hasUsableModelFiles) {
            return resolved
        }

        return candidates.first ?? downloadedModelDirectory
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let home = FileManager.default.homeDirectoryForCurrentUser.path(percentEncoded: false)
        let buildRunner = "\(home)/executorch/cmake-out/examples/models/parakeet/parakeet_helper"
        let savedRunnerPath = defaults.string(forKey: "runnerPath")
        let migratedSavedRunnerPath = Self.migrateHelperPath(savedRunnerPath)

        enableGlobalHotkey = defaults.object(forKey: "enableGlobalHotkey") as? Bool ?? true
        dictationShortcut = Self.loadDictationShortcut(from: defaults)
        selectedMicrophoneID = defaults.string(forKey: "selectedMicrophoneID") ?? ""
        silenceThreshold = defaults.object(forKey: "silenceThreshold") as? Double ?? 0.02
        silenceTimeout = defaults.object(forKey: "silenceTimeout") as? Double ?? 1.5

        let bundledRunner = bundledRunnerPath
        runnerPath = Self.resolveRunnerPath(
            savedRunnerPath: migratedSavedRunnerPath,
            savedRunnerExists: migratedSavedRunnerPath.map {
                FileManager.default.isExecutableFile(atPath: $0)
            } ?? false,
            bundledRunnerPath: bundledRunner,
            bundledRunnerExists: FileManager.default.isExecutableFile(atPath: bundledRunner),
            buildRunnerPath: buildRunner
        )

        let preferredModelDir = Self.resolveModelDirectory(
            savedModelDirectory: defaults.string(forKey: "modelDirectory"),
            bundledModelDirectory: bundledModelDirectoryURL?.path(percentEncoded: false),
            downloadedModelDirectory: downloadedModelDirectoryURL.path(percentEncoded: false)
        ) { candidate in
            let directoryURL = URL(fileURLWithPath: candidate, isDirectory: true)
            let modelPath = directoryURL.appendingPathComponent("model.pte").path(percentEncoded: false)
            let tokenizerPath = directoryURL.appendingPathComponent("tokenizer.model").path(percentEncoded: false)
            return FileManager.default.fileExists(atPath: modelPath) && FileManager.default.fileExists(atPath: tokenizerPath)
        }
        modelDirectory = preferredModelDir

        try? FileManager.default.createDirectory(
            at: downloadedModelDirectoryURL,
            withIntermediateDirectories: true
        )
    }

    private static func migrateHelperPath(_ savedRunnerPath: String?) -> String? {
        guard let savedRunnerPath, !savedRunnerPath.isEmpty else { return savedRunnerPath }
        let savedURL = URL(fileURLWithPath: savedRunnerPath)
        guard savedURL.lastPathComponent == "parakeet_runner" else { return savedRunnerPath }

        let siblingHelperPath = savedURL
            .deletingLastPathComponent()
            .appendingPathComponent("parakeet_helper")
            .path(percentEncoded: false)
        if FileManager.default.isExecutableFile(atPath: siblingHelperPath) {
            return siblingHelperPath
        }

        return savedRunnerPath
    }

    private static func loadDictationShortcut(from defaults: UserDefaults) -> DictationShortcut {
        guard let data = defaults.data(forKey: "dictationShortcut"),
              let shortcut = try? JSONDecoder().decode(DictationShortcut.self, from: data)
        else {
            return .controlSpace
        }
        return shortcut
    }

    private static func persist(dictationShortcut: DictationShortcut, in defaults: UserDefaults) {
        guard let data = try? JSONEncoder().encode(dictationShortcut) else { return }
        defaults.set(data, forKey: "dictationShortcut")
    }
}
