from functools import lru_cache
from typing import Literal

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict
from sqlalchemy.engine import URL


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    app_name: str = "Recommendation-Service"
    app_port: int = 8090

    mysql_host: str = "localhost"
    mysql_port: int = 3306
    mysql_database: Literal["recommendation_db"] = "recommendation_db"
    mysql_user: str
    mysql_password: SecretStr
    mysql_echo_sql: bool = False

    catalog_service_url: str = "http://localhost:8081"
    internal_header_secret: str = "dev-internal-header-secret"
    internal_service_name: str = "recommendation-service"

    @property
    def sqlalchemy_database_url(self) -> str:
        return URL.create(
            drivername="mysql+pymysql",
            username=self.mysql_user,
            password=self.mysql_password.get_secret_value(),
            host=self.mysql_host,
            port=self.mysql_port,
            database=self.mysql_database,
            query={"charset": "utf8mb4"},
        ).render_as_string(hide_password=False)


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
