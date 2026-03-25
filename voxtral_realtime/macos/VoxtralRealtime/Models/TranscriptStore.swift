/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import AppKit
import Foundation

@MainActor @Observable
final class TranscriptStore {
    enum SessionState: Equatable {
        case idle
        case loading
        case transcribing
        case paused
    }

    enum ModelState: Equatable {
        case unloaded
        case loading
        case ready
    }

    var sessions: [Session] = []
    var selectedSessionID: UUID?
    var liveTranscript = ""
    var sessionState: SessionState = .idle
    var modelState: ModelState = .unloaded
    var currentError: RunnerError?
    var healthResult: HealthCheck.Result?
    var audioLevel: Float = 0
    var statusMessage = ""

    var dictationText = ""
    var isDictating = false
    var wakeState: WakeState = .disabled

    var hasActiveSession: Bool { sessionState != .idle }
    var isTranscribing: Bool { sessionState == .transcribing }
    var isPaused: Bool { sessionState == .paused }
    var isLoading: Bool { sessionState == .loading }
    var isModelReady: Bool { modelState == .ready }
    var recentDictationSessions: [Session] {
        sessions.filter { $0.source == .dictation }.prefix(5).map { $0 }
    }

    private let runner = RunnerBridge()
    private let preferences: Preferences
    private let textPipeline: TextPipeline
    private var startDate: Date?
    private var streamTask: Task<Void, Never>?

    init(
        preferences: Preferences,
        textPipeline: TextPipeline
    ) {
        self.preferences = preferences
        self.textPipeline = textPipeline
        loadSessions()
    }

    // MARK: - Model lifecycle

    func preloadModel() async {
        guard modelState == .unloaded else { return }
        await ensureRunnerLaunched()
    }

    func unloadModel() async {
        if hasActiveSession { await endSession() }
        await runner.stop()
        modelState = .unloaded
        statusMessage = ""
        streamTask?.cancel()
        streamTask = nil
    }

    // MARK: - Transcription

    func startTranscription() async {
        guard sessionState == .idle else { return }

        let micOK = await checkMicPermissionLive()
        guard micOK else { return }

        if modelState != .ready {
            await ensureRunnerLaunched()
            while modelState == .loading {
                try? await Task.sleep(for: .milliseconds(100))
            }
            guard modelState == .ready else { return }
        }

        sessionState = .transcribing
        liveTranscript = ""
        startDate = .now
        currentError = nil
        statusMessage = "Transcribing"

        do {
            try await runner.startAudioCapture()
        } catch {
            currentError = .launchFailed(description: error.localizedDescription)
            sessionState = .idle
        }
    }

    func resumeTranscription() async {
        guard sessionState == .paused else { return }

        let micOK = await checkMicPermissionLive()
        guard micOK else { return }

        if modelState != .ready {
            await ensureRunnerLaunched()
            while modelState == .loading {
                try? await Task.sleep(for: .milliseconds(100))
            }
            guard modelState == .ready else { return }
        }

        sessionState = .transcribing
        statusMessage = "Transcribing"

        do {
            try await runner.startAudioCapture()
        } catch {
            currentError = .launchFailed(description: error.localizedDescription)
            sessionState = .paused
        }
    }

    func pauseTranscription() async {
        guard sessionState == .transcribing || sessionState == .loading else { return }
        await runner.stopAudioCapture()
        audioLevel = 0
        statusMessage = "Paused"
        sessionState = .paused
    }

    func endSession() async {
        if sessionState == .transcribing {
            await runner.stopAudioCapture()
        }

        let duration = startDate.map { Date.now.timeIntervalSince($0) } ?? 0

        if !liveTranscript.isEmpty {
            let processed = textPipeline.process(liveTranscript, context: .sessionSave)
            let session = Session(
                date: startDate ?? .now,
                transcript: processed.outputText,
                duration: duration,
                source: .transcription,
                rawTranscript: processed.rawText == processed.outputText ? nil : processed.rawText,
                tags: processed.tags,
                usedSnippetIDs: processed.usedSnippetIDs
            )
            sessions.insert(session, at: 0)
            selectedSessionID = session.id
            saveSessions()
        }

        liveTranscript = ""
        audioLevel = 0
        statusMessage = isModelReady ? "Model ready" : ""
        sessionState = .idle
        startDate = nil
    }

