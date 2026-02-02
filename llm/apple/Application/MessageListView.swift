/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

struct MessageListView: View {
  @Binding var messages: [Message]
  @State private var showScrollToBottomButton = false
  @State private var userHasScrolled = false
  #if os(iOS)
  @State private var keyboardHeight: CGFloat = 0
  #endif

  var body: some View {
    ScrollViewReader { value in
      ScrollView {
        VStack {
          ForEach(messages) { message in
            MessageView(message: message)
              .padding([.leading, .trailing], 20)
          }
          GeometryReader { geometry -> Color in
            DispatchQueue.main.async {
              let maxY = geometry.frame(in: .global).maxY
              #if os(iOS)
              let screenHeight = UIScreen.main.bounds.height - keyboardHeight
              #elseif os(macOS)
              let screenHeight = NSScreen.main?.frame.height ?? 800
              #endif
              let isBeyondBounds = maxY > screenHeight - 50
              if showScrollToBottomButton != isBeyondBounds {
                showScrollToBottomButton = isBeyondBounds
                userHasScrolled = isBeyondBounds
              }
            }
            return Color.clear
          }
          .frame(height: 0)
        }
      }
      .onChange(of: messages) {
        if !userHasScrolled, let lastMessageId = messages.last?.id {
          withAnimation {
            value.scrollTo(lastMessageId, anchor: .bottom)
          }
        }
      }
      .overlay(
        Group {
          if showScrollToBottomButton {
            Button(action: {
              withAnimation {
                if let lastMessageId = messages.last?.id {
                  value.scrollTo(lastMessageId, anchor: .bottom)
                }
                userHasScrolled = false
              }
            }) {
              ZStack {
                Circle()
                  #if os(iOS)
                  .fill(Color(UIColor.secondarySystemBackground).opacity(0.9))
                  #elseif os(macOS)
                  .fill(Color(NSColor.controlBackgroundColor).opacity(0.9))
                  #endif
                  .frame(height: 28)
                Image(systemName: "arrow.down.circle")
                  .resizable()
                  .aspectRatio(contentMode: .fit)
                  .frame(height: 28)
              }
            }
            .buttonStyle(.plain)
            .transition(AnyTransition.opacity.animation(.easeInOut(duration: 0.2)))
          }
        },
        alignment: .bottom
      )
    }
    #if os(iOS)
    .onAppear {
      NotificationCenter.default.addObserver(forName: UIResponder.keyboardWillShowNotification, object: nil, queue: .main) { notification in
        let keyboardFrame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect ?? .zero
        keyboardHeight = keyboardFrame.height - 40
      }
      NotificationCenter.default.addObserver(forName: UIResponder.keyboardWillHideNotification, object: nil, queue: .main) { _ in
        keyboardHeight = 0
      }
    }
    .onDisappear {
      NotificationCenter.default.removeObserver(self, name: UIResponder.keyboardWillShowNotification, object: nil)
      NotificationCenter.default.removeObserver(self, name: UIResponder.keyboardWillHideNotification, object: nil)
    }
    #endif
  }
}
