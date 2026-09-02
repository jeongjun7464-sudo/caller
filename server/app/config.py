from functools import lru_cache
from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    instagram_mode: str = "mock"
    instagram_access_token: str = ""
    instagram_user_id: str = ""
    instagram_username: str = "cardcaller_magic"
    instagram_profile_url: str = "https://www.instagram.com/cardcaller_magic/"
    meta_graph_version: str = "v23.0"
    app_secret: str = ""
    database_path: Path = Path("data/cardcaller.db")
    media_dir: Path = Path("media")
    public_media_base_url: str = "http://localhost:8000/media"
    public_app_base_url: str = "http://localhost:8000"
    s3_bucket: str = ""
    s3_region: str = ""
    s3_endpoint_url: str = ""
    s3_access_key_id: str = ""
    s3_secret_access_key: str = ""
    publish_poll_seconds: float = 1.0
    publish_poll_attempts: int = 20
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    def validate_production(self) -> None:
        if self.instagram_mode != "live":
            return
        missing = [name for name, value in {
            "INSTAGRAM_ACCESS_TOKEN": self.instagram_access_token,
            "INSTAGRAM_USER_ID": self.instagram_user_id,
            "APP_SECRET": self.app_secret,
            "S3_BUCKET": self.s3_bucket,
            "PUBLIC_MEDIA_BASE_URL": self.public_media_base_url,
            "PUBLIC_APP_BASE_URL": self.public_app_base_url,
        }.items() if not value]
        if missing:
            raise RuntimeError(f"Missing production settings: {', '.join(missing)}")
        if not self.public_media_base_url.startswith("https://"):
            raise RuntimeError("PUBLIC_MEDIA_BASE_URL must use HTTPS in live mode")
        if not self.public_app_base_url.startswith("https://"):
            raise RuntimeError("PUBLIC_APP_BASE_URL must use HTTPS in live mode")

@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    settings.validate_production()
    return settings