    func togglePauseResume() async {
        switch sessionState {
        case .idle:
            await startTranscription()
        case .loading, .transcribing:
            await pauseTranscription()
        case .paused:
            await resumeTranscription()
        }
    }

    // MARK: - Private

    private func ensureRunnerLaunched() async {
        guard await !runner.isRunnerAlive else { return }

        guard healthResult?.allGood == true else {
            currentError = healthResult.flatMap { result in
                if !result.runnerAvailable { return .binaryNotFound(path: preferences.runnerPath) }
                if let missing = result.missingFiles.first { return .modelMissing(file: missing) }
                if result.micPermission != .authorized { return .microphonePermissionDenied }
                return nil
            }
            return
        }

        modelState = .loading
        statusMessage = "Launching runner..."

        let streams = await runner.launchRunner(
            runnerPath: preferences.runnerPath,
            modelPath: preferences.modelPath,
            tokenizerPath: preferences.tokenizerPath,
            preprocessorPath: preferences.preprocessorPath
        )

        streamTask?.cancel()
        streamTask = Task {
            await withTaskGroup(of: Void.self) { group in
                group.addTask { @MainActor [weak self] in
                    for await token in streams.tokens {
                        if self?.isDictating == true {
                            self?.dictationText += token
                        } else if self?.hasActiveSession == true {
                            self?.liveTranscript += token
                        }
                    }
                }

                group.addTask { @MainActor [weak self] in
                    for await error in streams.errors {
                        self?.currentError = error
                        if self?.isTranscribing == true {
                            await self?.pauseTranscription()
                        }
                    }
                }

                group.addTask { @MainActor [weak self] in
                    for await level in streams.audioLevel {
                        self?.audioLevel = level
                    }
                }

                group.addTask { @MainActor [weak self] in
                    for await status in streams.status {
                        self?.statusMessage = status
                    }
                }

                group.addTask { @MainActor [weak self] in
                    for await state in streams.modelState {
                        switch state {
                        case .unloaded:
                            self?.modelState = .unloaded
                        case .loading:
                            self?.modelState = .loading
                        case .ready:
                            self?.modelState = .ready
                            if self?.statusMessage == "Warming up..." || self?.statusMessage.contains("Loading") == true {
                                self?.statusMessage = "Model ready"
                            }
                        }
                    }
                }
            }
        }
    }

    // MARK: - Dictation

    func startDictation(initialSamples: [Float] = []) async {
        guard !isDictating else { return }

        let micOK = await checkMicPermissionLive()
        guard micOK else { return }

        if modelState != .ready {
            await ensureRunnerLaunched()
            while modelState == .loading {
                try? await Task.sleep(for: .milliseconds(100))
            }
            guard modelState == .ready else { return }
        }

        isDictating = true
        dictationText = ""
        audioLevel = 0

        do {
            try await runner.primeAudioSamples(initialSamples)
            try await runner.startAudioCapture()
        } catch {
            isDictating = false
        }
    }

    func stopDictation() async -> String {
        guard isDictating else { return "" }
        await runner.stopAudioCapture()
        isDictating = false
        audioLevel = 0
        let result = dictationText
        dictationText = ""
        return result
    }

    // MARK: - Session Management

    func deleteSession(_ session: Session) {
        sessions.removeAll { $0.id == session.id }
        if selectedSessionID == session.id {
            selectedSessionID = sessions.first?.id
        }
        saveSessions()
    }

    func renameSession(_ session: Session, to newTitle: String) {
        guard let idx = sessions.firstIndex(where: { $0.id == session.id }) else { return }
        sessions[idx].title = newTitle
        saveSessions()
    }

