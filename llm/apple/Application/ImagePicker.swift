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

struct ImagePicker: UIViewControllerRepresentable {
  class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
    let parent: ImagePicker

    init(parent: ImagePicker) {
      self.parent = parent
    }

    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
      if let image = info[.originalImage] as? UIImage {
        parent.selectedImage = image
      }

      parent.presentationMode.wrappedValue.dismiss()
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
      parent.selectedImage = nil
      parent.presentationMode.wrappedValue.dismiss()
    }
  }

  @Environment(\.presentationMode) var presentationMode
  @Binding var selectedImage: UIImage?
  var sourceType: UIImagePickerController.SourceType = .photoLibrary

  func makeCoordinator() -> Coordinator {
    Coordinator(parent: self)
  }

  func makeUIViewController(context: Context) -> UIImagePickerController {
    let picker = UIImagePickerController()
    picker.delegate = context.coordinator
    picker.sourceType = sourceType
    return picker
  }

  func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
}

#elseif os(macOS)
import AppKit

struct ImagePicker: View {
  @Environment(\.dismiss) var dismiss
  @Binding var selectedImage: NSImage?

  var body: some View {
    Color.clear
      .onAppear {
        selectImage()
      }
  }

  private func selectImage() {
    let panel = NSOpenPanel()
    panel.allowsMultipleSelection = false
    panel.canChooseDirectories = false
    panel.canChooseFiles = true
    panel.allowedContentTypes = [.image, .png, .jpeg, .gif, .heic]
    panel.message = "Select an image"
    panel.prompt = "Select"

    if panel.runModal() == .OK {
      if let url = panel.url, let image = NSImage(contentsOf: url) {
        selectedImage = image
      }
    }
    dismiss()
  }
}
#endif
