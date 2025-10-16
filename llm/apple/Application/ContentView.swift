import ExecuTorchLLM
import SwiftUI
import UniformTypeIdentifiers

class RunnerHolder: ObservableObject {
  var textRunner: TextRunner?
  var multimodalRunner: MultimodalRunner?
}

extension UIImage {
  func centerCropped(to sideSize: CGFloat) -> UIImage {
    precondition(sideSize > 0)
    let format = UIGraphicsImageRendererFormat.default()
    format.scale = 1
    format.opaque = false
    return UIGraphicsImageRenderer(size: CGSize(width: sideSize, height: sideSize), format: format).image { _ in
      let scaleFactor = max(sideSize / size.width, sideSize / size.height)
      let scaledWidth = size.width * scaleFactor
      let scaledHeight = size.height * scaleFactor
      let originX = (sideSize - scaledWidth) / 2
      let originY = (sideSize - scaledHeight) / 2
      draw(in: CGRect(x: originX, y: originY, width: scaledWidth, height: scaledHeight))
    }
  }

  func rgbBytes() -> [UInt8]? {
    guard let cgImage = cgImage else { return nil }
    let pixelWidth = Int(cgImage.width)
    let pixelHeight = Int(cgImage.height)
    let pixelCount = pixelWidth * pixelHeight
    let bytesPerPixel = 4
    let bytesPerRow = pixelWidth * bytesPerPixel
    var rgbaBuffer = [UInt8](repeating: 0, count: pixelCount * bytesPerPixel)
    guard let context = CGContext(
      data: &rgbaBuffer,
      width: pixelWidth,
      height: pixelHeight,
      bitsPerComponent: 8,
      bytesPerRow: bytesPerRow,
      space: CGColorSpaceCreateDeviceRGB(),
      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
    ) else { return nil }
    context.draw(cgImage, in: CGRect(x: 0, y: 0, width: pixelWidth, height: pixelHeight))
    var rgbBytes = [UInt8](repeating: 0, count: pixelCount * 3)
    for pixelIndex in 0..<pixelCount {
      let sourceIndex = pixelIndex * bytesPerPixel
      rgbBytes[pixelIndex] = rgbaBuffer[sourceIndex + 0]
      rgbBytes[pixelIndex + pixelCount] = rgbaBuffer[sourceIndex + 1]
      rgbBytes[pixelIndex + 2 * pixelCount] = rgbaBuffer[sourceIndex + 2]
    }
    return rgbBytes
  }

  func rgbBytesNormalized(mean: [Float] = [0, 0, 0], std: [Float] = [1, 1, 1]) -> [Float]? {
    precondition(mean.count == 3 && std.count == 3)
    precondition(std[0] != 0 && std[1] != 0 && std[2] != 0)
    guard let rgbBytes = rgbBytes() else { return nil }
    let pixelCount = rgbBytes.count / 3
    var rgbBytesNormalized = [Float](repeating: 0, count: pixelCount * 3)
    for pixelIndex in 0..<pixelCount {
      rgbBytesNormalized[pixelIndex] = (Float(rgbBytes[pixelIndex]) / 255.0 - mean[0]) / std[0]
      rgbBytesNormalized[pixelIndex + pixelCount] = (Float(rgbBytes[pixelIndex + pixelCount]) / 255.0 - mean[1]) / std[1]
      rgbBytesNormalized[pixelIndex + 2 * pixelCount] = (Float(rgbBytes[pixelIndex + 2 * pixelCount]) / 255.0 - mean[2]) / std[2]
    }
    return rgbBytesNormalized
  }

  func asImage(_ sideSize: CGFloat) -> ExecuTorchLLM.Image {
    return Image(
      data: Data(centerCropped(to: sideSize).rgbBytes() ?? []),
      width: Int(sideSize),
      height: Int(sideSize),
      channels: 3
    )
  }

  func asNormalizedImage(_ sideSize: CGFloat, mean: [Float] = [0.485, 0.456, 0.406], std: [Float] = [0.229, 0.224, 0.225]) -> ExecuTorchLLM.Image {
    return Image(
      float: (centerCropped(to: sideSize).rgbBytesNormalized(mean: mean, std: std) ?? []).withUnsafeBufferPointer { Data(buffer: $0) },
      width: Int(sideSize),
      height: Int(sideSize),
      channels: 3
    )
  }
}

