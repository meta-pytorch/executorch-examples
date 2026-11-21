# MathPal — 8th Grade Math Tutor

An on-device AI math tutor for Android, powered by ExecuTorch. Runs entirely on the phone — no internet, no accounts, no data collection.

## Features

- **Ask Anything** — type or speak any math problem, get step-by-step solutions
- **Practice Mode** — 40 grade-leveled problems (Grade 4–8) across 10 categories
- **On-Device AI** — model runs locally via ExecuTorch + XNNPACK, works offline
- **Lazy KV Cache** — uses `DYNAMIC_UNBOUND` allocation to defer ~500MB of KV cache memory until first inference ([PR #18350](https://github.com/pytorch/executorch/pull/18350))
- **Voice Input** — tap the mic icon to speak your math problem
- **Answer Validation** — parses `####` and `\boxed{}` answer formats automatically

## Supported Models

| Model | Params | Quantization | .pte Size | GSM8K Accuracy | Speed (S23) | Recommended |
|-------|--------|-------------|-----------|---------------|-------------|-------------|
| Qwen2.5-Math-1.5B-Instruct | 1.5B | 8da4w | 1.6 GB | **83%** | 28–37 tok/s | Best accuracy |
| Qwen3-0.6B (GSM8K fine-tuned) | 0.6B | fp16 | 1.4 GB | 47% | 10–13 tok/s | Good for prototyping |
| Qwen3-1.7B (GSM8K fine-tuned) | 1.7B | fp16 | 3.8 GB | ~60% | 8–12 tok/s | Needs 12GB+ RAM phone |
| Any Qwen2.5/Qwen3 model | — | — | — | — | — | Bring your own |

### Accuracy by Category (Qwen2.5-Math-1.5B-Instruct)

| Category | Accuracy | Example Problem |
|----------|----------|-----------------|
| Arithmetic | ~95% | "What is 47 x 86?" |
| Fractions and Decimals | ~90% | "What is 2/3 + 3/4?" |
| Percentages | ~88% | "A $80 shirt is 25% off. Final price?" |
| Ratios and Proportions | ~85% | "Ratio 3:5, if 15 cats how many dogs?" |
| Linear Equations | ~82% | "Solve 3x - 7 = 14" |
| Geometry (area/volume) | ~80% | "Cylinder r=5, h=12, volume?" |
| Rate/Speed/Work | ~75% | "Train A at 60mph, Train B at 80mph..." |
| Probability | ~70% | "Draw 2 cards, P(both aces)?" |
| Multi-step Word Problems | ~78% | "Buy-2-get-1-free + 10% coupon..." |
| Combinatorics | ~55% | "Arrangements of MISSISSIPPI?" |

## Quick Start

### Option 1: Use Pre-trained Qwen2.5-Math (Recommended)

```bash
# 1. Download model from HuggingFace
pip install huggingface_hub
python -c "
from huggingface_hub import snapshot_download
snapshot_download('Qwen/Qwen2.5-Math-1.5B-Instruct', local_dir='./qwen2.5-math-1.5b-hf')
"

# 2. Convert weights to Meta/Llama format
python -m executorch.examples.models.qwen2_5.convert_weights \
  ./qwen2.5-math-1.5b-hf \
  ./qwen2.5-math-1.5b-meta.pth

# 3. Export to .pte with XNNPACK + 8da4w quantization + lazy KV cache
python -m executorch.examples.models.llama.export_llama \
  --model qwen2_5_1_5b \
  -c ./qwen2.5-math-1.5b-meta.pth \
  -p examples/models/qwen2_5/config/1_5b_config.json \
  --max_context_length 4096 \
  -kv --use_sdpa_with_kv_cache \
  -X --xnnpack-extended-ops \
  --pt2e_quantize xnnpack_dynamic_qc4 \
  --lazy_kv_cache \
  --metadata '{"get_bos_id":151643, "get_eos_ids":[151645,151643]}' \
  -o ./

# 4. Push to phone
adb shell mkdir -p /data/local/tmp/llama
adb push qwen2_5_1_5b_h.pte /data/local/tmp/llama/model.pte
adb push ./qwen2.5-math-1.5b-hf/tokenizer.json /data/local/tmp/llama/tokenizer.json
```

### Option 2: Use Qwen3-0.6B (Smaller, faster export)

```bash
# 1. Download
python -c "
from huggingface_hub import snapshot_download
snapshot_download('Qwen/Qwen3-0.6B', local_dir='./qwen3-0.6b-hf')
"

# 2. Convert
python -m executorch.examples.models.qwen3.convert_weights \
  ./qwen3-0.6b-hf \
  ./qwen3-0.6b-meta.bin

# 3. Export (fp16, no quantization)
python -m executorch.examples.models.llama.export_llama \
  --model qwen3_0_6b \
  -c ./qwen3-0.6b-meta.bin \
  -p examples/models/qwen3/config/0_6b_config.json \
  --max_context_length 4096 \
  -kv --use_sdpa_with_kv_cache \
  -X --dtype fp16 \
  --lazy_kv_cache \
  --metadata '{"get_bos_id":199999, "get_eos_ids":[200020,199999]}' \
  -o ./

# 4. Push to phone
adb push qwen3_0_6b_h.pte /data/local/tmp/llama/model.pte
adb push ./qwen3-0.6b-hf/tokenizer.json /data/local/tmp/llama/tokenizer.json
```

### Option 3: Fine-tune Your Own Math Model

You can fine-tune any small model on math datasets for better accuracy:

```bash
# Fine-tune Qwen3-0.6B on GSM8K (requires GPU, ~10 min on A100)
pip install transformers datasets trl

python -c "
from datasets import load_dataset
from transformers import AutoModelForCausalLM, AutoTokenizer
from trl import SFTTrainer, SFTConfig

model = AutoModelForCausalLM.from_pretrained('Qwen/Qwen3-0.6B', torch_dtype='bfloat16')
tokenizer = AutoTokenizer.from_pretrained('Qwen/Qwen3-0.6B')
dataset = load_dataset('openai/gsm8k', 'main', split='train')

def format_gsm8k(example):
    return {'text': '<|im_start|>user\n' + example['question'] + '<|im_end|>\n<|im_start|>assistant\n' + example['answer'] + '<|im_end|>'}

dataset = dataset.map(format_gsm8k)

trainer = SFTTrainer(
    model=model,
    train_dataset=dataset,
    args=SFTConfig(output_dir='./gsm8k-finetuned', num_train_epochs=3, per_device_train_batch_size=8, learning_rate=2e-5, bf16=True),
    dataset_text_field='text',
)
trainer.train()
model.save_pretrained('./gsm8k-finetuned')
tokenizer.save_pretrained('./gsm8k-finetuned')
"

# Then convert + export using the same steps as Option 2,
# but point -c to ./gsm8k-finetuned instead
```

Other math datasets to consider: `hendrycks/competition_math` (MATH), `deepmind/math_dataset`, `microsoft/orca-math-word-problems`.

### Option 4: Bring Any Custom Model

Any Qwen2.5 or Qwen3 model works. The app expects:
- `.pte` file at `/data/local/tmp/llama/model.pte`
- `tokenizer.json` at `/data/local/tmp/llama/tokenizer.json`
- Model that responds to Qwen chat template (`<|im_start|>user\n...<|im_end|>\n<|im_start|>assistant\n`)

## Build the Android App

### Prerequisites

- Android SDK (API 28+)
- Android NDK 29+
- ExecuTorch AAR (pre-built or build from source)

### Using Pre-built AAR

```bash
cd llm/android/MathPal
ANDROID_HOME=/path/to/android/sdk ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Using Local AAR (with Lazy KV Cache)

To use `--lazy_kv_cache`, build a custom AAR with `EXECUTORCH_ENABLE_DYNAMIC_ALLOCATOR=ON`:

```bash
# 1. Build native libraries
cd /path/to/executorch
cmake . -DCMAKE_INSTALL_PREFIX=cmake-out-android-arm64-v8a \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  --preset android-arm64-v8a \
  -DANDROID_PLATFORM=android-26 \
  -DEXECUTORCH_BUILD_EXTENSION_LLM=ON \
  -DEXECUTORCH_BUILD_LLAMA_JNI=ON \
  -DEXECUTORCH_ENABLE_DYNAMIC_ALLOCATOR=ON \
  -DCMAKE_BUILD_TYPE=Release \
  -Bcmake-out-android-arm64-v8a
cmake --build cmake-out-android-arm64-v8a -j$(nproc) --target install

# 2. Stage .so
mkdir -p cmake-out-android-so/arm64-v8a
cp cmake-out-android-arm64-v8a/extension/android/*.so cmake-out-android-so/arm64-v8a/libexecutorch.so
$ANDROID_NDK/toolchains/llvm/prebuilt/*/bin/llvm-strip cmake-out-android-so/arm64-v8a/libexecutorch.so

# 3. Build AAR
cd extension/android
ANDROID_HOME=/path/to/android/sdk ./gradlew :executorch_android:assembleDebug

# 4. Build MathPal with local AAR
mkdir -p /path/to/MathPal/app/libs
cp executorch_android/build/outputs/aar/executorch_android-debug.aar /path/to/MathPal/app/libs/executorch.aar
cd /path/to/MathPal
ANDROID_HOME=/path/to/android/sdk ./gradlew assembleDebug -PuseLocalAar=true
```

## Lazy KV Cache (DYNAMIC_UNBOUND)

MathPal leverages `--lazy_kv_cache` which marks KV cache buffers as `DYNAMIC_UNBOUND`. The KV cache is not allocated until the first inference call:

| Phase | Without lazy KV cache | With lazy KV cache |
|-------|----------------------|-------------------|
| Model load | ~2150 MiB | ~100 MiB |
| First inference | ~2150 MiB | ~1730 MiB |
| 10+ turns | ~2150 MiB | ~1730 MiB (stable) |

KV cache cost by context length (Qwen3-0.6B, fp16):

| max_context_length | KV Cache Size | At load without PR | At load with PR |
|---|---|---|---|
| 128 (default) | 14 MB | 14 MB pre-allocated | 0 MB |
| 1024 | 115 MB | 115 MB pre-allocated | 0 MB |
| 2048 (standard) | 229 MB | 229 MB pre-allocated | 0 MB |
| 4096 (our test) | 459 MB | 459 MB pre-allocated | 0 MB |
| 16384 | 1.8 GB | OOM at load | 0 MB |

Requires AAR built with `-DEXECUTORCH_ENABLE_DYNAMIC_ALLOCATOR=ON`.

## App Architecture

```
com.mathpal.app/
├── MathPalActivity.kt          # Single activity, Compose navigation
├── MathPalApplication.kt       # Lifecycle, memory management
├── data/
│   ├── model/                  # Problem, Badge, BossBattle data classes
│   ├── db/                     # SQLite (XP, streaks, progress)
│   └── repository/             # MathRepository
├── inference/
│   ├── InferenceEngine.kt      # ExecuTorch LlmModule wrapper
│   ├── PromptFormatter.kt      # Qwen chat template
│   ├── AnswerValidator.kt      # #### and \boxed{} parsing
│   └── StepParser.kt           # Token stream to step cards
├── ui/
│   ├── home/                   # Ask Anything + daily challenge
│   ├── solve/                  # Reasoning card + answer (MathViewModel)
│   ├── practice/               # Grade 4-8 problem bank
│   ├── progress/               # Stats and topic mastery
│   ├── components/             # StepCard, StreakBadge
│   └── theme/                  # Material3 theme
└── gamification/
    ├── XPManager.kt            # 50 levels, 6 tiers
    ├── StreakManager.kt         # Daily streaks with grace periods
    ├── BossManager.kt          # 8 math boss battles
    ├── BadgeManager.kt         # 30 achievements
    └── ProblemBank.kt          # 40 problems (Grade 4-8)
```

## Tested Hardware

| Device | RAM | Model | Result |
|--------|-----|-------|--------|
| Samsung Galaxy S23 | 8 GB | Qwen2.5-Math-1.5B (8da4w) | 10+ turns, 28 tok/s |
| Samsung Galaxy S23 | 8 GB | Qwen3-0.6B (fp16) | 10+ turns, 12 tok/s |
| Samsung Galaxy S23 | 8 GB | Qwen3-1.7B (fp16) + lazy KV | Works with lazy allocation |

## License

BSD-style license. See LICENSE file in the root directory.
