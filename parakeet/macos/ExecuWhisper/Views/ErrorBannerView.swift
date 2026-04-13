/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

struct ErrorBannerView: View {
    @Environment(TranscriptStore.self) private var store

    var body: some View {
        if let error = store.currentError {
            HStack(spacing: 12) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(.white)
                Text(error.localizedDescription)
                    .font(.callout)
                    .foregroundStyle(.white)
                    .lineLimit(3)
                Spacer()
                Button {
                    store.clearError()
                } label: {
                    Image(systemName: "xmark")
                        .foregroundStyle(.white.opacity(0.8))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(.red.gradient, in: RoundedRectangle(cornerRadius: 8))
            .padding(.horizontal, 16)
            .padding(.top, 8)
        }
    }
}
