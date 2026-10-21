from typing import Literal

from pydantic import Field, SecretStr
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
    mysql_database: str = "recommendation_db"
    mysql_user: str
    mysql_password: SecretStr
    mysql_echo_sql: bool = False

    catalog_service_url: str = "http://localhost:8081"
    internal_header_secret: str = Field(default="", repr=False)
    app_environment: Literal["production", "development", "test"] = "production"
    internal_replay_backend: Literal["redis", "memory"] = "redis"
    internal_replay_redis_url: SecretStr | None = None
    internal_context_max_skew_seconds: int = Field(default=60, ge=1, le=300)
    recommendation_internal_callers: str = ""
    recommendation_staff_roles: str = ""  # Explicit operator authorization required.
    catalog_sync_internal_callers: str = "catalog-Service"
    internal_service_name: str = "recommendation-service"
    file_service_url: str = "http://localhost:8101"
    # Exact origins only; signed storage URLs are never supplied by the caller.
    ai_report_storage_origins: str = "http://localhost:9000"
    ai_report_data_source: Literal["OPERATIONAL", "OFFLINE_EVALUATION"] | None = None
    ai_report_offline_bundle_path: str | None = None
    ai_report_offline_bundle_sha256: str | None = None
    ai_report_file_timeout_seconds: float = 30.0
    ai_report_evaluation_days: int = Field(default=7, ge=1, le=90)
    ai_report_max_period_days: int = Field(default=366, ge=1, le=366)
    ai_report_max_evidence_rows: int = Field(default=20000, ge=1, le=100000)

    model_artifact_path: str = "artifacts/lightfm_model.pkl"  # Legacy research training only.
    v1_artifact_path: str = "artifacts/v1_hybrid_model.npz"
    recommendation_default_limit: int = 10
    recommendation_max_limit: int = 50

    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_group_id: str = "recommendation-service-order-completed"
    kafka_order_completed_topic: str = "order.completed"
    kafka_return_completed_topic: str = "notification.return.completed"
    kafka_auto_offset_reset: str = "earliest"
    kafka_enable_auto_commit: bool = False

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

