import sqlite3
from pathlib import Path
from .models import PublishResponse, PublishStatus, utc_now

class PublishDatabase:
    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True); self.path = path
        with self._connect() as db: db.execute("""CREATE TABLE IF NOT EXISTS publish_requests (
            request_id TEXT PRIMARY KEY, card_id TEXT NOT NULL, publish_type TEXT NOT NULL, status TEXT NOT NULL,
            media_id TEXT, permalink TEXT, created_at TEXT NOT NULL, error TEXT)""")
    def _connect(self): return sqlite3.connect(self.path)
    def find(self, request_id: str) -> PublishResponse | None:
        with self._connect() as db: row = db.execute("SELECT request_id,status,media_id,permalink,created_at,error FROM publish_requests WHERE request_id=?",(request_id,)).fetchone()
        return PublishResponse(requestId=row[0],status=PublishStatus(row[1]),mediaId=row[2],permalink=row[3],createdAt=row[4],error=row[5]) if row else None
    def begin(self, request_id: str, card_id: str, publish_type: str) -> bool:
        try:
            with self._connect() as db: db.execute("INSERT INTO publish_requests VALUES (?,?,?,?,?,?,?,?)",(request_id,card_id,publish_type,PublishStatus.PUBLISHING.value,None,None,utc_now().isoformat(),None))
            return True
        except sqlite3.IntegrityError: return False
    def restart_failed(self, request_id: str) -> bool:
        with self._connect() as db:
            cursor=db.execute("UPDATE publish_requests SET status=?,error=NULL WHERE request_id=? AND status=?",(PublishStatus.PUBLISHING.value,request_id,PublishStatus.FAILED.value))
            return cursor.rowcount==1
    def finish(self, request_id: str, status: PublishStatus, media_id: str | None=None, permalink: str | None=None, error: str | None=None, created_at=None):
        created_at = created_at or utc_now()
        with self._connect() as db: db.execute("UPDATE publish_requests SET status=?,media_id=?,permalink=?,created_at=?,error=? WHERE request_id=?",(status.value,media_id,permalink,created_at.isoformat(),error,request_id))
        return self.find(request_id)
