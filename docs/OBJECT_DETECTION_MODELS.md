# Object Detection Models for Residential Inspection

## Overview

This document recommends object detection models for the Glass AR residential inspection system. The RGB camera streams images to a remote server that performs real-time object detection and sends annotations back to Glass for AR overlay.

## Use Case: Residential Inspection

Typical residential inspection scenarios:

### Interior Inspection
- **Electrical Systems**: Outlets, wiring, circuit breakers, electrical panels
- **Plumbing**: Pipes, leaks, fixtures, water damage
- **HVAC**: Vents, ducts, thermostats, air handlers
- **Structural**: Cracks, damage, foundation issues
- **Safety Hazards**: Smoke detectors, carbon monoxide detectors, fire hazards
- **Moisture/Mold**: Water damage, mold growth, dampness
- **Insulation**: Insulation gaps, thermal bridges (combined with Boson thermal data)
- **General Defects**: Damage, wear, maintenance issues

### Outdoor/Yard Inspection
- **Exterior Walls**: Siding damage, cracks, water intrusion, paint peeling
- **Roof**: Shingles, flashing, gutters, chimneys, vents
- **Drainage**: Gutters, downspouts, grading issues, erosion
- **Hardscapes**: Driveways, sidewalks, patios, retaining walls (cracks, settling)
- **Fencing**: Gates, posts, damage, rot, structural integrity
- **Outdoor Electrical**: Exterior outlets, lighting fixtures, meter boxes
- **Landscape**: Tree proximity to structures, overgrown vegetation
- **Water Features**: Pools, spas, fountains (equipment, cracks, leaks)
- **Irrigation**: Sprinkler heads, valves, controller boxes
- **Foundation**: Visible foundation cracks, drainage issues

---

## Recommended Models (2025)

### 1. YOLOv12 (Recommended)

**Best for**: Real-time residential inspection with high accuracy

**Specifications:**
- **Released**: February 2025
- **Architecture**: Attention-centric with R-ELAN backbone
- **Performance**: YOLOv12-N achieves 40.6% mAP at 1.64ms latency (T4 GPU)
- **Larger variants**: YOLOv12-M achieves ~68.9% mAP for higher accuracy
- **Speed**: 30-100+ FPS depending on model size (n/s/m/l/x)

**Advantages:**
- State-of-the-art accuracy (4-8% higher mAP than YOLOv11)
- Excellent balance of speed and accuracy
- Easy Python implementation via Ultralytics
- Can detect general objects (doors, windows, fixtures)
- TensorRT acceleration support for production deployment

**Implementation:**
```python
from ultralytics import YOLO

# Load model
model = YOLO('yolov12m.pt')  # or yolov12n.pt for faster inference

# Inference on RGB frame from Glass
results = model.predict(image, conf=0.3)

# Extract detections
for result in results:
    boxes = result.boxes.xyxy  # bounding boxes
    confidences = result.boxes.conf
    classes = result.boxes.cls
```

**Recommended Variant**: YOLOv12-M for balanced accuracy/speed, or YOLOv12-S for faster inference

---

### 2. RT-DETR / RF-DETR

**Best for**: High-accuracy detection with transformer architecture

**Specifications:**
- **Architecture**: Transformer-based with region-focused attention
- **Performance**: RF-DETR-Medium achieves 54.7% mAP at 4.52ms latency (T4 GPU)
- **Speed**: 100+ FPS on NVIDIA T4
- **Accuracy**: Over 60% mAP on COCO benchmark

**Advantages:**
- Superior accuracy for complex scenes
- Better handling of occluded/overlapping objects
- Excellent for detailed residential inspection

**Disadvantages:**
- Slightly higher latency than YOLO variants
- More complex deployment

**When to use**: When inspection requires maximum accuracy and server has powerful GPU

---

### 3. YOLOv11 (Alternative)

**Best for**: Proven reliability with extensive documentation

**Specifications:**
- **Released**: 2024
- **Performance**: YOLOv11-M achieves ~67% mAP
- **Mature ecosystem**: Extensive tutorials, pretrained models, community support

**Advantages:**
- Well-documented and battle-tested
- Large community and resources
- Compatible with Ultralytics ecosystem

**When to use**: If YOLOv12 has compatibility issues or you need proven stability

---

## Custom Training for Residential Inspection

### Dataset Preparation

For residential-specific detection, create custom dataset with classes:

**Interior Classes:**
- `electrical_outlet`
- `light_fixture`
- `smoke_detector`
- `hvac_vent`
- `water_stain`
- `crack_interior`
- `pipe`
- `valve`
- `thermostat`
- `electrical_panel`
- `water_heater`
- `furnace`
- `mold_growth`
- `ceiling_damage`

**Outdoor/Yard Classes:**
- `roof_damage`
- `gutter`
- `downspout`
- `siding_damage`
- `foundation_crack`
- `driveway_crack`
- `fence_damage`
- `outdoor_outlet`
- `exterior_light`
- `sprinkler_head`
- `pool_equipment`
- `retaining_wall_crack`
- `erosion`
- `tree_overhang`

### Training Process

**Using Roboflow + Ultralytics:**

