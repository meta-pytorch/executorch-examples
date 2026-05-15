/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import AppKit
import Foundation
import os

private let storeLog = Logger(subsystem: "org.pytorch.executorch.ExecuWhisper", category: "TranscriptStore")

@MainActor @Observable
final class TranscriptStore {
    enum SessionState: Equatable {
        case idle
        case recording
        case transcribing
    }

    enum ModelState: Equatable {
        case checking
        case missing
        case downloading
        case ready
    }

    var sessions: [Session] = []
    var selectedSessionID: UUID?
    var selectedHistorySessionIDs: Set<UUID> = []
    var liveTranscript = ""
    var sessionState: SessionState = .idle
    var modelState: ModelState = .checking
    var currentError: RunnerError?
    var healthResult: HealthCheck.Result?
    var audioLevel: Float = 0
    var statusMessage = ""
    var helperState: RunnerBridge.ResidencyState = .unloaded
    var helperStatusMessage = ""

    var hasActiveSession: Bool { sessionState != .idle }
    var isRecording: Bool { sessionState == .recording }
    var isTranscribing: Bool { sessionState == .transcribing }
    var isModelReady: Bool { modelState == .ready }
    var resourcesReady: Bool { healthResult?.resourcesReady == true }
    var isHelperWarm: Bool { helperState == .warm }
    var isHelperLoading: Bool { helperState == .loading }

    private let recorder: AudioRecorder
    private let runner: any RunnerBridgeClient
    private let preferences: Preferences
    private let downloader: ModelDownloader
    private let sessionsURL: URL
    private let textPipeline: TextPipeline?
    private let audioDecoder: any ImportedAudioDecoding
    private let maxRecordingDuration: TimeInterval
    private var recordingStartDate: Date?
    private var initialized = false
    private var warmupTask: Task<Void, Never>?
    private var recordingLimitTask: Task<Void, Never>?
    private var explicitlyUnloaded = false

    init(
        preferences: Preferences,
        downloader: ModelDownloader,
        sessionsURL: URL = PersistencePaths.sessionsURL,
        textPipeline: TextPipeline? = nil,
        audioDecoder: any ImportedAudioDecoding = ImportedAudioDecoder(),
        recorder: AudioRecorder = AudioRecorder(),
        runner: any RunnerBridgeClient = RunnerBridge(),
        maxRecordingDuration: TimeInterval = 30 * 60
    ) {
        self.recorder = recorder
        self.runner = runner
        self.preferences = preferences
        self.downloader = downloader
        self.sessionsURL = sessionsURL
        self.textPipeline = textPipeline
        self.audioDecoder = audioDecoder
        self.maxRecordingDuration = maxRecordingDuration
        loadSessions()
    }

    func initialize() async {
        guard !initialized else { return }
        initialized = true
        await runHealthCheck()

        if healthResult?.modelAssetsMissing == true {
            await downloadModelIfNeeded()
        }

        if preferences.enableSmartFormatting && !formatterAssetsReady {
            await downloadFormatterModelIfNeeded()
        }

        await autoPreloadModelIfReady()
    }

    func runHealthCheck() async {
        var result = await HealthCheck.run(
            runnerPath: preferences.runnerPath,
            modelPath: preferences.modelPath,
            tokenizerPath: preferences.tokenizerPath
        )

        if result.runnerAvailable && !result.resourcesReady {
            let bundledPath = preferences.bundledModelDirectoryURL?.path(percentEncoded: false)
            let candidates = Preferences.modelDirectoryCandidates(
                savedModelDirectory: preferences.modelDirectory,
                bundledModelDirectory: bundledPath,
                downloadedModelDirectory: preferences.downloadedModelDirectoryURL.path(percentEncoded: false)
            )

            for candidate in candidates where candidate != preferences.modelDirectory {
                let candidateURL = URL(fileURLWithPath: candidate, isDirectory: true)
                let candidateResult = await HealthCheck.run(
                    runnerPath: preferences.runnerPath,
                    modelPath: candidateURL.appendingPathComponent("model.pte").path(percentEncoded: false),
                    tokenizerPath: candidateURL.appendingPathComponent("tokenizer.model").path(percentEncoded: false)
                )
                if candidateResult.resourcesReady {
                    preferences.modelDirectory = candidate
                    result = candidateResult
                    break
                }
            }
        }

        healthResult = result

        if downloader.isDownloading {
            modelState = .downloading
            if statusMessage.isEmpty || statusMessage == "Ready" {
                statusMessage = "Downloading model..."
            }
        } else if result.resourcesReady {
            modelState = .ready
            if !hasActiveSession {
                statusMessage = "Ready"
            }
        } else {
            modelState = .missing
            if !hasActiveSession {
                statusMessage = result.setupStatusMessage
            }
        }

        if result.resourcesReady {
            await syncHelperState()
            if !hasActiveSession {
                await autoPreloadModelIfReady()
            }
        } else {
            helperState = .unloaded
            helperStatusMessage = ""
            warmupTask?.cancel()
            warmupTask = nil
        }
    }

