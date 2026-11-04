"""Explicit demo settings without Product settings, dotenv, DB or Kafka defaults."""
from pathlib import Path

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class DemoConfigurationError(ValueError):
    pass


class IngredientDemoSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="INGREDIENT_", env_file=None, extra="ignore")

    demo_enabled: bool = False
    demo_environment: str = "production"
    demo_auth_mode: str = "bearer"
    demo_token: SecretStr | None = None
    dataset_path: Path | None = None
    dataset_sha256: str = ""
    dataset_id: str = ""
    model_path: Path | None = None
    model_sha256: str = ""

    def validate_demo(self):
        if not self.demo_enabled:
            return
        if self.demo_environment not in ("demo", "test") or self.demo_auth_mode != "bearer":
            raise DemoConfigurationError("Ingredient demo requires an isolated demo/test environment and bearer auth")
        token = self.demo_token.get_secret_value() if self.demo_token else ""
        if len(token) < 32 or not token.isascii() or any(c.isspace() for c in token):
            raise DemoConfigurationError("A private external demo token of at least 32 ASCII characters is required")
        for path, digest in ((self.dataset_path, self.dataset_sha256), (self.model_path, self.model_sha256)):
            if path is None or not path.is_absolute() or len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
                raise DemoConfigurationError("Absolute artifact paths and explicit SHA-256 values are required")
        if not self.dataset_id:
            raise DemoConfigurationError("An explicit expected dataset ID is required")
