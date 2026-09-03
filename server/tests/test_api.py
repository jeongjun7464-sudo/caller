from pathlib import Path
from fastapi.testclient import TestClient
from PIL import Image
from app.cards import create_card_image
from app.config import Settings
from app.main import create_app
from app.instagram import MetaInstagramPublisher
from app.models import PublishType
import asyncio, httpx

def settings(tmp_path: Path) -> Settings:
    return Settings(instagram_mode="mock",database_path=tmp_path/"test.db",media_dir=tmp_path/"media",public_media_base_url="http://testserver/media",instagram_username="test_magician",instagram_profile_url="https://instagram.com/test_magician/")

def test_story_image_is_1080_by_1920(tmp_path):
    path=create_card_image("HEARTS_ACE",tmp_path/"card.jpg")
    with Image.open(path) as image: assert image.size==(1080,1920) and image.format=="JPEG"

def test_mock_story_publish_full_flow(tmp_path):
    client=TestClient(create_app(settings(tmp_path)))
    response=client.post("/api/v1/publish",headers={"Idempotency-Key":"req-story-1"},json={"cardId":"HEARTS_ACE","publishType":"STORY"})
    assert response.status_code==200; body=response.json(); assert body["status"]=="SUCCEEDED" and body["mediaId"].startswith("mock_story")

def test_same_idempotency_key_does_not_publish_twice(tmp_path):
    client=TestClient(create_app(settings(tmp_path))); headers={"Idempotency-Key":"same-request"}; payload={"cardId":"SPADES_KING","publishType":"FEED"}
    first=client.post("/api/v1/publish",headers=headers,json=payload).json(); second=client.post("/api/v1/publish",headers=headers,json=payload).json()
    assert first==second

def test_invalid_card_is_rejected(tmp_path):
    client=TestClient(create_app(settings(tmp_path)))
    assert client.post("/api/v1/publish",headers={"Idempotency-Key":"bad"},json={"cardId":"JOKER","publishType":"STORY"}).status_code==422

def test_account_has_no_secret(tmp_path):
    body=TestClient(create_app(settings(tmp_path))).get("/api/v1/account").json()
    assert body=={"username":"test_magician","profileUrl":"https://instagram.com/test_magician/"}
    assert "token" not in str(body).lower()

def test_meta_client_creates_waits_publishes_and_reads_media(tmp_path):
    calls=[]
    def handler(request:httpx.Request):
        calls.append((request.method,request.url.path,str(request.url.query)))
        if request.url.path.endswith("/media"): return httpx.Response(200,json={"id":"container-1"})
        if request.url.path.endswith("/container-1"): return httpx.Response(200,json={"status_code":"FINISHED"})
        if request.url.path.endswith("/media_publish"): return httpx.Response(200,json={"id":"media-1"})
        return httpx.Response(200,json={"permalink":"https://instagram.com/p/media-1/","timestamp":"2026-09-02T00:00:00Z"})
    config=settings(tmp_path).model_copy(update={"instagram_mode":"live","instagram_access_token":"secret","instagram_user_id":"ig-1","app_secret":"app-secret","s3_bucket":"bucket","public_media_base_url":"https://cdn.example.com"})
    publisher=MetaInstagramPublisher(config,httpx.AsyncClient(transport=httpx.MockTransport(handler)))
    media_id,permalink,_=asyncio.run(publisher.publish("https://cdn.example.com/card.jpg",PublishType.STORY))
    assert media_id=="media-1" and permalink.endswith("media-1/")
    assert [item[1] for item in calls]==["/v23.0/ig-1/media","/v23.0/container-1","/v23.0/ig-1/media_publish","/v23.0/media-1"]

def test_prophecy_prepares_reveals_once(tmp_path):
    client=TestClient(create_app(settings(tmp_path)))
    created=client.post("/api/v1/prophecies",json={"expiresInMinutes":5}).json()
    preparing=client.get(f"/p/{created['roomCode']}/{created['token']}")
    assert "예언을 준비" in preparing.text
    client.put(f"/api/v1/prophecies/{created['roomCode']}/{created['token']}/card",json={"cardId":"HEARTS_ACE"}).raise_for_status()
    first=client.get(f"/p/{created['roomCode']}/{created['token']}")
    second=client.get(f"/p/{created['roomCode']}/{created['token']}")
    assert "♥" in first.text and "이미 공개" in second.text

def test_each_audience_gets_unique_room_and_token(tmp_path):
    client=TestClient(create_app(settings(tmp_path)))
    a=client.post("/api/v1/prophecies",json={"cardId":"CLUBS_TWO"}).json();b=client.post("/api/v1/prophecies",json={"cardId":"DIAMONDS_QUEEN"}).json()
    assert a["roomCode"]!=b["roomCode"] and a["token"]!=b["token"]

def test_live_session_websocket_receives_idempotent_command(tmp_path):
    client=TestClient(create_app(settings(tmp_path)))
    code=client.post("/api/v1/sessions",json={"expiresInMinutes":10}).json()["code"]
    with client.websocket_connect(f"/ws/sessions/{code}") as socket:
        assert socket.receive_json()["type"]=="CONNECTED"
        payload={"type":"REVEAL","cardId":"SPADES_SEVEN","message":"검은 카드입니다"}
        first=client.post(f"/api/v1/sessions/{code}/commands",headers={"Idempotency-Key":"cmd-1"},json=payload)
        assert first.json()=={"accepted":True,"duplicate":False}
        assert socket.receive_json()["cardId"]=="SPADES_SEVEN"
        duplicate=client.post(f"/api/v1/sessions/{code}/commands",headers={"Idempotency-Key":"cmd-1"},json=payload)
        assert duplicate.json()=={"accepted":False,"duplicate":True}

def test_invalid_session_code_is_rejected(tmp_path):
    client=TestClient(create_app(settings(tmp_path)))
    assert client.post("/api/v1/sessions/UNKNOWN/reaction",json={"emoji":"👏"}).status_code==404
