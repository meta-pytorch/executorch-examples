/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation
import Testing

struct RunnerBridgeTests {
    @Test
    func runOptionsCanEnableRuntimeProfilingFromEnvironment() {
        let enabled = RunnerBridge.RunOptions.fromEnvironment([
            "EXECUWHISPER_ENABLE_RUNTIME_PROFILE": "1"
        ])
        let disabled = RunnerBridge.RunOptions.fromEnvironment([:])

        #expect(enabled.enableRuntimeProfile)
        #expect(!disabled.enableRuntimeProfile)
    }

    @Test
    func warmHelperIsReusedAcrossTwoRequests() async throws {
        let sandbox = makeSandbox()
        let launchCountURL = sandbox.appendingPathComponent("launch_count.txt")
        let helperURL = try makeFakeHelper(
            in: sandbox,
            name: "helper_a.py",
            launchCountURL: launchCountURL,
            transcriptPrefix: "warm"
        )
        let modelURL = createDummyFile(named: "model.pte", in: sandbox)
        let tokenizerURL = createDummyFile(named: "tokenizer.model", in: sandbox)
        let bridge = RunnerBridge()

        try await bridge.prepare(
            runnerPath: helperURL.path,
            modelPath: modelURL.path,
            tokenizerPath: tokenizerURL.path
        )
        let first = try await collectResult(
            from: await bridge.transcribePCM(
                runnerPath: helperURL.path,
                modelPath: modelURL.path,
                tokenizerPath: tokenizerURL.path,
                pcmData: makePCMData(sampleCount: 1600),
                options: .init()
            )
        )
        let second = try await collectResult(
            from: await bridge.transcribePCM(
                runnerPath: helperURL.path,
                modelPath: modelURL.path,
                tokenizerPath: tokenizerURL.path,
                pcmData: makePCMData(sampleCount: 3200),
                options: .init(enableRuntimeProfile: true)
            )
        )
        await bridge.shutdown()

        #expect(first.text == "warm:1600")
        #expect(second.text == "warm:3200")
        #expect(second.runtimeProfile == "RUNTIME_PROFILE decode_loop_ms=1.0 host_overhead_ms=0.2")

        let launches = try String(contentsOf: launchCountURL, encoding: .utf8)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        #expect(launches == "1")
    }

    @Test
    func prepareRestartsWarmHelperWhenBinaryPathChanges() async throws {
        let sandbox = makeSandbox(named: "runner bridge sandbox")
        let launchCountAURL = sandbox.appendingPathComponent("launch_count_a.txt")
        let launchCountBURL = sandbox.appendingPathComponent("launch_count_b.txt")
        let helperAURL = try makeFakeHelper(
            in: sandbox,
            name: "helper_a.py",
            launchCountURL: launchCountAURL,
            transcriptPrefix: "alpha"
        )
        let helperBURL = try makeFakeHelper(
            in: sandbox,
            name: "helper_b.py",
            launchCountURL: launchCountBURL,
            transcriptPrefix: "beta"
        )
        let modelURL = createDummyFile(named: "model.pte", in: sandbox)
        let tokenizerURL = createDummyFile(named: "tokenizer.model", in: sandbox)
        let bridge = RunnerBridge()

        try await bridge.prepare(
            runnerPath: helperAURL.path,
            modelPath: modelURL.path,
            tokenizerPath: tokenizerURL.path
        )
        try await bridge.prepare(
            runnerPath: helperBURL.path,
            modelPath: modelURL.path,
            tokenizerPath: tokenizerURL.path
        )
        let result = try await collectResult(
            from: await bridge.transcribePCM(
                runnerPath: helperBURL.path,
                modelPath: modelURL.path,
                tokenizerPath: tokenizerURL.path,
                pcmData: makePCMData(sampleCount: 800),
                options: .init()
            )
        )
        await bridge.shutdown()

        #expect(result.text == "beta:800")

        let launchesA = try String(contentsOf: launchCountAURL, encoding: .utf8)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let launchesB = try String(contentsOf: launchCountBURL, encoding: .utf8)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        #expect(launchesA == "1")
        #expect(launchesB == "1")
    }

    private func collectResult(
        from events: AsyncThrowingStream<RunnerBridge.Event, Error>
    ) async throws -> RunnerBridge.TranscriptionResult {
        var completed: RunnerBridge.TranscriptionResult?
        for try await event in events {
            if case .completed(let result) = event {
                completed = result
            }
        }
        return try #require(completed)
    }

    private func makeFakeHelper(
        in sandbox: URL,
        name: String,
        launchCountURL: URL,
        transcriptPrefix: String
    ) throws -> URL {
        let helperURL = sandbox.appendingPathComponent(name)
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

        transcript_prefix = \(pythonStringLiteral(transcriptPrefix))
        sys.stdout.write(json.dumps({"type": "ready", "version": 1}) + "\\n")
        sys.stdout.flush()

        while True:
            header = sys.stdin.buffer.readline()
            if not header:
                break
            request = json.loads(header.decode("utf-8"))
            request_type = request.get("type")
            if request_type == "shutdown":
                break
            if request_type != "transcribe":
                sys.stdout.write(json.dumps({
                    "type": "error",
                    "version": 1,
                    "message": "unsupported request"
                }) + "\\n")
                sys.stdout.flush()
                continue

            request_id = request["request_id"]
            payload_size = request["audio"]["payload_byte_count"]
            payload = sys.stdin.buffer.read(payload_size)
            sample_count = payload_size // 4
            sys.stdout.write(json.dumps({
                "type": "status",
                "version": 1,
                "request_id": request_id,
                "phase": "running_encoder",
                "message": "Running encoder..."
            }) + "\\n")
            result_payload = {
                "type": "result",
                "version": 1,
                "request_id": request_id,
                "text": f"{transcript_prefix}:{sample_count}",
                "stdout": "PyTorchObserver {}",
                "stderr": ""
            }
            if request.get("enable_runtime_profile"):
                result_payload["runtime_profile"] = "RUNTIME_PROFILE decode_loop_ms=1.0 host_overhead_ms=0.2"
            sys.stdout.write(json.dumps(result_payload) + "\\n")
            sys.stdout.flush()
        """
        try script.write(to: helperURL, atomically: true, encoding: .utf8)
        try FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: helperURL.path)
        return helperURL
    }

    private func makePCMData(sampleCount: Int) -> Data {
        var samples = (0..<sampleCount).map { Float($0) / Float(max(sampleCount, 1)) }
        return Data(bytes: &samples, count: samples.count * MemoryLayout<Float>.size)
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

    private func makeSandbox(named name: String = UUID().uuidString) -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("\(name)-\(UUID().uuidString)", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }
}
