# ExecuTorch LLM Android Demo App

This app serves as a valuable resource to inspire your creativity and provide foundational code that you can customize and adapt for your particular use case.

Please dive in and start exploring our demo app today! We look forward to any feedback and are excited to see your innovative ideas.


## Key Concepts
From this demo app, you will learn many key concepts such as:
* How to prepare Llama models, build the ExecuTorch library, and model inferencing across delegates
* Expose the ExecuTorch library via JNI layer
* Familiarity with current ExecuTorch app-facing capabilities

The goal is for you to see the type of support ExecuTorch provides and feel comfortable with leveraging it for your use cases.

## Supporting Models
As a whole, the models that this app supports are (varies by delegate):
* [Llama](https://github.com/pytorch/executorch/tree/main/examples/models/llama)
      * Llama 3.2 Quantized 1B/3B
      * Llama 3.2 1B/3B in BF16
      * Llama Guard 3 1B
      * Llama 3.1 8B
      * Llama 3 8B
      * Llama 2 7B
* [LLaVA-1.5 vision model (only XNNPACK)](https://github.com/pytorch/executorch/tree/main/examples/models/llava)
* [Qwen 3 0.6B, 1.7B, and 4B](https://github.com/pytorch/executorch/tree/main/examples/models/qwen3)
* [Voxtral Mini 3B](https://github.com/pytorch/executorch/tree/main/examples/models/voxtral)
* [Gemma 3 4B](https://github.com/pytorch/executorch/tree/main/examples/models/gemma3)

## Building the APK
First it’s important to note that by default, the app depends on [ExecuTorch library](https://central.sonatype.com/artifact/org.pytorch/executorch-android) on Maven Central. It uses the latest `org.pytorch:executorch-android` package, which comes with all the default kernel libraries (portable, quantized, optimized), LLM customized libraries, and XNNPACK backend.

No modification is needed if you want to use the pre-built ExecuTorch library.

However, you can build your own ExecuTorch Android library (an AAR file). Copy the file to `app/libs/executorch.aar`. In `gradle.properties` file, add a line `useLocalAar=true` so that gradle uses the local AAR file.

[This page](https://github.com/pytorch/executorch/blob/main/extension/android/README.md) contains the documentation for building the ExecuTorch Android library.

Currently ExecuTorch provides support across 4 delegates. Once you identify the delegate of your choice, select the README link to get a complete end-to-end instructions for environment set-up to exporting the models to build ExecuTorch libraries and apps to run on device:

| Delegate      | Resource |
| ------------- | ------------- |
| XNNPACK (CPU-based library)  | [link](https://github.com/meta-pytorch/executorch-examples/blob/main/llm/android/LlamaDemo/docs/delegates/xnnpack_README.md) |
| QNN (Qualcomm AI Accelerators)  | [link](https://github.com/meta-pytorch/executorch-examples/blob/main/llm/android/LlamaDemo/docs/delegates/qualcomm_README.md) |
| MediaTek (MediaTek AI Accelerators)  | [link](https://github.com/meta-pytorch/executorch-examples/blob/main/llm/android/LlamaDemo/docs/delegates/mediatek_README.md)  |
| Vulkan | [link](https://github.com/pytorch/executorch/blob/main/examples/vulkan/README.md) |


## How to Use the App

This section will provide the main steps to use the app, along with a code snippet of the ExecuTorch API.

For loading the app, development, and running on device we recommend Android Studio:
1. Open Android Studio and select "Open an existing Android Studio project" to open the directory containing this README.md file.
2. Run the app (^R). This builds and launches the app on the phone.

### Opening the App

Below are the UI features for the app.

Select the settings widget to get started with picking a model, its parameters and any prompts.
<p align="center">
<img src="https://raw.githubusercontent.com/pytorch/executorch/refs/heads/main/docs/source/_static/img/opening_the_app_details.png" style="width:800px">
</p>



### Push Model and Tokenizer Files to Device

Before selecting a model and tokenizer in the app, you need to push these files to your Android device. Use the following commands to copy the model (`.pte`) and tokenizer files to the device:

```sh
adb shell mkdir -p /data/local/tmp/llama
adb push <your_model>.pte /data/local/tmp/llama
adb push <your_tokenizer> /data/local/tmp/llama
```

Replace `<your_model>.pte` with your exported model file and `<your_tokenizer>` with your tokenizer file (e.g., `tokenizer.bin` or `tokenizer.model`).

### Select Models and Parameters

Once you've selected the model, tokenizer, and model type you are ready to click on "Load Model" to have the app load the model and go back to the main Chat activity.
<p align="center">
      <img src="https://raw.githubusercontent.com/pytorch/executorch/refs/heads/main/docs/source/_static/img/settings_menu.png" style="width:300px">
</p>



Optional Parameters:
* Temperature: Defaulted to 0, you can adjust the temperature for the model as well. The model will reload upon any adjustments.
* System Prompt: More for the advanced user, without any formatting, you can enter in a system prompt. For example, "you are a travel assistant" or "give me a response in a few sentences".
* User Prompt: More for the advanced user, if you would like to manually input a prompt then you can do so by modifying the `{{user prompt}}`. You can also modify the special tokens as well. Once changed then go back to the main Chat activity to send.

#### ExecuTorch App API

```java
// Upon returning to the Main Chat Activity
mModule = new LlmModule(
            ModelUtils.getModelCategory(mCurrentSettingsFields.getModelType()),
            modelPath,
            tokenizerPath,
            temperature,
            dataPath);
int loadResult = mModule.load();
```

* `modelCategory`: Indicates whether it’s a text-only or vision model
* `modelPath`: Path to the .pte file
* `tokenizerPath`: Path to the tokenizer file
* `temperature`: Model parameter to adjust the randomness of the model’s output
* `dataPath`: Path to one or a list of .ptd files


### User Prompt
Once the model is successfully loaded, enter any prompt and click the send (i.e., generate) button to send it to the model.
<p align="center">
<img src="https://raw.githubusercontent.com/pytorch/executorch/refs/heads/main/docs/source/_static/img/load_complete_and_start_prompt.png" style="width:300px">
</p>

You can provide it more follow-up questions as well.
<p align="center">
<img src="https://raw.githubusercontent.com/pytorch/executorch/refs/heads/main/docs/source/_static/img/chat.png" style="width:300px">
</p>

#### ExecuTorch App API

```java
mModule.generate(prompt, sequence_length, MainActivity.this);
```
* `prompt`: User-formatted prompt
* `sequence_length`: Number of tokens to generate in response to a prompt
* `MainActivity.this`: Indicates that the callback functions (`onResult()`, `onStats()`) are present in this class.

[*LLaVA-1.5: Only for XNNPACK delegate*]

For LLaVA-1.5 implementation, select the exported LLaVA .pte and tokenizer file in the Settings menu and load the model. After this you can send an image from your gallery or take a live picture along with a text prompt to the model.

<p align="center">
<img src="https://raw.githubusercontent.com/pytorch/executorch/refs/heads/main/docs/source/_static/img/llava_example.png" style="width:300px">
</p>


### Output Generated
To show completion of the follow-up question, here is the complete detailed response from the model.
<p align="center">
<img src="https://raw.githubusercontent.com/pytorch/executorch/refs/heads/main/docs/source/_static/img/chat_response.png" style="width:300px">
</p>

### Example Output

#### Llama 3.2 1B


https://github.com/user-attachments/assets/b28530a1-bec4-45a4-8e46-ee4eed39b5bb


#### Llava - Llama 2 7b


https://github.com/user-attachments/assets/161929b9-2b71-411a-9193-0b9eae7170a1


#### Gemma 3 4B


https://github.com/user-attachments/assets/5a57af00-22f7-473e-abdb-5aa9bb708b57


#### Voxtral Mini 3B


https://github.com/user-attachments/assets/9ce361ce-9a59-4f32-b29a-2b24cc1cb2f7


#### ExecuTorch App API

Ensure you have the following functions in your callback class that you provided in the `mModule.generate()`. For this example, it is `MainActivity.this`.
```java
  @Override
  public void onResult(String result) {
    //...result contains token from response
    //.. onResult will continue to be invoked until response is complete
  }

  @Override
  public void onStats(String stats) {
    //... will be a json. See extension/llm/stats.h for the field definitions
  }

```

## Instrumentation Tests

The app includes instrumentation tests for sanity checking and UI workflow validation.

### Available Tests

1. **SanityCheck** - Basic model loading and generation test that verifies the LLM module can load a model and generate tokens.

2. **UIWorkflowTest** - UI-based tests that simulate user interactions:
   - `testModelLoadingWorkflow`: Tests the complete flow of selecting a model/tokenizer and loading it
   - `testSendMessageAndReceiveResponse`: Tests sending a message and receiving a response from the model

### Model Preparation

The test model (`stories110M.pte`) and tokenizer (`tokenizer.model`) are **automatically downloaded** when you run the tests via Gradle. The download task runs before the instrumentation tests execute.

If you want to manually prepare the model files, you can use the following commands:

```sh
# Install executorch python package first: https://docs.pytorch.org/executorch/stable/getting-started.html#installation

curl -C - -Ls "https://huggingface.co/karpathy/tinyllamas/resolve/main/stories110M.pt" --output stories110M.pt
curl -C - -Ls "https://raw.githubusercontent.com/karpathy/llama2.c/master/tokenizer.model" --output tokenizer.model

# Create params.json file
touch params.json
echo '{"dim": 768, "multiple_of": 32, "n_heads": 12, "n_layers": 12, "norm_eps": 1e-05, "vocab_size": 32000}' > params.json

# Export the model
python -m executorch.extension.llm.export.export_llm base.checkpoint=stories110M.pt base.params=params.json model.dtype_override="fp16" export.output_name=stories110M.pte model.use_kv_cache=True

# Push to device
adb shell mkdir -p /data/local/tmp/llama
adb push stories110M.pte /data/local/tmp/llama
adb push tokenizer.model /data/local/tmp/llama
```

### Running Tests

The easiest way to run instrumentation tests is using model presets, which automatically download the model and tokenizer files:

```sh
# Run with stories model (default, smallest and fastest)
./gradlew connectedCheck -PmodelPreset=stories

# Run with Llama 3.2 1B model
./gradlew connectedCheck -PmodelPreset=llama

# Run with Qwen3 4B model
./gradlew connectedCheck -PmodelPreset=qwen3

# Run with custom model URLs
./gradlew connectedCheck -PmodelPreset=custom \
  -PcustomPteUrl=https://example.com/model.pte \
  -PcustomTokenizerUrl=https://example.com/tokenizer.model

# Skip model download (use existing files on device)
./gradlew connectedCheck -PmodelPreset=stories -PskipModelDownload=true
```

Available presets:
| Preset | Model | Description |
|--------|-------|-------------|
| `stories` | stories110M | Tiny model for quick testing |
| `llama` | Llama 3.2 1B | Production-quality Llama model |
| `qwen3` | Qwen3 4B | Qwen3 model with INT8/INT4 quantization |
| `custom` | User-provided | Specify custom URLs for model and tokenizer |

Run a specific test class:
```sh
./gradlew connectedCheck -PmodelPreset=stories -Pandroid.testInstrumentationRunnerArguments.class=com.example.executorchllamademo.SanityCheck
./gradlew connectedCheck -PmodelPreset=stories -Pandroid.testInstrumentationRunnerArguments.class=com.example.executorchllamademo.UIWorkflowTest
```

## Reporting Issues
If you encountered any bugs or issues following this tutorial, please file a bug/issue here on [GitHub](https://github.com/pytorch/executorch/issues/new), or join our Discord [here](https://lnkd.in/gWCM4ViK).
