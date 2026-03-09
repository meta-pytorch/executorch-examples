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

    var body: some View {
        @Bindable var store = store

        NavigationSplitView(columnVisibility: $columnVisibility) {
            SidebarView()
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
        .task {
            await store.runHealthCheck()
        }
    }

    @ViewBuilder
    private var detailContent: some View {
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
        } else if let id = store.selectedSessionID,
                  let session = store.sessions.first(where: { $0.id == id }) {
            TranscriptView(text: session.transcript, isLive: false)
                .navigationTitle(session.displayTitle)
        } else {
            WelcomeView()
        }
    }
}
