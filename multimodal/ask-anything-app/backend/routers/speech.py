"""Speech-to-text transcription endpoint."""
from fastapi import APIRouter, Request, UploadFile, File, HTTPException
from pydantic import BaseModel

router = APIRouter()


class TranscriptionResponse(BaseModel):
    """Response from speech transcription."""

    transcription: str


@router.post("/transcribe", response_model=TranscriptionResponse)
async def transcribe_audio(request: Request, audio: UploadFile = File(...)):
    """Transcribe audio file to text.

    Args:
        audio: Audio file upload (WAV, MP3, etc.)

    Returns:
        Transcribed text from Whisper
    """
    whisper = request.app.state.whisper

    if not whisper.is_loaded:
        raise HTTPException(status_code=503, detail="Whisper model not loaded")

    try:
        audio_bytes = await audio.read()
        transcription = whisper.infer(audio_bytes=audio_bytes)

        return TranscriptionResponse(transcription=transcription)

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Transcription failed: {str(e)}")
