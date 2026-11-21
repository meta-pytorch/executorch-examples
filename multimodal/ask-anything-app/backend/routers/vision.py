"""Vision-language inference endpoint."""
from fastapi import APIRouter, Request, HTTPException
from pydantic import BaseModel
from typing import Optional

router = APIRouter()


class VisionRequest(BaseModel):
    """Request body for vision inference."""

    prompt: str
    image_base64: Optional[str] = None
    max_new_tokens: int = 256
    temperature: float = 0.7


class VisionResponse(BaseModel):
    """Response from vision inference."""

    response: str
    tokens_generated: int


@router.post("/infer", response_model=VisionResponse)
async def vision_infer(request: Request, body: VisionRequest):
    """Run vision-language inference on an image with a text prompt.

    Args:
        body: Request with prompt and optional base64 image

    Returns:
        Generated text response from Gemma3
    """
    gemma3 = request.app.state.gemma3

    if not gemma3.is_loaded:
        raise HTTPException(status_code=503, detail="Gemma3 model not loaded")

    try:
        response = gemma3.infer(
            prompt=body.prompt,
            image_base64=body.image_base64,
            max_new_tokens=body.max_new_tokens,
            temperature=body.temperature,
        )

        # Approximate token count from response length
        tokens_generated = len(response.split())

        return VisionResponse(response=response, tokens_generated=tokens_generated)

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Inference failed: {str(e)}")