    func downloadModelIfNeeded(force: Bool = false) async {
        if !force && healthResult?.resourcesReady == true {
            modelState = .ready
            return
        }
        if !force && healthResult?.shouldOfferModelDownload != true {
            return
        }
        await downloadModel()
    }

    func downloadFormatterModelIfNeeded(force: Bool = false) async {
        guard force || !formatterAssetsReady else { return }
        await downloadFormatterModel()
    }

    func downloadModel() async {
        guard !downloader.isDownloading else {
            modelState = .downloading
            return
        }

        preferences.modelDirectory = preferences.downloadedModelDirectoryURL.path(percentEncoded: false)
        modelState = .downloading
        statusMessage = "Downloading model..."
        currentError = nil

        do {
            try await downloader.downloadModels(
                destinationDirectory: preferences.downloadedModelDirectoryURL
            )
            if preferences.enableSmartFormatting && !formatterAssetsReady {
                preferences.formatterModelDirectory = preferences.downloadedFormatterModelDirectoryURL.path(percentEncoded: false)
                try await downloader.downloadFormatterModels(
                    destinationDirectory: preferences.downloadedFormatterModelDirectoryURL
                )
            }
            await runHealthCheck()
            await autoPreloadModelIfReady()
        } catch let error as RunnerError {
            currentError = error
            await runHealthCheck()
        } catch {
            currentError = .downloadFailed(file: "Parakeet model", description: error.localizedDescription)
            await runHealthCheck()
        }
    }

    func downloadFormatterModel() async {
        guard !downloader.isDownloading else {
            modelState = .downloading
            return
        }

        preferences.formatterModelDirectory = preferences.downloadedFormatterModelDirectoryURL.path(percentEncoded: false)
        modelState = .downloading
        statusMessage = "Downloading formatter..."
        currentError = nil

        do {
            try await downloader.downloadFormatterModels(
                destinationDirectory: preferences.downloadedFormatterModelDirectoryURL
            )
            await runHealthCheck()
        } catch let error as RunnerError {
            currentError = error
            await runHealthCheck()
        } catch {
            currentError = .downloadFailed(file: "LFM2.5 formatter", description: error.localizedDescription)
            await runHealthCheck()
        }
    }

    func preloadModel() async {
        explicitlyUnloaded = false
        await performHelperWarmupIfNeeded(updateStatusMessage: true)
    }

    func unloadModel() async {
        explicitlyUnloaded = true
        warmupTask?.cancel()
        warmupTask = nil
        await runner.shutdown()
        helperState = .unloaded
        helperStatusMessage = resourcesReady ? "Helper unloaded" : ""
        if !hasActiveSession {
            statusMessage = resourcesReady ? "Ready" : (healthResult?.setupStatusMessage ?? "Ready")
        }
    }

    func startRecording() async {
        guard sessionState == .idle else { return }

        await runHealthCheck()
        if healthResult?.shouldOfferModelDownload == true {
            await downloadModelIfNeeded()
            await runHealthCheck()
        }
        guard resourcesReady else {
            if healthResult?.runnerAvailable == false {
                currentError = .binaryNotFound(path: preferences.runnerPath)
            }
            return
        }

        let micPermission = await HealthCheck.liveMicPermission()
        if micPermission == .notDetermined {
            let granted = await HealthCheck.requestMicrophoneAccess()
            if !granted {
                currentError = .microphonePermissionDenied
                return
            }
        } else if micPermission == .denied {
            currentError = .microphonePermissionDenied
            return
        }

        selectedSessionID = nil
        selectedHistorySessionIDs = []
        liveTranscript = ""
        audioLevel = 0
        statusMessage = "Recording..."
        currentError = nil
        sessionState = .recording
        recordingStartDate = .now
        scheduleRecordingLimit {
            await self.stopRecordingAndTranscribe()
        }

        do {
            try await recorder.startRecording(selectedMicrophoneID: preferences.selectedMicrophoneID) { [weak self] level in
                Task { @MainActor in
                    self?.audioLevel = level
                }
            }
            startBackgroundWarmupIfNeeded()
        } catch let error as RunnerError {
            cancelRecordingLimit()
            currentError = error
            sessionState = .idle
        } catch {
            cancelRecordingLimit()
            currentError = .launchFailed(description: error.localizedDescription)
            sessionState = .idle
        }
    }

