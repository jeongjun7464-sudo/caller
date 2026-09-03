from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    environment: str = "development"
    database_url: str = "sqlite:///./cardcaller.db"
    redis_url: str = "redis://localhost:6379/0"
    cors_origins: str = "http://localhost:3000"
    public_web_url: str = "http://localhost:3000"
    instagram_mode: str = "mock"
    instagram_access_token: str = ""
    instagram_user_id: str = ""
    instagram_app_secret: str = ""
    meta_graph_version: str = "v23.0"
    webhook_verify_token: str = ""
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    @property
    def allowed_origins(self) -> list[str]:
        return [x.strip() for x in self.cors_origins.split(",") if x.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
