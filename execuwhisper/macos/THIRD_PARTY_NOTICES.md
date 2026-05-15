# Third-Party Notices

ExecuWhisper bundles, links against, or otherwise relies on the following third-party components. Each component retains its own copyright and license. This file does not modify those licenses; it serves as a one-page index pointing readers at the upstream sources.

If you redistribute a build of ExecuWhisper, please ensure your distribution carries the upstream license texts where required.

---

## NVIDIA Parakeet-TDT

- **Used for:** automatic speech recognition (ASR) in `parakeet_helper`.
- **Upstream:** <https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2>
- **License:** see the upstream model card. The runtime artifacts redistributed at <https://huggingface.co/younghan-meta/Parakeet-TDT-ExecuTorch-Metal> follow the same terms.
- **Notes:** ExecuWhisper does not modify the model. Re-export to `.pte` is described in the executorch ASR helper PR ([pytorch/executorch#18861](https://github.com/pytorch/executorch/pull/18861)).

## LiquidAI LFM2.5-350M

- **Used for:** the base model that the ExecuWhisper formatter is fine-tuned from.
- **Upstream:** <https://huggingface.co/LiquidAI/LFM2.5-350M>
- **License:** see the upstream model card (Liquid AI Open License or successor; the fine-tuned derivative inherits the upstream terms).
- **Notes:** the fine-tune lives at <https://huggingface.co/younghan-meta/LFM2.5-350M-ExecuWhisper-Formatter> with full eval reports and a re-export guide.

## PyTorch / ExecuTorch

- **Used for:** runtime, Metal backend, MLX delegate, MLX export pipeline.
- **Upstream:** <https://github.com/pytorch/executorch>
- **License:** [BSD-3-Clause](https://github.com/pytorch/executorch/blob/main/LICENSE).

## Apple MLX

- **Used for:** runtime acceleration of the LFM2.5 formatter via the ExecuTorch MLX delegate.
- **Upstream:** <https://github.com/ml-explore/mlx>
- **License:** [MIT](https://github.com/ml-explore/mlx/blob/main/LICENSE).

## LLVM OpenMP runtime (`libomp`)

- **Used for:** required at runtime by `parakeet_helper` and `lfm25_formatter_helper`.
- **Upstream:** <https://openmp.llvm.org/> (part of the [LLVM project](https://github.com/llvm/llvm-project)).
- **License:** [Apache-2.0 with LLVM exception](https://llvm.org/LICENSE.txt).
- **Distribution note:** ExecuWhisper does **not** redistribute `libomp.dylib`. Users must obtain it via `brew install libomp` or another official LLVM source. The post-compile build script copies it into the app bundle from `/opt/homebrew/opt/libomp/lib/` at build time so that the resulting `.app` is self-contained for the developer's machine.

## Unsloth

- **Used for:** the v3 formatter fine-tune was produced following the [Unsloth LFM2.5 fine-tuning tutorial](https://unsloth.ai/docs/models/tutorials/lfm2.5).
- **Upstream:** <https://github.com/unslothai/unsloth>
- **License:** [Apache-2.0](https://github.com/unslothai/unsloth/blob/main/LICENSE).

## AMI Meeting Corpus

- **Used for:** evaluation and a portion of the v3 formatter's training data (AMI dictation pairs).
- **Upstream:** <https://groups.inf.ed.ac.uk/ami/corpus/>
- **License:** [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/).
- **Citation:** Carletta, J. et al. _The AMI Meeting Corpus: A pre-announcement_. Machine Learning for Multimodal Interaction, 2005.

## Hugging Face Hub client (`huggingface_hub`)

- **Used for:** the optional `--download-models` flow in `scripts/build.sh`.
- **Upstream:** <https://github.com/huggingface/huggingface_hub>
- **License:** [Apache-2.0](https://github.com/huggingface/huggingface_hub/blob/main/LICENSE).

## xcodegen

- **Used for:** generating `ExecuWhisper.xcodeproj` from `project.yml`.
- **Upstream:** <https://github.com/yonaskolb/XcodeGen>
- **License:** [MIT](https://github.com/yonaskolb/XcodeGen/blob/master/LICENSE).

---

If you believe a component is missing from this notice, or that its attribution is incorrect, please open an issue on the [`meta-pytorch/executorch-examples`](https://github.com/meta-pytorch/executorch-examples) repo.
