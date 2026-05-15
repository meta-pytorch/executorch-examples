/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation
import os

private let formatterLog = Logger(subsystem: "org.pytorch.executorch.ExecuWhisper", category: "FormatterBridge")

private final class FormatterDataAccumulator: @unchecked Sendable {
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
}

private final class FormatterLineAccumulator: @unchecked Sendable {
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

private struct FormatterLaunchConfiguration: Equatable, Sendable {
    let runnerPath: String
    let modelPath: String
    let tokenizerPath: String
    let tokenizerConfigPath: String
}

protocol FormatterBridgeClient: Sendable {
    func runtimeSnapshot() async -> FormatterBridge.RuntimeSnapshot

    func prepare(
        runnerPath: String,
        modelPath: String,
        tokenizerPath: String,
        tokenizerConfigPath: String
    ) async throws

    func shutdown() async

    func format(
        runnerPath: String,
        modelPath: String,
        tokenizerPath: String,
        tokenizerConfigPath: String,
        prompt: String,
        maxNewTokens: Int,
        temperature: Double
    ) async throws -> FormatterBridge.FormatResult
}

actor FormatterBridge: FormatterBridgeClient {
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
        let tokenizerConfigPath: String?
        let statusMessage: String
    }

    struct FormatResult: Sendable, Equatable {
        let text: String
        let stdout: String
        let stderr: String
        let tokensPerSecond: Double?
    }

    private struct PendingRequest {
        let requestID: String
        let continuation: CheckedContinuation<FormatResult, Error>
    }

    private var process: Process?
    private var stdinHandle: FileHandle?
    private var stderrAccumulator = FormatterDataAccumulator()
    private var activeConfiguration: FormatterLaunchConfiguration?
    private var activeTraceID: String?
    private var runtimeState: ResidencyState = .unloaded
    private var lastError: RunnerError?
    private var pendingRequest: PendingRequest?
    private var pendingTimeoutTask: Task<Void, Never>?
    private var statusMessage = ""

    func runtimeSnapshot() -> RuntimeSnapshot {
        RuntimeSnapshot(
            state: runtimeState,
            runnerPath: activeConfiguration?.runnerPath,
            modelPath: activeConfiguration?.modelPath,
            tokenizerPath: activeConfiguration?.tokenizerPath,
            tokenizerConfigPath: activeConfiguration?.tokenizerConfigPath,
            statusMessage: statusMessage
        )
    }

    func prepare(
        runnerPath: String,
        modelPath: String,
        tokenizerPath: String,
        tokenizerConfigPath: String
    ) async throws {
        let configuration = FormatterLaunchConfiguration(
            runnerPath: runnerPath,
            modelPath: modelPath,
            tokenizerPath: tokenizerPath,
            tokenizerConfigPath: tokenizerConfigPath
        )

        if activeConfiguration != configuration {
            await shutdown()
        }

        if process?.isRunning == true, runtimeState == .warm, activeConfiguration == configuration {
            return
        }

        try validate(configuration)
        if process?.isRunning != true || activeConfiguration != configuration {
            try launchHelper(configuration)
        }

        try await waitForWarmRuntime(expected: configuration)
    }

    func shutdown() async {
        finishPendingRequest(
            throwing: RunnerError.transcriptionFailed(description: "Formatting was cancelled.")
        )

        if let stdinHandle {
            if let payload = try? Self.encodeJSONLine(FormatterHelperProtocol.ShutdownRequest()) {
                try? stdinHandle.write(contentsOf: payload)
            }
            try? stdinHandle.close()
        }

        if let process, process.isRunning {
            process.terminate()
        }

        process = nil
        stdinHandle = nil
        stderrAccumulator = FormatterDataAccumulator()
        activeConfiguration = nil
        activeTraceID = nil
        runtimeState = .unloaded
        statusMessage = ""
        lastError = nil
        pendingTimeoutTask?.cancel()
        pendingTimeoutTask = nil
    }