    func startDictationCapture() async -> Bool {
        guard sessionState == .idle else { return false }
        storeLog.info("Dictation capture requested")

        await runHealthCheck()
        if healthResult?.shouldOfferModelDownload == true {
            await downloadModelIfNeeded()
            await runHealthCheck()
        }
        guard resourcesReady else {
            if healthResult?.runnerAvailable == false {
                currentError = .binaryNotFound(path: preferences.runnerPath)
            }
            return false
        }

        let micPermission = await HealthCheck.liveMicPermission()
        if micPermission == .notDetermined {
            let granted = await HealthCheck.requestMicrophoneAccess()
            if !granted {
                currentError = .microphonePermissionDenied
                return false
            }
        } else if micPermission == .denied {
            currentError = .microphonePermissionDenied
            return false
        }

        selectedSessionID = nil
        selectedHistorySessionIDs = []
        liveTranscript = ""
        audioLevel = 0
        statusMessage = "Listening..."
        currentError = nil
        sessionState = .recording
        recordingStartDate = .now
        storeLog.info("Dictation capture starting with runnerPath=\(self.preferences.runnerPath, privacy: .public) modelPath=\(self.preferences.modelPath, privacy: .public)")

        do {
            try await recorder.startRecording(selectedMicrophoneID: preferences.selectedMicrophoneID) { [weak self] level in
                Task { @MainActor in
                    self?.audioLevel = level
                }
            }
            startBackgroundWarmupIfNeeded()
            storeLog.info("Dictation capture started")
            return true
        } catch let error as RunnerError {
            cancelRecordingLimit()
            storeLog.error("Dictation capture failed to start: \(error.localizedDescription, privacy: .public)")
            currentError = error
            resetLiveState(status: "Ready")
            return false
        } catch {
            cancelRecordingLimit()
            storeLog.error("Dictation capture failed with unexpected error: \(error.localizedDescription, privacy: .public)")
            currentError = .launchFailed(description: error.localizedDescription)
            resetLiveState(status: "Ready")
            return false
        }
    }

    func finishDictationCapture() async throws -> TextProcessingResult {
        guard sessionState == .recording else {
            throw RunnerError.dictationNotActive
        }

        let duration = recordingStartDate.map { Date.now.timeIntervalSince($0) } ?? 0
        sessionState = .transcribing
        statusMessage = "Transcribing..."
        audioLevel = 0
        cancelRecordingLimit()
        storeLog.info("Dictation capture stopping after duration=\(duration, format: .fixed(precision: 3))s")

        do {
            let pcmData = try await recorder.stopRecording()
            storeLog.info("Dictation captured pcmBytes=\(pcmData.count)")
            let finalResult = try await transcribeCapturedAudio(pcmData)
            liveTranscript = finalResult.text
            storeLog.info("Dictation transcription completed textLength=\(finalResult.text.count)")
            return await storeDictationTranscription(rawText: finalResult.text, duration: duration)
        } catch {
            storeLog.error("Dictation transcription failed: \(error.localizedDescription, privacy: .public)")
            resetLiveState(status: "Ready")
            throw error
        }
    }

    func stopRecordingAndTranscribe() async {
        guard sessionState == .recording else { return }

        let duration = recordingStartDate.map { Date.now.timeIntervalSince($0) } ?? 0
        sessionState = .transcribing
        statusMessage = "Finalizing recording..."
        audioLevel = 0
        cancelRecordingLimit()

        do {
            let pcmData = try await recorder.stopRecording()
            storeLog.info("Recording captured pcmBytes=\(pcmData.count)")
            let finalResult = try await transcribeCapturedAudio(pcmData)
            liveTranscript = finalResult.text
            await storeCompletedTranscription(rawText: finalResult.text, duration: duration)
        } catch let error as RunnerError {
            currentError = error
            resetLiveState()
        } catch {
            currentError = .transcriptionFailed(description: error.localizedDescription)
            resetLiveState()
        }
    }

