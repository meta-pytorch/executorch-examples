"""
YOLO Object Detection with ExecuTorch Runtime

Demonstrates YOLO inference using ExecuTorch with XNNPACK backend.
Uses Ultralytics library for image preprocessing.

Usage:
    python yolo_test.py [--image PATH] [--model PATH] [--conf THRESHOLD]
"""

import argparse
from pathlib import Path

import cv2
import numpy as np
import torch
from executorch.runtime import Runtime
from PIL import Image, ImageDraw
from ultralytics.data.augment import LetterBox


# COCO class names (80 classes)
COCO_CLASSES = [
    "person",
    "bicycle",
    "car",
    "motorcycle",
    "airplane",
    "bus",
    "train",
    "truck",
    "boat",
    "traffic light",
    "fire hydrant",
    "stop sign",
    "parking meter",
    "bench",
    "bird",
    "cat",
    "dog",
    "horse",
    "sheep",
    "cow",
    "elephant",
    "bear",
    "zebra",
    "giraffe",
    "backpack",
    "umbrella",
    "handbag",
    "tie",
    "suitcase",
    "frisbee",
    "skis",
    "snowboard",
    "sports ball",
    "kite",
    "baseball bat",
    "baseball glove",
    "skateboard",
    "surfboard",
    "tennis racket",
    "bottle",
    "wine glass",
    "cup",
    "fork",
    "knife",
    "spoon",
    "bowl",
    "banana",
    "apple",
    "sandwich",
    "orange",
    "broccoli",
    "carrot",
    "hot dog",
    "pizza",
    "donut",
    "cake",
    "chair",
    "couch",
    "potted plant",
    "bed",
    "dining table",
    "toilet",
    "tv",
    "laptop",
    "mouse",
    "remote",
    "keyboard",
    "cell phone",
    "microwave",
    "oven",
    "toaster",
    "sink",
    "refrigerator",
    "book",
    "clock",
    "vase",
    "scissors",
    "teddy bear",
    "hair drier",
    "toothbrush",
]

# Random colors for visualization
np.random.seed(42)
CLASS_COLORS = [
    (int(r), int(g), int(b)) for r, g, b in np.random.randint(0, 255, size=(80, 3))
]


def preprocess(image_path: str, imgsz: int = 640):
    """
    Preprocess image using Ultralytics LetterBox transform.

    Returns:
        input_tensor: Preprocessed image tensor [1, 3, H, W]
        scale: Scale factor applied during resize
        padding: (pad_w, pad_h) padding applied
        orig_shape: Original image shape (height, width)
    """
    img = cv2.imread(str(image_path))
    orig_shape = img.shape[:2]  # (H, W)

    # Apply Ultralytics LetterBox
    letterbox = LetterBox(new_shape=(imgsz, imgsz), auto=False, stride=32)
    img_lb = letterbox(image=img)

    # Calculate transform parameters
    h, w = orig_shape
    scale = min(imgsz / h, imgsz / w)
    pad_h = (imgsz - int(h * scale)) // 2
    pad_w = (imgsz - int(w * scale)) // 2

    # Convert BGR->RGB, normalize, to tensor
    img_rgb = cv2.cvtColor(img_lb, cv2.COLOR_BGR2RGB)
    img_norm = img_rgb.astype(np.float32) / 255.0
    tensor = torch.from_numpy(img_norm).permute(2, 0, 1).contiguous().unsqueeze(0)

    return tensor, scale, (pad_w, pad_h), orig_shape


def postprocess(output, conf_thresh: float, scale: float, padding: tuple):
    """
    Post-process YOLO end-to-end output [batch, 300, 6].

    Output format: [x1, y1, x2, y2, confidence, class_id] (xyxy corner format)

    Returns:
        List of detections as dicts with keys: x1, y1, x2, y2, conf, cls
    """
    preds = output[0] if len(output.shape) == 3 else output
    pad_w, pad_h = padding

    # Filter by confidence
    mask = preds[:, 4] > conf_thresh

    detections = []
    for i in range(len(preds)):
        if mask[i]:
            # Convert from letterbox space to original image space
            x1 = (preds[i, 0].item() - pad_w) / scale
            y1 = (preds[i, 1].item() - pad_h) / scale
            x2 = (preds[i, 2].item() - pad_w) / scale
            y2 = (preds[i, 3].item() - pad_h) / scale

            detections.append(
                {
                    "x1": x1,
                    "y1": y1,
                    "x2": x2,
                    "y2": y2,
                    "conf": preds[i, 4].item(),
                    "cls": int(preds[i, 5].item()),
                }
            )

    return sorted(detections, key=lambda d: d["conf"], reverse=True)


