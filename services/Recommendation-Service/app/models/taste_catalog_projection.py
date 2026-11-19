from datetime import datetime

from sqlalchemy import JSON
from sqlalchemy.dialects.mysql import BIGINT, CHAR, DATETIME, VARCHAR
from sqlalchemy.orm import Mapped, mapped_column

from app.database.base import Base


class TasteCatalogProjection(Base):
    __tablename__ = "taste_catalog_projection"

    source_key: Mapped[str] = mapped_column(
        VARCHAR(40, charset="ascii", collation="ascii_bin"), primary_key=True
    )
    projection_revision: Mapped[int] = mapped_column(BIGINT(unsigned=True), nullable=False)
    source_read_at: Mapped[datetime] = mapped_column(DATETIME(fsp=6), nullable=False)
    synced_at: Mapped[datetime] = mapped_column(DATETIME(fsp=6), nullable=False)
    payload_sha256: Mapped[str] = mapped_column(
        CHAR(64, charset="ascii", collation="ascii_bin"), nullable=False
    )
    payload: Mapped[dict] = mapped_column(JSON, nullable=False)
