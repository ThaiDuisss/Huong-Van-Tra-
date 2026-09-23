from dataclasses import dataclass
from typing import Literal


MODEL_TYPE = "lightfm"
TRAINING_CONTRACT_VERSION = "v1"


@dataclass(frozen=True, slots=True)
class TrainingConfig:
    """Locked deterministic baseline configuration for LightFM V1."""

    loss: Literal["warp"] = "warp"
    no_components: int = 32
    learning_rate: float = 0.05
    epochs: int = 30
    num_threads: int = 1
    random_seed: int = 42

    def __post_init__(self) -> None:
        if self.loss != "warp":
            raise ValueError("Training Contract V1 requires loss='warp'")
        if self.no_components <= 0:
            raise ValueError("no_components must be greater than zero")
        if self.learning_rate <= 0:
            raise ValueError("learning_rate must be greater than zero")
        if self.epochs <= 0:
            raise ValueError("epochs must be greater than zero")
        if self.num_threads != 1:
            raise ValueError(
                "Training Contract V1 requires num_threads=1 for deterministic training"
            )
        if self.random_seed < 0:
            raise ValueError("random_seed must be zero or greater")
