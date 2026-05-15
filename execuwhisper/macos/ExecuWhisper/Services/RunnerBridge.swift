/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation
import os

private let runnerLog = Logger(subsystem: "org.pytorch.executorch.ExecuWhisper", category: "RunnerBridge")

private final class DataAccumulator: @unchecked Sendable {
    private let lock = NSLock()
    private var data = Data()

    func append(_ chunk: Data) {
        lock.lock()
        data.append(chunk)
        lock.unlock()
    }

    func stringValue() -> String {
        lock.lock()
        defer { lock.unlock() }
        return String(decoding: data, as: UTF8.self)
    }

    func byteCount() -> Int {
        lock.lock()
        defer { lock.unlock() }
        return data.count
    }

    func preview(limit: Int = 400) -> String {
        lock.lock()
        defer { lock.unlock() }
        let preview = String(decoding: data.prefix(limit), as: UTF8.self)
        if data.count > limit {
            return preview + "\n...[truncated \(data.count - limit) bytes]"
        }
        return preview
    }
}

private final class LineAccumulator: @unchecked Sendable {
    private let lock = NSLock()
    private var buffer = Data()
    private let lineHandler: @Sendable (String) -> Void

    init(lineHandler: @escaping @Sendable (String) -> Void) {
        self.lineHandler = lineHandler
    }

    func append(_ chunk: Data) {
        lock.lock()
        buffer.append(chunk)
        var readyLines: [Data] = []
        while let newlineIndex = buffer.firstIndex(of: 10) {
            let line = buffer.prefix(upTo: newlineIndex)
            readyLines.append(Data(line))
            buffer.removeSubrange(...newlineIndex)
        }
        lock.unlock()

        for lineData in readyLines {
            let trimmed = String(decoding: lineData, as: UTF8.self)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty {
                lineHandler(trimmed)
            }
        }
    }

    func flush() {
        lock.lock()
        let remainderData = buffer
        buffer.removeAll()
        lock.unlock()

        let remainder = String(decoding: remainderData, as: UTF8.self)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if !remainder.isEmpty {
            lineHandler(remainder)
        }
    }
}

private struct HelperLaunchConfiguration: Equatable, Sendable {
    let runnerPath: String
    let modelPath: String
    let tokenizerPath: String
}

protocol RunnerBridgeClient: Sendable {
    func runtimeSnapshot() async -> RunnerBridge.RuntimeSnapshot

    func prepare(
        runnerPath: String,
        modelPath: String,
        tokenizerPath: String
    ) async throws

    func shutdown() async

    func transcribe(
        runnerPath: String,
        modelPath: String,
        tokenizerPath: String,
        audioPath: String,
        options: RunnerBridge.RunOptions
    ) async -> AsyncThrowingStream<RunnerBridge.Event, Error>

    func transcribePCM(
        runnerPath: String,
        modelPath: String,
        tokenizerPath: String,
        pcmData: Data,
        options: RunnerBridge.RunOptions
    ) async -> AsyncThrowingStream<RunnerBridge.Event, Error>
}

