import AVFoundation

struct HealthCheck: Sendable {
    struct Result: Sendable {
        var runnerAvailable: Bool
        var modelAvailable: Bool
        var preprocessorAvailable: Bool
        var tokenizerAvailable: Bool
        var micPermission: MicPermission

        var allGood: Bool {
            runnerAvailable && modelAvailable && preprocessorAvailable && tokenizerAvailable && micPermission == .authorized
        }

        var missingFiles: [String] {
            var missing: [String] = []
            if !runnerAvailable { missing.append("voxtral_realtime_runner") }
            if !modelAvailable { missing.append("model-metal-int4.pte") }
            if !preprocessorAvailable { missing.append("preprocessor.pte") }
            if !tokenizerAvailable { missing.append("tekken.json") }
            return missing
        }
    }

    enum MicPermission: Sendable {
        case authorized, denied, notDetermined
    }

    static func run(
        runnerPath: String,
        modelPath: String,
        tokenizerPath: String,
        preprocessorPath: String
    ) async -> Result {
        let fm = FileManager.default
        let micPerm = await microphonePermission()

        return Result(
            runnerAvailable: fm.isExecutableFile(atPath: runnerPath),
            modelAvailable: fm.fileExists(atPath: modelPath),
            preprocessorAvailable: fm.fileExists(atPath: preprocessorPath),
            tokenizerAvailable: fm.fileExists(atPath: tokenizerPath),
            micPermission: micPerm
        )
    }

    static func requestMicrophoneAccess() async -> Bool {
        await AVCaptureDevice.requestAccess(for: .audio)
    }

    static func liveMicPermission() async -> MicPermission {
        await microphonePermission()
    }

    private static func microphonePermission() async -> MicPermission {
        switch AVCaptureDevice.authorizationStatus(for: .audio) {
        case .authorized: .authorized
        case .denied, .restricted: .denied
        case .notDetermined: .notDetermined
        @unknown default: .notDetermined
        }
    }
}