struct ContentView: View {
  @State private var prompt = ""
  @State private var messages: [Message] = []
  @State private var showingLogs = false
  @State private var pickerType: PickerType?
  @State private var isGenerating = false
  @State private var shouldStopGenerating = false
  @State private var shouldStopShowingToken = false
  @State private var thinkingMode = false
  @State private var showThinkingModeNotification = false
  private let runnerQueue = DispatchQueue(label: "org.pytorch.executorch.etllm")
  @StateObject private var runnerHolder = RunnerHolder()
  @StateObject private var resourceManager = ResourceManager()
  @StateObject private var resourceMonitor = ResourceMonitor()
  @StateObject private var logManager = LogManager()
  @State private var isImagePickerPresented = false
  @State private var selectedImage: UIImage?
  @State private var imagePickerSourceType: UIImagePickerController.SourceType = .photoLibrary
  @State private var showingSettings = false
  @FocusState private var textFieldFocused: Bool
  @State private var lastPreloadedKey: String?

  enum PickerType {
    case model
    case tokenizer
  }

  enum ModelType {
    case gemma3
    case llama
    case llava
    case qwen3
    case phi4
    case smollm3
    case voxtral

    static func fromPath(_ path: String) -> ModelType {
      let filename = (path as NSString).lastPathComponent.lowercased()
      if filename.hasPrefix("gemma3") {
        return .gemma3
      } else if filename.hasPrefix("llama") {
        return .llama
      } else if filename.hasPrefix("llava") {
        return .llava
      } else if filename.hasPrefix("qwen3") {
        return .qwen3
      } else if filename.hasPrefix("phi4") {
        return .phi4
      } else if filename.contains("smollm3") {
        return .smollm3
      } else if filename.hasPrefix("voxtral") {
        return .voxtral
      }
      print("Unknown model type in path: \(path).")
      exit(1)
    }
  }

  private var placeholder: String {
    resourceManager.isModelValid ? resourceManager.isTokenizerValid ? "Prompt..." : "Select Tokenizer..." : "Select Model..."
  }

  private var title: String {
    resourceManager.isModelValid ? resourceManager.isTokenizerValid ? resourceManager.modelName : "Select Tokenizer..." : "Select Model..."
  }

  private var modelTitle: String {
    resourceManager.isModelValid ? resourceManager.modelName : "Select Model..."
  }

  private var tokenizerTitle: String {
    resourceManager.isTokenizerValid ? resourceManager.tokenizerName : "Select Tokenizer..."
  }

  private var isInputEnabled: Bool { resourceManager.isModelValid && resourceManager.isTokenizerValid }

