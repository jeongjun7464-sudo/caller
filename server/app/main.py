from pathlib import Path
from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.staticfiles import StaticFiles
from .cards import create_card_image
from .config import Settings, get_settings
from .database import PublishDatabase
from .instagram import build_publisher
from .models import AccountResponse, PublishRequest, PublishResponse, PublishStatus
from .storage import build_storage

def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings(); settings.media_dir.mkdir(parents=True, exist_ok=True)
    app = FastAPI(title="Card Caller Instagram Publisher", version="1.0.0")
    app.mount("/media", StaticFiles(directory=settings.media_dir), name="media")
    database, storage, publisher = PublishDatabase(settings.database_path), build_storage(settings), build_publisher(settings)
    @app.get("/health")
    async def health(): return {"status":"ok", "instagramMode":settings.instagram_mode}
    @app.get("/api/v1/account", response_model=AccountResponse, response_model_by_alias=True)
    async def account(): return AccountResponse(username=settings.instagram_username, profileUrl=settings.instagram_profile_url)
    @app.post("/api/v1/publish", response_model=PublishResponse, response_model_by_alias=True)
    async def publish(request: PublishRequest, idempotency_key: str = Header(..., alias="Idempotency-Key")):
        if not idempotency_key.strip() or len(idempotency_key) > 100: raise HTTPException(400,"Invalid Idempotency-Key")
        try: request.validate_card()
        except ValueError as error: raise HTTPException(422,str(error)) from error
        existing = database.find(idempotency_key)
        if existing and existing.status != PublishStatus.FAILED: return existing
        if existing and not database.restart_failed(idempotency_key): return database.find(idempotency_key)
        if not existing and not database.begin(idempotency_key, request.card_id, request.publish_type.value): return database.find(idempotency_key)
        try:
            filename = f"{idempotency_key}.jpg"
            local_path = create_card_image(request.card_id, settings.media_dir / filename, 1920 if request.publish_type.value == "STORY" else 1350)
            public_url = storage.upload(local_path, filename)
            media_id, permalink, created_at = await publisher.publish(public_url, request.publish_type)
            return database.finish(idempotency_key,PublishStatus.SUCCEEDED,media_id,permalink,None,created_at)
        except Exception as error:
            database.finish(idempotency_key,PublishStatus.FAILED,error=str(error))
            raise HTTPException(502,"Instagram publishing failed") from error
        finally:
            try: storage.delete(filename)
            except Exception: pass
    return app

app = create_app()
