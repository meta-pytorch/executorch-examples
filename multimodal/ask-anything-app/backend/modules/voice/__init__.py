"""Voice (speech-to-text) modules."""
try:
    from .whisper_module import WhisperModule
except ImportError:
    from whisper_module import WhisperModule

__all__ = ["WhisperModule"]
