# YOLO Object Detection with ExecuTorch

This example demonstrates YOLO object detection inference using ExecuTorch with the XNNPACK backend.

## Prerequisites

- Python 3.10+
- ExecuTorch runtime
- Ultralytics library

## Installation

```bash
# Install dependencies
pip install ultralytics opencv-python pillow

# Install ExecuTorch (if not already installed)
pip install executorch
```

## Download Model

Download the YOLO26m ExecuTorch model from Hugging Face:

```bash
hf download larryliu0820/yolo26m-ExecuTorch-XNNPACK \
    --local-dir models/yolo26m-ExecuTorch-XNNPACK
```

## Usage

### Basic Usage

```bash
python yolo_test.py
```

### With Custom Options

```bash
# Specify image, model, and confidence threshold
python yolo_test.py --image bus.jpg --conf 0.5

# Use a different model
python yolo_test.py --model path/to/model.pte --image my_image.jpg

# Full options
python yolo_test.py \
    --image example.jpg \
    --model models/yolo26m-ExecuTorch-XNNPACK/yolo26m_xnnpack.pte \
    --output result.jpg \
    --conf 0.25
```

### Command Line Arguments

| Argument | Default | Description |
|----------|---------|-------------|
| `--image` | `example.jpg` | Input image path |
| `--model` | `models/yolo26m-ExecuTorch-XNNPACK/yolo26m_xnnpack.pte` | ExecuTorch model path |
| `--output` | `output.jpg` | Output image path |
| `--conf` | `0.25` | Confidence threshold (0-1) |

## Expected Output

```
Model: models/yolo26m-ExecuTorch-XNNPACK/yolo26m_xnnpack.pte
Image: bus.jpg
Confidence threshold: 0.25

Output shape: torch.Size([1, 300, 6])
Confidence range: [0.0010, 0.9280]

Detections: 5
  1. person: 0.928 @ (589, 267) 260x267
  2. person: 0.876 @ (53, 212) 106x374
  3. bus: 0.831 @ (653, 212) 133x341
  4. person: 0.782 @ (257, 171) 110x215
  5. person: 0.732 @ (374, 257) 206x286

Saved: output.jpg
```

## Model Output Format

The YOLO26 end-to-end model outputs a tensor of shape `[1, 300, 6]`:

| Index | Field | Description |
|-------|-------|-------------|
| 0 | x_center | Box center X coordinate (pixels) |
| 1 | y_center | Box center Y coordinate (pixels) |
| 2 | width | Box width (pixels) |
| 3 | height | Box height (pixels) |
| 4 | confidence | Detection confidence (0-1) |
| 5 | class_id | COCO class ID (0-79) |

## COCO Classes

The model detects 80 COCO classes:

```
0: person, 1: bicycle, 2: car, 3: motorcycle, 4: airplane,
5: bus, 6: train, 7: truck, 8: boat, 9: traffic light,
10: fire hydrant, 11: stop sign, 12: parking meter, 13: bench,
14: bird, 15: cat, 16: dog, 17: horse, 18: sheep, 19: cow,
...
```

## Troubleshooting

### Low confidence scores

If all detections have very low confidence (< 25%), the model file may be corrupted or incorrectly exported. Try re-downloading the model:

```bash
rm -rf models/yolo26m-ExecuTorch-XNNPACK
huggingface-cli download larryliu0820/yolo26m-ExecuTorch-XNNPACK \
    --local-dir models/yolo26m-ExecuTorch-XNNPACK
```

### Import errors

Ensure all dependencies are installed:

```bash
pip install ultralytics opencv-python pillow torch executorch
```

### Model not found

Verify the model path exists:

```bash
ls -la models/yolo26m-ExecuTorch-XNNPACK/
```

## References

- [Ultralytics YOLO](https://github.com/ultralytics/ultralytics)
- [ExecuTorch](https://github.com/pytorch/executorch)
- [XNNPACK Backend](https://github.com/google/XNNPACK)
