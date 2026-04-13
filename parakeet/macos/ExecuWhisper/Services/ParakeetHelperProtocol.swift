/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

enum ParakeetHelperProtocol {
    static let version = 1

    enum AudioEncoding: String, Codable, Sendable, Equatable {
        case float32LittleEndian = "f32le"
    }

    struct AudioDescriptor: Codable, Sendable, Equatable {
        let encoding: AudioEncoding
        let sampleRate: Int
        let channelCount: Int
        let payloadByteCount: Int

        enum CodingKeys: String, CodingKey {
            case encoding
            case sampleRate = "sample_rate"
            case channelCount = "channel_count"
            case payloadByteCount = "payload_byte_count"
        }
    }

    struct TranscribeRequest: Codable, Sendable, Equatable {
        let type: String
        let version: Int
        let requestID: String
        let audio: AudioDescriptor
        let enableRuntimeProfile: Bool

        init(
            requestID: String,
            audio: AudioDescriptor,
            enableRuntimeProfile: Bool
        ) {
            self.type = "transcribe"
            self.version = ParakeetHelperProtocol.version
            self.requestID = requestID
            self.audio = audio
            self.enableRuntimeProfile = enableRuntimeProfile
        }

        enum CodingKeys: String, CodingKey {
            case type
            case version
            case requestID = "request_id"
            case audio
            case enableRuntimeProfile = "enable_runtime_profile"
        }
    }

    struct ReadyMessage: Codable, Sendable, Equatable {
        let type: String
        let version: Int

        init() {
            self.type = "ready"
            self.version = ParakeetHelperProtocol.version
        }
    }

    struct StatusMessage: Codable, Sendable, Equatable {
        let type: String
        let version: Int
        let requestID: String?
        let phase: String
        let message: String

        enum CodingKeys: String, CodingKey {
            case type
            case version
            case requestID = "request_id"
            case phase
            case message
        }
    }

    struct ResultMessage: Codable, Sendable, Equatable {
        let type: String
        let version: Int
        let requestID: String
        let text: String
        let stdout: String
        let stderr: String
        let runtimeProfile: String?

        enum CodingKeys: String, CodingKey {
            case type
            case version
            case requestID = "request_id"
            case text
            case stdout
            case stderr
            case runtimeProfile = "runtime_profile"
        }
    }

    struct ErrorMessage: Codable, Sendable, Equatable {
        let type: String
        let version: Int
        let requestID: String?
        let message: String
        let details: String?

        enum CodingKeys: String, CodingKey {
            case type
            case version
            case requestID = "request_id"
            case message
            case details
        }
    }

    enum HelperMessage: Decodable, Sendable, Equatable {
        case ready(ReadyMessage)
        case status(StatusMessage)
        case result(ResultMessage)
        case error(ErrorMessage)

        private enum CodingKeys: String, CodingKey {
            case type
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            let type = try container.decode(String.self, forKey: .type)
            switch type {
            case "ready":
                self = .ready(try ReadyMessage(from: decoder))
            case "status":
                self = .status(try StatusMessage(from: decoder))
            case "result":
                self = .result(try ResultMessage(from: decoder))
            case "error":
                self = .error(try ErrorMessage(from: decoder))
            default:
                throw DecodingError.dataCorruptedError(
                    forKey: .type,
                    in: container,
                    debugDescription: "Unknown helper message type: \(type)"
                )
            }
        }
    }
}
