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
struct TranscriptStoreLatencyTests {
    @Test
    func transcribeCapturedAudioUsesPCMHelperPath() async throws {
        let sandbox = makeSandbox()
        let fakeRunner = FakeRunnerBridge()
        let preferences = Preferences()
        let store = TranscriptStore(
            preferences: preferences,
            downloader: ModelDownloader(),
            sessionsURL: sandbox.appendingPathComponent("sessions.json"),
            runner: fakeRunner
        )
        let pcmData = makePCMData(sampleCount: 1600)

        let result = try await store.transcribeCapturedAudio(pcmData)
        let snapshot = await fakeRunner.snapshot()

        #expect(result.text == "direct-pcm")
        #expect(snapshot.audioPathCallCount == 0)
        #expect(snapshot.pcmCallCount == 1)
        #expect(snapshot.lastPCMData == pcmData)
    }

    @Test
    func importAudioFileUsesDecoderAndPCMHelperPathAndPersistsHistory() async throws {
        let sandbox = makeSandbox()
        let fakeRunner = FakeRunnerBridge()
        let fakeDecoder = FakeImportedAudioDecoder(
            decodedAudio: .init(
                pcmData: makePCMData(sampleCount: 3_200),
                duration: 2.5
            )
        )
        let preferences = try makeReadyPreferences(in: sandbox)
        let store = TranscriptStore(
            preferences: preferences,
            downloader: ModelDownloader(),
            sessionsURL: sandbox.appendingPathComponent("sessions.json"),
            audioDecoder: fakeDecoder,
            runner: fakeRunner
        )
        let inputURL = sandbox.appendingPathComponent("meeting-notes.mp3")
        try Data("fake mp3 contents".utf8).write(to: inputURL, options: .atomic)

        let didImport = await store.importAudioFile(inputURL)
        let snapshot = await fakeRunner.snapshot()

        #expect(didImport)
        #expect(snapshot.audioPathCallCount == 0)
        #expect(snapshot.pcmCallCount == 1)
        #expect(snapshot.lastPCMData == makePCMData(sampleCount: 3_200))
        let saved = try #require(store.sessions.first)
        #expect(saved.title == "meeting-notes")
        #expect(saved.duration == 2.5)
        #expect(saved.transcript == "direct-pcm")
    }

    @Test
    func importAudioFileRestoresSelectionWhenDecodingFails() async throws {
        let sandbox = makeSandbox()
        let preferences = try makeReadyPreferences(in: sandbox)
        let sessionsURL = sandbox.appendingPathComponent("sessions.json")
        let existing = Session(title: "Existing", transcript: "saved", duration: 1)
        try JSONEncoder().encode([existing]).write(to: sessionsURL, options: .atomic)

        let store = TranscriptStore(
            preferences: preferences,
            downloader: ModelDownloader(),
            sessionsURL: sessionsURL,
            audioDecoder: FailingImportedAudioDecoder()
        )
        store.selectedSessionID = existing.id
        store.selectedHistorySessionIDs = [existing.id]

        let didImport = await store.importAudioFile(sandbox.appendingPathComponent("broken.mp3"))

        #expect(!didImport)
        #expect(store.selectedSessionID == existing.id)
        #expect(store.selectedHistorySessionIDs == [existing.id])
        #expect(store.currentError != nil)
    }

    @Test
    func importAudioFileRestoresSelectionWhenRunnerFails() async throws {
        let sandbox = makeSandbox()
        let preferences = try makeReadyPreferences(in: sandbox)
        let sessionsURL = sandbox.appendingPathComponent("sessions.json")
        let existing = Session(title: "Existing", transcript: "saved", duration: 1)
        try JSONEncoder().encode([existing]).write(to: sessionsURL, options: .atomic)

        let store = TranscriptStore(
            preferences: preferences,
            downloader: ModelDownloader(),
            sessionsURL: sessionsURL,
            audioDecoder: FakeImportedAudioDecoder(
                decodedAudio: .init(
                    pcmData: makePCMData(sampleCount: 1_600),
                    duration: 1
                )
            ),
            runner: FakeRunnerBridge(
                pcmError: RunnerError.runnerCrashed(exitCode: 1, stderr: "boom")
            )
        )
        store.selectedSessionID = existing.id
        store.selectedHistorySessionIDs = [existing.id]

        let didImport = await store.importAudioFile(sandbox.appendingPathComponent("broken.wav"))

        #expect(!didImport)
        #expect(store.selectedSessionID == existing.id)
        #expect(store.selectedHistorySessionIDs == [existing.id])
        #expect(store.currentError != nil)
    }

