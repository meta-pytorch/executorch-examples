/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import ExecuTorch
import ImageClassification
import UIKit

import os.log

public enum MobileNetClassifierError: Error {
  case rawData
  case transform

  var localizedDescription: String {
    switch self {
    case .rawData:
      return "Cannot get the pixel data from the image"
    case .transform:
      return "Cannot transform the image"
    }
  }
}

// See https://pytorch.org/vision/main/models/generated/torchvision.models.mobilenet_v3_small.html
// on model input/output spec.
public class MobileNetClassifier: ImageClassification {
  private static let resizeSize: CGFloat = 256
  private static let cropSize: CGFloat = 224

  private var module: Module
  private var labels: [String] = []
  private var rawDataBuffer: [UInt8]
  private var normalizedBuffer: [Float]

  public init?(modelFilePath: String, labelsFilePath: String) throws {
    labels = try String(contentsOfFile: labelsFilePath, encoding: .utf8)
      .components(separatedBy: .newlines)
    module = Module(filePath: modelFilePath)
    rawDataBuffer = [UInt8](repeating: 0, count: Int(Self.cropSize * Self.cropSize) * 4)
    normalizedBuffer = [Float](repeating: 0, count: rawDataBuffer.count / 4 * 3)

    #if DEBUG
    Log.shared.add(sink: self)
    #endif
  }

  deinit {
    #if DEBUG
    Log.shared.remove(sink: self)
    #endif
  }

  public func classify(image: UIImage) throws -> [Classification] {
    try normalize(transformed(image))
    let input = Tensor<Float>(&normalizedBuffer, shape: [1, 3, 224, 224])
    let output = try Tensor<Float>(module.forward(input))
    return softmax(output.scalars())
      .enumerated()
      .sorted(by: { $0.element > $1.element })
      .compactMap { index, probability in
        index < labels.count ? Classification(label: labels[index], confidence: probability) : nil
      }
  }

  private func transformed(_ image: UIImage) throws -> UIImage {
    let aspectRatio = image.size.width / image.size.height
    let targetSize =
      aspectRatio > 1
      ? CGSize(width: Self.resizeSize * aspectRatio, height: Self.resizeSize)
      : CGSize(width: Self.resizeSize, height: Self.resizeSize / aspectRatio)
    let cropRect = CGRect(
      x: (targetSize.width - Self.cropSize) / 2,
      y: (targetSize.height - Self.cropSize) / 2,
      width: Self.cropSize,
      height: Self.cropSize)

    UIGraphicsBeginImageContextWithOptions(cropRect.size, false, 1)
    defer { UIGraphicsEndImageContext() }
    image.draw(
      in: CGRect(
        x: -cropRect.origin.x,
        y: -cropRect.origin.y,
        width: targetSize.width,
        height: targetSize.height))
    guard let resizedAndCroppedImage = UIGraphicsGetImageFromCurrentImageContext()
    else {
      throw MobileNetClassifierError.transform
    }
    return resizedAndCroppedImage
  }

  private func normalize(_ image: UIImage) throws {
    guard let cgImage = image.cgImage else {
      throw MobileNetClassifierError.rawData
    }
    let context = CGContext(
      data: &rawDataBuffer,
      width: cgImage.width,
      height: cgImage.height,
      bitsPerComponent: 8,
      bytesPerRow: cgImage.width * 4,
      space: CGColorSpaceCreateDeviceRGB(),
      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
    )
    context?.draw(
      cgImage,
      in: CGRect(
        origin: CGPoint.zero,
        size: CGSize(width: cgImage.width, height: cgImage.height)))
    let mean: [Float] = [0.485, 0.456, 0.406]
    let std: [Float] = [0.229, 0.224, 0.225]
    let pixelCount = rawDataBuffer.count / 4

    for i in 0..<pixelCount {
      normalizedBuffer[i] = (Float(rawDataBuffer[i * 4 + 0]) / 255 - mean[0]) / std[0]
      normalizedBuffer[i + pixelCount] = (Float(rawDataBuffer[i * 4 + 1]) / 255 - mean[1]) / std[1]
      normalizedBuffer[i + pixelCount * 2] = (Float(rawDataBuffer[i * 4 + 2]) / 255 - mean[2]) / std[2]
    }
  }

  private func softmax(_ input: [Float]) -> [Float] {
    let maxInput = input.max() ?? 0
    let expInput = input.map { exp($0 - maxInput) }
    let sumExpInput = expInput.reduce(0, +)
    return expInput.map { $0 / sumExpInput }
  }
}

#if DEBUG
extension MobileNetClassifier: LogSink {
  public func log(level: LogLevel, timestamp: TimeInterval, filename: String, line: UInt, message: String) {
    let logMessage = "executorch:\(filename):\(line) \(message)"

    switch level {
    case .debug:
      os_log(.debug, "%{public}@", logMessage)
    case .info:
      os_log(.info, "%{public}@", logMessage)
    case .error:
      os_log(.error, "%{public}@", logMessage)
    case .fatal:
      os_log(.fault, "%{public}@", logMessage)
    default:
      os_log("%{public}@", logMessage)
    }
  }
}
#endif
