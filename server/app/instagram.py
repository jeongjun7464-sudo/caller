import asyncio
from datetime import datetime
import httpx
from .config import Settings
from .models import PublishType, utc_now

class InstagramPublisher:
    async def publish(self, image_url: str, publish_type: PublishType) -> tuple[str, str | None, datetime]:
        raise NotImplementedError

class MockInstagramPublisher(InstagramPublisher):
    async def publish(self, image_url: str, publish_type: PublishType) -> tuple[str, str, datetime]:
        await asyncio.sleep(0.02)
        media_id = f"mock_{publish_type.value.lower()}_{abs(hash(image_url))}"
        return media_id, f"https://www.instagram.com/p/{media_id}/", utc_now()

class MetaInstagramPublisher(InstagramPublisher):
    def __init__(self, settings: Settings, client: httpx.AsyncClient | None = None):
        self.settings, self.client = settings, client or httpx.AsyncClient(timeout=30)
        self.base = f"https://graph.instagram.com/{settings.meta_graph_version}"
    async def _post(self, path: str, data: dict) -> dict:
        response = await self.client.post(f"{self.base}/{path}", data={**data, "access_token": self.settings.instagram_access_token})
        response.raise_for_status(); return response.json()
    async def publish(self, image_url: str, publish_type: PublishType) -> tuple[str, str | None, datetime]:
        data = {"image_url": image_url}
        if publish_type == PublishType.STORY: data["media_type"] = "STORIES"
        container_id = (await self._post(f"{self.settings.instagram_user_id}/media", data))["id"]
        for _ in range(self.settings.publish_poll_attempts):
            response = await self.client.get(f"{self.base}/{container_id}", params={"fields":"status_code,status", "access_token":self.settings.instagram_access_token})
            response.raise_for_status(); state = response.json().get("status_code")
            if state == "FINISHED": break
            if state in {"ERROR", "EXPIRED"}: raise RuntimeError(f"Instagram container status: {state}")
            await asyncio.sleep(self.settings.publish_poll_seconds)
        else: raise TimeoutError("Instagram container processing timed out")
        media_id = (await self._post(f"{self.settings.instagram_user_id}/media_publish", {"creation_id":container_id}))["id"]
        detail = await self.client.get(f"{self.base}/{media_id}", params={"fields":"permalink,timestamp", "access_token":self.settings.instagram_access_token})
        detail.raise_for_status(); payload = detail.json()
        created = datetime.fromisoformat(payload["timestamp"].replace("Z", "+00:00")) if payload.get("timestamp") else utc_now()
        return media_id, payload.get("permalink") or self.settings.instagram_profile_url, created

def build_publisher(settings: Settings) -> InstagramPublisher:
    return MetaInstagramPublisher(settings) if settings.instagram_mode == "live" else MockInstagramPublisher()
