from pathlib import Path
import shutil
import boto3
from .config import Settings

class ImageStorage:
    def upload(self, source: Path, object_name: str) -> str:
        raise NotImplementedError
    def delete(self, object_name: str) -> None:
        raise NotImplementedError

class LocalImageStorage(ImageStorage):
    def __init__(self, settings: Settings): self.settings = settings
    def upload(self, source: Path, object_name: str) -> str:
        target = self.settings.media_dir / object_name
        target.parent.mkdir(parents=True, exist_ok=True)
        if source.resolve() != target.resolve(): shutil.copyfile(source, target)
        return f"{self.settings.public_media_base_url.rstrip('/')}/{object_name}"
    def delete(self, object_name: str) -> None:
        (self.settings.media_dir / object_name).unlink(missing_ok=True)

class S3ImageStorage(ImageStorage):
    def __init__(self, settings: Settings):
        self.settings = settings
        self.client = boto3.client("s3", region_name=settings.s3_region or None, endpoint_url=settings.s3_endpoint_url or None,
            aws_access_key_id=settings.s3_access_key_id or None, aws_secret_access_key=settings.s3_secret_access_key or None)
    def upload(self, source: Path, object_name: str) -> str:
        self.client.upload_file(str(source), self.settings.s3_bucket, object_name, ExtraArgs={"ContentType":"image/jpeg"})
        return f"{self.settings.public_media_base_url.rstrip('/')}/{object_name}"
    def delete(self, object_name: str) -> None:
        self.client.delete_object(Bucket=self.settings.s3_bucket, Key=object_name)

def build_storage(settings: Settings) -> ImageStorage:
    return S3ImageStorage(settings) if settings.instagram_mode == "live" else LocalImageStorage(settings)
