import hashlib
import hmac
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.config import Settings
from app.database import Base, get_db
from app.main import create_app


def client(tmp_path):
    engine = create_engine(f"sqlite:///{tmp_path/'test.db'}")
    Base.metadata.create_all(engine)
    factory = sessionmaker(engine, expire_on_commit=False)
    app = create_app(
        Settings(
            database_url=f"sqlite:///{tmp_path/'unused.db'}",
            instagram_app_secret="secret",
        ),
        create_tables=False,
    )

    def override():
        with factory() as db:
            yield db

    app.dependency_overrides[get_db] = override
    return TestClient(app), factory


def test_session_and_command_are_idempotent(tmp_path):
    c, _ = client(tmp_path)
    payload = {"requestId": "session-1", "expiresInMinutes": 10}
    a = c.post("/api/v1/sessions", json=payload).json()
    b = c.post("/api/v1/sessions", json=payload).json()
    assert a["sessionId"] == b["sessionId"]
    assert a["roomCode"] == b["roomCode"] and len(a["roomCode"]) == 6
    cmd = {"messageId": "message-1", "type": "INCOMING", "cardId": "SPADES_SEVEN"}
    assert c.post(f"/api/v1/sessions/{a['sessionId']}/commands", json=cmd).json()[
        "accepted"
    ]
    assert c.post(f"/api/v1/sessions/{a['sessionId']}/commands", json=cmd).json()[
        "duplicate"
    ]


def test_reveal_token_is_one_time_and_hash_only(tmp_path):
    c, factory = client(tmp_path)
    created = c.post(
        "/api/v1/reveals",
        json={"requestId": "reveal-1", "cardId": "HEARTS_ACE", "expiresInSeconds": 60},
    ).json()
    token = created["token"]
    from app.models import RevealToken

    with factory() as db:
        row = db.get(RevealToken, "reveal-1")
        assert (
            row.token_hash == hashlib.sha256(token.encode()).hexdigest()
            and token != row.token_hash
        )
    assert c.get(f"/api/v1/reveals/{token}").json()["cardId"] == "HEARTS_ACE"
    assert c.get(f"/api/v1/reveals/{token}").status_code == 410


def test_mock_instagram_background_publish(tmp_path):
    c, _ = client(tmp_path)
    body = {"requestId": "ig-1", "cardId": "CLUBS_KING", "publishType": "STORY"}
    assert c.post("/api/v1/instagram/publish", json=body).status_code == 202
    result = c.get("/api/v1/instagram/publish/ig-1").json()
    assert result["status"] == "SUCCEEDED" and result["mediaId"].startswith("mock_")


def test_webhook_signature(tmp_path):
    c, _ = client(tmp_path)
    raw = b'{"entry":[]}'
    sig = "sha256=" + hmac.new(b"secret", raw, hashlib.sha256).hexdigest()
    assert (
        c.post(
            "/api/v1/instagram/webhook",
            content=raw,
            headers={"X-Hub-Signature-256": sig},
        ).status_code
        == 200
    )
    assert c.post("/api/v1/instagram/webhook", content=raw).status_code == 401
