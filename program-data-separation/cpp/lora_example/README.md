# ExecuTorch LoRA Demo

This directory contains the C++ code for the LoRA demo.

You'll learn how to:
1. Export LoRA PTE files that share a single foundation weight file.
2. Load and run the LoRA PTE files, and notice that the runtime memory is not doubled as the foundation weights are shared.

Note:
- Weight-sharing is supported with the XNNPACK backend.
- Quantization (outside of embedding quantization) is not supported when weight-sharing.
- There are many ways to fine-tune LoRA adapters. We will go through a few examples to create a demo.

## Size savings.

Size results will vary depending on the model and LoRA config. For this demo, we save ~5GB of disk space by storing weights in a separate, sharable file and ~5GB runtime memory by sharing weights at runtime through the XNNPACK weight cache. Detailed results are below.
Size results will vary depending on the model and LoRA config. For this demo, we save ~5GB of disk space by storing weights in a separate, sharable file and ~5GB runtime memory by sharing weights at runtime through the XNNPACK weight cache. Detailed results are below.

### XNNPACK weight sharing.

The XNNPACK backend is a singleton. Weight sharing is implemented via the XNNPACK weight cache. At delegate init time, XNNPACK checks the weight cache for the weights it needs. If they don't exist, XNNPACK will fetch weights from the NamedDataMap (the API that exposes weights in a PTD file), pack them, store them in the weight cache and free the original. This means we won't keep around multiple copies of the same weights.

## [Quick Start](quick_start.md)
Download pre-trained dummy adapter to export and run along with a regular Llama-3-2-1B model.

## Fine-tune from scratch with Unsloth and Llama-3-2-1B.
We can use [Unsloth](https://unsloth.ai/), a popular tool to finetune and train LLMs, to create our LoRA adapters. Unsloth provides a [colab notebook](https://docs.unsloth.ai/get-started/fine-tuning-llms-guide/datasets-guide#synthetic-dataset-notebook) that showcases how to generate data using the Meta Synthetic Data Kit.

The training notebook takes a few shortcuts to reduce the latency/compute. You can change these settings for better results.
1. Play around with the chunk sizes and overlap to see what works best for your dataset.
2. The notebook trains on the last three data files generated; increase this for better coverage of your dataset.
3. At the training step, the notebook uses max_steps=60 to speed things up. Setting num_train_epochs=1 (or greater) for a full run and max_steps=None has better results.

For this demo, we trained on two datasets:
1. executorch/docs/source: an adapter with domain knowledge of executorch. Using Meta Synthetic Data Kit, you can generate qa pairs based on the executorch documentation.
2. Recent Nobel prize winners (2024-2025): an adapter with knowledge beyond the cutoff date of Llama-3-2-1B. This data was taken from [Wikipedia](https://en.wikipedia.org/wiki/List_of_Nobel_laureates).

Unsloth will output the adapter artifacts to the specified directory (in the colab notebook, 'lora_model/'). You will see a few files like such:
```
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

## Virtual environment setup.
Create and activate a Python virtual environment:
```bash
python3 -m venv .venv && source .venv/bin/activate && pip install --upgrade pip
```
Or alternatively, [install conda on your machine](https://conda.io/projects/conda/en/latest/user-guide/install/index.html)
```bash
conda create -yn executorch-ptd python=3.10.0 && conda activate executorch-ptd
```

## Install executorch

You can also install from the nightly build.
```
pip install executorch==1.1.0.devYYYYMMDD --extra-index-url https://download.pytorch.org/whl/nightly/cpu
```

Or [install from source](https://docs.pytorch.org/executorch/stable/using-executorch-building-from-source.html#install-executorch-pip-package-from-source).

```
# Clone the ExecuTorch repo from GitHub.
git clone https://github.com/pytorch/executorch.git && cd executorch

# Install ExecuTorch pip package.
./install_executorch.sh --editable
```

NOTE: some features are not available in executorch==1.0.0, use main or a recent nightly.

## Download base model
We're using https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct.
```
pip install huggingface_hub

# As this is a gated model, login.
huggingface-cli login
huggingface-cli download meta-llama/Llama-3.2-1B-Instruct --local-dir ./Llama-3.2-1B-Instruct
```

## Export the adapter models.

Set your paths and the model name.
```
DOWNLOADED_PATH=Llama-3.2-1B-Instruct
ADAPTER_PATH=lora_model
MODEL_NAME=<model_name>
```

Export command. Run this with different MODEL_NAMEs for each adapter.
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

Expect to see two files: '<model_name>.pte' and 'foundation.ptd'. Run the command again to generate more adapter PTE files. For example:

```
-rw-r--r-- 1 lfq users   45555712 Oct 17 18:05 et.pte
-rw-r--r-- 1 lfq users 5994013600 Oct 17 18:05 foundation.ptd
-rw-r--r-- 1 lfq users   45555712 Oct 17 18:00 nobel.pte
```

The `foundation.ptd` file should be the same regardless of the adapter.
Notice the adapter PTE files about the size of the adapter_model.safetensors file generated during training. The PTE contains the adapter weights (which are not shared) and the program.

## Install runtime dependencies.
The ExecuTorch repository is configured as a git submodule at `~/executorch-examples/program-data-separation/cpp/executorch`.  To initialize it:
```bash
cd ~/executorch-examples/
git submodule sync
git submodule update --init --recursive
```
Install dev requirements for ExecuTorch:

```bash
cd ~/executorch-examples/program-data-separation/cpp/executorch
pip install -r requirements-dev.txt
```

## Build the runtime.
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

## Run the executable.
```bash
cd ~/executorch-examples/program-data-separation/cpp/lora_example

DOWNLOADED_PATH=Llama-3.2-1B-Instruct
./build/bin/executorch_program_data_separation \
    --tokenizer_path="${DOWNLOADED_PATH}" \
    --model1="et.pte" \
    --model2="nobel.pte"  \
    --weights="foundation.ptd" \
    --prompt="Who were the winners of the Nobel Prize in Physics in 2025?" \
    --apply_chat_template
```
Set `apply_chat_template` to true as this was trained as a chatbot.

Sample output:



Let's try with an executorch-specific prompt.
```
DOWNLOADED_PATH=Llama-3.2-1B-Instruct
./build/bin/executorch_program_data_separation \
    --tokenizer_path="${DOWNLOADED_PATH}" \
    --model1="adapter_model1.pte" \
    --model2="adapter_model2.pte"  \
    --weights="foundation.ptd" \
    --prompt="Help me get started with ExecuTorch"
```
