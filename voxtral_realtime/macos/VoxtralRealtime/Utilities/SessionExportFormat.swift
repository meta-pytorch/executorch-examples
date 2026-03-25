/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation
import UniformTypeIdentifiers

enum SessionExportFormat: String, CaseIterable, Sendable {
    case text
    case json
    case srt

    var fileExtension: String {
        switch self {
        case .text:
            return "txt"
        case .json:
            return "json"
        case .srt:
            return "srt"
        }
    }

    var contentType: UTType {
        switch self {
        case .text:
            return .plainText
        case .json:
            return .json
        case .srt:
            return UTType(filenameExtension: "srt") ?? .plainText
        }
    }

    func render(_ session: Session) -> String {
        switch self {
        case .text:
            return session.transcript
        case .json:
            let payload = ExportPayload(
                title: session.displayTitle,
                date: session.date,
                source: session.source,
                transcript: session.transcript,
                rawTranscript: session.rawTranscript,
                duration: session.duration,
                tags: session.tags,
                wakeTriggered: session.wakeTriggered
            )
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
            encoder.dateEncodingStrategy = .iso8601
            let data = (try? encoder.encode(payload)) ?? Data("{}".utf8)
            return String(decoding: data, as: UTF8.self)
        case .srt:
            let end = max(session.duration, 1)
            return """
            1
            00:00:00,000 --> \(srtTimestamp(end))
            \(session.transcript)
            """
        }
    }

    private func srtTimestamp(_ interval: TimeInterval) -> String {
        let totalMilliseconds = Int((interval * 1000).rounded())
        let hours = totalMilliseconds / 3_600_000
        let minutes = (totalMilliseconds / 60_000) % 60
        let seconds = (totalMilliseconds / 1_000) % 60
        let milliseconds = totalMilliseconds % 1_000
        return String(format: "%02d:%02d:%02d,%03d", hours, minutes, seconds, milliseconds)
    }

    private struct ExportPayload: Codable {
        let title: String
        let date: Date
        let source: SessionSource
        let transcript: String
        let rawTranscript: String?
        let duration: TimeInterval
        let tags: [String]
        let wakeTriggered: Bool
    }
}
