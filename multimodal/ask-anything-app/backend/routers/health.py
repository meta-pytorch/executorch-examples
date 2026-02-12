"""Health check and status endpoints."""
from fastapi import APIRouter, Request

router = APIRouter()


@router.get("/health")
async def health_check():
    """Check if the server is running."""
    return {"status": "healthy"}


@router.get("/status")
async def model_status(request: Request):
    """Get status of all loaded models."""
    return {
        "gemma3": request.app.state.gemma3.get_status(),
        "whisper": request.app.state.whisper.get_status(),
    }
