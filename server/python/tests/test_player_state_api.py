"""API tests for the protected player save state."""

from __future__ import annotations

from typing import Callable

from fastapi.testclient import TestClient


def test_state_requires_authentication(client: TestClient) -> None:
    assert client.get("/api/v1/player/state").status_code == 401
    assert (
        client.put(
            "/api/v1/player/state",
            json={"experience": 1, "level": 1, "coins": 1},
        ).status_code
        == 401
    )


def test_get_state_creates_default_state(
    client: TestClient, account_factory: Callable[..., dict],
    auth_factory: Callable[..., dict],
) -> None:
    account_factory()
    headers = auth_factory()

    response = client.get("/api/v1/player/state", headers=headers)
    assert response.status_code == 200
    data = response.json()
    assert data["experience"] == 0
    assert data["level"] == 1
    assert data["coins"] == 0
    assert isinstance(data["player_id"], int)
    assert data["updated_at"]


def test_put_state_updates_and_get_returns_it(
    client: TestClient, account_factory: Callable[..., dict],
    auth_factory: Callable[..., dict],
) -> None:
    account_factory()
    headers = auth_factory()

    put = client.put(
        "/api/v1/player/state",
        headers=headers,
        json={"experience": 250, "level": 3, "coins": 1500},
    )
    assert put.status_code == 200
    assert put.json()["experience"] == 250
    assert put.json()["level"] == 3
    assert put.json()["coins"] == 1500

    fetched = client.get("/api/v1/player/state", headers=headers).json()
    assert fetched["experience"] == 250
    assert fetched["level"] == 3
    assert fetched["coins"] == 1500


def test_put_state_before_get_creates_row(
    client: TestClient, account_factory: Callable[..., dict],
    auth_factory: Callable[..., dict],
) -> None:
    account_factory()
    headers = auth_factory()
    put = client.put(
        "/api/v1/player/state",
        headers=headers,
        json={"experience": 10, "level": 2, "coins": 5},
    )
    assert put.status_code == 200
    assert client.get("/api/v1/player/state", headers=headers).json()["coins"] == 5


def test_put_state_rejects_invalid_values(
    client: TestClient, account_factory: Callable[..., dict],
    auth_factory: Callable[..., dict],
) -> None:
    account_factory()
    headers = auth_factory()
    for payload in (
        {"experience": -1, "level": 1, "coins": 0},
        {"experience": 0, "level": 0, "coins": 0},
        {"experience": 0, "level": 1, "coins": -5},
    ):
        response = client.put("/api/v1/player/state", headers=headers, json=payload)
        assert response.status_code == 422, payload


def test_state_of_other_account_cannot_be_changed(
    client: TestClient, account_factory: Callable[..., dict],
    auth_factory: Callable[..., dict],
) -> None:
    account_factory(username="state-a")
    account_factory(username="state-b")
    headers_a = auth_factory(username="state-a")
    headers_b = auth_factory(username="state-b")

    put_a = client.put(
        "/api/v1/player/state",
        headers=headers_a,
        json={"experience": 999, "level": 9, "coins": 9999},
    )
    assert put_a.status_code == 200

    # Account B still sees its own (untouched) state.
    state_b = client.get("/api/v1/player/state", headers=headers_b).json()
    assert state_b["experience"] == 0
    assert state_b["level"] == 1
    assert state_b["coins"] == 0

    # Account A's state is unchanged by anything B does either.
    state_a = client.get("/api/v1/player/state", headers=headers_a).json()
    assert state_a["experience"] == 999
    assert state_a["player_id"] != state_b["player_id"]


def test_state_uses_distinct_rows_per_account(
    client: TestClient, account_factory: Callable[..., dict],
    auth_factory: Callable[..., dict],
) -> None:
    account_factory(username="row-a")
    account_factory(username="row-b")
    headers_a = auth_factory(username="row-a")
    headers_b = auth_factory(username="row-b")

    player_a = client.get("/api/v1/player/state", headers=headers_a).json()["player_id"]
    player_b = client.get("/api/v1/player/state", headers=headers_b).json()["player_id"]
    assert player_a != player_b
