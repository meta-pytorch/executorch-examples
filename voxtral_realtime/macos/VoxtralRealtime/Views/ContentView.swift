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

    var body: some View {
        NavigationSplitView(columnVisibility: $columnVisibility) {
            SidebarView(activePage: $activePage)
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
        .onChange(of: activePage) { _, newPage in
            if case .session(let id) = newPage {
                store.selectedSessionID = id
            }
        }
        .onChange(of: store.selectedSessionID) { _, newID in
            if let newID {
                activePage = .session(newID)
            }
        }
        .task {
            await store.runHealthCheck()
        }
    }

    @ViewBuilder
    private var detailContent: some View {
        switch activePage {
        case .replacements:
            ReplacementManagementView()
                .padding()
                .navigationTitle("Replacements")
        case .snippets:
            SnippetManagementView()
                .padding()
                .navigationTitle("Snippets")
        case .wake:
            WakeSettingsView()
                .padding()
                .navigationTitle("Wake")
        case .settings:
            SettingsView()
                .padding()
                .navigationTitle("Settings")
        case .home:
            homeContent
        case .session(let id):
            if let session = store.sessions.first(where: { $0.id == id }) {
                TranscriptView(text: session.transcript, isLive: false)
                    .navigationTitle(session.displayTitle)
            } else {
                homeContent
            }
        }
    }

    @ViewBuilder
    private var homeContent: some View {
        if store.healthResult?.allGood == false && !store.hasActiveSession && store.modelState == .unloaded {
            SetupGuideView()
        } else if store.hasActiveSession {
            TranscriptView(
                text: store.liveTranscript,
                isLive: store.isTranscribing,
                isPaused: store.isPaused,
                audioLevel: store.audioLevel,
                statusMessage: store.statusMessage
            )
        } else {
            WelcomeView()
        }
    }
}
