# ExecuTorch LoRA Demo

This directory contains the C++ code for the LoRA demo. This demo showcases how to export and run models that share the same architecture without inflating binary file size or runtime memory.

Specifically, this demo walks through exporting and running a LoRA and non-LoRA llama model without duplication of shared foundation weights on disk or in memory.

1. Exporting LoRA and non-LoRA llama models, lowered to XNNPACK, with weights in a separate file.
2. Loading and running models with weights in a separate file.
3. Runtime weight sharing via XNNPACK.

## Size savings.

Size results will vary depending on the model, quantization and LoRA config. For this demo, we save ~5GB of disk space by storing weights in a separate, sharable file and ~5GB runtime memory by sharing weights at runtime through the XNNPACK weight cache. Detailed results are below.

### XNNPACK weight sharing.

The XNNPACK backend is a singleton. Weight sharing is implemented via the XNNPACK weight cache. At delegate init time, XNNPACK checks the weight cache for the weights it needs. If they don't exist, XNNPACK will fetch weights from the NamedDataMap (the API that exposes weights in a PTD file), pack them, store them in the weight cache and free the original. This means we won't keep around multiple copies of the same weights.

## Virtual environment setup.
Create and activate a Python virtual environment:
```bash
python3 -m venv .venv && source .venv/bin/activate && pip install --upgrade pip
```
Or alternatively, [install conda on your machine](https://conda.io/projects/conda/en/latest/user-guide/install/index.html)
```bash
conda create -yn executorch-ptd python=3.10.0 && conda activate executorch-ptd
```

Install dependencies:
LoRA isn't available in the 0.7.0 release of ExecuTorch. Instead, please install from source until ExecuTorch 1.0 is released.

[Install ExecuTorch pip package from source](https://docs.pytorch.org/executorch/stable/using-executorch-building-from-source.html#install-executorch-pip-package-from-source).

Currently, the LoRA changes aren't in nightlies. Once they are in, you can also install from the nightly build.
```
pip install executorch==0.8.0.devYYYYMMDD --extra-index-url https://download.pytorch.org/whl/nightly/cpu
```

## Export the model/s.
Change into the program-data-separation directory and create a directory to hold exported artifacts.
```bash
cd ~/executorch-examples/program-data-separation
mkdir models
```

Export models into the `models` directory. The first command will generated undelegated model/data files, and the second will generate XNNPACK-delegated model/data files.
```bash
sh export_lora.sh
```
Expect the files:
- llama_3_2_1B.pte
- llama_3_2_1B.ptd
- llama_3_2_1B_lora.pte
- foundation_weights.ptd
- tokenizer.model

llama_3_2_1B.ptd and foundation_weights.ptd contain the same contents, and you can remove llama_3_2_1B.ptd.
tokenizer.model is copied from the temp directory where we downloaded the HF artifacts. It will be used at runtime.

Note:
- PTE: contains the program execution logic.
- PTD: contains the constant tensors used by the PTE. This format is similar to safetensors, but relying on flatbuffer instead of json for serde.

Sample file sizes:
```
-rw-r--r-- 1 lfq users 4943000480 Aug 11 15:55 foundation.ptd
-rw-r--r-- 1 lfq users 1078636416 Aug 11 15:55 llama_3_2_1B_lora.pte
-rw-r--r-- 1 lfq users 1051324736 Aug 11 15:53 llama_3_2_1B.pte
```

Notice the lora - llama file size difference is about 27.3MB. This will change depending on the LoRA config. This demo is using the config from https://huggingface.co/lucylq/llama3_1B_lora/blob/main/adapter_config.json
```
{"r": 64, "lora_alpha": 128, "target_modules": ["q_proj", "v_proj", "o_proj"], "peft_type": "LORA", "base_model_name_or_path": "meta-llama/Llama-3.2-1B-Instruct"}
```

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

./build/bin/executorch_program_data_separation --lora_model_path=../../llama_3_2_1B_lora.pte --llama_model_path=../../llama_3_2_1B.pte --tokenizer_path=../../tokenizer.model --foundation_weights_path=../../foundation.ptd
```

You should see some logs showing the Resident Set Size (RSS) at various points of the execution. Some sample logs may look like this:

```
Generating with llama...
RSS after loading model: 7886.125000 MiB
RSS after prompt prefill: 7886.125000 MiB
RSS after finishing text generation: 7886.125000 MiB

Generating with lora...
RSS after loading model: 7933.523438 MiB
RSS after prompt prefill: 7933.523438 MiB
RSS after finishing text generation: 7933.523438 MiB
```
Notice the memory increase of ~47 MiB from running llama model to running lora model. You can see the difference without weight-sharing by removing the flag `-DEXECUTORCH_XNNPACK_ENABLE_WEIGHT_CACHE=True` from `build_example.sh`.

## Clean up.
```bash
rm -rf build
cd ~/executorch-examples/program-data-separation
rm -rf *.pte *.ptd tokenizer.model
```
