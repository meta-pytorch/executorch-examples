/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation
import Testing

struct ParakeetHelperProtocolTests {
    @Test
    func transcribeRequestEncodesStableWireFormat() throws {
        let request = ParakeetHelperProtocol.TranscribeRequest(
            requestID: "req-123",
            audio: .init(
                encoding: .float32LittleEndian,
                sampleRate: 16_000,
                channelCount: 1,
                payloadByteCount: 6400
            ),
            enableRuntimeProfile: true
        )

        let data = try JSONEncoder().encode(request)
        let json = try #require(
            JSONSerialization.jsonObject(with: data) as? [String: Any]
        )

        #expect(json["type"] as? String == "transcribe")
        #expect(json["version"] as? Int == 1)
        #expect(json["request_id"] as? String == "req-123")
        #expect(json["enable_runtime_profile"] as? Bool == true)

        let audio = try #require(json["audio"] as? [String: Any])
        #expect(audio["encoding"] as? String == "f32le")
        #expect(audio["sample_rate"] as? Int == 16_000)
        #expect(audio["channel_count"] as? Int == 1)
        #expect(audio["payload_byte_count"] as? Int == 6400)
    }

    @Test
    func helperMessageDecodesResultEnvelope() throws {
        let data = Data(
            """
            {
              "type": "result",
              "version": 1,
              "request_id": "req-123",
              "text": "hello world",
              "stdout": "PyTorchObserver foo",
              "stderr": "",
              "runtime_profile": "decode_loop_ms=12.5"
            }
            """.utf8
        )

        let message = try JSONDecoder().decode(ParakeetHelperProtocol.HelperMessage.self, from: data)

        guard case .result(let result) = message else {
            Issue.record("Expected result message")
            return
        }

        #expect(result.requestID == "req-123")
        #expect(result.text == "hello world")
        #expect(result.stdout == "PyTorchObserver foo")
        #expect(result.stderr == "")
        #expect(result.runtimeProfile == "decode_loop_ms=12.5")
    }
}
