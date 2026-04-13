/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

struct ContentView: View {
    @Environment(TranscriptStore.self) private var store
    @State private var columnVisibility: NavigationSplitViewVisibility = .doubleColumn
    @State private var activePage: SidebarPage = .home
    @State private var selectedSidebarPages: Set<SidebarPage> = [.home]
    @State private var isAudioDropTargeted = false

    var body: some View {
        NavigationSplitView(columnVisibility: $columnVisibility) {
            SidebarView(selectedPages: $selectedSidebarPages)
                .navigationSplitViewColumnWidth(min: 180, ideal: 220, max: 320)
        } detail: {
            detailContent
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .navigationSplitViewStyle(.balanced)
        .toolbar { RecordingControls() }
        .overlay(alignment: .top) {
            if store.currentError != nil {
                ErrorBannerView()
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.25), value: store.currentError != nil)
        .onChange(of: selectedSidebarPages) { _, newSelection in
            let selectedSessionIDs = Set(newSelection.compactMap { page -> UUID? in
                if case .session(let id) = page {
                    return id
                }
                return nil
            })
            store.selectedHistorySessionIDs = selectedSessionIDs

            guard newSelection.count == 1, let selectedPage = newSelection.first else {
                if case .session(let id) = activePage, selectedSessionIDs.contains(id) {
                    store.selectedSessionID = id
                } else {
                    store.selectedSessionID = nil
                    if newSelection.isEmpty {
                        activePage = .home
                    }
                }
                return
            }

            activePage = selectedPage
            if case .session(let id) = selectedPage {
                store.selectedSessionID = id
            } else {
                store.selectedSessionID = nil
            }
        }
        .onChange(of: store.selectedSessionID) { _, newID in
            if let newID {
                let page = SidebarPage.session(newID)
                activePage = page
                selectedSidebarPages = [page]
            }
        }
        .onChange(of: store.sessions) { _, newSessions in
            let existingIDs = Set(newSessions.map(\.id))
            let filteredSelection = Set(selectedSidebarPages.compactMap { page -> SidebarPage? in
                if case .session(let id) = page, !existingIDs.contains(id) {
                    return nil
                }
                return page
            })
            let resolvedSelection = filteredSelection.isEmpty ? Set([SidebarPage.home]) : filteredSelection
            if resolvedSelection != selectedSidebarPages {
                selectedSidebarPages = resolvedSelection
            }
        }
        .task {
            await store.initialize()
            if let selectedSessionID = store.selectedSessionID {
                let page = SidebarPage.session(selectedSessionID)
                activePage = page
                selectedSidebarPages = [page]
            } else {
                activePage = .home
                selectedSidebarPages = [.home]
            }
        }
    }

    @ViewBuilder
    private var detailContent: some View {
        if store.hasActiveSession {
            TranscriptView(
                text: store.liveTranscript,
                isLive: true,
                isRecording: store.isRecording,
                isTranscribing: store.isTranscribing,
                audioLevel: store.audioLevel,
                statusMessage: store.statusMessage
            )
        } else {
            switch activePage {
            case .replacements:
                ReplacementManagementView()
                    .padding()
                    .navigationTitle("Replacements")
            case .snippets:
                SnippetManagementView()
                    .padding()
                    .navigationTitle("Snippets")
            case .settings:
                SettingsView()
                    .padding()
                    .navigationTitle("Settings")
            case .session(let id):
                if let session = store.sessions.first(where: { $0.id == id }) {
                    TranscriptView(
                        text: session.transcript,
                        isLive: false,
                        onExport: { format in
                            store.exportSession(session, format: format)
                        }
                    )
                        .navigationTitle(session.displayTitle)
                } else {
                    homeContent
                }
            case .home:
                homeContent
            }
        }
    }

    @ViewBuilder
    private var homeContent: some View {
        Group {
            if !store.resourcesReady {
                SetupGuideView()
            } else {
                WelcomeView(isDropTargeted: acceptsDroppedAudioFiles && isAudioDropTargeted)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
        .dropDestination(
            for: URL.self,
            action: handleDroppedAudioFiles(_:_:),
            isTargeted: { isTargeted in
                isAudioDropTargeted = isTargeted
            }
        )
    }

    private var acceptsDroppedAudioFiles: Bool {
        activePage == .home && !store.hasActiveSession
    }

    private func handleDroppedAudioFiles(_ urls: [URL], _: CGPoint) -> Bool {
        guard acceptsDroppedAudioFiles else { return false }

        guard let url = ImportedAudioDecoder.importableAudioFile(from: urls) else {
            store.currentError = .transcriptionFailed(description: "Drop exactly one .wav or .mp3 file to transcribe.")
            return false
        }

        Task { @MainActor in
            await store.importAudioFile(url)
        }
        return true
    }
}