    func format(
        runnerPath: String,
        modelPath: String,
        tokenizerPath: String,
        tokenizerConfigPath: String,
        prompt: String,
        maxNewTokens: Int,
        temperature: Double
    ) async throws -> FormatResult {
        let configuration = FormatterLaunchConfiguration(
            runnerPath: runnerPath,
            modelPath: modelPath,
            tokenizerPath: tokenizerPath,
            tokenizerConfigPath: tokenizerConfigPath
        )
        try await prepare(
            runnerPath: runnerPath,
            modelPath: modelPath,
            tokenizerPath: tokenizerPath,
            tokenizerConfigPath: tokenizerConfigPath
        )
        return try await sendFormatRequest(
            configuration: configuration,
            prompt: prompt,
            maxNewTokens: maxNewTokens,
            temperature: temperature
        )
    }

    private func validate(_ configuration: FormatterLaunchConfiguration) throws {
        let fileManager = FileManager.default
        guard fileManager.isExecutableFile(atPath: configuration.runnerPath) else {
            throw RunnerError.binaryNotFound(path: configuration.runnerPath)
        }
        for path in [configuration.modelPath, configuration.tokenizerPath, configuration.tokenizerConfigPath] {
            guard fileManager.fileExists(atPath: path) else {
                throw RunnerError.modelMissing(file: URL(fileURLWithPath: path).lastPathComponent)
            }
        }
    }

