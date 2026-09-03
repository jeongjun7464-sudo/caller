from datetime import datetime, timezone
from sqlalchemy import Boolean, DateTime, ForeignKey, String, Text
from sqlalchemy.orm import Mapped, mapped_column
from .database import Base


def now():
    return datetime.now(timezone.utc)


class User(Base):
    __tablename__ = "users"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class PerformanceSession(Base):
    __tablename__ = "performance_sessions"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    room_code: Mapped[str] = mapped_column(String(6), unique=True, index=True)
    status: Mapped[str] = mapped_column(String(32), default="CREATED")
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class CardCommand(Base):
    __tablename__ = "card_commands"
    id: Mapped[str] = mapped_column(String(100), primary_key=True)
    session_id: Mapped[str] = mapped_column(
        ForeignKey("performance_sessions.id"), index=True
    )
    command_type: Mapped[str] = mapped_column(String(32))
    card_id: Mapped[str | None] = mapped_column(String(32))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class RevealToken(Base):
    __tablename__ = "reveal_tokens"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    card_id: Mapped[str | None] = mapped_column(String(32))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    consumed: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class InstagramPublish(Base):
    __tablename__ = "instagram_publishes"
    request_id: Mapped[str] = mapped_column(String(100), primary_key=True)
    card_id: Mapped[str] = mapped_column(String(32))
    publish_type: Mapped[str] = mapped_column(String(16))
    status: Mapped[str] = mapped_column(String(24))
    media_id: Mapped[str | None] = mapped_column(String(128))
    error: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class AuditEvent(Base):
    __tablename__ = "audit_events"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    event_type: Mapped[str] = mapped_column(String(64))
    entity_id: Mapped[str] = mapped_column(String(100))
    detail: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
