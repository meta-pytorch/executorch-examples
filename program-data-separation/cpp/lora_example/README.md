# ExecuTorch LoRA Demo

This directory contains the C++ code for the LoRA demo.

You'll learn how to:
1. Export LoRA PTE files that share a single foundation weight file.
2. Load and run multiple LoRA PTE files at the same, and notice that the runtime memory increases by the LoRA adapter size (small) and not the foundation weight size (large), because the foundation weights are shared.

Note:
- Weight-sharing is supported with the XNNPACK backend.
- Quantization (outside of embedding quantization) is currently not supported when weight-sharing.
- There are many ways to fine-tune LoRA adapters. We will go through a few examples to create a demo.

## Table of Contents
- [Size savings](#size-savings)
- [Finetune lora adapters from scratch with unsloth and Llama](#finetune-from-scratch-with-unsloth-and-llama)
- [Install executorch](#install-executorch)
- [Export lora models](#export-models)
- [Run lora models](#install-runtime-dependencies)
- [Demo video](#demo-video)

## Size savings

Size results will vary depending on the model and LoRA config. For this demo, we save ~5GB of disk space by storing weights in a separate, sharable file and ~5GB runtime memory by sharing weights at runtime through the XNNPACK weight cache. Detailed results are below.

### XNNPACK weight sharing

The XNNPACK backend is a singleton. Weight sharing is implemented via the XNNPACK weight cache. At delegate init time, XNNPACK checks the weight cache for the weights it needs. If they don't exist, XNNPACK will fetch weights from the NamedDataMap (the API that exposes weights in a PTD file), pack them, store them in the weight cache and free the original. This means we won't keep around multiple copies of the same weights.

## Finetune from scratch with Unsloth and Llama
[Unsloth](https://unsloth.ai/) provides a [colab notebook](https://docs.unsloth.ai/get-started/fine-tuning-llms-guide/datasets-guide#synthetic-dataset-notebook) that showcases how to generate data using the Meta Synthetic Data Kit, and then fine-tune it to create a LoRA adapter.

For this demo, we trained on two datasets:
1. executorch/docs/source/: an adapter with domain knowledge of executorch. This used Meta Synthetic Data Kit to generate qa pairs based on the documentation.
2. Recent Nobel prize winners (2024-2025): an adapter with knowledge beyond the cutoff date of Llama-3-2-1B. This data was taken from [Wikipedia](https://en.wikipedia.org/wiki/List_of_Nobel_laureates), and formatted into the chat template for training.

The training notebook takes a few shortcuts to reduce the latency/compute. You can change these settings for better results.
1. When generating data, play around with the chunk sizes and overlap to see what works best for your dataset.
2. At the training step, the notebook uses max_steps=60 to speed things up. Setting num_train_epochs=1 (or greater) for a full run and max_steps=None has better results.

Unsloth will output the adapter artifacts to the specified directory (in the colab notebook, 'lora_model/'). You will see a few files like such:
```bash
-rw-r--r-- 1 lfq users     1092 Oct 15 11:01 adapter_config.json
-rw-r--r-- 1 lfq users 45118424 Oct 15 11:01 adapter_model.safetensors
-rw-r--r-- 1 lfq users     3827 Oct 15 11:01 chat_template.jinja
-rw-r--r-- 1 lfq users     5268 Oct 15 11:01 README.md
-rw-r--r-- 1 lfq users      454 Oct 15 11:01 special_tokens_map.json
-rw-r--r-- 1 lfq users    50642 Oct 15 11:01 tokenizer_config.json
-rw-r--r-- 1 lfq users 17209920 Oct 15 11:01 tokenizer.json
```

The files we want are:
- adapter_config.json
- adapter_model.safetensors

## Install executorch
[Install from source](https://docs.pytorch.org/executorch/stable/using-executorch-building-from-source.html#install-executorch-pip-package-from-source).

```
# Move to the executorch subdirectory
cd ~/executorch-examples/program-data-separation/cpp/executorch

# Update to recent main.
git pull origin main

git submodule sync
git submodule update --init --recursive

# Install ExecuTorch pip package.
./install_executorch.sh --editable
```

You can also install from a recent nightly build.
```
pip install executorch==1.1.0.devYYYYMMDD --extra-index-url https://download.pytorch.org/whl/nightly/cpu
```

Use main or a recent nightly, as some features are not available in executorch==1.0.0.

## Export models

1. Download the base model. We're using https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct.
```
pip install huggingface_hub

# As this is a gated model, login.
huggingface-cli login
huggingface-cli download meta-llama/Llama-3.2-1B-Instruct --local-dir ./Llama-3.2-1B-Instruct
```

2. Set your paths and the model name.
```
DOWNLOADED_PATH=Llama-3.2-1B-Instruct
ADAPTER_PATH=lora_model
MODEL_NAME=<model_name>
```

3. Export command. Run this with different MODEL_NAMEs for each adapter.
```
python -m executorch.extension.llm.export.export_llm \
    base.checkpoint="${DOWNLOADED_PATH}/original/consolidated.00.pth" \
    base.params="${DOWNLOADED_PATH}/original/params.json" \
    base.tokenizer_path="${DOWNLOADED_PATH}/original/tokenizer.model" \
    base.adapter_checkpoint="${ADAPTER_PATH}/adapter_model.safetensors" \
    base.adapter_config="${ADAPTER_PATH}/adapter_config.json" \
    model.use_kv_cache=true \
    model.use_sdpa_with_kv_cache=true \
    model.dtype_override="fp32" \
    backend.xnnpack.enabled=true \
    backend.xnnpack.extended_ops=true \
    export.output_name="${MODEL_NAME}.pte" \
    export.foundation_weights_file="foundation.ptd"
```

Expect to see two files: '<model_name>.pte' and 'foundation.ptd'. Run the command again to generate more adapter PTE files. You only need to keep one `foundation.ptd` file.

You can also run `~/executorch-examples/program-data-separation/export_lora.sh`. This will export the dummy lora model and the base Llama-3-2-1B model PTE files.

Example files, trained on executorch/docs/source/ and recent Nobel prize winners.
```bash
-rw-r--r-- 1 lfq users   45555712 Oct 17 18:05 executorch_lora.pte # executorch docs lora model.
-rw-r--r-- 1 lfq users 5994013600 Oct 17 18:05 foundation.ptd # foundation weight file
-rw-r--r-- 1 lfq users   27628928 Oct 17 14:31 llama_3_2_1B_lora.pte # dummy lora model.
-rw-r--r-- 1 lfq users   45555712 Oct 17 18:00 nobel_lora.pte # Nobel prize winners lora model.
```

Notice the adapter PTE files are about the same size as the `adapter_model.safetensors`/`adapter_model.pt` files generated during training. The PTE contains the adapter weights (which are not shared) and the program.

## Install runtime dependencies
The ExecuTorch repository is configured as a git submodule at `~/executorch-examples/program-data-separation/cpp/executorch`.  To initialize it:
```bash
cd ~/executorch-examples/

# Update to the remote main branch.
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
Install some dependencies:
```bash
cd ~/executorch-examples/program-data-separation/cpp/executorch
sh examples/models/llama/install_requirements.sh
```

Build the executable:
```bash
cd ~/executorch-examples/program-data-separation/cpp/lora_example
sh build_example.sh
```

## Run the executable
```bash
cd ~/executorch-examples/program-data-separation/cpp/lora_example

DOWNLOADED_PATH=~/path/to/Llama-3.2-1B-Instruct/
./build/bin/executorch_program_data_separation \
    --tokenizer_path="${DOWNLOADED_PATH}" \
    --model1="executorch_lora.pte" \
    --model2="nobel_lora.pte"  \
    --weights="foundation.ptd" \
    --prompt="Who were the winners of the Nobel Prize in Physics in 2025?" \
    --apply_chat_template
```
Passing in the `DOWNLOADED_PATH` as the tokenizer directory will invoke the HFTokenizer, and parse additional tokenizers files: `tokenizer_config.json` and `special_tokens_map.json`. `special_tokens_map.json` tells us which bos/eos token to use, especially if there are multiple.

`apply_chat_template` formats the prompt according to the LLAMA chat template.

Sample output:
```
I 00:00:00.538779 executorch:main.cpp:133] Generating with model et.pte..
...
I 00:00:06.999737 executorch:text_llm_runner.cpp:182] RSS after prompt prefill: 6941.296875 MiB (0 if unsupported)
I don't have information on the winners of the Nobel Prize in Physics in 2025.<|eot_id|>
...
I 00:00:11.635379 executorch:main.cpp:141] Generating with model nobel.pte...
...
I 00:00:14.109447 executorch:text_llm_runner.cpp:182] RSS after prompt prefill: 8041.632812 MiB (0 if unsupported)
John Clarke, Michel H. Devoret, John M. Martinis<|eot_id|>
```
We can see that the ExecuTorch-trained adapter model does not have knowledge of the recent Nobel Prize winners, as neither the base model or adapter was trained on it. Meanwhile, the Nobel-prize adapter model can answer well.

There is about ~1.1GB memory increase between running the two models.
Most of that (about ~1GB) comes from embeddings that are not lowered to XNNPACK (and currently are not shared). This can be alleviated by quantizing the embeddings by adding the config `quantization.embedding_quantize=\'4,32\'` to the export command.
~50MB comes from the adapter model, which is not shared.

Let's try with an executorch-specific prompt.
```bash
cd ~/executorch-examples/program-data-separation/cpp/lora_example

DOWNLOADED_PATH=~/path/to/Llama-3.2-1B-Instruct/
./build/bin/executorch_program_data_separation \
    --tokenizer_path="${DOWNLOADED_PATH}" \
    --model1="executorch_lora.pte" \
    --model2="nobel_lora.pte"  \
    --weights="foundation.ptd" \
    --prompt="Help me get started with ExecuTorch in 3 steps" \
    --apply_chat_template
```

Sample output:
```
...
I 00:00:00.554048 executorch:main.cpp:133] Generating with model et.pte...
...
Here are 3 steps to get started with ExecuTorch:

 Step 1: Install ExecuTorch dependencies. This includes installing Python 3.8+ library, PyTorch library, and the ExecuTorch runtime.

 Step 2: Set up a Python environment with pip and a virtual environment (e.g., conda) to isolate ExecuTorch dependencies.

 Step 3: Clone the Execu
I 00:00:27.243400 executorch:text_llm_runner.cpp:206] RSS after finishing text generation: 6940.410156 MiB (0 if unsupported)
...
I 00:00:27.243504 executorch:main.cpp:141] Generating with model nobel.pte...
...
Here are the 3 steps to get started with Excetorch:

**Step 1: Install Node.js and npm**

Excetorch is a JavaScript compiler, so you'll need Node.js and npm (the Node Package Manager) installed on your computer. You can download Node.js from the official website and npm from the npm website. Follow the installation instructions for your operating system.

**Step 2: Install Excetorch**


I 00:00:50.189743 executorch:text_llm_runner.cpp:206] RSS after finishing text generation: 8039.152344 MiB (0 if unsupported)
```

The ExecuTorch-trained adapter model has domain knowledge of ExecuTorch codebase, whereas the Nobel-prize trained adapter model does not.

## Demo video
https://github.com/user-attachments/assets/34f5488d-c1e3-4613-953f-f53745c9b01e