1. **Annotate images** in Roboflow (https://roboflow.com)
2. **Export dataset** in YOLO format
3. **Train model**:

```python
from ultralytics import YOLO

# Load pretrained model
model = YOLO('yolov12m.pt')

# Train on custom residential inspection dataset
model.train(
    data='residential_inspection.yaml',
    epochs=100,
    imgsz=640,
    batch=16,
    device='0'  # GPU
)

# Export for deployment
model.export(format='onnx')  # or 'engine' for TensorRT
```

4. **Deploy to server** and integrate with Socket.IO backend

---

## Server Integration Architecture

```
Glass EE2 (RGB Camera)
    ↓ [Socket.IO: RGB frame as base64 JPEG]
Server (Python/Node.js)
    ↓ [Decode image]
YOLO Model Inference
    ↓ [Object detection]
Annotation JSON
    ↓ [Socket.IO: bbox + class + confidence]
Glass EE2
    ↓ [AR overlay on RGB view]
Display
```

### Python Server Example

```python
import socketio
import cv2
import numpy as np
from ultralytics import YOLO
import base64

# Initialize model
model = YOLO('yolov12m.pt')

sio = socketio.Server(cors_allowed_origins='*')

@sio.on('rgb_frame')
def handle_rgb_frame(sid, data):
    # Decode base64 image from Glass
    img_data = base64.b64decode(data['image'])
    nparr = np.frombuffer(img_data, np.uint8)
    image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

    # Run inference
    results = model.predict(image, conf=0.3, verbose=False)

    # Extract annotations
    annotations = []
    for result in results:
        boxes = result.boxes
        for i in range(len(boxes)):
            bbox = boxes.xyxy[i].tolist()  # [x1, y1, x2, y2]
            conf = float(boxes.conf[i])
            cls = int(boxes.cls[i])
            class_name = model.names[cls]

            annotations.append({
                'bbox': bbox,
                'confidence': conf,
                'class': class_name
            })

    # Send annotations back to Glass
    sio.emit('rgb_annotations', {
        'detections': annotations,
        'timestamp': data.get('timestamp')
    }, room=sid)
```

---

## Performance Recommendations

### Hardware Requirements

**Minimum (Cloud/Server):**
- NVIDIA T4 GPU (16GB VRAM)
- 16GB RAM
- 4-core CPU

**Recommended:**
- NVIDIA A4000/A5000 or RTX 4080
- 32GB RAM
- 8-core CPU

**Edge Deployment (Optional):**
- NVIDIA Jetson Orin Nano (YOLOv12-S/N variants)
- Glass → WiFi → Local Jetson → Back to Glass (lower latency)

### Latency Targets

- **End-to-end latency**: < 200ms (Glass → Server → Glass)
  - Frame encoding: ~20ms
  - Network transmission: ~30-50ms (WiFi)
  - Inference: ~5-15ms (YOLOv12-M on T4)
  - Annotation overlay: ~10ms
  - Total: ~65-95ms (achievable)

- **Target FPS**: 10-15 FPS for object detection overlay
- **Thermal FPS**: 30 FPS (unaffected by RGB processing)

---

## Alternative: Multi-Model Approach

For comprehensive residential inspection, consider running multiple specialized models:

1. **General Objects**: YOLOv12 (pretrained COCO) - doors, windows, furniture
2. **Defects**: Custom YOLO trained on cracks, water damage, mold
3. **Text/Labels**: PaddleOCR or EasyOCR - reading labels, serial numbers, dates
4. **Thermal Anomalies**: Custom classifier on Boson thermal data

Models can run in parallel or sequentially based on inspection mode.

---

## Deployment Options

### Cloud (Recommended for Testing)
- **AWS EC2 g4dn.xlarge** (NVIDIA T4, $0.526/hour)
- **Google Cloud Platform** Compute Engine with T4
- **Azure** NC-series VMs

### On-Premise
- Dedicated inspection workstation with RTX GPU
- Lower latency, no internet dependency

### Edge (Advanced)
- NVIDIA Jetson Orin Nano in inspection vehicle
- Portable, low-latency, WiFi hotspot for Glass connection

---

## Next Steps

1. **Set up server** with Python + YOLOv12
2. **Test with COCO pretrained model** first (general objects)
3. **Collect residential inspection images** during field testing
4. **Annotate dataset** (Roboflow or Label Studio)
5. **Fine-tune YOLOv12** on custom residential dataset
6. **Deploy and iterate** based on field results

---

## References

- **YOLOv12 GitHub**: https://github.com/sunsmarterjie/yolov12
- **Ultralytics Docs**: https://docs.ultralytics.com/models/yolo12/
- **Roboflow**: https://roboflow.com
- **RT-DETR Paper**: https://arxiv.org/abs/2304.08069
- **Object Detection Survey (2025)**: https://arxiv.org/html/2508.02067v1

---

## Summary

**Recommended Stack:**
- **Model**: YOLOv12-M (best balance for residential inspection)
- **Server**: Python with Ultralytics YOLO
- **Hardware**: NVIDIA T4 GPU (cloud or on-premise)
- **Training**: Start with COCO pretrained, fine-tune on custom residential dataset
- **Target**: < 200ms end-to-end latency, 10-15 FPS object detection

This setup provides professional-grade residential inspection capability with real-time AR overlays on Glass EE2.