    @discardableResult
    func importAudioFile(_ url: URL) async -> Bool {
        guard sessionState == .idle else {
            currentError = .transcriptionFailed(description: "Wait for the current transcription to finish before importing another audio file.")
            return false
        }

        await runHealthCheck()
        if healthResult?.shouldOfferModelDownload == true {
            await downloadModelIfNeeded()
            await runHealthCheck()
        }
        guard resourcesReady else {
            if healthResult?.runnerAvailable == false {
                currentError = .binaryNotFound(path: preferences.runnerPath)
            }
            return false
        }

        let previousSelectedSessionID = selectedSessionID
        let previousHistorySelection = selectedHistorySessionIDs
        selectedSessionID = nil
        selectedHistorySessionIDs = []
        liveTranscript = ""
        audioLevel = 0
        statusMessage = "Preparing audio file..."
        currentError = nil
        sessionState = .transcribing
        recordingStartDate = .now

        do {
            let decoded = try audioDecoder.decodeAudioFile(at: url)
            let finalResult = try await transcribeCapturedAudio(decoded.pcmData)
            liveTranscript = finalResult.text
            await storeImportedTranscription(
                rawText: finalResult.text,
                duration: decoded.duration,
                title: importedSessionTitle(for: url)
            )
            return true
        } catch let error as RunnerError {
            selectedSessionID = previousSelectedSessionID
            selectedHistorySessionIDs = previousHistorySelection
            currentError = error
            resetLiveState()
            return false
        } catch {
            selectedSessionID = previousSelectedSessionID
            selectedHistorySessionIDs = previousHistorySelection
            currentError = .transcriptionFailed(description: error.localizedDescription)
            resetLiveState()
            return false
        }
    }

    func deleteSession(_ session: Session) {
        sessions.removeAll { $0.id == session.id }
        selectedHistorySessionIDs.remove(session.id)
        if selectedSessionID == session.id {
            selectedSessionID = sessions.first?.id
        }
        saveSessions()
    }

    func deleteSessions(ids: Set<UUID>) {
        guard !ids.isEmpty else { return }
        sessions.removeAll { ids.contains($0.id) }
        selectedHistorySessionIDs.subtract(ids)
        if let selectedSessionID, ids.contains(selectedSessionID) {
            self.selectedSessionID = nil
        }
        saveSessions()
    }

    func renameSession(_ session: Session, to newTitle: String) {
        guard let idx = sessions.firstIndex(where: { $0.id == session.id }) else { return }
        sessions[idx].title = newTitle
        saveSessions()
    }

    func togglePinned(_ session: Session) {
        guard let idx = sessions.firstIndex(where: { $0.id == session.id }) else { return }
        sessions[idx].pinned.toggle()
        saveSessions()
    }

    func clearError() {
        currentError = nil
    }

    func exportSession(_ session: Session, format: SessionExportFormat) {
        let panel = NSSavePanel()
        panel.allowedContentTypes = [format.contentType]
        panel.canCreateDirectories = true
        panel.nameFieldStringValue = suggestedExportFileName(for: session, format: format)

        guard panel.runModal() == .OK, let url = panel.url else { return }

        do {
            try writeSessionExport(session, format: format, to: url)
        } catch {
            currentError = .exportFailed(description: error.localizedDescription)
        }
    }

    func importAudioFileWithPanel() {
        let panel = NSOpenPanel()
        panel.allowedContentTypes = ImportedAudioDecoder.allowedContentTypes
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false

        guard panel.runModal() == .OK, let url = panel.url else { return }
        Task { @MainActor in
            await importAudioFile(url)
        }
    }

    func writeSessionExport(_ session: Session, format: SessionExportFormat, to url: URL) throws {
        let rendered = format.render(session)
        try rendered.write(to: url, atomically: true, encoding: .utf8)
    }

    func storeCompletedTranscription(rawText: String, duration: TimeInterval) async {
        _ = await processCompletedTranscription(
            rawText: rawText,
            duration: duration,
            context: .standard,
            persistSession: true,
            titleOverride: nil
        )
    }

    @discardableResult
    func storeDictationTranscription(rawText: String, duration: TimeInterval) async -> TextProcessingResult {
        await processCompletedTranscription(
            rawText: rawText,
            duration: duration,
            context: .dictation,
            persistSession: false,
            titleOverride: nil
        )
    }

    func storeImportedTranscription(rawText: String, duration: TimeInterval, title: String) async {
        _ = await processCompletedTranscription(
            rawText: rawText,
            duration: duration,
            context: .standard,
            persistSession: true,
            titleOverride: title
        )
    }

