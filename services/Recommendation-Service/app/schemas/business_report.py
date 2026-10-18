from datetime import date, datetime, timezone
from uuid import UUID

from pydantic import BaseModel, ConfigDict, field_validator, model_validator


class BusinessExportRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    fromDate: date
    toDate: date
    requestId: UUID

    @model_validator(mode="after")
    def dates(self):
        if self.fromDate > self.toDate:
            raise ValueError("From date must not exceed to date")
        return self


class BusinessExportResponse(BaseModel):
    id: str
    fileId: str
    fileName: str
    fromDate: date
    toDate: date
    generatedAt: datetime
    sha256: str

    @field_validator("generatedAt", mode="before")
    @classmethod
    def utc(cls, value):
        return value.replace(tzinfo=timezone.utc) if isinstance(value, datetime) and value.tzinfo is None else value
