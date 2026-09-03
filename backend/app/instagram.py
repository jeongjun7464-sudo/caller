import asyncio
from typing import Protocol


class InstagramPublisher(Protocol):
    async def publish(self, card_id: str, publish_type: str) -> str: ...


class MockInstagramPublisher:
    async def publish(self, card_id: str, publish_type: str) -> str:
        await asyncio.sleep(0.01)
        return f"mock_{publish_type.lower()}_{card_id.lower()}"
