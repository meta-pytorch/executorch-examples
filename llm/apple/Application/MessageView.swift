/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import SwiftUI

struct MessageView: View {
  let message: Message

  var body: some View {
    let isAssistant = {
      switch message.type {
      case .llamagenerated, .llavagenerated, .qwengenerated, .phi4generated, .gemma3generated, .smollm3generated, .voxtralgenerated:
        return true
      default:
        return false
      }
    }()

    VStack(alignment: .center) {
      if message.type == .info {
        Text(message.text)
          .font(.caption)
          .foregroundColor(.secondary)
          .padding([.leading, .trailing], 10)
      } else {
        VStack(alignment: isAssistant ? .leading : .trailing) {
          if isAssistant || message.type == .prompted {
            Text({
              switch message.type {
              case .gemma3generated: return "Gemma 3"
              case .llamagenerated: return "LLaMA"
              case .llavagenerated: return "LLaVA"
              case .phi4generated: return "Phi-4"
              case .qwengenerated: return "Qwen 3"
              case .smollm3generated: return "SmolLM3"
              case .voxtralgenerated: return "Voxtral"
              default: return "Prompt"
              }
            }())
              .font(.caption)
              .foregroundColor(.secondary)
              .padding(isAssistant ? .trailing : .leading, 20)
          }
          HStack {
            if !isAssistant { Spacer() }
            if message.text.isEmpty {
              if let img = message.image {
                Image(uiImage: img)
                  .resizable()
                  .scaledToFit()
                  .frame(maxWidth: 200, maxHeight: 200)
                  .padding()
                  .background(Color.gray.opacity(0.2))
                  .cornerRadius(8)
                  .padding(.vertical, 2)
              } else {
                ProgressView()
                  .progressViewStyle(CircularProgressViewStyle())
              }
            } else {
              Text(message.text)
                .padding(10)
                .foregroundColor(isAssistant ? .primary : .white)
                .background(isAssistant ? Color(UIColor.secondarySystemBackground) : Color.blue)
                .cornerRadius(20)
                .contextMenu {
                  Button(action: {
                    UIPasteboard.general.string = message.text
                  }) {
                    Text("Copy")
                    Image(systemName: "doc.on.doc")
                  }
                }
            }
            if isAssistant { Spacer() }
          }
          .frame(maxWidth: .infinity)
          let elapsedTime = message.dateUpdated.timeIntervalSince(message.dateCreated)
          if elapsedTime > 0 && message.type != .info {
            Text(String(format: "%.1f t/s", Double(message.tokenCount) / elapsedTime))
              .font(.caption)
              .foregroundColor(.secondary)
              .padding(isAssistant ? .trailing : .leading, 20)
          }
        }
        .padding([.leading, .trailing], message.type == .info ? 0 : 10)
      }
    }
  }
}
