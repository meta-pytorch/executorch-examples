/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

struct SessionHistorySection: Identifiable, Equatable {
    let title: String
    let sessions: [Session]

    var id: String { title }
}

enum SessionHistory {
    static func visibleSessions(in sessions: [Session], matching searchText: String) -> [Session] {
        let sortedSessions = sessions.sorted { $0.date > $1.date }
        guard !searchText.isEmpty else { return sortedSessions }

        return sortedSessions.filter { session in
            session.transcript.localizedCaseInsensitiveContains(searchText) ||
            session.title.localizedCaseInsensitiveContains(searchText) ||
            (session.rawTranscript?.localizedCaseInsensitiveContains(searchText) ?? false) ||
            session.tags.joined(separator: " ").localizedCaseInsensitiveContains(searchText)
        }
    }

    static func pinnedSessions(
        in sessions: [Session],
        matching searchText: String,
        referenceDate: Date = .now,
        calendar: Calendar = .current
    ) -> [Session] {
        visibleSessions(in: sessions, matching: searchText)
            .filter(\.pinned)
    }

    static func sections(
        in sessions: [Session],
        matching searchText: String,
        referenceDate: Date = .now,
        calendar: Calendar = .current
    ) -> [SessionHistorySection] {
        let pinnedIDs = Set(
            pinnedSessions(
                in: sessions,
                matching: searchText,
                referenceDate: referenceDate,
                calendar: calendar
            ).map(\.id)
        )
        let visible = visibleSessions(in: sessions, matching: searchText)
            .filter { !pinnedIDs.contains($0.id) }

        let grouped = Dictionary(grouping: visible) { session in
            bucketTitle(for: session.date, referenceDate: referenceDate, calendar: calendar)
        }

        return ["Today", "Yesterday", "Earlier"].compactMap { title in
            guard let sectionSessions = grouped[title], !sectionSessions.isEmpty else { return nil }
            return SessionHistorySection(title: title, sessions: sectionSessions)
        }
    }

    private static func bucketTitle(for date: Date, referenceDate: Date, calendar: Calendar) -> String {
        let startOfReference = calendar.startOfDay(for: referenceDate)
        let startOfDate = calendar.startOfDay(for: date)
        let dayDifference = calendar.dateComponents([.day], from: startOfDate, to: startOfReference).day ?? 0

        switch dayDifference {
        case 0:
            return "Today"
        case 1:
            return "Yesterday"
        default:
            return "Earlier"
        }
    }
}
