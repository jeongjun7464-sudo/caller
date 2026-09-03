import hashlib
import hmac
import secrets
import string
import time
import uuid
from typing import Any, cast
from collections import defaultdict, deque
from datetime import datetime, timedelta, timezone
from fastapi import (
    BackgroundTasks,
    Depends,
    FastAPI,
    Header,
    HTTPException,
    Request,
    Response,
)
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import select
from sqlalchemy.orm import Session
from .config import Settings, get_settings
from .database import Base, engine, get_db
from .instagram import MockInstagramPublisher
from .models import (
    AuditEvent,
    CardCommand,
    InstagramPublish,
    PerformanceSession,
    RevealToken,
)
from .schemas import (
    CommandCreate,
    PublishCreate,
    PublishOut,
    RevealCreate,
    RevealOut,
    SessionCreate,
    SessionOut,
)


def utcnow():
    return datetime.now(timezone.utc)


def token_hash(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


def room_code() -> str:
    return "".join(
        secrets.choice(string.ascii_uppercase + string.digits) for _ in range(6)
    )


def create_app(settings: Settings | None = None, create_tables: bool = True) -> FastAPI:
    settings = settings or get_settings()
    if create_tables:
        Base.metadata.create_all(engine)
    app = FastAPI(
        title="Magic Caller Platform API",
        version="2.0.0",
        description="모든 변경 요청은 requestId 또는 messageId로 멱등 처리됩니다.",
    )
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.allowed_origins,
        allow_credentials=False,
        allow_methods=["GET", "POST"],
        allow_headers=["Content-Type", "X-Hub-Signature-256"],
    )
    buckets: dict[str, deque[float]] = defaultdict(deque)

    @app.middleware("http")
    async def rate_limit(request: Request, call_next):
        key = f"{request.client.host if request.client else 'local'}:{request.url.path}"
        now = time.monotonic()
        q = buckets[key]
        while q and now - q[0] > 60:
            q.popleft()
        if len(q) >= 120:
            return Response("rate limit exceeded", 429)
        q.append(now)
        return await call_next(request)

    def audit(db: Session, event: str, entity: str):
        db.add(AuditEvent(id=str(uuid.uuid4()), event_type=event, entity_id=entity))

    @app.post(
        "/api/v1/sessions", response_model=SessionOut, response_model_by_alias=True
    )
    def create_session(body: SessionCreate, db: Session = Depends(get_db)):
        existing = db.get(PerformanceSession, body.request_id)
        if existing:
            return SessionOut(
                sessionId=existing.id,
                roomCode=existing.room_code,
                status=existing.status,
                expiresAt=existing.expires_at,
            )
        code = room_code()
        while db.scalar(
            select(PerformanceSession).where(PerformanceSession.room_code == code)
        ):
            code = room_code()
        row = PerformanceSession(
            id=body.request_id,
            room_code=code,
            status="READY",
            expires_at=utcnow() + timedelta(minutes=body.expires_in_minutes),
        )
        db.add(row)
        audit(db, "SESSION_CREATED", row.id)
        db.commit()
        return SessionOut(
            sessionId=row.id, roomCode=code, status=row.status, expiresAt=row.expires_at
        )

    @app.post("/api/v1/sessions/{session_id}/commands")
    def command(session_id: str, body: CommandCreate, db: Session = Depends(get_db)):
        session = db.get(PerformanceSession, session_id)
        if not session:
            raise HTTPException(404, "session not found")
        if session.expires_at.replace(tzinfo=timezone.utc) <= utcnow():
            raise HTTPException(410, "session expired")
        if db.get(CardCommand, body.message_id):
            return {"accepted": False, "duplicate": True}
        db.add(
            CardCommand(
                id=body.message_id,
                session_id=session_id,
                command_type=body.type,
                card_id=body.card_id,
            )
        )
        session.status = body.type
        audit(db, "COMMAND_ACCEPTED", body.message_id)
        db.commit()
        return {"accepted": True, "duplicate": False}

    @app.get(
        "/api/v1/sessions/{session_id}/status",
        response_model=SessionOut,
        response_model_by_alias=True,
    )
    def session_status(session_id: str, db: Session = Depends(get_db)):
        row = db.get(PerformanceSession, session_id)
        if not row:
            raise HTTPException(404, "session not found")
        return SessionOut(
            sessionId=row.id,
            roomCode=row.room_code,
            status=row.status,
            expiresAt=row.expires_at,
        )

    @app.post("/api/v1/reveals", response_model=RevealOut, response_model_by_alias=True)
    def create_reveal(body: RevealCreate, db: Session = Depends(get_db)):
        existing = db.get(RevealToken, body.request_id)
        if existing:
            return RevealOut(
                status="READY", cardId=existing.card_id, expiresAt=existing.expires_at
            )
        raw = secrets.token_urlsafe(32)
        row = RevealToken(
            id=body.request_id,
            token_hash=token_hash(raw),
            card_id=body.card_id,
            expires_at=utcnow() + timedelta(seconds=body.expires_in_seconds),
        )
        db.add(row)
        audit(db, "REVEAL_CREATED", body.request_id)
        db.commit()
        return RevealOut(
            token=raw, status="READY", cardId=None, expiresAt=row.expires_at
        )

    @app.get(
        "/api/v1/reveals/{token}",
        response_model=RevealOut,
        response_model_by_alias=True,
    )
    def get_reveal(token: str, db: Session = Depends(get_db)):
        row = db.scalar(
            select(RevealToken).where(RevealToken.token_hash == token_hash(token))
        )
        if not row:
            raise HTTPException(404, "reveal not found")
        if row.expires_at.replace(tzinfo=timezone.utc) <= utcnow():
            raise HTTPException(410, "reveal expired")
        if row.consumed:
            raise HTTPException(410, "reveal consumed")
        if not row.card_id:
            return RevealOut(status="PREPARING", cardId=None, expiresAt=row.expires_at)
        row.consumed = True
        audit(db, "REVEAL_CONSUMED", row.id)
        db.commit()
        return RevealOut(
            status="REVEALED", cardId=row.card_id, expiresAt=row.expires_at
        )

    async def publish_job(
        database_url: str, request_id: str, card_id: str, publish_type: str
    ):
        from sqlalchemy import create_engine
        from sqlalchemy.orm import sessionmaker

        factory = sessionmaker(create_engine(database_url), expire_on_commit=False)
        with factory() as db:
            row = db.get(InstagramPublish, request_id)
            if row is None:
                return
            try:
                row.media_id = await MockInstagramPublisher().publish(
                    card_id, publish_type
                )
                row.status = "SUCCEEDED"
            except Exception as error:
                row.status = "FAILED"
                row.error = type(error).__name__
            audit(db, "INSTAGRAM_FINISHED", request_id)
            db.commit()

    @app.post(
        "/api/v1/instagram/publish",
        response_model=PublishOut,
        response_model_by_alias=True,
        status_code=202,
    )
    def publish(
        body: PublishCreate, tasks: BackgroundTasks, db: Session = Depends(get_db)
    ):
        row = db.get(InstagramPublish, body.request_id)
        if row:
            return PublishOut(
                requestId=row.request_id,
                status=row.status,
                mediaId=row.media_id,
                error=row.error,
            )
        row = InstagramPublish(
            request_id=body.request_id,
            card_id=body.card_id,
            publish_type=body.publish_type,
            status="QUEUED",
        )
        db.add(row)
        audit(db, "INSTAGRAM_QUEUED", body.request_id)
        db.commit()
        tasks.add_task(
            publish_job,
            str(cast(Any, db.get_bind()).url),
            body.request_id,
            body.card_id,
            body.publish_type,
        )
        return PublishOut(requestId=row.request_id, status=row.status, mediaId=None)

    @app.get(
        "/api/v1/instagram/publish/{request_id}",
        response_model=PublishOut,
        response_model_by_alias=True,
    )
    def publish_status(request_id: str, db: Session = Depends(get_db)):
        row = db.get(InstagramPublish, request_id)
        if not row:
            raise HTTPException(404, "publish not found")
        return PublishOut(
            requestId=row.request_id,
            status=row.status,
            mediaId=row.media_id,
            error=row.error,
        )

    @app.post("/api/v1/instagram/webhook")
    async def webhook(
        request: Request,
        x_hub_signature_256: str = Header(""),
        db: Session = Depends(get_db),
    ):
        raw = await request.body()
        expected = (
            "sha256="
            + hmac.new(
                settings.instagram_app_secret.encode(), raw, hashlib.sha256
            ).hexdigest()
        )
        if not settings.instagram_app_secret or not hmac.compare_digest(
            expected, x_hub_signature_256
        ):
            raise HTTPException(401, "invalid signature")
        audit(db, "INSTAGRAM_WEBHOOK", "meta")
        db.commit()
        return {"received": True}

    return app


app = create_app()