actor RunnerBridge {
    enum Event: Sendable {
        case status(String)
        case completed(TranscriptionResult)
    }

    enum ResidencyState: Sendable, Equatable {
        case unloaded
        case loading
        case warm
        case failed
    }

    struct RuntimeSnapshot: Sendable, Equatable {
        let state: ResidencyState
        let runnerPath: String?
        let modelPath: String?
        let tokenizerPath: String?
    }

    struct RunOptions: Sendable, Equatable {
        var enableRuntimeProfile: Bool = false

        static func fromEnvironment(_ environment: [String: String]) -> Self {
            Self(enableRuntimeProfile: environment["EXECUWHISPER_ENABLE_RUNTIME_PROFILE"] == "1")
        }
    }

    struct TranscriptionResult: Sendable, Equatable {
        let text: String
        let stdout: String
        let stderr: String
        let stats: String?
        let runtimeProfile: String?
    }

    private struct PendingRequest {
        let requestID: String
        let continuation: AsyncThrowingStream<Event, Error>.Continuation
    }

    private var process: Process?
    private var stdinHandle: FileHandle?
    private var stderrAccumulator = DataAccumulator()
    private var activeConfiguration: HelperLaunchConfiguration?
    private var activeTraceID: String?
    private var runtimeState: ResidencyState = .unloaded
    private var lastError: RunnerError?
    private var pendingRequest: PendingRequest?

    func health() -> RuntimeSnapshot {
        RuntimeSnapshot(
            state: runtimeState,
            runnerPath: activeConfiguration?.runnerPath,
            modelPath: activeConfiguration?.modelPath,
            tokenizerPath: activeConfiguration?.tokenizerPath
        )
    }

    func runtimeSnapshot() -> RuntimeSnapshot {
        health()
    }

    func prepare(
        runnerPath: String,
        modelPath: String,
        tokenizerPath: String
    ) async throws {
        let configuration = HelperLaunchConfiguration(
            runnerPath: runnerPath,
            modelPath: modelPath,
            tokenizerPath: tokenizerPath
        )

        if activeConfiguration != configuration {
            await shutdown()
        }

        if process?.isRunning == true, runtimeState == .warm, activeConfiguration == configuration {
            return
        }

        if process?.isRunning != true || activeConfiguration != configuration {
            try launchHelper(configuration)
        }

        try await waitForWarmRuntime(expected: configuration)
    }

    func shutdown() async {
        finishPendingRequest(
            throwing: RunnerError.transcriptionFailed(description: "Transcription was cancelled.")
        )

        if let stdinHandle {
            if let payload = try? RunnerBridge.encodeJSONLine(["type": "shutdown", "version": ParakeetHelperProtocol.version]) {
                try? stdinHandle.write(contentsOf: payload)
            }
            try? stdinHandle.close()
        }

        if let process, process.isRunning {
            process.terminate()
        }

        process = nil
        stdinHandle = nil
        stderrAccumulator = DataAccumulator()
        activeConfiguration = nil
        activeTraceID = nil
        runtimeState = .unloaded
        lastError = nil
    }

    func transcribe(
        runnerPath: String,
        modelPath: String,
        tokenizerPath: String,
        audioPath: String,
        options: RunOptions = .init()
    ) async -> AsyncThrowingStream<Event, Error> {
        do {
            let pcmData = try Self.loadPCMFloat32MonoWAV(from: URL(fileURLWithPath: audioPath))
            return await transcribePCM(
                runnerPath: runnerPath,
                modelPath: modelPath,
                tokenizerPath: tokenizerPath,
                pcmData: pcmData,
                options: options
            )
        } catch {
            return AsyncThrowingStream { continuation in
                continuation.finish(throwing: error)
            }
        }
    }

    func transcribePCM(
        runnerPath: String,
        modelPath: String,
        tokenizerPath: String,
        pcmData: Data,
        options: RunOptions = .init()
    ) async -> AsyncThrowingStream<Event, Error> {
        let configuration = HelperLaunchConfiguration(
            runnerPath: runnerPath,
            modelPath: modelPath,
            tokenizerPath: tokenizerPath
        )

        let shouldWarm = activeConfiguration != configuration || runtimeState != .warm || process?.isRunning != true
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    if shouldWarm {
                        continuation.yield(.status("Warming model..."))
                    }
                    try await self.prepare(
                        runnerPath: runnerPath,
                        modelPath: modelPath,
                        tokenizerPath: tokenizerPath
                    )
                    try await self.sendTranscriptionRequest(
                        configuration: configuration,
                        pcmData: pcmData,
                        options: options,
                        continuation: continuation
                    )
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }

    private func launchHelper(_ configuration: HelperLaunchConfiguration) throws {
        let traceID = String(UUID().uuidString.prefix(8))
        let process = Process()
        let stdinPipe = Pipe()
        let stdoutPipe = Pipe()
        let stderrPipe = Pipe()
        stderrAccumulator = DataAccumulator()
        activeConfiguration = configuration
        activeTraceID = traceID
        runtimeState = .loading
        lastError = nil

        process.executableURL = URL(fileURLWithPath: configuration.runnerPath)
        process.arguments = [
            "--model_path", configuration.modelPath,
            "--tokenizer_path", configuration.tokenizerPath,
        ]
        process.currentDirectoryURL = URL(fileURLWithPath: configuration.modelPath).deletingLastPathComponent()

        var environment = ProcessInfo.processInfo.environment
        let bundleResources = Bundle.main.resourcePath ?? ""
        var dyldEntries: [String] = []
        if !bundleResources.isEmpty {
            dyldEntries.append(bundleResources)
        }
        if let existing = environment["DYLD_LIBRARY_PATH"], !existing.isEmpty {
            dyldEntries.append(contentsOf: existing.components(separatedBy: ":"))
        }
        let uniqueEntries = Array(NSOrderedSet(array: dyldEntries)).compactMap { $0 as? String }
        environment["DYLD_LIBRARY_PATH"] = uniqueEntries.joined(separator: ":")
        process.environment = environment

        process.standardInput = stdinPipe
        process.standardOutput = stdoutPipe
        process.standardError = stderrPipe

        let stdoutLines = LineAccumulator { line in
            Task { await self.handleHelperLine(line, traceID: traceID) }
        }

        process.terminationHandler = { process in
            Task {
                await self.handleTermination(
                    exitCode: process.terminationStatus,
                    reason: String(describing: process.terminationReason),
                    traceID: traceID
                )
            }
        }

        do {
            try process.run()
            runnerLog.info(
                "RunnerBridge[\(traceID, privacy: .public)] launched helper runnerPath=\(configuration.runnerPath, privacy: .public) modelPath=\(configuration.modelPath, privacy: .public) tokenizerPath=\(configuration.tokenizerPath, privacy: .public) pid=\(process.processIdentifier)"
            )
        } catch {
            runtimeState = .failed
            lastError = .launchFailed(description: error.localizedDescription)
            throw lastError!
        }

        self.process = process
        self.stdinHandle = stdinPipe.fileHandleForWriting

        DispatchQueue.global(qos: .userInitiated).async {
            let handle = stdoutPipe.fileHandleForReading
            while true {
                let data = handle.availableData
                if data.isEmpty {
                    break
                }
                stdoutLines.append(data)
            }
            stdoutLines.flush()
        }

        let stderrAccumulator = self.stderrAccumulator
        DispatchQueue.global(qos: .utility).async {
            let handle = stderrPipe.fileHandleForReading
            while true {
                let data = handle.availableData
                if data.isEmpty {
                    break
                }
                stderrAccumulator.append(data)
            }
        }
    }

    private func waitForWarmRuntime(expected configuration: HelperLaunchConfiguration) async throws {
        let deadline = Date().addingTimeInterval(30)
        while Date() < deadline {
            if activeConfiguration != configuration {
                throw RunnerError.launchFailed(description: "Parakeet helper configuration changed during warmup.")
            }

            switch runtimeState {
            case .warm:
                return
            case .failed:
                throw lastError ?? RunnerError.launchFailed(description: "Parakeet helper failed to warm.")
            case .unloaded:
                throw lastError ?? RunnerError.launchFailed(description: "Parakeet helper exited before becoming ready.")
            case .loading:
                try await Task.sleep(for: .milliseconds(50))
            }
        }
        throw RunnerError.launchFailed(description: "Timed out waiting for the Parakeet helper to become ready.")
    }

    private func sendTranscriptionRequest(
        configuration: HelperLaunchConfiguration,
        pcmData: Data,
        options: RunOptions,
        continuation: AsyncThrowingStream<Event, Error>.Continuation
    ) async throws {
        guard activeConfiguration == configuration, runtimeState == .warm else {
            throw RunnerError.launchFailed(description: "Parakeet helper is not warm.")
        }
        guard pendingRequest == nil else {
            throw RunnerError.transcriptionFailed(description: "Parakeet helper is already processing another request.")
        }
        guard let stdinHandle else {
            throw RunnerError.launchFailed(description: "Parakeet helper stdin is unavailable.")
        }

        let requestID = UUID().uuidString
        pendingRequest = PendingRequest(requestID: requestID, continuation: continuation)

        let header = ParakeetHelperProtocol.TranscribeRequest(
            requestID: requestID,
            audio: .init(
                encoding: .float32LittleEndian,
                sampleRate: 16_000,
                channelCount: 1,
                payloadByteCount: pcmData.count
            ),
            enableRuntimeProfile: options.enableRuntimeProfile
        )

        do {
            let headerData = try JSONEncoder().encode(header) + Data("\n".utf8)
            try stdinHandle.write(contentsOf: headerData)
            try stdinHandle.write(contentsOf: pcmData)
        } catch {
            finishPendingRequest(throwing: RunnerError.launchFailed(description: error.localizedDescription))
            throw RunnerError.launchFailed(description: error.localizedDescription)
        }
    }

    private func handleHelperLine(_ line: String, traceID: String) async {
        guard traceID == activeTraceID else { return }
        guard let data = line.data(using: .utf8) else {
            runnerLog.error("RunnerBridge[\(traceID, privacy: .public)] helper emitted non-utf8 line")
            return
        }

        do {
            let message = try JSONDecoder().decode(ParakeetHelperProtocol.HelperMessage.self, from: data)
            switch message {
            case .ready:
                runtimeState = .warm
                lastError = nil
                runnerLog.info("RunnerBridge[\(traceID, privacy: .public)] helper reported ready")
            case .status(let status):
                if status.requestID == pendingRequest?.requestID {
                    pendingRequest?.continuation.yield(.status(status.message))
                }
            case .result(let result):
                guard result.requestID == pendingRequest?.requestID else { return }
                let transcription = TranscriptionResult(
                    text: result.text,
                    stdout: result.stdout,
                    stderr: result.stderr,
                    stats: RunnerBridge.statsLine(from: result.stdout),
                    runtimeProfile: result.runtimeProfile
                )
                pendingRequest?.continuation.yield(.completed(transcription))
                pendingRequest?.continuation.finish()
                pendingRequest = nil
            case .error(let errorMessage):
                let description = errorMessage.details ?? errorMessage.message
                if errorMessage.requestID == pendingRequest?.requestID {
                    finishPendingRequest(throwing: RunnerError.transcriptionFailed(description: description))
                } else {
                    runtimeState = .failed
                    lastError = .launchFailed(description: description)
                }
            }
        } catch {
            runnerLog.error(
                "RunnerBridge[\(traceID, privacy: .public)] failed to parse helper message: \(error.localizedDescription, privacy: .public)\n\(line, privacy: .public)"
            )
        }
    }

    private func handleTermination(exitCode: Int32, reason: String, traceID: String) async {
        guard traceID == activeTraceID else { return }
        let stderr = stderrAccumulator.stringValue()
        runnerLog.info(
            "RunnerBridge[\(traceID, privacy: .public)] helper terminated exitCode=\(exitCode) reason=\(reason, privacy: .public)"
        )

        let error = RunnerError.runnerCrashed(
            exitCode: exitCode,
            stderr: stderr.isEmpty ? "Parakeet helper terminated unexpectedly." : stderr
        )

        if pendingRequest != nil {
            finishPendingRequest(throwing: error)
        }

        if activeConfiguration != nil {
            runtimeState = exitCode == 0 ? .unloaded : .failed
            lastError = exitCode == 0 ? nil : error
        } else {
            runtimeState = .unloaded
            lastError = nil
        }

        process = nil
        stdinHandle = nil
        activeTraceID = nil
    }

    private func finishPendingRequest(throwing error: Error) {
        pendingRequest?.continuation.finish(throwing: error)
        pendingRequest = nil
    }

    private static func statsLine(from stdout: String) -> String? {
        stdout
            .components(separatedBy: .newlines)
            .first(where: { $0.contains("PyTorchObserver") })
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
    }

    private static func loadPCMFloat32MonoWAV(from url: URL) throws -> Data {
        let data = try Data(contentsOf: url)
        guard data.count > 44 else {
            throw RunnerError.transcriptionFailed(description: "Recorded WAV file is too small.")
        }

        func readUInt16(at offset: Int) -> UInt16 {
            data.withUnsafeBytes { bytes in
                bytes.load(fromByteOffset: offset, as: UInt16.self).littleEndian
            }
        }

        func readUInt32(at offset: Int) -> UInt32 {
            data.withUnsafeBytes { bytes in
                bytes.load(fromByteOffset: offset, as: UInt32.self).littleEndian
            }
        }

        guard String(decoding: data.prefix(4), as: UTF8.self) == "RIFF",
              String(decoding: data[8..<12], as: UTF8.self) == "WAVE" else {
            throw RunnerError.transcriptionFailed(description: "Recorded file is not a WAV container.")
        }

        let audioFormat = readUInt16(at: 20)
        let channelCount = readUInt16(at: 22)
        let bitsPerSample = readUInt16(at: 34)
        guard audioFormat == 3, channelCount == 1, bitsPerSample == 32 else {
            throw RunnerError.transcriptionFailed(description: "Expected float32 mono WAV audio.")
        }

        var offset = 12
        while offset + 8 <= data.count {
            let chunkID = String(decoding: data[offset..<(offset + 4)], as: UTF8.self)
            let chunkSize = Int(readUInt32(at: offset + 4))
            let chunkStart = offset + 8
            let chunkEnd = chunkStart + chunkSize
            guard chunkEnd <= data.count else {
                throw RunnerError.transcriptionFailed(description: "Recorded WAV data chunk is truncated.")
            }
            if chunkID == "data" {
                return data.subdata(in: chunkStart..<chunkEnd)
            }
            offset = chunkEnd + (chunkSize % 2)
        }

        throw RunnerError.transcriptionFailed(description: "Recorded WAV file is missing PCM data.")
    }

    private static func encodeJSONLine(_ dictionary: [String: Any]) throws -> Data {
        let data = try JSONSerialization.data(withJSONObject: dictionary)
        return data + Data("\n".utf8)
    }
}

extension RunnerBridge: RunnerBridgeClient {}
