# Program Data Separation Examples

This directory provides an example of the Program Data Separation APIs in ExecuTorch. Specifically, it showcases:
1. Program data separation examples using a linear model with the portable operators and XNNPACK.
2. LoRA inference example with a LoRA and non-LoRA model sharing foundation weights.

## Program Data Separation

The program-data separation APIs allow users to generate a separate data file when exporting and lowering a model. i.e., generate a PTE file containing the model execution program, and one (or more) [PTD](https://github.com/pytorch/executorch/blob/main/extension/flat_tensor/README.md) file/s containing only weights.

PTD files are used to store data outside of the PTE file. Some use-cases:
- On-device training: checkpointing for model weights.
- Deduplication: sharing model weights between multiple executable PTE files. This can significantly reduce binary file size and runtime memory usage.
- Flexible deployment: allow async updates between program and data, especially if they are updated with different cadences.

For more information on the PTD data format, please see the [flat_tensor](https://github.com/pytorch/executorch/blob/main/extension/flat_tensor/README.md) directory.

## Linear example
For a demo of the program-data separation APIs using a linear model, please see [program-data-separation/cpp/linear_example](linear_example/). This example generates and runs a program-data separated linear model, with weights and bias in a separate .ptd file.

## LoRA example
A major use-case that program-data separation enables is inference with multiple LoRA adapters. LoRA is a fine-tuning technique introduced in [LoRA: Low-Rank Adaptation of Large Language Models](https://arxiv.org/abs/2106.09685). LoRA fine-tuning produces lightweight 'adapter' weights that can be applied to an existing model to adapt it to a new task. LoRA adapters are typically small in comparison to LLM foundation weights, on the order of KB-MB depending on the finetuning setup and model size.

To enable LoRA, we generate:
- PTE file/s: containing program and LoRA adapter weights.
- PTD file: containing foundation weights.

Multiple LoRA-adapted PTE files can share the same foundation weights and adding a model adapted to a new task incurs minimal binary size and runtime memory overhead.

Please take a look at [program-data-separation/cpp/lora_example](lora_example/) for a demo of the program-data separation APIs with LoRA. This example generates and runs a LoRA and a non-LoRA model that share foundation weights. At runtime, we see that memory usage does not double.

### Requirements
LoRA is currently supported on executorch main. [Please install ExecuTorch pip package from source](https://docs.pytorch.org/executorch/stable/using-executorch-building-from-source.html#install-executorch-pip-package-from-source), until executorch==1.0 is released.
