/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

struct Session: Identifiable, Codable, Sendable, Hashable {
    let id: UUID
    let date: Date
    var title: String
    var transcript: String
    var duration: TimeInterval

    init(
        id: UUID = UUID(),
        date: Date = .now,
        title: String = "",
        transcript: String = "",
        duration: TimeInterval = 0
    ) {
        self.id = id
        self.date = date
        self.title = title
        self.transcript = transcript
        self.duration = duration
    }

    var displayTitle: String {
        if !title.isEmpty { return title }
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}
