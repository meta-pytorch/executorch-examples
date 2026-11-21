"""Inference modules for Ask Anything."""
try:
    from .base import BaseModule
except ImportError:
    from base import BaseModule

__all__ = ["BaseModule"]
