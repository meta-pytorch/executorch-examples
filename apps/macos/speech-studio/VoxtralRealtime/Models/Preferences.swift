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

    var modelPath: String { "\(modelDirectory)/model-metal-int4.pte" }
    var tokenizerPath: String { "\(modelDirectory)/tekken.json" }
    var preprocessorPath: String { "\(modelDirectory)/preprocessor.pte" }

    /// True when all paths resolve to the app bundle (self-contained distribution).
    var usingBundledResources: Bool {
        runnerPath.hasPrefix(Bundle.main.bundlePath)
            && modelDirectory.hasPrefix(Bundle.main.bundlePath)
    }

    init() {
        let defaults = UserDefaults.standard
        let home = FileManager.default.homeDirectoryForCurrentUser.path()
        let bundleResources = Bundle.main.resourcePath ?? ""

        let bundledRunner = "\(bundleResources)/voxtral_realtime_runner"
        let buildRunner = "\(home)/project/executorch/cmake-out/examples/models/voxtral_realtime/voxtral_realtime_runner"

        self.runnerPath = defaults.string(forKey: "runnerPath")
            ?? (FileManager.default.isExecutableFile(atPath: bundledRunner) ? bundledRunner : nil)
            ?? buildRunner
        self.modelDirectory = defaults.string(forKey: "modelDirectory")
            ?? (FileManager.default.fileExists(atPath: "\(bundleResources)/model-metal-int4.pte") ? bundleResources : nil)
            ?? "\(home)/voxtral_realtime_quant_metal"
        self.audioDeviceID = defaults.string(forKey: "audioDeviceID")
    }
}