    @Test
    func preloadAndUnloadUpdateHelperResidencyState() async {
        let sandbox = makeSandbox()
        let fakeRunner = FakeRunnerBridge()
        let preferences = Preferences()
        let store = TranscriptStore(
            preferences: preferences,
            downloader: ModelDownloader(),
            sessionsURL: sandbox.appendingPathComponent("sessions.json"),
            runner: fakeRunner
        )
        store.healthResult = HealthCheck.Result(
            runnerAvailable: true,
            modelAvailable: true,
            tokenizerAvailable: true,
            micPermission: .authorized
        )

        await store.preloadModel()
        let warmSnapshot = await fakeRunner.snapshot()

        #expect(store.helperState == .warm)
        #expect(store.helperStatusMessage == "Model preloaded")
        #expect(warmSnapshot.prepareCallCount == 1)

        await store.unloadModel()
        let unloadedSnapshot = await fakeRunner.snapshot()

        #expect(store.helperState == .unloaded)
        #expect(unloadedSnapshot.shutdownCallCount == 1)
    }

    private func makePCMData(sampleCount: Int) -> Data {
        var samples = (0..<sampleCount).map { Float($0) / Float(max(sampleCount, 1)) }
        return Data(bytes: &samples, count: samples.count * MemoryLayout<Float>.size)
    }

    private func makeSandbox() -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }

    private func makeReadyPreferences(in sandbox: URL) throws -> Preferences {
        let suiteName = "TranscriptStoreLatencyTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)

        let preferences = Preferences(defaults: defaults)
        let runnerPath = sandbox.appendingPathComponent("parakeet_helper").path(percentEncoded: false)
        FileManager.default.createFile(atPath: runnerPath, contents: Data(), attributes: [.posixPermissions: 0o755])
        let modelDirectory = sandbox.appendingPathComponent("model", isDirectory: true)
        try FileManager.default.createDirectory(at: modelDirectory, withIntermediateDirectories: true)
        try Data("model".utf8).write(to: modelDirectory.appendingPathComponent("model.pte"), options: .atomic)
        try Data("tokenizer".utf8).write(to: modelDirectory.appendingPathComponent("tokenizer.model"), options: .atomic)

        preferences.runnerPath = runnerPath
        preferences.modelDirectory = modelDirectory.path(percentEncoded: false)
        return preferences
    }
}

private actor FakeRunnerBridge: RunnerBridgeClient {
    struct Snapshot: Sendable {
        let audioPathCallCount: Int
        let pcmCallCount: Int
        let lastPCMData: Data?
        let prepareCallCount: Int
        let shutdownCallCount: Int
        let runtimeState: RunnerBridge.ResidencyState
    }

    private var audioPathCallCount = 0
    private var pcmCallCount = 0
    private var lastPCMData: Data?
    private var prepareCallCount = 0
    private var shutdownCallCount = 0
    private var runtimeState: RunnerBridge.ResidencyState = .unloaded
    private let pcmError: Error?

    init(pcmError: Error? = nil) {
        self.pcmError = pcmError
    }

    func runtimeSnapshot() async -> RunnerBridge.RuntimeSnapshot {
        RunnerBridge.RuntimeSnapshot(
            state: runtimeState,
            runnerPath: nil,
            modelPath: nil,
            tokenizerPath: nil
        )
    }

    func prepare(
        runnerPath: String,
        modelPath: String,
        tokenizerPath: String
    ) async throws {
        prepareCallCount += 1
        runtimeState = .warm
    }

    func shutdown() async {
        shutdownCallCount += 1
        runtimeState = .unloaded
    }

    func transcribe(
        runnerPath: String,
        modelPath: String,
        tokenizerPath: String,
        audioPath: String,
        options: RunnerBridge.RunOptions
    ) async -> AsyncThrowingStream<RunnerBridge.Event, Error> {
        audioPathCallCount += 1
        return AsyncThrowingStream { continuation in
            continuation.yield(.completed(.init(
                text: "legacy-wav",
                stdout: "",
                stderr: "",
                stats: nil,
                runtimeProfile: nil
            )))
            continuation.finish()
        }
    }

    func transcribePCM(
        runnerPath: String,
        modelPath: String,
        tokenizerPath: String,
        pcmData: Data,
        options: RunnerBridge.RunOptions
    ) async -> AsyncThrowingStream<RunnerBridge.Event, Error> {
        pcmCallCount += 1
        lastPCMData = pcmData
        return AsyncThrowingStream { continuation in
            if let pcmError {
                continuation.finish(throwing: pcmError)
                return
            }
            continuation.yield(.completed(.init(
                text: "direct-pcm",
                stdout: "",
                stderr: "",
                stats: nil,
                runtimeProfile: nil
            )))
            continuation.finish()
        }
    }

    func snapshot() -> Snapshot {
        Snapshot(
            audioPathCallCount: audioPathCallCount,
            pcmCallCount: pcmCallCount,
            lastPCMData: lastPCMData,
            prepareCallCount: prepareCallCount,
            shutdownCallCount: shutdownCallCount,
            runtimeState: runtimeState
        )
    }
}

private struct FakeImportedAudioDecoder: ImportedAudioDecoding {
    let decodedAudio: DecodedImportedAudioFile

    func decodeAudioFile(at url: URL) throws -> DecodedImportedAudioFile {
        decodedAudio
    }
}

private struct FailingImportedAudioDecoder: ImportedAudioDecoding {
    func decodeAudioFile(at url: URL) throws -> DecodedImportedAudioFile {
        throw RunnerError.transcriptionFailed(description: "decode failed")
    }
}
