from datetime import datetime, timezone
from enum import Enum
from pydantic import BaseModel, ConfigDict, Field

SUITS = {"SPADES", "HEARTS", "CLUBS", "DIAMONDS"}
RANKS = {"ACE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN", "JACK", "QUEEN", "KING"}

class PublishType(str, Enum):
    STORY = "STORY"
    FEED = "FEED"

class PublishStatus(str, Enum):
    PUBLISHING = "PUBLISHING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"

class PublishRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    card_id: str = Field(alias="cardId")
    publish_type: PublishType = Field(alias="publishType")

    def validate_card(self) -> None:
        parts = self.card_id.split("_", 1)
        if len(parts) != 2 or parts[0] not in SUITS or parts[1] not in RANKS:
            raise ValueError("Invalid cardId")

class AccountResponse(BaseModel):
    username: str
    profile_url: str = Field(alias="profileUrl")

class PublishResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    request_id: str = Field(alias="requestId")
    status: PublishStatus
    media_id: str | None = Field(default=None, alias="mediaId")
    permalink: str | None = None
    created_at: datetime = Field(alias="createdAt")
    error: str | None = None

class ProphecyCreateRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    card_id: str | None = Field(default=None, alias="cardId")
    expires_in_minutes: int = Field(default=5, ge=1, le=60, alias="expiresInMinutes")

class ProphecyCardRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    card_id: str = Field(alias="cardId")

class ProphecyResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    room_code: str = Field(alias="roomCode")
    token: str
    qr_url: str = Field(alias="qrUrl")
    expires_at: datetime = Field(alias="expiresAt")
    card_id: str | None = Field(default=None, alias="cardId")
    consumed: bool = False

def utc_now() -> datetime:
    return datetime.now(timezone.utc)
