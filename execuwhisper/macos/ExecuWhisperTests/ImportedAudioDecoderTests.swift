/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import AVFoundation
import Foundation
import Testing

struct ImportedAudioDecoderTests {
    @Test
    func supportsWavAndMp3ExtensionsOnly() {
        #expect(ImportedAudioDecoder.supportsAudioFile(URL(fileURLWithPath: "/tmp/sample.wav")))
        #expect(ImportedAudioDecoder.supportsAudioFile(URL(fileURLWithPath: "/tmp/sample.mp3")))
        #expect(!ImportedAudioDecoder.supportsAudioFile(URL(fileURLWithPath: "/tmp/sample.m4a")))
        #expect(!ImportedAudioDecoder.supportsAudioFile(URL(fileURLWithPath: "/tmp/sample.txt")))
    }

    @Test
    func importableAudioFileRequiresExactlyOneSupportedFile() {
        let wavURL = URL(fileURLWithPath: "/tmp/sample.wav")
        let mp3URL = URL(fileURLWithPath: "/tmp/sample.mp3")
        let txtURL = URL(fileURLWithPath: "/tmp/sample.txt")

        #expect(ImportedAudioDecoder.importableAudioFile(from: [wavURL]) == wavURL)
        #expect(ImportedAudioDecoder.importableAudioFile(from: [mp3URL]) == mp3URL)
        #expect(ImportedAudioDecoder.importableAudioFile(from: [txtURL]) == nil)
        #expect(ImportedAudioDecoder.importableAudioFile(from: [wavURL, mp3URL]) == nil)
    }

    @Test
    func decodeAudioFileNormalizesWavToFloat32Mono16kPCM() throws {
        let sandbox = makeSandbox()
        let inputURL = sandbox.appendingPathComponent("input.wav")
        try writeTestWAV(to: inputURL, sampleRate: 44_100, channelCount: 2, frameCount: 4_410)

        let decoded = try ImportedAudioDecoder().decodeAudioFile(at: inputURL)

        #expect(decoded.duration > 0.09 && decoded.duration < 0.11)
        #expect(decoded.pcmData.count % MemoryLayout<Float>.size == 0)
        let sampleCount = decoded.pcmData.count / MemoryLayout<Float>.size
        #expect(sampleCount > 1_500 && sampleCount < 1_700)
    }

    private func writeTestWAV(
        to url: URL,
        sampleRate: Double,
        channelCount: AVAudioChannelCount,
        frameCount: AVAudioFrameCount
    ) throws {
        let format = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: sampleRate,
            channels: channelCount,
            interleaved: false
        )!
        let file = try AVAudioFile(forWriting: url, settings: format.settings)
        let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frameCount)!
        buffer.frameLength = frameCount

        for channel in 0..<Int(channelCount) {
            let samples = buffer.floatChannelData![channel]
            for frame in 0..<Int(frameCount) {
                samples[frame] = Float(frame) / Float(max(Int(frameCount), 1))
            }
        }

        try file.write(from: buffer)
    }

    private func makeSandbox() -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }
}
