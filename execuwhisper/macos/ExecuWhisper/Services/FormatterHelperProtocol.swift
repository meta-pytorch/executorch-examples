/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

enum FormatterHelperProtocol {
    static let version = 1

    struct FormatRequest: Codable, Sendable, Equatable {
        let type: String
        let version: Int
        let requestID: String
        let prompt: String
        let maxNewTokens: Int
        let temperature: Double

        init(
            requestID: String,
            prompt: String,
            maxNewTokens: Int,
            temperature: Double
        ) {
            self.type = "format"
            self.version = FormatterHelperProtocol.version
            self.requestID = requestID
            self.prompt = prompt
            self.maxNewTokens = maxNewTokens
            self.temperature = temperature
        }

        enum CodingKeys: String, CodingKey {
            case type
            case version
            case requestID = "request_id"
            case prompt
            case maxNewTokens = "max_new_tokens"
            case temperature
        }
    }

    struct ShutdownRequest: Codable, Sendable, Equatable {
        let type: String
        let version: Int

        init() {
            self.type = "shutdown"
            self.version = FormatterHelperProtocol.version
        }
    }

    struct ReadyMessage: Codable, Sendable, Equatable {
        let type: String
        let version: Int
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
        let tokensPerSecond: Double?

        enum CodingKeys: String, CodingKey {
            case type
            case version
            case requestID = "request_id"
            case text
            case stdout
            case stderr
            case tokensPerSecond = "tokens_per_second"
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
                    debugDescription: "Unknown formatter helper message type: \(type)"
                )
            }
        }
    }
}
