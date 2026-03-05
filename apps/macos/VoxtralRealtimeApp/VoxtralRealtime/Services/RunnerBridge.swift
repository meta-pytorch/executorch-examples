/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation
import os

private let log = Logger(subsystem: "org.pytorch.executorch.VoxtralRealtime", category: "RunnerBridge")

actor RunnerBridge {
    enum ModelState: Sendable, Equatable {
        case unloaded
        case loading
        case ready
    }

    struct Streams: Sendable {
        let tokens: AsyncStream<String>
        let errors: AsyncStream<RunnerError>
        let modelState: AsyncStream<ModelState>
        let audioLevel: AsyncStream<Float>
        let status: AsyncStream<String>
    }

    private var process: Process?
    private var stdinPipe: Pipe?
    private let audioEngine = AudioEngine()
    private var levelContinuation: AsyncStream<Float>.Continuation?
    private var tokenContinuation: AsyncStream<String>.Continuation?
    private var statusContinuation: AsyncStream<String>.Continuation?
    private var errorContinuation: AsyncStream<RunnerError>.Continuation?
    private var modelStateContinuation: AsyncStream<ModelState>.Continuation?

    private(set) var modelState: ModelState = .unloaded

    var isRunnerAlive: Bool { process?.isRunning == true }

    // MARK: - Process lifecycle

    func launchRunner(
        runnerPath: String,
        modelPath: String,
        tokenizerPath: String,
        preprocessorPath: String
    ) -> Streams {
        if isRunnerAlive { stop() }

        let stdoutPipe = Pipe()
        let stderrPipe = Pipe()
        let stdinPipe = Pipe()
        self.stdinPipe = stdinPipe

        let proc = Process()
        proc.executableURL = URL(fileURLWithPath: runnerPath)
        proc.arguments = [
            "--model_path", modelPath,
            "--tokenizer_path", tokenizerPath,
            "--preprocessor_path", preprocessorPath,
            "--mic"
        ]

        var env = ProcessInfo.processInfo.environment
        if let bundleResources = Bundle.main.resourcePath {
            let existing = env["DYLD_LIBRARY_PATH"] ?? ""
            env["DYLD_LIBRARY_PATH"] = existing.isEmpty ? bundleResources : "\(bundleResources):\(existing)"
        }
        proc.environment = env
        proc.standardInput = stdinPipe
        proc.standardOutput = stdoutPipe
        proc.standardError = stderrPipe
        self.process = proc

        let (tokenStream, tokenCont) = AsyncStream<String>.makeStream()
        let (errorStream, errorCont) = AsyncStream<RunnerError>.makeStream()
        let (modelStateStream, modelStateCont) = AsyncStream<ModelState>.makeStream()
        let (levelStream, levelCont) = AsyncStream<Float>.makeStream()
        let (statusStream, statusCont) = AsyncStream<String>.makeStream()

        self.tokenContinuation = tokenCont
        self.errorContinuation = errorCont
        self.modelStateContinuation = modelStateCont
        self.levelContinuation = levelCont
        self.statusContinuation = statusCont

        modelState = .loading
        modelStateCont.yield(.loading)

        log.info("Launching runner: \(runnerPath)")
        statusCont.yield("Launching runner...")

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let handle = stdoutPipe.fileHandleForReading
            var sawListening = false

            while true {
                let data = handle.availableData
                if data.isEmpty {
                    log.info("Runner stdout EOF")
                    tokenCont.finish()
                    break
                }

                guard let text = String(data: data, encoding: .utf8) else { continue }
                log.debug("Runner stdout (\(data.count) bytes): \(text.prefix(200))")

                if !sawListening && text.contains("Listening") {
                    sawListening = true
                    log.info("Model loaded — runner ready")
                    modelStateCont.yield(.ready)
                    Task { await self?.setModelState(.ready) }

                    let remainder = text.replacingOccurrences(of: "Listening (Ctrl+C to stop)...", with: "")
                        .trimmingCharacters(in: .whitespacesAndNewlines)
                    if !remainder.isEmpty { tokenCont.yield(remainder) }
                    continue
                }

                if text.contains("PyTorchObserver") {
                    let parts = text.components(separatedBy: "\n")
                    let nonStats = parts.filter { !$0.contains("PyTorchObserver") }.joined(separator: "\n")
                    if !nonStats.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        tokenCont.yield(nonStats)
                    }
                    continue
                }

                let cleaned = text.replacingOccurrences(
                    of: "\u{1B}\\[[0-9;]*m", with: "", options: .regularExpression
                )
                if !cleaned.isEmpty { tokenCont.yield(cleaned) }
            }
        }

        DispatchQueue.global(qos: .utility).async {
            let handle = stderrPipe.fileHandleForReading
            while true {
                let data = handle.availableData
                if data.isEmpty { break }
                guard let text = String(data: data, encoding: .utf8) else { continue }
                log.debug("Runner stderr: \(text.trimmingCharacters(in: .newlines))")

                let lastLine = text.components(separatedBy: "\n")
                    .filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
                    .last ?? ""
                if lastLine.contains("Loading model") { statusCont.yield("Loading model...") }
                else if lastLine.contains("Loading tokenizer") { statusCont.yield("Loading tokenizer...") }
                else if lastLine.contains("Loading preprocessor") { statusCont.yield("Loading preprocessor...") }
                else if lastLine.contains("Warming up") { statusCont.yield("Warming up...") }
                else if lastLine.contains("Warmup complete") { statusCont.yield("Model ready") }
            }
        }

        proc.terminationHandler = { [weak self] process in
            let code = process.terminationStatus
            log.info("Runner exited with code \(code)")
            if code != 0 && code != 2 {
                errorCont.yield(.runnerCrashed(exitCode: code, stderr: "Exit code: \(code)"))
            }
            tokenCont.finish()
            errorCont.finish()
            levelCont.finish()
            statusCont.finish()
            modelStateCont.yield(.unloaded)
            modelStateCont.finish()
            Task { await self?.onProcessExited() }
        }

        do {
            try proc.run()
            log.info("Runner process started (pid: \(proc.processIdentifier))")
        } catch {
            log.error("Failed to launch runner: \(error.localizedDescription)")
            errorCont.yield(.launchFailed(description: error.localizedDescription))
            errorCont.finish()
            tokenCont.finish()
            levelCont.finish()
            statusCont.finish()
            modelStateCont.yield(.unloaded)
            modelStateCont.finish()
        }

        return Streams(
            tokens: tokenStream, errors: errorStream, modelState: modelStateStream,
            audioLevel: levelStream, status: statusStream
        )
    }

    // MARK: - Audio lifecycle (independent of process)

    func startAudioCapture() async throws {
        guard let handle = stdinPipe?.fileHandleForWriting else {
            throw RunnerError.launchFailed(description: "Runner stdin not available")
        }
        let cont = self.levelContinuation
        log.info("Starting audio capture")
        try await audioEngine.startCapture(writingTo: handle) { level in
            cont?.yield(level)
        }
    }

    func stopAudioCapture() async {
        await audioEngine.stopCapture()
        levelContinuation?.yield(0)
        log.info("Audio capture stopped (runner still alive)")
    }

    // MARK: - Full shutdown

    func stop() {
        Task { await audioEngine.stopCapture() }

        stdinPipe?.fileHandleForWriting.closeFile()
        stdinPipe = nil

        if let proc = process, proc.isRunning {
            proc.interrupt()
            proc.waitUntilExit()
        }
        process = nil
        modelState = .unloaded
        clearContinuations()
    }

    // MARK: - Private

    private func setModelState(_ state: ModelState) {
        modelState = state
    }

    private func onProcessExited() {
        stdinPipe = nil
        process = nil
        modelState = .unloaded
        clearContinuations()
    }

    private func clearContinuations() {
        tokenContinuation = nil
        errorContinuation = nil
        modelStateContinuation = nil
        levelContinuation = nil
        statusContinuation = nil
    }
}
