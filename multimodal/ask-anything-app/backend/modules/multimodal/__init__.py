"""Multimodal (vision-language) modules."""
try:
    from .gemma3_module import Gemma3Module
except ImportError:
    from gemma3_module import Gemma3Module

__all__ = ["Gemma3Module"]