  var body: some View {
    NavigationView {
      ZStack {
        VStack {
          if showingSettings {
            VStack(spacing: 20) {
              HStack {
                VStack(spacing: 10) {
                  Button(action: { pickerType = .model }) {
                    Label(modelTitle, systemImage: "doc")
                      .lineLimit(1)
                      .truncationMode(.middle)
                      .frame(maxWidth: 300, alignment: .leading)
                  }
                  Button(action: { pickerType = .tokenizer }) {
                    Label(tokenizerTitle, systemImage: "doc")
                      .lineLimit(1)
                      .truncationMode(.middle)
                      .frame(maxWidth: 300, alignment: .leading)
                  }
                }
                .padding()
                .background(Color.gray.opacity(0.1))
                .cornerRadius(10)
                .fixedSize(horizontal: true, vertical: false)
                Spacer()
              }
              .padding()
            }
          }

          MessageListView(messages: $messages)
            .simultaneousGesture(
              DragGesture().onChanged { value in
                if value.translation.height > 10 {
                  hideKeyboard()
                }
                showingSettings = false
                textFieldFocused = false
              }
            )
            .onTapGesture {
              showingSettings = false
              textFieldFocused = false
            }

          HStack {
            Button(action: {
              imagePickerSourceType = .photoLibrary
              isImagePickerPresented = true
            }) {
              Image(systemName: "photo.on.rectangle")
                .resizable()
                .scaledToFit()
                .frame(width: 24, height: 24)
            }
            .background(Color.clear)
            .cornerRadius(8)

            Button(action: {
              if UIImagePickerController.isSourceTypeAvailable(.camera) {
                imagePickerSourceType = .camera
                isImagePickerPresented = true
              } else {
                print("Camera not available")
              }
            }) {
              Image(systemName: "camera")
                .resizable()
                .scaledToFit()
                .frame(width: 24, height: 24)
            }
            .background(Color.clear)
            .cornerRadius(8)

            if resourceManager.isModelValid && ModelType.fromPath(resourceManager.modelPath) == .qwen3 {
              Button(action: {
                thinkingMode.toggle()
                showThinkingModeNotification = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                  showThinkingModeNotification = false
                }
              }) {
                Image(systemName: "brain")
                  .resizable()
                  .scaledToFit()
                  .frame(width: 24, height: 24)
                  .foregroundColor(thinkingMode ? .blue : .gray)
              }
              .background(Color.clear)
              .cornerRadius(8)
            }

            TextField(placeholder, text: $prompt, axis: .vertical)
              .padding(8)
              .background(Color.gray.opacity(0.1))
              .cornerRadius(20)
              .lineLimit(1...10)
              .overlay(
                RoundedRectangle(cornerRadius: 20)
                  .stroke(isInputEnabled ? Color.blue : Color.gray, lineWidth: 1)
              )
              .disabled(!isInputEnabled)
              .focused($textFieldFocused)
              .onAppear { textFieldFocused = false }
              .onTapGesture {
                showingSettings = false
              }
              .onChange(of: prompt) { newValue in
                guard !newValue.isEmpty else { return }
                if let key = currentPreloadKey(), key != lastPreloadedKey {
                  lastPreloadedKey = key
                  loadModelIfNeededAsync(reportToUI: false)
                }
              }

            Button(action: isGenerating ? stop : generate) {
              Image(systemName: isGenerating ? "stop.circle" : "arrowshape.up.circle.fill")
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(height: 28)
            }
            .disabled(isGenerating ? shouldStopGenerating : (!isInputEnabled || prompt.isEmpty))
          }
          .padding([.leading, .trailing, .bottom], 10)
        }
        .sheet(isPresented: $isImagePickerPresented, onDismiss: addSelectedImageMessage) {
          ImagePicker(selectedImage: $selectedImage, sourceType: imagePickerSourceType)
            .id(imagePickerSourceType.rawValue)
        }

        if showThinkingModeNotification {
          Text(thinkingMode ? "Thinking mode enabled" : "Thinking mode disabled")
            .padding(8)
            .background(Color(UIColor.secondarySystemBackground))
            .cornerRadius(8)
            .transition(.opacity)
            .animation(.easeInOut(duration: 0.2), value: showThinkingModeNotification)
        }
      }
      .navigationBarTitle(title, displayMode: .inline)
      .navigationBarItems(
        leading:
          Button(action: {
            showingSettings.toggle()
          }) {
            Image(systemName: "folder")
              .imageScale(.large)
          },
        trailing:
          HStack {
            Menu {
              Section(header: Text("Memory")) {
                Text("Used: \(resourceMonitor.usedMemory) Mb")
                Text("Available: \(resourceMonitor.usedMemory) Mb")
              }
            } label: {
              Text("\(resourceMonitor.usedMemory) Mb")
            }
            .onAppear {
              resourceMonitor.start()
            }
            .onDisappear {
              resourceMonitor.stop()
            }
            Button(action: { showingLogs = true }) {
              Image(systemName: "list.bullet.rectangle")
            }
          }
      )
      .sheet(isPresented: $showingLogs) {
        NavigationView {
          LogView(logManager: logManager)
        }
      }
      .fileImporter(
        isPresented: Binding<Bool>(
          get: { pickerType != nil },
          set: { if !$0 { pickerType = nil } }
        ),
        allowedContentTypes: allowedContentTypes(),
        allowsMultipleSelection: false
      ) { [pickerType] result in
        handleFileImportResult(pickerType, result)
      }
      .onAppear {
        do {
          try resourceManager.createDirectoriesIfNeeded()
        } catch {
          withAnimation {
            messages.append(Message(type: .info, text: "Error creating content directories: \(error.localizedDescription)"))
          }
        }
      }
    }
    .navigationViewStyle(StackNavigationViewStyle())
  }

  private func addSelectedImageMessage() {
    if let selectedImage {
      messages.append(Message(image: selectedImage))
    }
  }

  private func generate() {
    guard !prompt.isEmpty else { return }
    isGenerating = true
    shouldStopGenerating = false
    shouldStopShowingToken = false
    let text = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
    let modelPath = resourceManager.modelPath
    let tokenizerPath = resourceManager.tokenizerPath
    let modelType = ModelType.fromPath(modelPath)
    let sequenceLength = (modelType == .llava || modelType == .gemma3 || modelType == .voxtral) ? 768 : ((modelType == .llama || modelType == .phi4) ? 128 : 768)
    prompt = ""
    hideKeyboard()
    showingSettings = false
    messages.append(Message(text: text))
    let outputType: MessageType = {
      switch modelType {
      case .gemma3: return .gemma3generated
      case .llama: return .llamagenerated
      case .llava: return .llavagenerated
      case .phi4: return .phi4generated
      case .qwen3: return .qwengenerated
      case .smollm3: return .smollm3generated
      case .voxtral: return .voxtralgenerated
      }
    }()
    messages.append(Message(type: outputType))

    runnerQueue.async {
      defer {
        DispatchQueue.main.async {
          isGenerating = false
          selectedImage = nil
        }
      }

      loadModelIfNeededSync(reportToUI: true)

      guard !shouldStopGenerating else {
        DispatchQueue.main.async {
          withAnimation {
            _ = messages.removeLast()
          }
        }
        return
      }
      defer {
        runnerHolder.textRunner?.reset()
        runnerHolder.multimodalRunner?.reset()
      }

      do {
        var tokens: [String] = []

        if (modelType == .llava || modelType == .gemma3), let image = selectedImage {
          let systemPrompt = "A chat between a curious human and an artificial intelligence assistant. The assistant gives helpful, detailed, and polite answers to the human's questions."
          var inputs: [MultimodalInput] = []
          var config = Config()
          var promptToIgnore = ""
          if modelType == .llava {
            let sideSize: CGFloat = 336
            inputs = [
              MultimodalInput(systemPrompt),
              MultimodalInput(image.asImage(sideSize)),
              MultimodalInput(String(format: Constants.llavaPromptTemplate, text))
            ]
            config.sequenceLength = 768
            promptToIgnore = String(format: Constants.llavaPromptTemplate, text)
          }
          if modelType == .gemma3 {
            let sideSize: CGFloat = 896
            inputs = [
              MultimodalInput(systemPrompt + "<start_of_image>"),
              MultimodalInput(image.asNormalizedImage(sideSize)),
              MultimodalInput("<end_of_image>" + String(format: Constants.gemma3PromptTemplate, text))
            ]
            config.sequenceLength = 768
            promptToIgnore = String(format: Constants.gemma3PromptTemplate, text)
          }

          try runnerHolder.multimodalRunner?.generate(inputs, config) { token in
            if token == promptToIgnore { return }
            if modelType == .gemma3 && token == "<end_of_turn>" {
              shouldStopGenerating = true
              runnerHolder.multimodalRunner?.stop()
              return
            }
            if modelType == .llava && token == "</s>" {
              shouldStopGenerating = true
              runnerHolder.multimodalRunner?.stop()
              return
            }
            tokens.append(token)
            if tokens.count > 2 {
              let streamedText = tokens.joined()
              let streamedCount = tokens.count
              tokens = []
              DispatchQueue.main.async {
                var message = messages.removeLast()
                message.text += streamedText
                message.tokenCount += streamedCount
                message.dateUpdated = Date()
                messages.append(message)
              }
            }
            if shouldStopGenerating {
              runnerHolder.multimodalRunner?.stop()
            }
          }
        } else if modelType == .voxtral {
          guard let audioPath = Bundle.main.path(forResource: "voxtral_input_features", ofType: "bin") else {
            DispatchQueue.main.async {
              withAnimation {
                var message = messages.removeLast()
                message.type = .info
                message.text = "Audio not found"
                messages.append(message)
              }
            }
            return
          }
          var audioData = try Data(contentsOf: URL(fileURLWithPath: audioPath), options: .mappedIfSafe)
          let floatByteCount = MemoryLayout<Float>.size
          guard audioData.count % floatByteCount == 0 else {
            DispatchQueue.main.async {
              withAnimation {
                var message = messages.removeLast()
                message.type = .info
                message.text = "Invalid audio data"
                messages.append(message)
              }
            }
            return
          }
          let bins = 128
          let frames = 3000
          let batchSize = audioData.count / floatByteCount / (bins * frames)

          try runnerHolder.multimodalRunner?.generate([
            MultimodalInput("<s>[INST][BEGIN_AUDIO]"),
            MultimodalInput(Audio(float: audioData, batchSize: batchSize, bins: bins, frames: frames)),
            MultimodalInput(String(format: Constants.voxtralPromptTemplate, text))
          ], Config {
            $0.maximumNewTokens = 256
          }) { token in
            tokens.append(token)
            if tokens.count > 2 {
              let streamedText = tokens.joined()
              let streamedCount = tokens.count
              tokens = []
              DispatchQueue.main.async {
                var message = messages.removeLast()
                message.text += streamedText
                message.tokenCount += streamedCount
                message.dateUpdated = Date()
                messages.append(message)
              }
            }
            if shouldStopGenerating {
              runnerHolder.multimodalRunner?.stop()
            }
          }
        } else if (modelType == .llava || modelType == .gemma3) {
          let systemPrompt = "A chat between a curious human and an artificial intelligence assistant. The assistant gives helpful, detailed, and polite answers to the human's questions."
          var inputs: [MultimodalInput] = []
          var config = Config()
          var promptToIgnore = ""
          if modelType == .llava {
            inputs = [
              MultimodalInput(systemPrompt),
              MultimodalInput(String(format: Constants.llavaPromptTemplate, text))
            ]
            config.sequenceLength = 768
            promptToIgnore = String(format: Constants.llavaPromptTemplate, text)
          } else {
            inputs = [
              MultimodalInput(systemPrompt),
              MultimodalInput(String(format: Constants.gemma3PromptTemplate, text))
            ]
            config.sequenceLength = 768
            promptToIgnore = String(format: Constants.gemma3PromptTemplate, text)
          }
          try runnerHolder.multimodalRunner?.generate(inputs, config) { token in
            if token == promptToIgnore { return }
            if modelType == .gemma3 && token == "<end_of_turn>" {
              shouldStopGenerating = true
              runnerHolder.multimodalRunner?.stop()
              return
            }
            if modelType == .llava && token == "</s>" {
              shouldStopGenerating = true
              runnerHolder.multimodalRunner?.stop()
              return
            }
            tokens.append(token)
            if tokens.count > 2 {
              let streamedText = tokens.joined()
              let streamedCount = tokens.count
              tokens = []
              DispatchQueue.main.async {
                var message = messages.removeLast()
                message.text += streamedText
                message.tokenCount += streamedCount
                message.dateUpdated = Date()
                messages.append(message)
              }
            }
            if shouldStopGenerating {
              runnerHolder.multimodalRunner?.stop()
            }
          }
        } else {
          let formattedPrompt: String
          switch modelType {
          case .gemma3:
            formattedPrompt = String(format: Constants.gemma3PromptTemplate, text)
          case .llama:
            formattedPrompt = String(format: Constants.llama3PromptTemplate, text)
          case .llava:
            formattedPrompt = String(format: Constants.llavaPromptTemplate, text)
          case .phi4:
            formattedPrompt = String(format: Constants.phi4PromptTemplate, text)
          case .qwen3:
            let basePrompt = String(format: Constants.qwen3PromptTemplate, text)
            formattedPrompt = thinkingMode ? basePrompt.replacingOccurrences(of: "<think>\n\n</think>\n\n\n", with: "") : basePrompt
          case .smollm3:
            formattedPrompt = String(format: Constants.smolLm3PromptTemplate, text)
          case .voxtral:
            formattedPrompt = String(format: Constants.voxtralPromptTemplate, text)
          }
          try runnerHolder.textRunner?.generate(formattedPrompt, Config {
            $0.sequenceLength = sequenceLength
          }) { token in
            if modelType == .gemma3 && token == "<end_of_turn>" {
              shouldStopGenerating = true
              runnerHolder.textRunner?.stop()
            }
            if modelType == .llama && (token == "<|eot_id|>" || token == "<|end_of_text|>") {
              shouldStopGenerating = true
              runnerHolder.textRunner?.stop()
              return
            }
            if modelType == .phi4 && token == "<|end|>" {
              shouldStopGenerating = true
              runnerHolder.textRunner?.stop()
              return
            }
            if (modelType == .qwen3 || modelType == .smollm3) && token == "<|im_end|>" {
              shouldStopGenerating = true
              runnerHolder.textRunner?.stop()
              return
            }
            if token != formattedPrompt {
              if token == "<|eot_id|>" {
                shouldStopShowingToken = true
              } else if token == "<|im_end|>" {
              } else if token == "<think>" {
                let flushedText = tokens.joined()
                let flushedCount = tokens.count
                tokens = []
                DispatchQueue.main.async {
                  var message = messages.removeLast()
                  message.text += flushedText
                  message.text += message.text.isEmpty ? "Thinking...\n\n" : "\n\nThinking...\n\n"
                  message.tokenCount += flushedCount + 1
                  message.dateUpdated = Date()
                  messages.append(message)
                }
              } else if token == "</think>" {
                let flushedText = tokens.joined()
                let flushedCount = tokens.count
                tokens = []
                DispatchQueue.main.async {
                  var message = messages.removeLast()
                  message.text += flushedText
                  message.text += "\n\nFinished thinking.\n\n"
                  message.tokenCount += flushedCount + 1
                  message.dateUpdated = Date()
                  messages.append(message)
                }
              } else {
                tokens.append(token.trimmingCharacters(in: .newlines))
                if tokens.count > 2 {
                  let streamedText = tokens.joined()
                  let streamedCount = tokens.count
                  tokens = []
                  DispatchQueue.main.async {
                    var message = messages.removeLast()
                    message.text += streamedText
                    message.tokenCount += streamedCount
                    message.dateUpdated = Date()
                    messages.append(message)
                  }
                }
                if shouldStopGenerating {
                  runnerHolder.textRunner?.stop()
                }
              }
            }
          }
        }
      } catch {
        DispatchQueue.main.async {
          withAnimation {
            var message = messages.removeLast()
            message.type = .info
            message.text = "Text generation failed: error \((error as NSError).code)"
            messages.append(message)
          }
        }
      }
    }
  }

  private func stop() {
    shouldStopGenerating = true
  }

  private func allowedContentTypes() -> [UTType] {
    guard let pickerType else { return [] }
    switch pickerType {
    case .model:
      return [UTType(filenameExtension: "pte")].compactMap { $0 }
    case .tokenizer:
      return [UTType(filenameExtension: "bin"), UTType(filenameExtension: "model"), UTType(filenameExtension: "json")].compactMap { $0 }
    }
  }

  private func handleFileImportResult(_ pickerType: PickerType?, _ result: Result<[URL], Error>) {
    switch result {
    case .success(let urls):
      guard let url = urls.first, let pickerType else {
        withAnimation {
          messages.append(Message(type: .info, text: "Failed to select a file"))
        }
        return
      }
      runnerQueue.async {
        runnerHolder.textRunner = nil
        runnerHolder.multimodalRunner = nil
      }
      switch pickerType {
      case .model:
        resourceManager.modelPath = url.path
      case .tokenizer:
        resourceManager.tokenizerPath = url.path
      }
      lastPreloadedKey = nil
      if resourceManager.isModelValid && resourceManager.isTokenizerValid {
        showingSettings = false
        textFieldFocused = true
      }
    case .failure(let error):
      withAnimation {
        messages.append(Message(type: .info, text: "Failed to select a file: \(error.localizedDescription)"))
      }
    }
  }
}

