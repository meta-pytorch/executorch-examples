/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

#if os(iOS)
import UIKit
typealias PlatformImage = UIImage
#elseif os(macOS)
import AppKit
typealias PlatformImage = NSImage
#endif

enum MessageType {
  case prompted
  case llamagenerated
  case qwengenerated
  case phi4generated
  case gemma3generated
  case llavagenerated
  case smollm3generated
  case voxtralgenerated
  case info
}

struct Message: Identifiable, Equatable {
  let id = UUID()
  let dateCreated = Date()
  var dateUpdated = Date()
  var type: MessageType = .prompted
  var text = ""
  var tokenCount = 0
  var image: PlatformImage?
  var tokensPerSecond: Double = 0
  var lastSpeedMeasurementAt: Date?
}
