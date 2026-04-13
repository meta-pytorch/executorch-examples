/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import AVFoundation
import Foundation
import UniformTypeIdentifiers

struct DecodedImportedAudioFile: Sendable, Equatable {
    let pcmData: Data
    let duration: TimeInterval
}

protocol ImportedAudioDecoding: Sendable {
    func decodeAudioFile(at url: URL) throws -> DecodedImportedAudioFile
}

struct ImportedAudioDecoder: ImportedAudioDecoding {
    private static let supportedExtensions: Set<String> = ["wav", "mp3"]
    private static let maxImportedDuration: TimeInterval = 30 * 60
    private let outputSampleRate: Double = 16_000

    static var allowedContentTypes: [UTType] {
        [UTType(filenameExtension: "wav"), UTType(filenameExtension: "mp3")].compactMap { $0 }
    }

    static func supportsAudioFile(_ url: URL) -> Bool {
        supportedExtensions.contains(url.pathExtension.lowercased())
    }

    static func importableAudioFile(from urls: [URL]) -> URL? {
        guard urls.count == 1, let url = urls.first, supportsAudioFile(url) else { return nil }
        return url
    }

    func decodeAudioFile(at url: URL) throws -> DecodedImportedAudioFile {
        guard Self.supportsAudioFile(url) else {
            throw RunnerError.transcriptionFailed(
                description: "Unsupported audio file type. Import a .wav or .mp3 file."
            )
        }

        let audioFile: AVAudioFile
        do {
            audioFile = try AVAudioFile(forReading: url)
        } catch {
            throw RunnerError.transcriptionFailed(
                description: "Could not open audio file '\(url.lastPathComponent)'."
            )
        }

        let sourceFormat = audioFile.processingFormat
        guard sourceFormat.channelCount > 0, sourceFormat.sampleRate > 0 else {
            throw RunnerError.transcriptionFailed(description: "Audio file is missing a readable audio stream.")
        }

        let frameCount = audioFile.length
        guard frameCount > 0, frameCount <= Int64(UInt32.max) else {
            throw RunnerError.transcriptionFailed(description: "Audio file is empty or too large to import.")
        }
        let duration = Double(frameCount) / sourceFormat.sampleRate
        guard duration <= Self.maxImportedDuration else {
            throw RunnerError.transcriptionFailed(description: "Audio file is too long to import. Please use a file shorter than 30 minutes.")
        }

        guard let inputBuffer = AVAudioPCMBuffer(
            pcmFormat: sourceFormat,
            frameCapacity: AVAudioFrameCount(frameCount)
        ) else {
            throw RunnerError.transcriptionFailed(description: "Could not allocate an audio decode buffer.")
        }

        do {
            try audioFile.read(into: inputBuffer)
        } catch {
            throw RunnerError.transcriptionFailed(description: "Could not decode audio frames from the file.")
        }

        guard inputBuffer.frameLength > 0 else {
            throw RunnerError.transcriptionFailed(description: "Audio file did not contain any decodable samples.")
        }

        let outputChannelCount: AVAudioChannelCount = 1
        guard let outputFormat = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: outputSampleRate,
            channels: outputChannelCount,
            interleaved: false
        ) else {
            throw RunnerError.transcriptionFailed(description: "Could not create the imported audio output format.")
        }

        guard let converter = AVAudioConverter(from: sourceFormat, to: outputFormat) else {
            throw RunnerError.transcriptionFailed(
                description: "Could not convert audio from \(Int(sourceFormat.sampleRate)) Hz to 16 kHz mono."
            )
        }

        let convertedCapacity = AVAudioFrameCount(
            ceil(Double(inputBuffer.frameLength) * outputSampleRate / sourceFormat.sampleRate)
        ) + 1
        guard let outputBuffer = AVAudioPCMBuffer(
            pcmFormat: outputFormat,
            frameCapacity: max(convertedCapacity, 1)
        ) else {
            throw RunnerError.transcriptionFailed(description: "Could not allocate a normalized audio buffer.")
        }

        var didConsumeInput = false
        var conversionError: NSError?
        let status = converter.convert(to: outputBuffer, error: &conversionError) { _, outStatus in
            if didConsumeInput {
                outStatus.pointee = .endOfStream
                return nil
            }
            didConsumeInput = true
            outStatus.pointee = .haveData
            return inputBuffer
        }

        if let conversionError {
            throw RunnerError.transcriptionFailed(description: conversionError.localizedDescription)
        }
        guard status != .error, outputBuffer.frameLength > 0, let channelData = outputBuffer.floatChannelData else {
            throw RunnerError.transcriptionFailed(description: "Could not normalize the imported audio samples.")
        }
        let byteCount = Int(outputBuffer.frameLength) * MemoryLayout<Float>.size
        let pcmData = Data(bytes: channelData[0], count: byteCount)
        return DecodedImportedAudioFile(pcmData: pcmData, duration: duration)
    }
}