extension ContentView {
  private func currentPreloadKey() -> String? {
    guard resourceManager.isModelValid && resourceManager.isTokenizerValid else { return nil }
    return resourceManager.modelPath + "|" + resourceManager.tokenizerPath
  }

  private func loadModelIfNeededAsync(reportToUI: Bool) {
    runnerQueue.async {
      loadModelIfNeededSync(reportToUI: reportToUI)
    }
  }

  private func loadModelIfNeededSync(reportToUI: Bool) {
    guard resourceManager.isModelValid && resourceManager.isTokenizerValid else { return }
    let modelPath = resourceManager.modelPath
    let tokenizerPath = resourceManager.tokenizerPath
    let modelType = ModelType.fromPath(modelPath)

    switch modelType {
    case .llama:
      runnerHolder.textRunner = runnerHolder.textRunner ?? TextRunner(
        modelPath: modelPath,
        tokenizerPath: tokenizerPath,
        specialTokens: [
          "<|begin_of_text|>",
          "<|end_of_text|>",
          "<|reserved_special_token_0|>",
          "<|reserved_special_token_1|>",
          "<|finetune_right_pad_id|>",
          "<|step_id|>",
          "<|start_header_id|>",
          "<|end_header_id|>",
          "<|eom_id|>",
          "<|eot_id|>",
          "<|python_tag|>"
        ] + (2..<256).map { "<|reserved_special_token_\($0)|>" }
      )
    case .qwen3, .phi4, .smollm3:
      runnerHolder.textRunner = runnerHolder.textRunner ?? TextRunner(
        modelPath: modelPath,
        tokenizerPath: tokenizerPath
      )
    case .llava, .gemma3, .voxtral:
      runnerHolder.multimodalRunner = runnerHolder.multimodalRunner ?? MultimodalRunner(
        modelPath: modelPath,
        tokenizerPath: tokenizerPath
      )
    }

    if (modelType == .llama || modelType == .qwen3 || modelType == .phi4 || modelType == .smollm3),
       let runner = runnerHolder.textRunner, !runner.isLoaded() {
      var err: Error?
      let start = Date()
      do { try runner.load() } catch { err = error }
      let dur = Date().timeIntervalSince(start)
      if reportToUI {
        DispatchQueue.main.async {
          withAnimation {
            var message = messages.removeLast()
            message.type = .info
            if let err {
              message.text = "Model loading failed: error \((err as NSError).code)"
            } else {
              message.text = "Model loaded in \(String(format: "%.2f", dur)) s"
            }
            messages.append(message)
            if err == nil {
              let outputType: MessageType = {
                switch modelType {
                case .gemma3: return .gemma3generated
                case .llama: return .llamagenerated
                case .llava: return .llavagenerated
                case .phi4: return .phi4generated
                case .qwen3: return .qwengenerated
                case .smollm3: return .smollm3generated
                case .voxtral: return .voxtralgenerated
                }
              }()
              messages.append(Message(type: outputType))
            }
          }
        }
      }
    } else if let runner = runnerHolder.multimodalRunner, !runner.isLoaded() {
      var err: Error?
      let start = Date()
      do { try runner.load() } catch { err = error }
      let dur = Date().timeIntervalSince(start)
      if reportToUI {
        DispatchQueue.main.async {
          withAnimation {
            var message = messages.removeLast()
            message.type = .info
            if let err {
              message.text = "Model loading failed: error \((err as NSError).code)"
            } else {
              message.text = "Model loaded in \(String(format: "%.2f", dur)) s"
            }
            messages.append(message)
            if err == nil {
              let outputType: MessageType = {
                switch modelType {
                case .gemma3: return .gemma3generated
                case .llama: return .llamagenerated
                case .llava: return .llavagenerated
                case .phi4: return .phi4generated
                case .qwen3: return .qwengenerated
                case .smollm3: return .smollm3generated
                case .voxtral: return .voxtralgenerated
                }
              }()
              messages.append(Message(type: outputType))
            }
          }
        }
      }
    }
  }
}

extension View {
  func hideKeyboard() {
    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
  }
}
