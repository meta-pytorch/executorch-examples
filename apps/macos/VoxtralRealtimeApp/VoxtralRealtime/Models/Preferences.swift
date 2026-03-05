import Foundation

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

    var modelPath: String { "\(modelDirectory)/model-metal-int4.pte" }
    var tokenizerPath: String { "\(modelDirectory)/tekken.json" }
    var preprocessorPath: String { "\(modelDirectory)/preprocessor.pte" }

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
    }
}
