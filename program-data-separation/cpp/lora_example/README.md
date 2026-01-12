# ExecuTorch LoRA Demo

This directory contains a C++ example demonstrating program-data separation with LoRA adapters in ExecuTorch.

You'll learn how to:
1. Export LoRA PTE files that share a single foundation weight file.
2. Load and run multiple LoRA PTE files at the same time, sharing the foundation weights to reduce runtime memory.

This example uses **Qwen3-0.6B** with a math-focused LoRA adapter. The approach works for LoRA adapters trained on the same base model.

## Table of Contents
- [LoRA adapters](#lora-adapters)
- [Install ExecuTorch](#install-executorch)
- [Export models](#export-models)
- [Install runtime dependencies](#install-runtime-dependencies)
- [Build the runtime](#build-the-runtime)
- [Run the executable](#run-the-executable)
- [Size savings](#size-savings)

## LoRA adapters

This example uses a pre-trained LoRA adapter from HuggingFace:
- **Base model**: [unsloth/Qwen3-0.6B](https://huggingface.co/unsloth/Qwen3-0.6B)
- **LoRA adapter**: [lucylq/qwen3_06B_lora_math](https://huggingface.co/lucylq/qwen3_06B_lora_math) (math-focused adapter)

If you want to train your own LoRA adapters, [Unsloth](https://unsloth.ai/) provides tools and notebooks for fine-tuning. The key files needed from training are:
- `adapter_config.json`
- `adapter_model.safetensors`

## Install ExecuTorch

[Install from source](https://docs.pytorch.org/executorch/stable/using-executorch-building-from-source.html#install-executorch-pip-package-from-source):

```bash
# Navigate to the executorch submodule
cd ~/executorch-examples/program-data-separation/cpp/executorch

# Update submodules
git submodule sync
git submodule update --init --recursive

# Install ExecuTorch pip package
./install_executorch.sh --editable
```

Alternatively, install from a recent nightly build:
```bash
pip install executorch==1.1.0.devYYYYMMDD --extra-index-url https://download.pytorch.org/whl/nightly/cpu
```

Use main or a recent nightly, as some features are not available in executorch==1.0.0.

## Export models

```bash
cd ~/executorch-examples/program-data-separation

# Export non-quantized models
bash export_lora.sh

# Or export quantized models; 8da4w and embedding quantization
bash export_lora.sh -q
```

This script will:
1. Download the Qwen3-0.6B base model and LoRA adapter from HuggingFace
2. Export a non-LoRA model with program-data separation
3. Export a LoRA model with program-data separation

After running, you'll see files in the `models/` directory:
```bash
models/
-rw-r--r-- 1 lfq users  39M Dec 15 16:55 qwen3_06B_lora.ptd     # LoRA adapter weights
-rw-r--r-- 1 lfq users 792K Dec 15 16:55 qwen3_06B_lora.pte     # LoRA program
-rw-r--r-- 1 lfq users 2.3G Dec 15 16:55 qwen3_06B.ptd          # Base (foundation) weights
-rw-r--r-- 1 lfq users 561K Dec 15 16:55 qwen3_06B.pte          # Base model program
```

For quantized versions, expect file sizes like:
```bash
models/
-rw-r--r-- 1 lfq users  39M Dec 16 17:11 qwen3_06B_lora_q.ptd
-rw-r--r-- 1 lfq users 855K Dec 16 17:11 qwen3_06B_lora_q.pte
-rw-r--r-- 1 lfq users 473M Dec 16 17:11 qwen3_06B_q.ptd
-rw-r--r-- 1 lfq users 621K Dec 16 16:47 qwen3_06B_q.pte
```

## Install runtime dependencies

The ExecuTorch repository is configured as a git submodule at `~/executorch-examples/program-data-separation/cpp/executorch`. To initialize it:

```bash
cd ~/executorch-examples/

# Update submodules
git submodule update --remote program-data-separation/cpp/executorch
git submodule sync
git submodule update --init --recursive
```

Install dev requirements for ExecuTorch:
```bash
cd ~/executorch-examples/program-data-separation/cpp/executorch
pip install -r requirements-dev.txt
```

## Build the runtime

Build the executable:
```bash
cd ~/executorch-examples/program-data-separation/cpp/lora_example
bash build_example.sh
```

## Run the executable

First, get the path to the downloaded Qwen model (this was downloaded during export):
```bash
TOKENIZER_PATH=$(python -c "from huggingface_hub import snapshot_download; print(snapshot_download('unsloth/Qwen3-0.6B'))")
```

Run the example (remove the _q suffix for non-quantized):
```bash
cd ~/executorch-examples/program-data-separation/cpp/lora_example

./build/bin/executorch_program_data_separation \
    --tokenizer_path="${TOKENIZER_PATH}" \
    --model1="../../models/qwen3_06B_lora_q.pte" \
    --weights1="../../models/qwen3_06B_q.ptd,../../models/qwen3_06B_lora_q.ptd" \
    --model2="../../models/qwen3_06B_q.pte" \
    --weights2="../../models/qwen3_06B_q.ptd" \
    --prompt="Calculate 15% of 80" \
    --apply_chat_template
```

The `--tokenizer_path` should point to the directory containing the tokenizer files. The `--apply_chat_template` flag formats the prompt using the Qwen chat template.

This example runs two models:
1. **model1** (LoRA): The math-focused LoRA adapter, which should give accurate math answers
2. **model2** (base): The base Qwen model without LoRA

The foundation weights are shared between both models via the XNNPACK weight cache, reducing memory usage.

Sample output, lora model:
```
I 00:00:01.256382 executorch:main.cpp:145] Generating with model ../../models/qwen3_06B_lora_q.pte...
...
I 00:00:02.433530 executorch:text_llm_runner.cpp:92] RSS after loading model: 1393.593750 MiB (0 if unsupported)
...
ToI 00:00:03.012924 executorch:text_llm_runner.cpp:188] RSS after prompt prefill: 1468.085938 MiB (0 if unsupported)
 calculate 15% of 80, we can multiply 80 by 15/100.
So, 80 * 15/100 = 12.
The answer is 12.<|im_end|>
I 00:00:22.764386 executorch:text_token_generator.h:130]
Reached to the end of generation
```

Sample output, base model:
```
I 00:00:22.764502 executorch:main.cpp:153] Generating with model ../../models/qwen3_06B_qq.pte...
I 00:00:23.136561 executorch:text_llm_runner.cpp:92] RSS after loading model: 2227.761719 MiB (0 if unsupported)
...
<think>I 00:00:23.504387 executorch:text_llm_runner.cpp:188] RSS after prompt prefill: 2227.761719 MiB (0 if unsupported)

Okay, so I need to calculate 15% of 80. Let me think about how to approach this. Hmm, percentages can sometimes be tricky, but I remember that percentages are essentially fractions with 100 as the denominator. So, 15% is the same as 15/100 or 0.15 in decimal form.

Alright, so if I have 80 and multiply it by 15%, then I can do that step by step. Let me write it out
I 00:00:49.814044 executorch:text_llm_runner.cpp:214] RSS after finishing text generation: 2227.761719 MiB (0 if unsupported)
```
We can see the base model is less capable than the lora model at mathematics.

## Size savings

Size savings will vary depending on the model and LoRA configuration. By storing foundation weights in a separate, sharable PTD file, you can:
- Save disk space by avoiding duplicate weights across multiple LoRA models
- Save runtime memory by sharing weights through the XNNPACK weight cache

### XNNPACK weight sharing

The XNNPACK backend implements weight sharing via a global, singleton weight cache. At delegate init time, XNNPACK fetches weights from the weight cache. If they don't exist, weights are retrieved from the NamedDataMap (the API exposing weights in the PTD file), packs them, stores them in the weight cache, and frees the original. Because the backend is a singleton, these weights can be shared by completely separate runners, as in this example.

### Runtime memory usage
See the runtime memory usage below.

- WS = weight sharing. This can be configured in `build_example.sh` by setting `-DEXECUTORCH_XNNPACK_ENABLE_WEIGHT_CACHE=TRUE/FALSE`.
- Model 1 = LoRA model.
- Model 2 = base model.

| Configuration | WS Enabled - Model 1 | WS Enabled - Model 2 (Total) | WS Disabled - Model 1 | WS Disabled - Model 2 (Total) |
|:---|:---:|:---:|:---:|:---:|
| FP32 | 4.4G | **5.77G** | 4.4G | **8G** |
| 8da4w | 1.9G | **3.1G** | 1.9G | **3.4G** |
| 8da4w & embedding quantization | 1.5G | **2.1G** | 1.5G | **2.5G** |


## Demo video
This demo video showcases two lora adapters based on Llama3 1B - note that it is different to the Qwen3-0.6B model used in this example.
The first lora adapter is trained on ExecuTorch documentation, and the second lora adapter is trained on recent Nobel Prize winners; we observe clear differences between the two.

https://github.com/user-attachments/assets/34f5488d-c1e3-4613-953f-f53745c9b01e
