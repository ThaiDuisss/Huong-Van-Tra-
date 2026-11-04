from datetime import date, datetime, timezone
from uuid import UUID
from typing import Literal

from pydantic import BaseModel, ConfigDict, field_validator, model_validator

BOM_FROM = date(2026, 4, 1)
BOM_TO = date(2026, 4, 30)  # Inclusive calendar date; TEST labels end 01/05 UTC exclusive.


class BusinessExportRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    fromDate: date | None = None
    toDate: date | None = None
    requestId: UUID
    reportType: Literal["PRODUCT", "CUSTOM_BOM"] = "PRODUCT"

    @model_validator(mode="after")
    def dates(self):
        if self.reportType == "CUSTOM_BOM":
            if self.fromDate not in (None, BOM_FROM) or self.toDate not in (None, BOM_TO):
                raise ValueError("BOM reports require the fixed TEST period")
            self.fromDate, self.toDate = BOM_FROM, BOM_TO
        elif self.fromDate is None or self.toDate is None:
            raise ValueError("Product reporting dates are required")
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
    # Omitted from legacy Product HTTP responses to preserve their contract.
    reportType: Literal["CUSTOM_BOM"] | None = None
    sourceKind: Literal["SYNTHETIC"] | None = None
    researchOnly: bool | None = None
    productionApproved: bool | None = None
    createdBy: str | None = None
    status: Literal["COMPLETE"] | None = None
    provenance: dict | None = None

    @field_validator("generatedAt", mode="before")
    @classmethod
    def utc(cls, value):
        return value.replace(tzinfo=timezone.utc) if isinstance(value, datetime) and value.tzinfo is None else value
