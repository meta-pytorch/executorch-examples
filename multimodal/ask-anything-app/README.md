# Ask Anything - Multimodal Web Dashboard

A two-column web dashboard with real-time camera streaming and Facebook-style chat interface, powered by **Gemma3** (vision-language) and **Whisper** (speech-to-text) ExecuTorch runtimes.

## Features

- Real-time camera streaming with frame capture
- Facebook-style chat interface (blue user bubbles, gray AI bubbles)
- Vision-language understanding via Gemma3 4B
- Speech-to-text transcription via Whisper (optional)
- Models loaded at startup for fast inference

## Quick Start

### 1. Start the Backend

```bash
# From the ask-anything-app directory
cd backend

# Install Python dependencies (if not already)
pip install -r ../requirements.txt

# Start the FastAPI server
python -m uvicorn main:app --reload --port 8000
```

The backend will load the Gemma3 and Whisper models at startup.

### 2. Start the Frontend

```bash
# From the ask-anything-app directory
npm install  # Install dependencies (first time only)
npm run dev  # Start the dev server
```

### 3. Open the App

Navigate to http://localhost:5173 in your browser.

- Allow camera access when prompted
- Type a question and press Enter
- The current camera frame will be sent to Gemma3 for analysis

## Project Structure

```
ask-anything-app/
├── backend/                    # FastAPI backend
│   ├── main.py                 # App entry point
│   ├── config.py               # Model paths
│   ├── modules/                # Inference modules
│   │   ├── base.py             # BaseModule interface
│   │   ├── multimodal/         # Gemma3 module
│   │   └── voice/              # Whisper module
│   └── routers/                # API endpoints
│       ├── health.py           # Health check
│       ├── vision.py           # Vision inference
│       └── speech.py           # Speech transcription
├── src/                        # React frontend
│   ├── components/             # UI components
│   │   ├── layout/             # SplitLayout
│   │   ├── camera/             # CameraStream
│   │   └── chat/               # ChatInterface
│   ├── contexts/               # Zustand store
│   ├── hooks/                  # Custom hooks
│   ├── services/               # API client
│   └── types/                  # TypeScript types
├── package.json
└── requirements.txt
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/health` | GET | Health check |
| `/api/status` | GET | Model status |
| `/api/vision/infer` | POST | Vision-language inference |
| `/api/speech/transcribe` | POST | Speech-to-text |

## Configuration

Model paths are configured in `backend/config.py`:

- **Gemma3**: `../text-image-runtime/gemma3/GEMMA3_4B_XNNPACK_INT8_INT4.pte`
- **Whisper**: `../voice-runtime/models/whisper-tiny-ExecuTorch-XNNPACK/`

## Tech Stack

- **Frontend**: React 19 + TypeScript + Vite + Tailwind CSS
- **State**: Zustand
- **Backend**: FastAPI + Uvicorn
- **ML Runtime**: ExecuTorch
