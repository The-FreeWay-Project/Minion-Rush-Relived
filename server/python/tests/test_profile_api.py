"""API tests for the protected profile and player ownership."""

from __future__ import annotations

from typing import Callable

from fastapi.testclient import TestClient


def test_profile_requires_authentication(client: TestClient) -> None:
    assert client.get("/api/v1/profile").status_code == 401


def test_profile_returns_account_and_own_player(
    client: TestClient, account_factory: Callable[..., dict],
    auth_factory: Callable[..., dict],
) -> None:
    account = account_factory(username="profile-user")
    headers = auth_factory(username="profile-user")

    response = client.get("/api/v1/profile", headers=headers)
    assert response.status_code == 200
    data = response.json()
    assert data["account_id"] == account["account_id"]
    assert data["username"] == "profile-user"

    player = data["player"]
    assert player is not None
    assert player["player_id"] == "player-0001"
    assert player["display_name"] == "profile-user"
    assert player["level"] == 1
    assert player["coins"] == 0
    # No internal ids or hashes leak beyond the documented shape.
    assert set(player) == {"player_id", "display_name", "level", "coins"}


def test_profiles_are_isolated_between_accounts(
    client: TestClient, account_factory: Callable[..., dict],
    auth_factory: Callable[..., dict],
) -> None:
    account_factory(username="user-a")
    account_factory(username="user-b")
    headers_a = auth_factory(username="user-a")
    headers_b = auth_factory(username="user-b")

    profile_a = client.get("/api/v1/profile", headers=headers_a).json()
    profile_b = client.get("/api/v1/profile", headers=headers_b).json()

    assert profile_a["username"] == "user-a"
    assert profile_b["username"] == "user-b"
    assert profile_a["player"]["player_id"] != profile_b["player"]["player_id"]
    # Each account only ever sees its own player.
    assert profile_a["player"]["display_name"] == "user-a"
    assert profile_b["player"]["display_name"] == "user-b"


def test_owner_can_fetch_own_player(
    client: TestClient, account_factory: Callable[..., dict],
    auth_factory: Callable[..., dict],
) -> None:
    account_factory(username="owner")
    headers = auth_factory(username="owner")
    player_id = client.get("/api/v1/profile", headers=headers).json()["player"]["player_id"]

    response = client.get(f"/api/v1/players/{player_id}", headers=headers)
    assert response.status_code == 200
    assert response.json()["player_id"] == player_id


def test_account_cannot_fetch_other_accounts_player(
    client: TestClient, account_factory: Callable[..., dict],
    auth_factory: Callable[..., dict],
) -> None:
    account_factory(username="intruder")
    account_factory(username="victim")
    intruder_headers = auth_factory(username="intruder")
    victim_headers = auth_factory(username="victim")
    victim_player_id = client.get(
        "/api/v1/profile", headers=victim_headers
    ).json()["player"]["player_id"]

    response = client.get(
        f"/api/v1/players/{victim_player_id}", headers=intruder_headers
    )
    assert response.status_code == 404


def test_owned_player_requires_authentication(
    client: TestClient, account_factory: Callable[..., dict],
    auth_factory: Callable[..., dict],
) -> None:
    account_factory(username="hidden")
    headers = auth_factory(username="hidden")
    player_id = client.get("/api/v1/profile", headers=headers).json()["player"]["player_id"]

    assert client.get(f"/api/v1/players/{player_id}").status_code == 401
    assert client.delete(f"/api/v1/players/{player_id}").status_code == 401


def test_unowned_synthetic_player_stays_public(client: TestClient) -> None:
    create = client.post(
        "/api/v1/players",
        json={"player_id": "synthetic-open", "display_name": "Open"},
    )
    assert create.status_code == 201
    assert client.get("/api/v1/players/synthetic-open").status_code == 200
