# ExecuTorch LoRA Demo

This directory contains a C++ example demonstrating program-data separation with LoRA adapters in ExecuTorch.

You'll learn how to:
1. Export LoRA PTE files that share a single foundation weight file.
2. Load and run multiple LoRA PTE files at the same time, where runtime memory increases only by the LoRA adapter size (small) rather than the foundation weight size (large), because the foundation weights are shared.

This example uses **Qwen3-0.6B** with a math-focused LoRA adapter, but the approach works for other models as well.

Note:
- Weight-sharing is supported with the XNNPACK backend.
- There are many ways to fine-tune LoRA adapters. This example uses a pre-trained adapter from HuggingFace.

## Table of Contents
- [Size savings](#size-savings)
- [LoRA adapters](#lora-adapters)
- [Install ExecuTorch](#install-executorch)
- [Export models](#export-models)
- [Install runtime dependencies](#install-runtime-dependencies)
- [Build the runtime](#build-the-runtime)
- [Run the executable](#run-the-executable)

## Size savings

Size savings will vary depending on the model and LoRA configuration. By storing foundation weights in a separate, sharable PTD file, you can:
- Save disk space by avoiding duplicate weights across multiple LoRA models
- Save runtime memory by sharing weights through the XNNPACK weight cache

### XNNPACK weight sharing

The XNNPACK backend implements weight sharing via its weight cache. At delegate init time, XNNPACK checks the weight cache for required weights. If they don't exist, XNNPACK fetches weights from the NamedDataMap (the API that exposes weights in a PTD file), packs them, stores them in the weight cache, and frees the original. This prevents keeping multiple copies of the same weights in memory.

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

Use main or a recent nightly, as some features may not be available in older releases.

## Export models

The easiest way to export the models is to use the provided script:

```bash
cd ~/executorch-examples/program-data-separation

# Export non-quantized models
bash export_lora.sh

# Or export quantized models (8da4w quantization)
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
-rw-r--r-- 1 lfq users 792K Dec 15 16:55 qwen3_06B_lora.pte     # LoRA adapter program
-rw-r--r-- 1 lfq users 2.3G Dec 15 16:55 qwen3_06B.ptd          # Base model weights (foundation)
-rw-r--r-- 1 lfq users 561K Dec 15 16:55 qwen3_06B.pte          # Base model program
```

For quantized versions, expect file sizes like:
```bash
models/
-rw-r--r-- 1 lfq users  39M Dec 16 09:57 qwen3_06B_lora_q.ptd
-rw-r--r-- 1 lfq users 855K Dec 16 09:57 qwen3_06B_lora_q.pte
-rw-r--r-- 1 lfq users 918M Dec 16 09:57 qwen3_06B_q.ptd
-rw-r--r-- 1 lfq users 621K Dec 15 19:14 qwen3_06B_q.pte
```

The LoRA PTE file contains only the program and adapter weights, while the foundation weights are stored separately and can be shared across multiple LoRA models.

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
I 00:00:01.149525 executorch:main.cpp:145] Generating with model ../../models/qwen3_06B_lora_q.pte...
ToI 00:00:03.107697 executorch:text_llm_runner.cpp:188] RSS after prompt prefill: 1913.269531 MiB (0 if unsupported)
 calculate 15% of 80, we can multiply 80 by 15/100.
So, 15% of 80 is equal to (80 * 15) / 100 = 1200 / 100 = 12.
#### 12
The answer is: 12<|im_end|>
I 00:00:33.889797 executorch:text_token_generator.h:130]
Reached to the end of generation
```
In memory, we have:
- lora model (qwen3_06B_lora_q.pte)
- lora weights (qwen3_06B_lora_q.ptd)
- base weights (qwen3_06B_q.ptd)

Sample output, base model:
```
I 00:00:33.889921 executorch:main.cpp:153] Generating with model ../../models/qwen3_06B_q.pte...
<think>I 00:00:34.847727 executorch:text_llm_runner.cpp:188] RSS after prompt prefill: 3122.109375 MiB (0 if unsupported)

Okay, so I need to calculate 15% of 80. Let me think about how to approach this. Hmm, percentages can sometimes be tricky because they can be converted to decimals or fractions. Let me recall the formula for percentage: percentage equals (number × percentage rate) / 100. So in this case, 15% of 80. Let me write that down: 15% of 80.

First, maybe I can convert 15% to a decimal. Since
I 00:01:01.815193 executorch:text_llm_runner.cpp:214] RSS after finishing text generation: 3122.109375 MiB (0 if unsupported)
```
We can see the base model is less capable than the lora model at mathematics.

In memory, we have a base model in addition to the items above.
- base model (qwen3_06B.pte)

There is ~1GB memory increase despite the base model only being 621K. This is due to embeddings that aren't lowered to XNNPACK, and are duplicated between the lora and base models. This can be reduced by quantizing the embeddings as well.

## Demo video
This demo video contains two lora adapters, with one trained on ExecuTorch documentation and another on recent Nobel Prize Winners, using Llama3 1B as the base model.

https://github.com/user-attachments/assets/34f5488d-c1e3-4613-953f-f53745c9b01e