def draw_boxes(image_path: str, detections: list, output_path: str):
    """Draw bounding boxes on image and save."""
    img = Image.open(image_path).convert("RGB")
    draw = ImageDraw.Draw(img)
    W, H = img.size

    for det in detections:
        x1, y1, x2, y2 = det["x1"], det["y1"], det["x2"], det["y2"]
        cls, conf = det["cls"], det["conf"]

        # Clip to image bounds
        x1, y1 = max(0, x1), max(0, y1)
        x2, y2 = min(W, x2), min(H, y2)

        if x2 <= x1 or y2 <= y1:
            continue

        color = CLASS_COLORS[cls % 80]
        label = f"{COCO_CLASSES[cls]}: {conf:.2f}"

        # Draw box
        draw.rectangle([x1, y1, x2, y2], outline=color, width=2)

        # Draw label
        bbox = draw.textbbox((0, 0), label)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        lx, ly = max(0, x1), max(0, y1 - th - 4)
        draw.rectangle([lx, ly, lx + tw + 4, ly + th + 4], fill=color)
        draw.text((lx + 2, ly + 2), label, fill=(255, 255, 255))

    img.save(output_path, quality=95)
    return len(detections)


def run_inference(
    model_path: str, image_path: str, output_path: str, conf_thresh: float = 0.25
):
    """Run YOLO inference with ExecuTorch."""

    # Load model
    runtime = Runtime.get()
    program = runtime.load_program(model_path)
    method = program.load_method("forward")

    # Preprocess
    input_tensor, scale, padding, orig_shape = preprocess(image_path)

    # Inference
    outputs = method.execute([input_tensor])

    # Post-process (use first output - end-to-end format [1, 300, 6])
    detections = postprocess(outputs[0], conf_thresh, scale, padding)

    # Draw and save
    num_drawn = draw_boxes(image_path, detections, output_path)

    return detections, outputs[0]


def main():
    parser = argparse.ArgumentParser(description="YOLO ExecuTorch Inference")
    parser.add_argument("--image", default="example.jpg", help="Input image path")
    parser.add_argument(
        "--model",
        default="models/yolo26m-ExecuTorch-XNNPACK/yolo26m_xnnpack.pte",
        help="ExecuTorch model path",
    )
    parser.add_argument("--output", default="output.jpg", help="Output image path")
    parser.add_argument("--conf", type=float, default=0.25, help="Confidence threshold")
    args = parser.parse_args()

    script_dir = Path(__file__).parent
    image_path = str(script_dir / args.image)
    model_path = str(script_dir / args.model)
    output_path = str(script_dir / args.output)

    print(f"Model: {model_path}")
    print(f"Image: {image_path}")
    print(f"Confidence threshold: {args.conf}")

    detections, raw_output = run_inference(
        model_path, image_path, output_path, args.conf
    )

    print(f"\nOutput shape: {raw_output.shape}")
    print(
        f"Confidence range: [{raw_output[0, :, 4].min():.4f}, {raw_output[0, :, 4].max():.4f}]"
    )
    print(f"\nDetections: {len(detections)}")

    for i, det in enumerate(detections[:10]):
        cls_name = COCO_CLASSES[det["cls"]] if det["cls"] < 80 else f"cls_{det['cls']}"
        w = det["x2"] - det["x1"]
        h = det["y2"] - det["y1"]
        cx = (det["x1"] + det["x2"]) / 2
        cy = (det["y1"] + det["y2"]) / 2
        print(
            f"  {i+1}. {cls_name}: {det['conf']:.3f} @ ({cx:.0f}, {cy:.0f}) {w:.0f}x{h:.0f}"
        )

    print(f"\nSaved: {output_path}")


if __name__ == "__main__":
    main()
