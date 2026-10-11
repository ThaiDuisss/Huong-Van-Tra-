from datetime import datetime, timezone
from typing import Annotated
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, field_serializer

Name = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=200)]
Version = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=50)]


class AiReportMetadata(BaseModel):
    model_config = ConfigDict(extra="forbid")
    name: Name
    version: Version


class AiReportCreate(AiReportMetadata):
    fileId: UUID


class AiReportResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True, populate_by_name=True)
    id: str
    name: str
    version: str
    fileId: str = Field(validation_alias="file_id")
    fileName: str = Field(validation_alias="file_name")
    fileType: str = Field(validation_alias="file_type")
    createdBy: str = Field(validation_alias="created_by")
    updatedBy: str | None = Field(validation_alias="updated_by")
    createdAt: datetime = Field(validation_alias="created_at")
    updatedAt: datetime = Field(validation_alias="updated_at")

    @field_serializer("createdAt", "updatedAt")
    def utc_timestamp(self, value: datetime) -> str:
        return value.replace(tzinfo=timezone.utc).isoformat().replace("+00:00", "Z")


class AiReportPage(BaseModel):
    content: list[AiReportResponse]
    page: int
    size: int
    totalElements: int
    totalPages: int
    first: bool
    last: bool


class TrustedReportFile(BaseModel):
    model_config = ConfigDict(extra="ignore")
    fileId: UUID
    ownerId: UUID
    fileName: str = Field(min_length=1, max_length=255)
    fileType: str
    contentType: str
    sizeBytes: int = Field(gt=0, le=10 * 1024 * 1024)
    status: str
    visibility: str
    deleted: bool
