import sqlite3
import secrets
from datetime import datetime, timedelta, timezone
from pathlib import Path
from .models import PublishResponse, PublishStatus, utc_now

class PublishDatabase:
    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True); self.path = path
        with self._connect() as db:
            db.execute("""CREATE TABLE IF NOT EXISTS publish_requests (
            request_id TEXT PRIMARY KEY, card_id TEXT NOT NULL, publish_type TEXT NOT NULL, status TEXT NOT NULL,
            media_id TEXT, permalink TEXT, created_at TEXT NOT NULL, error TEXT)""")
            db.execute("""CREATE TABLE IF NOT EXISTS prophecies (
            room_code TEXT NOT NULL, token TEXT NOT NULL UNIQUE, card_id TEXT, expires_at TEXT NOT NULL,
            consumed_at TEXT, created_at TEXT NOT NULL, PRIMARY KEY(room_code,token))""")
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
    def create_prophecy(self, card_id: str | None, expiry_minutes: int):
        expires=utc_now()+timedelta(minutes=expiry_minutes)
        for _ in range(20):
            room=f"{secrets.randbelow(900000)+100000}";token=secrets.token_urlsafe(24)
            try:
                with self._connect() as db: db.execute("INSERT INTO prophecies VALUES (?,?,?,?,?,?)",(room,token,card_id,expires.isoformat(),None,utc_now().isoformat()))
                return self.get_prophecy(room,token)
            except sqlite3.IntegrityError: continue
        raise RuntimeError("Could not allocate prophecy room")
    def get_prophecy(self,room:str,token:str):
        with self._connect() as db: row=db.execute("SELECT room_code,token,card_id,expires_at,consumed_at FROM prophecies WHERE room_code=? AND token=?",(room,token)).fetchone()
        return {"room_code":row[0],"token":row[1],"card_id":row[2],"expires_at":row[3],"consumed":row[4] is not None} if row else None
    def set_prophecy_card(self,room:str,token:str,card_id:str):
        with self._connect() as db: cursor=db.execute("UPDATE prophecies SET card_id=? WHERE room_code=? AND token=? AND consumed_at IS NULL AND expires_at>?",(card_id,room,token,utc_now().isoformat()))
        return self.get_prophecy(room,token) if cursor.rowcount else None
    def consume_prophecy(self,room:str,token:str):
        now=utc_now().isoformat()
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            row=db.execute("SELECT card_id,expires_at,consumed_at FROM prophecies WHERE room_code=? AND token=?",(room,token)).fetchone()
            if not row:return {"state":"missing"}
            if row[2]:return {"state":"consumed"}
            if row[1]<=now:return {"state":"expired"}
            if not row[0]:return {"state":"preparing"}
            db.execute("UPDATE prophecies SET consumed_at=? WHERE room_code=? AND token=? AND consumed_at IS NULL",(now,room,token))
            return {"state":"revealed","card_id":row[0]}
