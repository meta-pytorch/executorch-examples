/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation
import Testing

struct FormatterHelperProtocolTests {
    @Test
    func formatRequestEncodesStableWireFormat() throws {
        let request = FormatterHelperProtocol.FormatRequest(
            requestID: "fmt-123",
            prompt: "format this",
            maxNewTokens: 128,
            temperature: 0.0
        )

        let data = try JSONEncoder().encode(request)
        let json = try #require(
            JSONSerialization.jsonObject(with: data) as? [String: Any]
        )

        #expect(json["type"] as? String == "format")
        #expect(json["version"] as? Int == 1)
        #expect(json["request_id"] as? String == "fmt-123")
        #expect(json["prompt"] as? String == "format this")
        #expect(json["max_new_tokens"] as? Int == 128)
        #expect(json["temperature"] as? Double == 0.0)
    }

    @Test
    func helperMessageDecodesFormatterResultEnvelope() throws {
        let data = Data(
            """
            {
              "type": "result",
              "version": 1,
              "request_id": "fmt-123",
              "text": "Polished output.",
              "stdout": "tokens=12",
              "stderr": "",
              "tokens_per_second": 42.5
            }
            """.utf8
        )

        let message = try JSONDecoder().decode(FormatterHelperProtocol.HelperMessage.self, from: data)

        guard case .result(let result) = message else {
            Issue.record("Expected result message")
            return
        }

        #expect(result.requestID == "fmt-123")
        #expect(result.text == "Polished output.")
        #expect(result.stdout == "tokens=12")
        #expect(result.stderr == "")
        #expect(result.tokensPerSecond == 42.5)
    }
}
