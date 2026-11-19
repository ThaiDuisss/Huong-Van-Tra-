from fastapi import APIRouter, Depends, HTTPException, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.routing import APIRoute
from starlette.concurrency import run_in_threadpool

from app.core.recommendation_auth import authorize_pos_actor
from app.schemas.taste_catalog import SnapshotError, parse_json
from app.schemas.taste_recommendation import TastePreviewRequest, TastePreviewResponse
from app.service.taste_catalog_sync_service import TasteCatalogSyncService, get_taste_catalog_sync
from app.service.taste_recommendation_service import InvalidTasteCriteria, preview_taste

MAX_REQUEST_BYTES = 20 * 1024


class PreviewRequest(Request):
    async def json(self):
        try:
            return parse_json(await self.body())
        except SnapshotError:
            raise HTTPException(422, "Payload khảo sát khẩu vị không hợp lệ") from None


class TastePreviewRoute(APIRoute):
    def get_route_handler(self):
        handle = super().get_route_handler()

        async def preview(request: Request) -> Response:
            await run_in_threadpool(authorize_pos_actor, request)
            size = 0

            async def bounded_receive():
                nonlocal size
                message = await request.receive()
                if message["type"] == "http.request":
                    size += len(message.get("body", b""))
                    if size > MAX_REQUEST_BYTES:
                        raise HTTPException(413, "Payload khảo sát khẩu vị vượt quá 20 KiB")
                return message

            try:
                return await handle(PreviewRequest(request.scope, bounded_receive))
            except RequestValidationError:
                # Validation responses must not echo arbitrary client fields.
                raise HTTPException(422, "Payload khảo sát khẩu vị không hợp lệ") from None

        return preview


router = APIRouter(prefix="/taste", tags=["taste-recommendations"], route_class=TastePreviewRoute)


@router.post("/preview", response_model=TastePreviewResponse,
             responses={503: {"model": TastePreviewResponse}})
def taste_preview(
    request: TastePreviewRequest,
    response: Response,
    projection: TasteCatalogSyncService = Depends(get_taste_catalog_sync),
) -> TastePreviewResponse:
    response.headers["Cache-Control"] = "private, no-store"
    try:
        result = preview_taste(request, projection.read)
    except InvalidTasteCriteria as failure:
        raise HTTPException(422, str(failure)) from None
    if result.status == "TASTE_CATALOG_UNAVAILABLE":
        response.status_code = 503
    return result
