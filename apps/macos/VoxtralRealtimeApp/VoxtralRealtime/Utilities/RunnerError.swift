import Foundation

enum RunnerError: Error, Sendable {
    case binaryNotFound(path: String)
    case modelMissing(file: String)
    case microphonePermissionDenied
    case runnerCrashed(exitCode: Int32, stderr: String)
    case transcriptionInterrupted(partial: String)
    case launchFailed(description: String)
}

extension RunnerError: LocalizedError {
    var errorDescription: String? {
        switch self {
        case .binaryNotFound(let path):
            "Runner binary not found at \(path)"
        case .modelMissing(let file):
            "Model file missing: \(file)"
        case .microphonePermissionDenied:
            "Microphone access is required for transcription"
        case .runnerCrashed(let code, let stderr):
            "Runner exited with code \(code): \(stderr)"
        case .transcriptionInterrupted:
            "Transcription was interrupted"
        case .launchFailed(let desc):
            "Failed to launch runner: \(desc)"
        }
    }
}
