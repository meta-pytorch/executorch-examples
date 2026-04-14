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
        let directory = appSupport.appendingPathComponent("ExecuWhisper", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }

    static var sessionsURL: URL {
        appSupportDirectory.appendingPathComponent("sessions.json")
    }

    static var modelsDirectoryURL: URL {
        appSupportDirectory.appendingPathComponent("models", isDirectory: true)
    }

    static var replacementsURL: URL {
        appSupportDirectory.appendingPathComponent("replacements.json")
    }

    static var logsDirectoryURL: URL {
        let directory = appSupportDirectory.appendingPathComponent("logs", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }
}
