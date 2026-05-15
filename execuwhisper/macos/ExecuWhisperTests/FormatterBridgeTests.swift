/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation
import Testing

struct FormatterBridgeTests {
    @Test
    func warmFormatterHelperIsReusedAcrossRequests() async throws {
        let sandbox = makeSandbox()
        let launchCountURL = sandbox.appendingPathComponent("launch_count.txt")
        let helperURL = try makeFakeFormatterHelper(in: sandbox, launchCountURL: launchCountURL)
        let modelURL = createDummyFile(named: "lfm2_5_350m_mlx_4w.pte", in: sandbox)
        let tokenizerURL = createDummyFile(named: "tokenizer.json", in: sandbox)
        let tokenizerConfigURL = createDummyFile(named: "tokenizer_config.json", in: sandbox)
        let bridge = FormatterBridge()

        try await bridge.prepare(
            runnerPath: helperURL.path,
            modelPath: modelURL.path,
            tokenizerPath: tokenizerURL.path,
            tokenizerConfigPath: tokenizerConfigURL.path
        )
        let first = try await bridge.format(
            runnerPath: helperURL.path,
            modelPath: modelURL.path,
            tokenizerPath: tokenizerURL.path,
            tokenizerConfigPath: tokenizerConfigURL.path,
            prompt: "first prompt",
            maxNewTokens: 96,
            temperature: 0.0
        )
        let second = try await bridge.format(
            runnerPath: helperURL.path,
            modelPath: modelURL.path,
            tokenizerPath: tokenizerURL.path,
            tokenizerConfigPath: tokenizerConfigURL.path,
            prompt: "second prompt",
            maxNewTokens: 96,
            temperature: 0.0
        )
        await bridge.shutdown()

        #expect(first.text == "formatted:first prompt")
        #expect(second.text == "formatted:second prompt")
        #expect(second.tokensPerSecond == 24.0)

        let launches = try String(contentsOf: launchCountURL, encoding: .utf8)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        #expect(launches == "1")
    }

    @Test
    func prepareValidatesFormatterAssetsBeforeLaunch() async throws {
        let sandbox = makeSandbox()
        let helperURL = try makeFakeFormatterHelper(
            in: sandbox,
            launchCountURL: sandbox.appendingPathComponent("launch_count.txt")
        )
        let modelURL = createDummyFile(named: "lfm2_5_350m_mlx_4w.pte", in: sandbox)
        let tokenizerURL = createDummyFile(named: "tokenizer.json", in: sandbox)
        let missingTokenizerConfig = sandbox.appendingPathComponent("tokenizer_config.json")
        let bridge = FormatterBridge()

        await #expect(throws: RunnerError.self) {
            try await bridge.prepare(
                runnerPath: helperURL.path,
                modelPath: modelURL.path,
                tokenizerPath: tokenizerURL.path,
                tokenizerConfigPath: missingTokenizerConfig.path
            )
        }
    }

    private func makeFakeFormatterHelper(in sandbox: URL, launchCountURL: URL) throws -> URL {
        let helperURL = sandbox.appendingPathComponent("formatter_helper.py")
        let script = """
        #!/usr/bin/env python3
        import json
        import pathlib
        import sys

        launch_path = pathlib.Path(\(pythonStringLiteral(launchCountURL.path)))
        launch_count = 0
        if launch_path.exists():
            launch_count = int(launch_path.read_text().strip() or "0")
        launch_path.write_text(str(launch_count + 1))

        sys.stdout.write(json.dumps({"type": "ready", "version": 1}) + "\\n")
        sys.stdout.flush()

        while True:
            line = sys.stdin.readline()
            if not line:
                break
            request = json.loads(line)
            request_type = request.get("type")
            if request_type == "shutdown":
                break
            if request_type != "format":
                sys.stdout.write(json.dumps({
                    "type": "error",
                    "version": 1,
                    "message": "unsupported request"
                }) + "\\n")
                sys.stdout.flush()
                continue

            request_id = request["request_id"]
            prompt = request["prompt"]
            sys.stdout.write(json.dumps({
                "type": "status",
                "version": 1,
                "request_id": request_id,
                "phase": "formatting",
                "message": "Formatting..."
            }) + "\\n")
            sys.stdout.write(json.dumps({
                "type": "result",
                "version": 1,
                "request_id": request_id,
                "text": "formatted:" + prompt,
                "stdout": "",
                "stderr": "",
                "tokens_per_second": 24.0
            }) + "\\n")
            sys.stdout.flush()
        """
        try script.write(to: helperURL, atomically: true, encoding: .utf8)
        try FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: helperURL.path)
        return helperURL
    }

    private func createDummyFile(named name: String, in sandbox: URL) -> URL {
        let url = sandbox.appendingPathComponent(name)
        FileManager.default.createFile(atPath: url.path, contents: Data("x".utf8))
        return url
    }

    private func pythonStringLiteral(_ value: String) -> String {
        let escaped = value
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
        return "\"\(escaped)\""
    }

    private func makeSandbox() -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("formatter-bridge-\(UUID().uuidString)", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }
}
