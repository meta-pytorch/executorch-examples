/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation
import CryptoKit
import os

private let downloadLog = Logger(subsystem: "org.pytorch.executorch.ExecuWhisper", category: "ModelDownloader")

private struct DownloadAsset: Sendable {
    let fileName: String
    let url: URL
    let minimumBytes: Int64
}

private struct ModelManifest: Decodable, Sendable {
    struct Asset: Decodable, Sendable {
        let bytes: Int64
        let sha256: String
    }

    let assets: [String: Asset]

    static func load() -> Self? {
        guard let url = Bundle.main.url(forResource: "model_manifest", withExtension: "json"),
              let data = try? Data(contentsOf: url)
        else {
            return nil
        }
        return try? JSONDecoder().decode(Self.self, from: data)
    }
}

private final class AssetDownloadDelegate: NSObject, URLSessionDownloadDelegate {
    private let destinationURL: URL
    private let progressHandler: @Sendable (Int64, Int64) -> Void
    private let completionHandler: @Sendable (Result<URL, Error>) -> Void
    private let lock = NSLock()
    private var finishedURL: URL?
    private var delivered = false
    private var responseError: Error?

    init(
        destinationURL: URL,
        progressHandler: @escaping @Sendable (Int64, Int64) -> Void,
        completionHandler: @escaping @Sendable (Result<URL, Error>) -> Void
    ) {
        self.destinationURL = destinationURL
        self.progressHandler = progressHandler
        self.completionHandler = completionHandler
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        progressHandler(totalBytesWritten, totalBytesExpectedToWrite)
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didReceive response: URLResponse,
        completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
    ) {
        if let httpResponse = response as? HTTPURLResponse,
           !(200...299).contains(httpResponse.statusCode) {
            lock.lock()
            responseError = RunnerError.downloadFailed(
                file: destinationURL.lastPathComponent,
                description: "Server returned HTTP \(httpResponse.statusCode)."
            )
            lock.unlock()
            completionHandler(.cancel)
            return
        }
        completionHandler(.allow)
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        do {
            let fm = FileManager.default
            if fm.fileExists(atPath: destinationURL.path(percentEncoded: false)) {
                try fm.removeItem(at: destinationURL)
            }
            try fm.createDirectory(at: destinationURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            try fm.moveItem(at: location, to: destinationURL)
            lock.lock()
            finishedURL = destinationURL
            lock.unlock()
        } catch {
            completeOnce(.failure(error))
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        lock.lock()
        let responseError = self.responseError
        lock.unlock()

        if let responseError {
            completeOnce(.failure(responseError))
            return
        }

        if let error {
            completeOnce(.failure(error))
            return
        }

        lock.lock()
        let url = finishedURL
        lock.unlock()

        if let url {
            completeOnce(.success(url))
        } else {
            completeOnce(.failure(RunnerError.downloadFailed(file: destinationURL.lastPathComponent, description: "Download completed without a file.")))
        }
    }

    private func completeOnce(_ result: Result<URL, Error>) {
        lock.lock()
        defer { lock.unlock() }
        guard !delivered else { return }
        delivered = true
        completionHandler(result)
    }
}

@MainActor @Observable
final class ModelDownloader {
    enum State: Equatable {
        case idle
        case downloading
        case finished
        case failed
    }

    private struct ActiveDownload {
        let session: URLSession
        let delegate: AssetDownloadDelegate
    }

    private let repoBaseURL = URL(string: "https://huggingface.co/younghan-meta/Parakeet-TDT-ExecuTorch-Metal/resolve/main/")!
    private let formatterRepoBaseURL = URL(string: "https://huggingface.co/younghan-meta/LFM2.5-350M-ExecuWhisper-Formatter/resolve/main/")!
    private let manifest = ModelManifest.load()
    private var activeDownload: ActiveDownload?

    var state: State = .idle
    var statusMessage = ""
    var currentFileName = ""
    var currentFileProgress = 0.0
    var overallProgress = 0.0
    var completedFiles = 0
    var lastError: String?

    var isDownloading: Bool { state == .downloading }

    func downloadModels(destinationDirectory: URL) async throws {
        guard !isDownloading else { return }

        let assets = [
            DownloadAsset(
                fileName: "model.pte",
                url: repoBaseURL.appendingPathComponent("model.pte"),
                minimumBytes: 1_000_000
            ),
            DownloadAsset(
                fileName: "tokenizer.model",
                url: repoBaseURL.appendingPathComponent("tokenizer.model"),
                minimumBytes: 1_000
            ),
        ]
        let stagingDirectory = destinationDirectory
            .deletingLastPathComponent()
            .appendingPathComponent(".ExecuWhisperDownload-\(UUID().uuidString)", isDirectory: true)

        try FileManager.default.createDirectory(at: destinationDirectory.deletingLastPathComponent(), withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: stagingDirectory, withIntermediateDirectories: true)

        state = .downloading
        statusMessage = "Preparing download..."
        currentFileName = ""
        currentFileProgress = 0
        overallProgress = 0
        completedFiles = 0
        lastError = nil

        do {
            for (index, asset) in assets.enumerated() {
                currentFileName = asset.fileName
                statusMessage = "Downloading \(asset.fileName)..."
                currentFileProgress = 0
                let stagedURL = stagingDirectory.appendingPathComponent(asset.fileName)
                _ = try await downloadAsset(asset, to: stagedURL, completedIndex: index, totalCount: assets.count)
                completedFiles = index + 1
                currentFileProgress = 1
                overallProgress = Double(completedFiles) / Double(assets.count)
            }

            statusMessage = "Validating downloads..."
            try validateStagedAssets(in: stagingDirectory, assets: assets)

            statusMessage = "Finalizing model files..."
            try activateDownloads(from: stagingDirectory, to: destinationDirectory, assets: assets)

            currentFileName = ""
            currentFileProgress = 1
            overallProgress = 1
            statusMessage = "Model download complete"
            state = .finished
        } catch {
            lastError = error.localizedDescription
            statusMessage = "Download failed"
            state = .failed
            try? FileManager.default.removeItem(at: stagingDirectory)
            throw error
        }

        try? FileManager.default.removeItem(at: stagingDirectory)
    }

    func downloadFormatterModels(destinationDirectory: URL) async throws {
        guard !isDownloading else { return }

        let assets = [
            DownloadAsset(
                fileName: "lfm2_5_350m_mlx_4w.pte",
                url: formatterRepoBaseURL.appendingPathComponent("lfm2_5_350m_mlx_4w.pte"),
                minimumBytes: 1_000_000
            ),
            DownloadAsset(
                fileName: "tokenizer.json",
                url: formatterRepoBaseURL.appendingPathComponent("tokenizer.json"),
                minimumBytes: 1_000
            ),
            DownloadAsset(
                fileName: "tokenizer_config.json",
                url: formatterRepoBaseURL.appendingPathComponent("tokenizer_config.json"),
                minimumBytes: 100
            ),
        ]
        let stagingDirectory = destinationDirectory
            .deletingLastPathComponent()
            .appendingPathComponent(".ExecuWhisperFormatterDownload-\(UUID().uuidString)", isDirectory: true)

        try FileManager.default.createDirectory(at: destinationDirectory.deletingLastPathComponent(), withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: stagingDirectory, withIntermediateDirectories: true)

        state = .downloading
        statusMessage = "Preparing formatter download..."
        currentFileName = ""
        currentFileProgress = 0
        overallProgress = 0
        completedFiles = 0
        lastError = nil

        do {
            for (index, asset) in assets.enumerated() {
                currentFileName = asset.fileName
                statusMessage = "Downloading \(asset.fileName)..."
                currentFileProgress = 0
                let stagedURL = stagingDirectory.appendingPathComponent(asset.fileName)
                _ = try await downloadAsset(asset, to: stagedURL, completedIndex: index, totalCount: assets.count)
                completedFiles = index + 1
                currentFileProgress = 1
                overallProgress = Double(completedFiles) / Double(assets.count)
            }

            statusMessage = "Validating formatter downloads..."
            try validateStagedAssets(in: stagingDirectory, assets: assets)

            statusMessage = "Finalizing formatter files..."
            try activateDownloads(from: stagingDirectory, to: destinationDirectory, assets: assets)

            currentFileName = ""
            currentFileProgress = 1
            overallProgress = 1
            statusMessage = "Formatter download complete"
            state = .finished
        } catch {
            lastError = error.localizedDescription
            statusMessage = "Formatter download failed"
            state = .failed
            try? FileManager.default.removeItem(at: stagingDirectory)
            throw error
        }

        try? FileManager.default.removeItem(at: stagingDirectory)
    }

    func reset() {
        activeDownload?.session.invalidateAndCancel()
        activeDownload = nil
        state = .idle
        statusMessage = ""
        currentFileName = ""
        currentFileProgress = 0
        overallProgress = 0
        completedFiles = 0
        lastError = nil
    }

    private func downloadAsset(
        _ asset: DownloadAsset,
        to destinationURL: URL,
        completedIndex: Int,
        totalCount: Int
    ) async throws -> URL {
        try await withCheckedThrowingContinuation { continuation in
            let delegate = AssetDownloadDelegate(
                destinationURL: destinationURL,
                progressHandler: { [weak self] written, expected in
                    guard let self else { return }
                    Task { @MainActor in
                        let progress: Double
                        if expected > 0 {
                            progress = Double(written) / Double(expected)
                        } else {
                            progress = 0
                        }
                        self.currentFileProgress = progress
                        self.overallProgress = (Double(completedIndex) + progress) / Double(totalCount)
                    }
                },
                completionHandler: { [weak self] result in
                    guard let self else { return }
                    Task { @MainActor in
                        self.activeDownload?.session.finishTasksAndInvalidate()
                        self.activeDownload = nil
                        continuation.resume(with: result)
                    }
                }
            )

            let config = URLSessionConfiguration.default
            config.timeoutIntervalForRequest = 300
            config.timeoutIntervalForResource = 60 * 60
            let session = URLSession(configuration: config, delegate: delegate, delegateQueue: nil)
            activeDownload = ActiveDownload(session: session, delegate: delegate)

            downloadLog.info("Downloading \(asset.fileName) to \(destinationURL.path(percentEncoded: false))")
            let task = session.downloadTask(with: asset.url)
            task.resume()
        }
    }

    private func activateDownloads(from stagingDirectory: URL, to destinationDirectory: URL, assets: [DownloadAsset]) throws {
        try FileManager.default.createDirectory(at: destinationDirectory, withIntermediateDirectories: true)

        for asset in assets {
            let stagedURL = stagingDirectory.appendingPathComponent(asset.fileName)
            let finalURL = destinationDirectory.appendingPathComponent(asset.fileName)

            if FileManager.default.fileExists(atPath: finalURL.path(percentEncoded: false)) {
                _ = try FileManager.default.replaceItemAt(
                    finalURL,
                    withItemAt: stagedURL,
                    backupItemName: nil,
                    options: .usingNewMetadataOnly
                )
            } else {
                try FileManager.default.moveItem(at: stagedURL, to: finalURL)
            }
        }
    }

    private func validateStagedAssets(in stagingDirectory: URL, assets: [DownloadAsset]) throws {
        for asset in assets {
            let stagedURL = stagingDirectory.appendingPathComponent(asset.fileName)
            let attributes = try FileManager.default.attributesOfItem(atPath: stagedURL.path(percentEncoded: false))
            let size = (attributes[.size] as? NSNumber)?.int64Value ?? 0

            guard size >= asset.minimumBytes else {
                throw RunnerError.downloadFailed(
                    file: asset.fileName,
                    description: "Downloaded file is unexpectedly small (\(size) bytes)."
                )
            }
            if let manifestAsset = manifest?.assets[asset.fileName] {
                guard size == manifestAsset.bytes else {
                    throw RunnerError.downloadFailed(
                        file: asset.fileName,
                        description: "Downloaded file size \(size) does not match expected size \(manifestAsset.bytes)."
                    )
                }
                let digest = try sha256Hex(for: stagedURL)
                guard digest == manifestAsset.sha256.lowercased() else {
                    throw RunnerError.downloadFailed(
                        file: asset.fileName,
                        description: "Downloaded file checksum does not match expected SHA256."
                    )
                }
            }

            let handle = try FileHandle(forReadingFrom: stagedURL)
            let headerData = try handle.read(upToCount: 256) ?? Data()
            try? handle.close()

            if let headerText = String(data: headerData, encoding: .utf8)?
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .lowercased(),
               headerText.contains("<html") || headerText.contains("<!doctype html") {
                throw RunnerError.downloadFailed(
                    file: asset.fileName,
                    description: "Downloaded HTML instead of a model artifact."
                )
            }
        }
    }

    private func sha256Hex(for url: URL) throws -> String {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }

        var hasher = SHA256()
        while autoreleasepool(invoking: {
            let data = handle.readData(ofLength: 8 * 1024 * 1024)
            guard !data.isEmpty else { return false }
            hasher.update(data: data)
            return true
        }) {}

        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }
}