    private func finishTranscription(
        rawText: String,
        transcript: String,
        tags: [String],
        duration: TimeInterval,
        persistSession: Bool,
        titleOverride: String?
    ) {
        if persistSession && !transcript.isEmpty {
            let session = Session(
                date: recordingStartDate ?? .now,
                title: titleOverride ?? "",
                transcript: transcript,
                duration: duration,
                rawTranscript: rawText,
                tags: tags
            )
            sessions.insert(session, at: 0)
            selectedSessionID = session.id
            selectedHistorySessionIDs = [session.id]
            saveSessions()
        }

        liveTranscript = transcript
        resetLiveState(status: transcript.isEmpty ? "No speech detected" : "Ready")
    }

    private func suggestedExportFileName(for session: Session, format: SessionExportFormat) -> String {
        let base = session.displayTitle
            .replacingOccurrences(of: "/", with: "-")
            .replacingOccurrences(of: ":", with: "-")
        return "\(base).\(format.fileExtension)"
    }

    private func transcribe(audioURL: URL) async throws -> RunnerBridge.TranscriptionResult {
        storeLog.info("Beginning batch transcription for audioPath=\(audioURL.path(percentEncoded: false), privacy: .public)")
        let events = await runner.transcribe(
            runnerPath: preferences.runnerPath,
            modelPath: preferences.modelPath,
            tokenizerPath: preferences.tokenizerPath,
            audioPath: audioURL.path(percentEncoded: false),
            options: .fromEnvironment(ProcessInfo.processInfo.environment)
        )
        return try await collectFinalResult(from: events)
    }

    func transcribeCapturedAudio(_ pcmData: Data) async throws -> RunnerBridge.TranscriptionResult {
        storeLog.info("Beginning batch transcription for capturedAudioBytes=\(pcmData.count)")
        if helperState != .warm {
            helperState = .loading
            helperStatusMessage = "Warming model..."
        }
        let events = await runner.transcribePCM(
            runnerPath: preferences.runnerPath,
            modelPath: preferences.modelPath,
            tokenizerPath: preferences.tokenizerPath,
            pcmData: pcmData,
            options: .fromEnvironment(ProcessInfo.processInfo.environment)
        )
        let result = try await collectFinalResult(from: events)
        await syncHelperState()
        return result
    }

    private func collectFinalResult(
        from events: AsyncThrowingStream<RunnerBridge.Event, Error>
    ) async throws -> RunnerBridge.TranscriptionResult {
        var finalResult: RunnerBridge.TranscriptionResult?
        for try await event in events {
            switch event {
            case .status(let status):
                statusMessage = status
                storeLog.info("Runner status event: \(status, privacy: .public)")
            case .completed(let result):
                finalResult = result
                storeLog.info("Runner completed event textLength=\(result.text.count) stdoutLength=\(result.stdout.count) stderrLength=\(result.stderr.count)")
                if DiagnosticLogging.shouldLogTranscriptsPublicly {
                    storeLog.info("Parakeet transcript: \(result.text, privacy: .public)")
                } else {
                    storeLog.info("Parakeet transcript: \(result.text, privacy: .private)")
                }
                if let runtimeProfile = result.runtimeProfile {
                    storeLog.info("Runner runtime profile: \(runtimeProfile, privacy: .public)")
                }
            }
        }

        guard let finalResult else {
            storeLog.error("Runner stream finished without a completed event")
            throw RunnerError.invalidRunnerOutput(stdout: "")
        }
        return finalResult
    }

    private func startBackgroundWarmupIfNeeded() {
        guard resourcesReady else { return }
        guard helperState == .unloaded || helperState == .failed else { return }
        guard warmupTask == nil else { return }

        warmupTask = Task { @MainActor [weak self] in
            await self?.performHelperWarmupIfNeeded(updateStatusMessage: false)
        }
    }

    private func autoPreloadModelIfReady() async {
        guard !explicitlyUnloaded else { return }
        guard resourcesReady else { return }
        guard helperState == .unloaded || helperState == .failed else { return }
        await performHelperWarmupIfNeeded(updateStatusMessage: false)
    }

