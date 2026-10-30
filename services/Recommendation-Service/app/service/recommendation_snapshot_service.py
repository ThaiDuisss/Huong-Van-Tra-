import logging
from datetime import datetime, timezone
from uuid import uuid4

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError, SQLAlchemyError

from app.core.config import get_settings
from app.models.business_report import RecommendationSnapshot
from app.schemas.recommendation import RecommendationResponse
from app.service.evidence_fingerprint import fingerprint

logger = logging.getLogger(__name__)


def utc_now():
    return datetime.now(timezone.utc).replace(tzinfo=None)


class RecommendationSnapshotService:
    """Fail closed: a response is issued only after its snapshot commits.

    UUID Idempotency-Key is scoped to customer, with normalized serving parameters.
    Reuse with a different request is a conflict; retry returns the frozen response.
    No client product list is accepted. Recording failures emit a sanitized error.
    """
    def issue(self, db, service, customer_id, limit, exclude_purchased, mode, key=None):
        key = key or str(uuid4())
        parameters = dict(limit=limit, exclude_purchased=exclude_purchased,
                          mode=mode.value if mode else None)
        digest = fingerprint(parameters)
        try:
            existing = db.scalar(select(RecommendationSnapshot).where(
                RecommendationSnapshot.customer_id == customer_id,
                RecommendationSnapshot.request_key == key))
            if existing:
                return self._replay(existing, digest)
            result = service.recommend(db, customer_id=customer_id, **dict(
                limit=limit, exclude_purchased=exclude_purchased, mode=mode))
            generated = utc_now()  # After inference: purchases observed during inference cannot match.
            result.recommendation_event_id = str(uuid4())
            result.generated_at = generated.replace(tzinfo=timezone.utc)
            # The legacy include-all API remains compatible; label it truthfully.
            effective_mode = result.mode.value if result.mode else (
                "DISCOVERY" if result.exclude_purchased else "LEGACY_ALL")
            snapshot = RecommendationSnapshot(id=result.recommendation_event_id,
                customer_id=customer_id, request_key=key, request_hash=digest,
                generated_at=generated, observed_at=utc_now(), mode=effective_mode,
                source=result.source, fallback_reason=result.fallback_reason,
                evaluation_days=get_settings().ai_report_evaluation_days,
                context={"kind": "GENERATED_RESPONSE", "endpoint": "/recommendations/{customer_id}",
                         "reportable": effective_mode != "LEGACY_ALL", "parameters": parameters},
                response=result.model_dump(mode="json"))
            db.add(snapshot)
            db.commit()
            return result
        except IntegrityError:
            db.rollback()
            existing = db.scalar(select(RecommendationSnapshot).where(
                RecommendationSnapshot.customer_id == customer_id,
                RecommendationSnapshot.request_key == key))
            if existing:
                return self._replay(existing, digest)
            logger.error("Recommendation snapshot constraint failure")
            raise HTTPException(503, "Recommendation recording unavailable") from None
        except SQLAlchemyError:
            db.rollback()
            logger.error("Recommendation snapshot persistence unavailable")
            raise HTTPException(503, "Recommendation recording unavailable") from None

    @staticmethod
    def _replay(snapshot, digest):
        if snapshot.request_hash != digest:
            raise HTTPException(409, "Idempotency key belongs to a different recommendation request")
        return RecommendationResponse.model_validate(snapshot.response)
