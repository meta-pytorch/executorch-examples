/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

struct ReplacementEntry: Identifiable, Codable, Sendable, Hashable {
    let id: UUID
    var trigger: String
    var replacement: String
    var isEnabled: Bool
    var isCaseSensitive: Bool
    var requiresWordBoundary: Bool
    var notes: String

    init(
        id: UUID = UUID(),
        trigger: String = "",
        replacement: String = "",
        isEnabled: Bool = true,
        isCaseSensitive: Bool = false,
        requiresWordBoundary: Bool = true,
        notes: String = ""
    ) {
        self.id = id
        self.trigger = trigger
        self.replacement = replacement
        self.isEnabled = isEnabled
        self.isCaseSensitive = isCaseSensitive
        self.requiresWordBoundary = requiresWordBoundary
        self.notes = notes
    }
}
