"""Abstract base class for inference modules."""
from abc import ABC, abstractmethod
from typing import Any, Dict


class BaseModule(ABC):
    """Abstract base class for all inference modules.

    All modules must implement load(), unload(), and infer() methods
    to provide a consistent interface.
    """

    def __init__(self):
        self._loaded = False
        self._model = None

    @abstractmethod
    def load(self, **kwargs) -> None:
        """Load the model into memory.

        Args:
            **kwargs: Model-specific configuration (paths, etc.)
        """
        pass

    @abstractmethod
    def unload(self) -> None:
        """Unload the model and free resources."""
        pass

    @abstractmethod
    def infer(self, **kwargs) -> Any:
        """Run inference on input data.

        Args:
            **kwargs: Model-specific input parameters

        Returns:
            Model-specific output
        """
        pass

    @property
    def is_loaded(self) -> bool:
        """Check if the model is loaded."""
        return self._loaded

    def get_status(self) -> Dict[str, Any]:
        """Get module status information."""
        return {
            "loaded": self._loaded,
            "model_type": self.__class__.__name__,
        }
