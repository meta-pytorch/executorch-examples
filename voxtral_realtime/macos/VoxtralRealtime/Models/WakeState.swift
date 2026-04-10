/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

enum WakeState: String, Sendable, Equatable {
    case disabled
    case listening
    case speechDetected
    case checkingPhrase
    case active
}