    private func performHelperWarmupIfNeeded(updateStatusMessage: Bool) async {
        guard resourcesReady else { return }

        if helperState == .warm {
            helperStatusMessage = "Model preloaded"
            return
        }

        if helperState == .loading, let warmupTask {
            await warmupTask.value
            return
        }

        helperState = .loading
        helperStatusMessage = "Warming model..."
        if updateStatusMessage && !hasActiveSession {
            statusMessage = "Warming model..."
        }

        do {
            try await runner.prepare(
                runnerPath: preferences.runnerPath,
                modelPath: preferences.modelPath,
                tokenizerPath: preferences.tokenizerPath
            )
            helperState = .warm
            helperStatusMessage = "Model preloaded"
            logResidentMemory(context: "Parakeet helper preloaded")
            if updateStatusMessage && !hasActiveSession {
                statusMessage = "Ready"
            }
        } catch let error as RunnerError {
            helperState = .failed
            helperStatusMessage = "Warmup failed"
            currentError = error
            if updateStatusMessage && !hasActiveSession {
                statusMessage = healthResult?.setupStatusMessage ?? "Ready"
            }
        } catch {
            helperState = .failed
            helperStatusMessage = "Warmup failed"
            currentError = .launchFailed(description: error.localizedDescription)
            if updateStatusMessage && !hasActiveSession {
                statusMessage = healthResult?.setupStatusMessage ?? "Ready"
            }
        }

        warmupTask = nil
    }

    private func syncHelperState() async {
        let snapshot = await runner.runtimeSnapshot()
        helperState = snapshot.state
        switch snapshot.state {
        case .unloaded:
            helperStatusMessage = resourcesReady ? "Helper unloaded" : ""
        case .loading:
            helperStatusMessage = "Warming model..."
        case .warm:
            helperStatusMessage = "Model preloaded"
        case .failed:
            helperStatusMessage = "Warmup failed"
        }
    }

    private func logResidentMemory(context: String) {
        guard UserDefaults.standard.bool(forKey: DiagnosticLogging.transcriptDebugKey),
              let bytes = DiagnosticLogging.residentMemoryBytes()
        else {
            return
        }
        storeLog.info("\(context, privacy: .public) residentMemoryBytes=\(bytes)")
    }

    @discardableResult
    private func processCompletedTranscription(
        rawText: String,
        duration: TimeInterval,
        context: TextPipeline.Context,
        persistSession: Bool,
        titleOverride: String?
    ) async -> TextProcessingResult {
        if preferences.enableSmartFormatting && !rawText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            statusMessage = "Formatting..."
            liveTranscript = "Formatting..."
        }

        let processed = await textPipeline?.process(
            rawText,
            context: context,
            smartFormattingEnabled: preferences.enableSmartFormatting
        )
            ?? TextProcessingResult(rawText: rawText, outputText: rawText, tags: [])
        finishTranscription(
            rawText: processed.rawText,
            transcript: processed.outputText,
            tags: processed.tags,
            duration: duration,
            persistSession: persistSession,
            titleOverride: titleOverride
        )
        return processed
    }

    private func importedSessionTitle(for url: URL) -> String {
        let title = url.deletingPathExtension().lastPathComponent.trimmingCharacters(in: .whitespacesAndNewlines)
        return title.isEmpty ? "Imported Audio" : title
    }

    private func resetLiveState(status: String = "Ready") {
        cancelRecordingLimit()
        audioLevel = 0
        sessionState = .idle
        recordingStartDate = nil
        statusMessage = status
    }

    private func scheduleRecordingLimit(onLimit: @escaping @MainActor () async -> Void) {
        cancelRecordingLimit()
        guard maxRecordingDuration > 0 else { return }
        recordingLimitTask = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .seconds(maxRecordingDuration))
            guard let self, self.sessionState == .recording else { return }
            self.statusMessage = "Maximum recording duration reached"
            self.currentError = .transcriptionFailed(description: "Maximum recording duration reached.")
            await onLimit()
        }
    }

    private func cancelRecordingLimit() {
        recordingLimitTask?.cancel()
        recordingLimitTask = nil
    }

    private var formatterAssetsReady: Bool {
        let fm = FileManager.default
        return fm.fileExists(atPath: preferences.formatterModelPath)
            && fm.fileExists(atPath: preferences.formatterTokenizerPath)
            && fm.fileExists(atPath: preferences.formatterTokenizerConfigPath)
    }

    private func saveSessions() {
        guard let data = try? JSONEncoder().encode(sessions) else { return }
        try? data.write(to: sessionsURL, options: .atomic)
    }

    private func loadSessions() {
        guard let data = try? Data(contentsOf: sessionsURL),
              let decoded = try? JSONDecoder().decode([Session].self, from: data)
        else {
            return
        }
        sessions = decoded.sorted { $0.date > $1.date }
        selectedSessionID = nil
    }
}
