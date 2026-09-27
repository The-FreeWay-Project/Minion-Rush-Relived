from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from mrr.api.deps import get_player_service
from mrr.api.main import app
from mrr.db import init_schema, open_connection
from mrr.services.players import PlayerService


@pytest.fixture()
def db_path(tmp_path: Path) -> Path:
    path = tmp_path / "api.db"
    with open_connection(path) as conn:
        init_schema(conn)
    return path


@pytest.fixture()
def client(db_path: Path):
    service = PlayerService(db_path)
    app.dependency_overrides[get_player_service] = lambda: service
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.pop(get_player_service, None)


def test_post_valid_player_returns_201(client: TestClient) -> None:
    response = client.post(
        "/api/v1/players",
        json={"player_id": "test-001", "display_name": "TestMinion", "level": 10, "coins": 5000},
    )
    assert response.status_code == 201
    data = response.json()
    assert data["id"] >= 1
    assert data["player_id"] == "test-001"
    assert data["display_name"] == "TestMinion"
    assert data["level"] == 10
    assert data["coins"] == 5000
    assert isinstance(data["created_at"], str) and data["created_at"]


def test_post_uses_defaults_when_level_and_coins_omitted(client: TestClient) -> None:
    response = client.post(
        "/api/v1/players",
        json={"player_id": "test-defaults", "display_name": "Defaults"},
    )
    assert response.status_code == 201
    data = response.json()
    assert data["level"] == 1
    assert data["coins"] == 0


def test_post_player_is_stored_in_database(client: TestClient, db_path: Path) -> None:
    response = client.post(
        "/api/v1/players",
        json={"player_id": "test-stored", "display_name": "Stored"},
    )
    assert response.status_code == 201
    with open_connection(db_path) as conn:
        row = conn.execute(
            "SELECT player_id, display_name FROM players WHERE player_id = ?",
            ("test-stored",),
        ).fetchone()
    assert row == ("test-stored", "Stored")


def test_post_duplicate_player_id_returns_409(client: TestClient) -> None:
    payload = {"player_id": "test-dup", "display_name": "First"}
    assert client.post("/api/v1/players", json=payload).status_code == 201
    response = client.post("/api/v1/players", json={**payload, "display_name": "Second"})
    assert response.status_code == 409


@pytest.mark.parametrize(
    "payload",
    [
        {"player_id": "", "display_name": "Ok"},
        {"player_id": "ok", "display_name": ""},
        {"player_id": "ok", "display_name": "Ok", "level": 0},
        {"player_id": "ok", "display_name": "Ok", "level": -3},
        {"player_id": "ok", "display_name": "Ok", "coins": -1},
        {"player_id": "ok"},
        {"display_name": "Ok"},
    ],
)
def test_post_invalid_payload_returns_422(client: TestClient, payload) -> None:
    response = client.post("/api/v1/players", json=payload)
    assert response.status_code == 422


def test_get_existing_player_returns_200(client: TestClient) -> None:
    client.post("/api/v1/players", json={"player_id": "test-get", "display_name": "Getter"})
    response = client.get("/api/v1/players/test-get")
    assert response.status_code == 200
    data = response.json()
    assert data["player_id"] == "test-get"
    assert data["display_name"] == "Getter"


def test_get_unknown_player_returns_404(client: TestClient) -> None:
    response = client.get("/api/v1/players/does-not-exist")
    assert response.status_code == 404


def test_delete_existing_player_returns_204(client: TestClient) -> None:
    client.post("/api/v1/players", json={"player_id": "test-del", "display_name": "Deleter"})
    response = client.delete("/api/v1/players/test-del")
    assert response.status_code == 204
    assert client.get("/api/v1/players/test-del").status_code == 404


def test_delete_unknown_player_returns_404(client: TestClient) -> None:
    response = client.delete("/api/v1/players/does-not-exist")
    assert response.status_code == 404


def test_list_empty_returns_empty_array(client: TestClient) -> None:
    response = client.get("/api/v1/players")
    assert response.status_code == 200
    assert response.json() == []


def test_list_returns_all_players(client: TestClient) -> None:
    for i in range(3):
        response = client.post(
            "/api/v1/players",
            json={"player_id": f"test-list-{i}", "display_name": f"Listed {i}"},
        )
        assert response.status_code == 201
    response = client.get("/api/v1/players")
    assert response.status_code == 200
    data = response.json()
    assert len(data) == 3
    assert [p["player_id"] for p in data] == ["test-list-0", "test-list-1", "test-list-2"]