    private func launchHelper(_ configuration: FormatterLaunchConfiguration) throws {
        let traceID = String(UUID().uuidString.prefix(8))
        let process = Process()
        let stdinPipe = Pipe()
        let stdoutPipe = Pipe()
        let stderrPipe = Pipe()
        stderrAccumulator = FormatterDataAccumulator()
        activeConfiguration = configuration
        activeTraceID = traceID
        runtimeState = .loading
        statusMessage = "Warming formatter..."
        lastError = nil

        process.executableURL = URL(fileURLWithPath: configuration.runnerPath)
        process.arguments = [
            "--model_path", configuration.modelPath,
            "--tokenizer_path", configuration.tokenizerPath,
            "--tokenizer_config_path", configuration.tokenizerConfigPath,
        ]
        process.currentDirectoryURL = URL(fileURLWithPath: configuration.modelPath).deletingLastPathComponent()
        process.environment = formatterEnvironment(modelPath: configuration.modelPath)
        process.standardInput = stdinPipe
        process.standardOutput = stdoutPipe
        process.standardError = stderrPipe

        let stdoutLines = FormatterLineAccumulator { line in
            Task { await self.handleHelperLine(line, traceID: traceID) }
        }

        process.terminationHandler = { process in
            Task {
                await self.handleTermination(
                    exitCode: process.terminationStatus,
                    traceID: traceID
                )
            }
        }

        do {
            try process.run()
            formatterLog.info(
                "FormatterBridge[\(traceID, privacy: .public)] launched helper runnerPath=\(configuration.runnerPath, privacy: .public) modelPath=\(configuration.modelPath, privacy: .public) pid=\(process.processIdentifier)"
            )
        } catch {
            runtimeState = .failed
            statusMessage = "Formatter launch failed"
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

    private func formatterEnvironment(modelPath: String) -> [String: String] {
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
        return environment
    }

    private func waitForWarmRuntime(expected configuration: FormatterLaunchConfiguration) async throws {
        let deadline = Date().addingTimeInterval(30)
        while Date() < deadline {
            if activeConfiguration != configuration {
                throw RunnerError.launchFailed(description: "Formatter helper configuration changed during warmup.")
            }

            switch runtimeState {
            case .warm:
                return
            case .failed:
                throw lastError ?? RunnerError.launchFailed(description: "Formatter helper failed to warm.")
            case .unloaded:
                throw lastError ?? RunnerError.launchFailed(description: "Formatter helper exited before becoming ready.")
            case .loading:
                try await Task.sleep(for: .milliseconds(50))
            }
        }
        throw RunnerError.launchFailed(description: "Timed out waiting for the formatter helper to become ready.")
    }

    private func sendFormatRequest(
        configuration: FormatterLaunchConfiguration,
        prompt: String,
        maxNewTokens: Int,
        temperature: Double
    ) async throws -> FormatResult {
        guard activeConfiguration == configuration, runtimeState == .warm else {
            throw RunnerError.launchFailed(description: "Formatter helper is not warm.")
        }
        guard pendingRequest == nil else {
            throw RunnerError.transcriptionFailed(description: "Formatter helper is already processing another request.")
        }
        guard let stdinHandle else {
            throw RunnerError.launchFailed(description: "Formatter helper stdin is unavailable.")
        }

        let requestID = UUID().uuidString
        return try await withCheckedThrowingContinuation { continuation in
            pendingRequest = PendingRequest(requestID: requestID, continuation: continuation)
            let timeoutSeconds = max(30, Int(ceil(Double(maxNewTokens) / 4.0)))
            let request = FormatterHelperProtocol.FormatRequest(
                requestID: requestID,
                prompt: prompt,
                maxNewTokens: maxNewTokens,
                temperature: temperature
            )

            do {
                statusMessage = "Formatting..."
                let requestData = try Self.encodeJSONLine(request)
                try stdinHandle.write(contentsOf: requestData)
                pendingTimeoutTask?.cancel()
                pendingTimeoutTask = Task {
                    try? await Task.sleep(for: .seconds(timeoutSeconds))
                    await self.timeoutPendingRequest(requestID: requestID)
                }
            } catch {
                finishPendingRequest(throwing: RunnerError.launchFailed(description: error.localizedDescription))
            }
        }
    }

    private func timeoutPendingRequest(requestID: String) {
        guard pendingRequest?.requestID == requestID else { return }
        finishPendingRequest(
            throwing: RunnerError.transcriptionFailed(description: "Timed out waiting for formatter output.")
        )
    }

    private func handleHelperLine(_ line: String, traceID: String) async {
        guard traceID == activeTraceID else { return }
        guard let data = line.data(using: .utf8) else {
            return
        }

        do {
            let message = try JSONDecoder().decode(FormatterHelperProtocol.HelperMessage.self, from: data)
            switch message {
            case .ready:
                runtimeState = .warm
                statusMessage = "Formatter ready"
                lastError = nil
            case .status(let status):
                if status.requestID == pendingRequest?.requestID {
                    statusMessage = status.message
                }
            case .result(let result):
                guard result.requestID == pendingRequest?.requestID else { return }
                finishPendingRequest(returning: FormatResult(
                    text: result.text,
                    stdout: result.stdout,
                    stderr: result.stderr,
                    tokensPerSecond: result.tokensPerSecond
                ))
                statusMessage = "Formatter ready"
            case .error(let errorMessage):
                let description = errorMessage.details ?? errorMessage.message
                if errorMessage.requestID == pendingRequest?.requestID {
                    finishPendingRequest(throwing: RunnerError.transcriptionFailed(description: description))
                } else {
                    runtimeState = .failed
                    statusMessage = "Formatter failed"
                    lastError = .launchFailed(description: description)
                }
            }
        } catch {
            formatterLog.error(
                "FormatterBridge[\(traceID, privacy: .public)] failed to parse helper message: \(error.localizedDescription, privacy: .public)\n\(line, privacy: .public)"
            )
        }
    }

    private func handleTermination(exitCode: Int32, traceID: String) async {
        guard traceID == activeTraceID else { return }
        let stderr = stderrAccumulator.stringValue()
        if pendingRequest != nil {
            finishPendingRequest(throwing: RunnerError.runnerCrashed(
                exitCode: exitCode,
                stderr: stderr.isEmpty ? "Formatter helper terminated unexpectedly." : stderr
            ))
        }
        runtimeState = .unloaded
        statusMessage = ""
        activeConfiguration = nil
        activeTraceID = nil
        process = nil
        stdinHandle = nil
    }

    private func finishPendingRequest(returning result: FormatResult) {
        guard let pendingRequest else { return }
        self.pendingRequest = nil
        pendingTimeoutTask?.cancel()
        pendingTimeoutTask = nil
        pendingRequest.continuation.resume(returning: result)
    }

    private func finishPendingRequest(throwing error: Error) {
        guard let pendingRequest else { return }
        self.pendingRequest = nil
        pendingTimeoutTask?.cancel()
        pendingTimeoutTask = nil
        pendingRequest.continuation.resume(throwing: error)
    }

    private static func encodeJSONLine<T: Encodable>(_ value: T) throws -> Data {
        try JSONEncoder().encode(value) + Data("\n".utf8)
    }
}
