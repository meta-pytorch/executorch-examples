/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

enum PersistencePaths {
    static var appSupportDirectory: URL {
        let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let directory = appSupport.appendingPathComponent("VoxtralRealtime", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }

    static var sessionsURL: URL {
        appSupportDirectory.appendingPathComponent("sessions.json")
    }

    static var replacementsURL: URL {
        appSupportDirectory.appendingPathComponent("replacements.json")
    }

    static var snippetsURL: URL {
        appSupportDirectory.appendingPathComponent("snippets.json")
    }
}
