/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

struct Snippet: Identifiable, Codable, Sendable, Hashable {
    let id: UUID
    var name: String
    var trigger: String
    var content: String
    var isEnabled: Bool
    var notes: String
    var lastUsedAt: Date?

    init(
        id: UUID = UUID(),
        name: String = "",
        trigger: String = "",
        content: String = "",
        isEnabled: Bool = true,
        notes: String = "",
        lastUsedAt: Date? = nil
    ) {
        self.id = id
        self.name = name
        self.trigger = trigger
        self.content = content
        self.isEnabled = isEnabled
        self.notes = notes
        self.lastUsedAt = lastUsedAt
    }
}
