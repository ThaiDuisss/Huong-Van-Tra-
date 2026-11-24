import asyncio
from contextlib import asynccontextmanager
import logging

from fastapi import FastAPI

from app.core.config import get_settings
from app.service.taste_catalog_sync_service import get_taste_catalog_sync

logger = logging.getLogger(__name__)


@asynccontextmanager
async def taste_catalog_lifespan(_application: FastAPI):
    settings = get_settings()
    if not settings.taste_catalog_auto_refresh:
        yield
        return
    if settings.taste_catalog_refresh_seconds >= settings.taste_catalog_ttl_seconds:
        logger.error("taste_catalog_refresh disabled: refresh interval must be shorter than TTL")
        yield
        return

    stop = asyncio.Event()

    async def refresh():
        while not stop.is_set():
            await asyncio.to_thread(get_taste_catalog_sync().sync_once)
            try:
                await asyncio.wait_for(stop.wait(), timeout=settings.taste_catalog_refresh_seconds)
            except TimeoutError:
                continue

    task = asyncio.create_task(refresh())
    try:
        yield
    finally:
        stop.set()
        await task
