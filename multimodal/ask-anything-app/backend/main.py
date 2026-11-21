"""
Ask Anything Backend - FastAPI server for multimodal inference.

This server loads Gemma3 (vision-language) and Whisper (speech-to-text) models
at startup and provides REST API endpoints for inference.

Usage:
    From ask-anything-app directory:
        python -m uvicorn backend.main:app --reload --port 8000

    Or from backend directory:
        python -m uvicorn main:app --reload --port 8000
"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

# Use try/except to handle both relative and absolute imports
try:
    from .config import HOST, PORT, CORS_ORIGINS
    from .modules.multimodal import Gemma3Module
    from .modules.voice import WhisperModule
    from .routers import health, vision, speech
except ImportError:
    from config import HOST, PORT, CORS_ORIGINS
    from modules.multimodal import Gemma3Module
    from modules.voice import WhisperModule
    from routers import health, vision, speech

# Global module instances (loaded at startup)
gemma3_module = Gemma3Module()
whisper_module = WhisperModule()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Load models at startup, cleanup on shutdown."""
    print("=" * 60)
    print("Ask Anything Backend - Starting up...")
    print("=" * 60)

    # Load Gemma3 (slower - 3.5GB model)
    print("\n[1/2] Loading Gemma3 vision-language model...")
    try:
        gemma3_module.load()
        print("  ✓ Gemma3 loaded successfully")
    except Exception as e:
        import traceback
        print(f"  ✗ Failed to load Gemma3: {e}")
        traceback.print_exc()

    # Load Whisper (faster - 231MB model)
    print("\n[2/2] Loading Whisper speech-to-text model...")
    try:
        whisper_module.load()
        print("  ✓ Whisper loaded successfully")
    except Exception as e:
        import traceback
        print(f"  ✗ Failed to load Whisper: {e}")
        traceback.print_exc()

    print("\n" + "=" * 60)
    print("Server ready!")
    print(f"  Gemma3: {'✓ Loaded' if gemma3_module.is_loaded else '✗ Not loaded'}")
    print(f"  Whisper: {'✓ Loaded' if whisper_module.is_loaded else '✗ Not loaded'}")
    print("=" * 60)

    yield

    # Cleanup on shutdown
    print("\nShutting down...")
    gemma3_module.unload()
    whisper_module.unload()
    print("Goodbye!")


# Create FastAPI app
app = FastAPI(
    title="Ask Anything API",
    description="Multimodal inference API for vision-language and speech-to-text using ExecuTorch",
    version="1.0.0",
    lifespan=lifespan,
)

# Configure CORS for React dev server
app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Store module references in app state for access in routes
app.state.gemma3 = gemma3_module
app.state.whisper = whisper_module

# Include routers
app.include_router(health.router, prefix="/api", tags=["Health"])
app.include_router(vision.router, prefix="/api/vision", tags=["Vision"])
app.include_router(speech.router, prefix="/api/speech", tags=["Speech"])


@app.get("/")
async def root():
    """Root endpoint with API information."""
    return {
        "name": "Ask Anything API",
        "version": "1.0.0",
        "endpoints": {
            "health": "/api/health",
            "status": "/api/status",
            "vision": "/api/vision/infer",
            "speech": "/api/speech/transcribe",
        },
    }


if __name__ == "__main__":
    uvicorn.run(app, host=HOST, port=PORT)
