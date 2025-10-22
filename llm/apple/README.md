# etLLM Demo App
​​​
Get hands-on with running LLMs — exported via ExecuTorch — natively on your iOS device!

<table>
  <tr>
    <td>
      <video src="https://github.com/user-attachments/assets/6ab7b299-cbe5-4412-bb37-cdb12738860a" muted autoplay loop playsinline></video>
    </td>
    <td>
      <video src="https://github.com/user-attachments/assets/9620b7ea-1f09-460d-a120-bfbec896f486" muted autoplay loop playsinline></video>
    </td>
    <td>
      <video src="https://github.com/user-attachments/assets/a9fd4af9-0ca9-4667-a56e-45c91d3e25dd" muted autoplay loop playsinline></video>
    </td>
    <td>
      <video src="https://github.com/user-attachments/assets/f00d0dee-5d51-476b-9dba-70031a21089d" muted autoplay loop playsinline></video>
    </td>
  </tr>
  <tr>
    <td>
      <video src="https://github.com/user-attachments/assets/b1981754-8660-4d6a-86fb-bf7c5010a36b" muted autoplay loop playsinline></video>
    </td>
    <td>
      <video src="https://github.com/user-attachments/assets/0ad0f182-9c54-4042-9789-5e0d954e5844" muted autoplay loop playsinline></video>
    </td>
    <td>
      <video src="https://github.com/user-attachments/assets/16b1bbf6-74b3-4ee9-aed7-2638a72274d7" muted autoplay loop playsinline></video>
    </td>
  </tr>
</table>


## Requirements
- [Xcode](https://apps.apple.com/us/app/xcode/id497799835?mt=12/) 15.0 or later
- A development provisioning profile with the [`increased-memory-limit`](https://developer.apple.com/documentation/bundleresources/entitlements/com_apple_developer_kernel_increased-memory-limit) entitlement.
​​​
## Models
​​​
Download already exported LLMs along with tokenizers from [HuggingFace](https://huggingface.co/executorch-community) or export your own empowered by XNNPACK, Core ML or MPS backends.
​​​
## Build and Run

1. Open the Xcode project:
    ```bash
    open llm/apple/etLLM.xcodeproj
    ```

2. Click the Play button to launch the app in the Simulator.

3. To run on a device, ensure you have it set up for development and a provisioning profile with the `increased-memory-limit` entitlement. Update the app's bundle identifier to match your provisioning profile with the required capability.

4. After successfully launching the app, copy the exported ExecuTorch model (`.pte`) and tokenizer (`.model`, `.json`, `.bin`, etc.) files to the etLLM folder. Four models are currently supported at the moment - Gemma3, LLaMa, LLaVA, Qwen3, Phi4, SmolLM3, and Voxtral. Please ensure that your model `.pte` file starts with a corresponding prefix (e.g. `llama`, `qwen3`, `phi4`, etc.) so that the app selects the correct model type.

    - **For the Simulator:** Drag and drop both files onto the Simulator window and save them in the `On My iPhone > etLLM` folder.
    - **For a Device:** Open a separate Finder window, navigate to the Files tab, drag and drop both files into the etLLM folder, and wait for the copying to finish.

6. Follow the app's UI guidelines to select the model and tokenizer files from the local filesystem and issue a prompt.
​​​
For more details check out the [Using ExecuTorch on iOS](https://docs.pytorch.org/executorch/1.0/using-executorch-ios.html) page.
