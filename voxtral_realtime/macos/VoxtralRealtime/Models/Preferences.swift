/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

enum StyleProfile: String, CaseIterable, Codable {
    case none
}

@MainActor @Observable
final class Preferences {
    var runnerPath: String {
        didSet { UserDefaults.standard.set(runnerPath, forKey: "runnerPath") }
    }

    var modelDirectory: String {
        didSet { UserDefaults.standard.set(modelDirectory, forKey: "modelDirectory") }
    }

    var audioDeviceID: String? {
        didSet { UserDefaults.standard.set(audioDeviceID, forKey: "audioDeviceID") }
    }

    var silenceThreshold: Double {
        didSet { UserDefaults.standard.set(silenceThreshold, forKey: "silenceThreshold") }
    }

    var silenceTimeout: Double {
        didSet { UserDefaults.standard.set(silenceTimeout, forKey: "silenceTimeout") }
    }

    var styleProfile: StyleProfile {
        didSet { UserDefaults.standard.set(styleProfile.rawValue, forKey: "styleProfile") }
    }

    var enableSileroVAD: Bool {
        didSet { UserDefaults.standard.set(enableSileroVAD, forKey: "enableSileroVAD") }
    }

    var vadRunnerPath: String {
        didSet { UserDefaults.standard.set(vadRunnerPath, forKey: "vadRunnerPath") }
    }

    var vadModelPath: String {
        didSet { UserDefaults.standard.set(vadModelPath, forKey: "vadModelPath") }
    }

    var vadThreshold: Double {
        didSet { UserDefaults.standard.set(vadThreshold, forKey: "vadThreshold") }
    }

    var vadHangoverMilliseconds: Double {
        didSet { UserDefaults.standard.set(vadHangoverMilliseconds, forKey: "vadHangoverMilliseconds") }
    }

    var enableWakePhrase: Bool {
        didSet { UserDefaults.standard.set(enableWakePhrase, forKey: "enableWakePhrase") }
    }

    var wakeKeyword: String {
        didSet { UserDefaults.standard.set(wakeKeyword, forKey: "wakeKeyword") }
    }

    var wakePhrase: String { "hey \(wakeKeyword)" }

    var wakeCheckSeconds: Double {
        didSet { UserDefaults.standard.set(wakeCheckSeconds, forKey: "wakeCheckSeconds") }
    }

    var modelPath: String { "\(modelDirectory)/model-metal-int4.pte" }
    var tokenizerPath: String { "\(modelDirectory)/tekken.json" }
    var preprocessorPath: String { "\(modelDirectory)/preprocessor.pte" }
    var defaultVadModelPath: String { "\(modelDirectory)/silero_vad.pte" }

    var usingBundledResources: Bool {
        runnerPath.hasPrefix(Bundle.main.bundlePath)
            && modelDirectory.hasPrefix(Bundle.main.bundlePath)
    }

    init() {
        let defaults = UserDefaults.standard
        let home = FileManager.default.homeDirectoryForCurrentUser.path()
        let bundleResources = Bundle.main.resourcePath ?? ""

        let bundledRunner = "\(bundleResources)/voxtral_realtime_runner"
        let bundledModel = "\(bundleResources)/model-metal-int4.pte"
        let buildRunner = "\(home)/executorch/cmake-out/examples/models/voxtral_realtime/voxtral_realtime_runner"
        let bundledVadRunner = "\(bundleResources)/silero_vad_stream_runner"
        let buildVadRunner = "\(home)/executorch/cmake-out/examples/models/silero_vad/silero_vad_stream_runner"

        let hasBundledRunner = FileManager.default.isExecutableFile(atPath: bundledRunner)
        let hasBundledModel = FileManager.default.fileExists(atPath: bundledModel)

        if hasBundledRunner {
            self.runnerPath = bundledRunner
        } else {
            self.runnerPath = defaults.string(forKey: "runnerPath") ?? buildRunner
        }

        if hasBundledModel {
            self.modelDirectory = bundleResources
        } else {
            self.modelDirectory = defaults.string(forKey: "modelDirectory")
                ?? "\(home)/voxtral_realtime_quant_metal"
        }

        self.audioDeviceID = defaults.string(forKey: "audioDeviceID")
        self.silenceThreshold = defaults.object(forKey: "silenceThreshold") as? Double ?? 0.02
        self.silenceTimeout = defaults.object(forKey: "silenceTimeout") as? Double ?? 2.0
        self.styleProfile = StyleProfile(rawValue: defaults.string(forKey: "styleProfile") ?? "") ?? .none
        self.enableSileroVAD = defaults.object(forKey: "enableSileroVAD") as? Bool ?? true
        self.vadRunnerPath = defaults.string(forKey: "vadRunnerPath") ?? buildVadRunner
        let storedVadModelPath = defaults.string(forKey: "vadModelPath")
        let bundledVadModelPath = "\(bundleResources)/silero_vad.pte"
        var resolvedVadModelPath = storedVadModelPath ?? bundledVadModelPath
        if resolvedVadModelPath.isEmpty {
            resolvedVadModelPath = "\(home)/voxtral_realtime_quant_metal/silero_vad.pte"
        }
        self.vadThreshold = defaults.object(forKey: "vadThreshold") as? Double ?? 0.55
        self.vadHangoverMilliseconds = defaults.object(forKey: "vadHangoverMilliseconds") as? Double ?? 320
        self.enableWakePhrase = defaults.object(forKey: "enableWakePhrase") as? Bool ?? true
        self.wakeKeyword = defaults.string(forKey: "wakeKeyword") ?? defaults.string(forKey: "wakePhrase").flatMap {
            let words = $0.lowercased().split(separator: " ")
            return words.count > 1 ? words.dropFirst().joined(separator: " ") : words.first.map(String.init)
        } ?? "torch"
        self.wakeCheckSeconds = defaults.object(forKey: "wakeCheckSeconds") as? Double ?? 4.0

        if !FileManager.default.fileExists(atPath: resolvedVadModelPath) {
            let probePaths = [
                storedVadModelPath,
                "\(home)/silero_vad_xnnpack/silero_vad.pte",
                "\(home)/voxtral_realtime_quant_metal/silero_vad.pte",
            ]
            resolvedVadModelPath = probePaths
                .compactMap { $0 }
                .first(where: { FileManager.default.fileExists(atPath: $0) })
                ?? "\(home)/silero_vad_xnnpack/silero_vad.pte"
        }
        self.vadModelPath = resolvedVadModelPath
        if FileManager.default.isExecutableFile(atPath: bundledVadRunner) {
            self.vadRunnerPath = bundledVadRunner
        }
    }
}
