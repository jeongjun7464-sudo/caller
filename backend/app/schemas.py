from datetime import datetime
from pydantic import BaseModel, ConfigDict, Field


class SessionCreate(BaseModel):
    request_id: str = Field(alias="requestId", examples=["9c2d-demo"])
    expires_in_minutes: int = Field(10, ge=1, le=60, alias="expiresInMinutes")


class CommandCreate(BaseModel):
    message_id: str = Field(alias="messageId", examples=["cmd-001"])
    type: str = Field(examples=["REVEAL"])
    card_id: str | None = Field(None, alias="cardId", examples=["SPADES_SEVEN"])


class RevealCreate(BaseModel):
    request_id: str = Field(alias="requestId")
    card_id: str | None = Field(None, alias="cardId")
    expires_in_seconds: int = Field(600, ge=5, le=3600, alias="expiresInSeconds")


class PublishCreate(BaseModel):
    request_id: str = Field(alias="requestId")
    card_id: str = Field(alias="cardId", examples=["HEARTS_ACE"])
    publish_type: str = Field(alias="publishType", pattern="^(STORY|FEED)$")


class ApiModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True, from_attributes=True)


class SessionOut(ApiModel):
    session_id: str = Field(alias="sessionId")
    room_code: str = Field(alias="roomCode")
    status: str
    expires_at: datetime = Field(alias="expiresAt")


class RevealOut(ApiModel):
    token: str | None = None
    status: str
    card_id: str | None = Field(None, alias="cardId")
    expires_at: datetime = Field(alias="expiresAt")


class PublishOut(ApiModel):
    request_id: str = Field(alias="requestId")
    status: str
    media_id: str | None = Field(default=None, alias="mediaId")
    error: str | None = None
