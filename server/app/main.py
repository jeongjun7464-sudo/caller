from pathlib import Path
from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import HTMLResponse
from html import escape
from fastapi.staticfiles import StaticFiles
from .cards import create_card_image
from .config import Settings, get_settings
from .database import PublishDatabase
from .instagram import build_publisher
from .models import AccountResponse, PublishRequest, PublishResponse, PublishStatus, ProphecyCreateRequest, ProphecyCardRequest, ProphecyResponse
from .storage import build_storage

def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings(); settings.media_dir.mkdir(parents=True, exist_ok=True)
    app = FastAPI(title="Card Caller Instagram Publisher", version="1.0.0")
    app.mount("/media", StaticFiles(directory=settings.media_dir), name="media")
    database, storage, publisher = PublishDatabase(settings.database_path), build_storage(settings), build_publisher(settings)
    @app.get("/health")
    async def health(): return {"status":"ok", "instagramMode":settings.instagram_mode}
    @app.get("/api/v1/account", response_model=AccountResponse, response_model_by_alias=True)
    async def account(): return AccountResponse(username=settings.instagram_username, profileUrl=settings.instagram_profile_url)
    @app.post("/api/v1/publish", response_model=PublishResponse, response_model_by_alias=True)
    async def publish(request: PublishRequest, idempotency_key: str = Header(..., alias="Idempotency-Key")):
        if not idempotency_key.strip() or len(idempotency_key) > 100: raise HTTPException(400,"Invalid Idempotency-Key")
        try: request.validate_card()
        except ValueError as error: raise HTTPException(422,str(error)) from error
        existing = database.find(idempotency_key)
        if existing and existing.status != PublishStatus.FAILED: return existing
        if existing and not database.restart_failed(idempotency_key): return database.find(idempotency_key)
        if not existing and not database.begin(idempotency_key, request.card_id, request.publish_type.value): return database.find(idempotency_key)
        try:
            filename = f"{idempotency_key}.jpg"
            local_path = create_card_image(request.card_id, settings.media_dir / filename, 1920 if request.publish_type.value == "STORY" else 1350)
            public_url = storage.upload(local_path, filename)
            media_id, permalink, created_at = await publisher.publish(public_url, request.publish_type)
            return database.finish(idempotency_key,PublishStatus.SUCCEEDED,media_id,permalink,None,created_at)
        except Exception as error:
            database.finish(idempotency_key,PublishStatus.FAILED,error=str(error))
            raise HTTPException(502,"Instagram publishing failed") from error
        finally:
            try: storage.delete(filename)
            except Exception: pass
    def prophecy_response(row):
        return ProphecyResponse(roomCode=row["room_code"],token=row["token"],qrUrl=f"{settings.public_app_base_url.rstrip('/')}/p/{row['room_code']}/{row['token']}",expiresAt=row["expires_at"],cardId=row["card_id"],consumed=row["consumed"])
    @app.post("/api/v1/prophecies",response_model=ProphecyResponse,response_model_by_alias=True)
    async def create_prophecy(request:ProphecyCreateRequest):
        if request.card_id:
            try: PublishRequest(cardId=request.card_id,publishType="STORY").validate_card()
            except ValueError as error: raise HTTPException(422,str(error)) from error
        return prophecy_response(database.create_prophecy(request.card_id,request.expires_in_minutes))
    @app.put("/api/v1/prophecies/{room}/{token}/card",response_model=ProphecyResponse,response_model_by_alias=True)
    async def set_prophecy_card(room:str,token:str,request:ProphecyCardRequest):
        try: PublishRequest(cardId=request.card_id,publishType="STORY").validate_card()
        except ValueError as error: raise HTTPException(422,str(error)) from error
        row=database.set_prophecy_card(room,token,request.card_id)
        if not row: raise HTTPException(404,"Prophecy unavailable")
        return prophecy_response(row)
    @app.get("/api/v1/prophecies/{room}/{token}",response_model=ProphecyResponse,response_model_by_alias=True)
    async def prophecy_status(room:str,token:str):
        row=database.get_prophecy(room,token)
        if not row: raise HTTPException(404,"Prophecy not found")
        return prophecy_response(row)
    @app.get("/p/{room}/{token}",response_class=HTMLResponse)
    async def reveal_prophecy(room:str,token:str):
        result=database.consume_prophecy(room,token);state=result["state"]
        if state=="revealed":
            suit,rank=result["card_id"].split("_",1);symbols={"SPADES":"♠","HEARTS":"♥","CLUBS":"♣","DIAMONDS":"♦"};labels={"ACE":"A","TWO":"2","THREE":"3","FOUR":"4","FIVE":"5","SIX":"6","SEVEN":"7","EIGHT":"8","NINE":"9","TEN":"10","JACK":"J","QUEEN":"Q","KING":"K"};content=f'<div class="card">{symbols[suit]}<br>{labels[rank]}</div><h1>당신이 선택한 카드입니다</h1>'
        elif state=="preparing":content='<div class="spinner"></div><h1>예언을 준비하고 있습니다</h1><p>잠시 후 새로고침해 주세요.</p>'
        elif state=="expired":content='<h1>예언 시간이 만료되었습니다</h1>'
        elif state=="consumed":content='<h1>이미 공개된 일회용 예언입니다</h1>'
        else:content='<h1>예언을 찾을 수 없습니다</h1>'
        return HTMLResponse(f'''<!doctype html><html lang="ko"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Card Caller 예언</title><style>body{{margin:0;min-height:100vh;background:#07111f;color:white;display:grid;place-content:center;text-align:center;font-family:sans-serif}}.card{{width:240px;height:360px;border-radius:24px;background:white;color:#d91e36;display:grid;place-content:center;font-size:88px;font-weight:bold;margin:auto;box-shadow:0 20px 60px #0008}}p{{color:#9fb0c5}}.spinner{{width:60px;height:60px;border:7px solid #27405e;border-top-color:#3ddc84;border-radius:50%;animation:s 1s linear infinite;margin:auto}}@keyframes s{{to{{transform:rotate(360deg)}}}}</style>{content}</html>''')
    return app

app = create_app()
