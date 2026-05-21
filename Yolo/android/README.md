# Overview

## 🐦 Android Bird Detection App with ExecuTorch

A real-time bird detection and species identification Android app using YOLO + EfficientNet models deployed via Meta's ExecuTorch framework. The app provides session-based bird watching with automatic logging, thumbnails, and timestamps for backyard bird enthusiasts.

**Bird Detection Feature:** Uses a two-stage pipeline where YOLO (COCO class 14) detects birds in camera frames, then EfficientNet classifier identifies the specific species from 525 possible bird types with 96.8% accuracy.

## Prerequisites

Install PyTorch and ExecuTorch by following the [ExecuTorch installation guide](https://docs.pytorch.org/executorch/main/getting-started.html), which pins the compatible torch version. The simplest path is:

```bash
pip install executorch
```

Then install the remaining dependencies:

```bash
pip install transformers ultralytics
```

For Android-specific setup, see the [Android section](https://docs.pytorch.org/executorch/main/android-section.html) of the ExecuTorch docs.

## Model Download and Conversion

### Step 1: Download and Convert Bird Classifier Model

Create `convert_bird_classifier.py`:

```python
import torch                                                                                                                                  
from transformers import AutoModelForImageClassification  
from torch.export import export                                                                                                               
from executorch.exir import to_edge_transform_and_lower   
from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner

# Download and load model
model = AutoModelForImageClassification.from_pretrained("chriamue/bird-species-classifier")
model.eval()

# Export to ExecuTorch
example_input = torch.randn(1, 3, 224, 224)
exported_program = export(model, (example_input,))
edge_program = to_edge_transform_and_lower(
    exported_program,
    partitioner=[XnnpackPartitioner()]
)
et_program = edge_program.to_executorch()

# Save as .pte file
with open("bird_classifier.pte", "wb") as f:
    et_program.write_to_file(f)
print("Bird classifier converted to bird_classifier.pte")
```

Run the script:

```bash
python convert_bird_classifier.py
```

### Step 2: Convert YOLO Model to .pte Format

Create `convert_yolo.py`:

```python
from ultralytics import YOLO
import torch
from torch.export import export
from executorch.exir import to_edge_transform_and_lower
from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner

# Load YOLO model
# Use 'yolov8n.pt' for YOLOv8 OR 'yolo26n.pt' for YOLOv26
yolo = YOLO('yolo26n.pt')  # Recommended: YOLOv26 for better performance
pytorch_model = yolo.model
pytorch_model.eval()

# Export to ExecuTorch
example_input = torch.randn(1, 3, 640, 640)
exported_program = export(pytorch_model, (example_input,))
edge_program = to_edge_transform_and_lower(
    exported_program,
    partitioner=[XnnpackPartitioner()]
)
et_program = edge_program.to_executorch()

# Save as .pte file
with open("yolo_detector.pte", "wb") as f:
    et_program.write_to_file(f)
print("YOLO model converted to yolo_detector.pte")
```

**Auto-Detection:** The app automatically detects which YOLO version you're using (v8 or v26) based on the model's output format. No code changes needed when switching between versions!

### Step 3: Generate Bird Species Names

Create `extract_species_names.py`:

```python
from transformers import AutoModelForImageClassification
import json

model = AutoModelForImageClassification.from_pretrained('chriamue/bird_classifier_model')
species_names = [model.config.id2label[i] for i in range(len(model.config.id2label))]

with open('bird_species.json', 'w') as f:
    json.dump(species_names, f, indent=2)

print(f"Saved {len(species_names)} bird species names to bird_species.json")
```

## Deploying Models to Android

### Step 4: Deploy Models to Android Device

#### Copy Models to Android Assets

```
Copy .pte files to Android assets directory
cp bird_classifier.pte /path/to/android/app/src/main/assets/
cp yolo_detector.pte /path/to/android/app/src/main/assets/
cp bird_species.json /path/to/android/app/src/main/assets/
```

#### Alternative: Push via ADB (for testing)

```bash
# Connect Android device and enable USB debugging
adb devices

# Create directory on device
adb shell mkdir -p /data/local/tmp/bird_detection/

# Push model files
adb push bird_classifier.pte /data/local/tmp/bird_detection/
adb push yolo_detector.pte /data/local/tmp/bird_detection/
adb push bird_species.json /data/local/tmp/bird_detection/

# Verify files are transferred
adb shell ls -la /data/local/tmp/bird_detection/
```

### File Structure

```
app/src/main/assets/
├── bird_classifier.pte    # EfficientNet bird species classifier (~8.5MB)
├── yolo_detector.pte      # YOLO bird detection model (~6MB)
└── bird_species.json      # List of 525 bird species names
```

## App Features

### Main Detection Screen

- **Camera Preview:** Real-time video feed from device camera
- **Live Detection:** Green bounding boxes around detected birds with species labels
- **Session Controls:** Start/Stop buttons for bird watching sessions

### Session Management

**Start Session Button:**
- Activates bird detection and logging
- Changes camera from passive viewing to active detection mode
- Begins collecting bird sightings with timestamps and thumbnails
- Button turns red and displays "Stop Session"

**Stop Session Button:**
- Deactivates detection and shows session summary
- Displays total birds detected and unique species count
- Button turns green and displays "Start Session"
- Preserves collected data for viewing

### Bird Log Viewer

**View Logs Button:**
- Opens detailed session log showing all detected birds
- Displays bird thumbnails, species names, detection times, and confidence scores
- Organized as scrollable list with visual bird identification records
- Useful for reviewing and verifying bird watching session results