    func togglePinned(_ session: Session) {
        guard let index = sessions.firstIndex(where: { $0.id == session.id }) else { return }
        sessions[index].pinned.toggle()
        saveSessions()
    }

    func saveDictationSession(
        result: TextProcessingResult,
        duration: TimeInterval,
        wakeTriggered: Bool
    ) {
        guard !result.outputText.isEmpty else { return }
        let session = Session(
            date: .now,
            transcript: result.outputText,
            duration: duration,
            source: .dictation,
            rawTranscript: result.rawText == result.outputText ? nil : result.rawText,
            tags: result.tags,
            wakeTriggered: wakeTriggered,
            usedSnippetIDs: result.usedSnippetIDs
        )
        sessions.insert(session, at: 0)
        selectedSessionID = session.id
        saveSessions()
    }

    func processDictationText(_ rawText: String) -> TextProcessingResult {
        textPipeline.process(rawText, context: .dictation)
    }

    func normalizeWakePhrase(_ text: String) -> String {
        textPipeline.normalizeForWakePhrase(text)
    }

    func stripLeadingWakePhrase(_ wakePhrase: String) {
        guard !dictationText.isEmpty, !wakePhrase.isEmpty else { return }

        let words = wakePhrase.lowercased()
            .components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }

        var farthestEnd = dictationText.startIndex
        let searchLimit = dictationText.index(
            dictationText.startIndex,
            offsetBy: min(dictationText.count, max(wakePhrase.count + 20, 30))
        )
        let leadingSlice = String(dictationText[..<searchLimit])

        for word in words {
            if let range = leadingSlice.range(of: word, options: [.caseInsensitive]) {
                if range.upperBound > farthestEnd {
                    farthestEnd = range.upperBound
                }
            }
        }

        guard farthestEnd > dictationText.startIndex else { return }

        let remainder = String(dictationText[farthestEnd...])
            .trimmingCharacters(in: CharacterSet(charactersIn: " ,.:;-").union(.whitespacesAndNewlines))
        dictationText = remainder
    }

    func exportSession(_ session: Session, format: SessionExportFormat) {
        let panel = NSSavePanel()
        panel.canCreateDirectories = true
        panel.nameFieldStringValue = suggestedExportName(for: session, format: format)
        panel.allowedContentTypes = [format.contentType]
        guard panel.runModal() == .OK, let url = panel.url else { return }
        try? format.render(session).write(to: url, atomically: true, encoding: .utf8)
    }

    func clearError() {
        currentError = nil
    }

    // MARK: - Health Check

    func runHealthCheck() async {
        healthResult = await HealthCheck.run(
            runnerPath: preferences.runnerPath,
            modelPath: preferences.modelPath,
            tokenizerPath: preferences.tokenizerPath,
            preprocessorPath: preferences.preprocessorPath
        )
    }

    private func checkMicPermissionLive() async -> Bool {
        let permission = await HealthCheck.liveMicPermission()

        if permission == .notDetermined {
            let granted = await HealthCheck.requestMicrophoneAccess()
            if granted {
                await runHealthCheck()
                return true
            }
        }

        if permission == .authorized { return true }

        currentError = .microphonePermissionDenied
        await runHealthCheck()
        return false
    }

    // MARK: - Persistence

    private func saveSessions() {
        guard let data = try? JSONEncoder().encode(sessions) else { return }
        try? data.write(to: PersistencePaths.sessionsURL, options: .atomic)
    }

    private func loadSessions() {
        guard let data = try? Data(contentsOf: PersistencePaths.sessionsURL),
              let decoded = try? JSONDecoder().decode([Session].self, from: data)
        else { return }
        sessions = decoded
    }

    private func suggestedExportName(for session: Session, format: SessionExportFormat) -> String {
        let formatter = ISO8601DateFormatter()
        let stamp = formatter.string(from: session.date).replacingOccurrences(of: ":", with: "-")
        return "voxtral-\(session.source.rawValue)-\(stamp).\(format.fileExtension)"
    }
}
