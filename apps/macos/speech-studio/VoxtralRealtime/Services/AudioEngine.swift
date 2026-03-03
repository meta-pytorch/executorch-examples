import AVFoundation
import Accelerate
import os

private let log = Logger(subsystem: "com.younghan.VoxtralRealtime", category: "AudioEngine")

actor AudioEngine {
    private var engine: AVAudioEngine?

    func startCapture(
        writingTo handle: FileHandle,
        levelHandler: @Sendable @escaping (Float) -> Void
    ) throws {
        let engine = AVAudioEngine()
        let inputNode = engine.inputNode
        let hwFormat = inputNode.outputFormat(forBus: 0)

        log.info("Hardware audio format: \(hwFormat.sampleRate)Hz, \(hwFormat.channelCount)ch")

        guard hwFormat.sampleRate > 0, hwFormat.channelCount > 0 else {
            throw RunnerError.launchFailed(description: "No audio input device available (hw format: \(hwFormat))")
        }

        let targetFormat = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: 16000,
            channels: 1,
            interleaved: false
        )!

        guard let converter = AVAudioConverter(from: hwFormat, to: targetFormat) else {
            throw RunnerError.launchFailed(
                description: "Cannot convert audio from \(hwFormat.sampleRate)Hz/\(hwFormat.channelCount)ch to 16kHz mono"
            )
        }

        let sampleRateRatio = 16000.0 / hwFormat.sampleRate
        var totalBytesWritten: Int64 = 0
        var writeErrorCount = 0

        inputNode.installTap(onBus: 0, bufferSize: 4096, format: hwFormat) { buffer, _ in
            let capacity = AVAudioFrameCount(Double(buffer.frameLength) * sampleRateRatio) + 1
            guard let converted = AVAudioPCMBuffer(pcmFormat: targetFormat, frameCapacity: capacity) else {
                log.warning("Failed to allocate conversion buffer")
                return
            }

            var consumed = false
            var error: NSError?
            converter.convert(to: converted, error: &error) { _, outStatus in
                if !consumed {
                    consumed = true
                    outStatus.pointee = .haveData
                    return buffer
                }
                outStatus.pointee = .noDataNow
                return nil
            }

            if let error {
                log.warning("Audio conversion error: \(error.localizedDescription)")
                return
            }

            guard converted.frameLength > 0,
                  let channelData = converted.floatChannelData
            else { return }

            let frameCount = Int(converted.frameLength)
            let samples = channelData[0]

            var rms: Float = 0
            vDSP_rmsqv(samples, 1, &rms, vDSP_Length(frameCount))
            levelHandler(rms)

            let byteCount = frameCount * MemoryLayout<Float>.size
            let data = Data(bytes: samples, count: byteCount)
            do {
                try handle.write(contentsOf: data)
                totalBytesWritten += Int64(byteCount)
                if totalBytesWritten % 160000 == 0 {
                    log.debug("Audio written: \(totalBytesWritten) bytes (\(totalBytesWritten / 64000)s @ 16kHz)")
                }
            } catch {
                writeErrorCount += 1
                if writeErrorCount <= 3 {
                    log.error("Pipe write failed (#\(writeErrorCount)): \(error.localizedDescription)")
                }
            }
        }

        try engine.start()
        log.info("AVAudioEngine started — capturing and piping to runner stdin")
        self.engine = engine
    }

    func stopCapture() {
        engine?.inputNode.removeTap(onBus: 0)
        engine?.stop()
        engine = nil
        log.info("Audio capture stopped")
    }
}
