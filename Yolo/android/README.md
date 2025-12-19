# Overview

## 🐦 Android Bird Detection App with ExecuTorch

A real-time bird detection and species identification Android app using YOLO + EfficientNet models deployed via Meta's ExecuTorch framework. The app provides session-based bird watching with automatic logging, thumbnails, and timestamps for backyard bird enthusiasts.
Bird Detection Feature: Uses a two-stage pipeline where YOLO (COCO class 14) detects birds in camera frames, then EfficientNet classifier identifies the specific species from 525 possible bird types with 96.8% accuracy.

## Model Download and Conversion

### Step 1: Download Models

#### Bird Classifier Model

Install dependencies

```
pip install transformers torch pillow executorch
Download EfficientNet bird classifier
python -c "
from transformers import AutoImageProcessor, AutoModelForImageClassification
import torch
model_name = 'dennisjooo/Birds-Classifier-EfficientNetB2'
processor = AutoImageProcessor.from_pretrained(model_name)
model = AutoModelForImageClassification.from_pretrained(model_name)
model.save_pretrained('./bird_classifier_model')
processor.save_pretrained('./bird_classifier_model')
print('Bird classifier downloaded')
"
```

### YOLO Detection Model

Install YOLOv8

```
pip install ultralytics
Download YOLO model
python -c "
from ultralytics import YOLO
model = YOLO('yolov8n.pt') # nano version for mobile
print('YOLO model downloaded')
"
```

## Model Conversion to ExecuTorch

### Step 2: Convert Models to .pte Format

#### Convert Bird Classifier

convert_bird_classifier.py

```
import torch
from transformers import AutoModelForImageClassification
from torch.export import export
from executorch.exir import to_edge_transform_and_lower
from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner
Load model
model = AutoModelForImageClassification.from_pretrained('./bird_classifier_model')
model.eval()
Export to ExecuTorch
example_input = torch.randn(1, 3, 224, 224)
exported_program = export(model, (example_input,))
edge_program = to_edge_transform_and_lower(
exported_program,
partitioner=[XnnpackPartitioner()]
)
et_program = edge_program.to_executorch()
Save as .pte file
with open("bird_classifier.pte", "wb") as f:
et_program.write_to_file(f)
print("Bird classifier converted to bird_classifier.pte")
```

### Convert YOLO Model

convert_yolo.py

```
from ultralytics import YOLO
import torch
from torch.export import export
from executorch.exir import to_edge_transform_and_lower
from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner
Load YOLO model
yolo = YOLO('yolov8n.pt')
pytorch_model = yolo.model
pytorch_model.eval()
Export to ExecuTorch
example_input = torch.randn(1, 3, 640, 640)
exported_program = export(pytorch_model, (example_input,))
edge_program = to_edge_transform_and_lower(
exported_program,
partitioner=[XnnpackPartitioner()]
)
et_program = edge_program.to_executorch()
Save as .pte file
with open("yolo_detector.pte", "wb") as f:
et_program.write_to_file(f)
print("YOLO model converted to yolo_detector.pte")
```

### Generate Bird Species Names

extract_species_names.py

```
from transformers import AutoModelForImageClassification
import json
model = AutoModelForImageClassification.from_pretrained('./bird_classifier_model')
species_names = [model.config.id2label[i] for i in range(len(model.config.id2label))]
with open('bird_species.json', 'w') as f:
json.dump(species_names, f, indent=2)
print(f"Saved {len(species_names)} bird species names to bird_species.json")
```

## Deploying Models to Android

###  Step 3: Deploy Models to Android Device

#### Copy Models to Android Assets

```
Copy .pte files to Android assets directory
cp bird_classifier.pte /path/to/android/app/src/main/assets/
cp yolo_detector.pte /path/to/android/app/src/main/assets/
cp bird_species.json /path/to/android/app/src/main/assets/
```

### Alternative: Push via ADB (for testing)

Connect Android device and enable USB debugging

```
adb devices
Create directory on device
adb shell mkdir -p /data/local/tmp/bird_detection/
Push model files
adb push bird_classifier.pte /data/local/tmp/bird_detection/
adb push yolo_detector.pte /data/local/tmp/bird_detection/
adb push bird_species.json /data/local/tmp/bird_detection/
Verify files are transferred
adb shell ls -la /data/local/tmp/bird_detection/
```

### File Structure

```
app/src/main/assets/
├── bird_classifier.pte # EfficientNet bird species classifier (8.5MB)
├── yolo_detector.pte # YOLOv8n bird detection model (6MB)
└── bird_species.json # List of 525 bird species names
```

## App Features

### Main Detection Screen

- Camera Preview: Real-time video feed from device camera
- Live Detection: Green bounding boxes around detected birds with species labels
- Session Controls: Start/Stop buttons for bird watching sessions

### Session Management

#### "Start Session" Button:

- Activates bird detection and logging
- Changes camera from passive viewing to active detection mode
- Begins collecting bird sightings with timestamps and thumbnails
- Button turns red and displays "Stop Session"
- "Stop Session" Button:
- Deactivates detection and shows session summary
- Displays total birds detected and unique species count
- Button turns green and displays "Start Session"
- Preserves collected data for viewing

### Bird Log Viewer

#### "View Logs" Button:

- Opens detailed session log showing all detected birds
- Displays bird thumbnails, species names, detection times, and confidence scores
- Organized as scrollable list with visual bird identification records
- Useful for reviewing and verifying bird watching session results
