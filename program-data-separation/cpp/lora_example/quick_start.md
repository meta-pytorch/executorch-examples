# Quick Start

Use the provided export scripts to generate and run LoRA models.

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
Please install executorch. If you are using your own trained adapter (not the example one), please use a recent nightly build or install from source.

```
pip install executorch==1.0.0
```

You can also install from the nightly build.
```
pip install executorch==1.1.0.devYYYYMMDD --extra-index-url https://download.pytorch.org/whl/nightly/cpu
```

Or [install from source](https://docs.pytorch.org/executorch/stable/using-executorch-building-from-source.html#install-executorch-pip-package-from-source).


## Export the model/s.
Change into the program-data-separation directory and create a directory to hold exported artifacts.
```bash
cd ~/executorch-examples/program-data-separation
mkdir models
```

Export models into the `models` directory.
- The first command generates a regular llama_3_2_1B model.
- The second command generates a llama_3_2_1B lora model.

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
tokenizer.model is copied from the temp directory where we downloaded the HF artifacts. It is used at runtime.

Note:
- PTE: contains the program execution logic.
- PTD: contains the constant tensors used by the PTE. This format is similar to safetensors. It relies on flatbuffers instead of json for serde.

Sample file sizes:
```
-rw-r--r-- 1 lfq users 5994013600 Oct 17 14:31 foundation.ptd
-rw-r--r-- 1 lfq users   27628928 Oct 17 14:31 llama_3_2_1B_lora.pte
-rw-r--r-- 1 lfq users     317248 Oct 17 14:28 llama_3_2_1B.pte
```

Notice the lora - llama file size difference is about 27.3MB. This is the size of the adapter weights, and changes depending on the LoRA config. This demo is using the config from https://huggingface.co/lucylq/llama3_1B_lora/blob/main/adapter_config.json.
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

./build/bin/executorch_program_data_separation \
    --tokenizer_path="../../tokenizer.model" \
    --model1="../../models/llama_3_2_1B_lora.pte" \
    --model2="../../models/llama_3_2_1B.pte"  \
    --weights="../../models/foundation.ptd"
```

You should see some logs showing the Resident Set Size (RSS) at various points of the execution. Some sample logs may look like this:

```
Generating with model <model file path>
RSS after loading model: 6909.328125 MiB
RSS after prompt prefill: 6909.328125 MiB
RSS after finishing text generation: 6909.328125 MiB

Generating with model <model file path>...
RSS after loading model: 7941.667969 MiB
RSS after prompt prefill: 7941.667969 MiB
RSS after finishing text generation: 7941.667969 MiB
```
There is about ~1.4GB memory increase between running the two models.
~1GB comes from embeddings that are not lowered to XNNPACK (and currently are not shared). This can be alleviated by quantizing the embeddings by adding the config `quantization.embedding_quantize=\'4,32\'` to the export command.
~40MB comes from running the non-lora model, to running the lora model.

You can see the difference without weight-sharing by removing the flag `-DEXECUTORCH_XNNPACK_ENABLE_WEIGHT_CACHE=True` from `build_example.sh`. Expect to see almost double the memory usage, ie. ~14-15GB instead of ~8GB.

## Fine-tuned adapter output.


## Clean up.
```bash
rm -rf build
cd ~/executorch-examples/program-data-separation
rm -rf models/
```
