"""API tests for registration, login, sessions and logout."""

from __future__ import annotations

from pathlib import Path
from typing import Callable

from fastapi.testclient import TestClient

from mrr.db import open_connection
from mrr.repositories.sessions import SessionRepository
from mrr.security import generate_session_token, hash_token, verify_password

from tests.conftest import DEFAULT_PASSWORD


def test_register_returns_account(client: TestClient) -> None:
    response = client.post(
        "/api/v1/auth/register",
        json={"username": "testuser", "password": DEFAULT_PASSWORD},
    )
    assert response.status_code == 201
    data = response.json()
    assert data["account_id"] >= 1
    assert data["username"] == "testuser"
    assert "password" not in data
    assert "password_hash" not in data


def test_register_duplicate_username_returns_409(client: TestClient) -> None:
    payload = {"username": "testuser", "password": DEFAULT_PASSWORD}
    assert client.post("/api/v1/auth/register", json=payload).status_code == 201
    response = client.post("/api/v1/auth/register", json=payload)
    assert response.status_code == 409


def test_register_empty_username_returns_422(client: TestClient) -> None:
    response = client.post(
        "/api/v1/auth/register",
        json={"username": "", "password": DEFAULT_PASSWORD},
    )
    assert response.status_code == 422


def test_register_empty_password_returns_422(client: TestClient) -> None:
    response = client.post(
        "/api/v1/auth/register",
        json={"username": "testuser", "password": ""},
    )
    assert response.status_code == 422


def test_password_is_hashed_in_database(client: TestClient, db_path: Path) -> None:
    client.post(
        "/api/v1/auth/register",
        json={"username": "testuser", "password": DEFAULT_PASSWORD},
    )
    with open_connection(db_path) as conn:
        row = conn.execute(
            "SELECT password_hash FROM accounts WHERE username = ?", ("testuser",)
        ).fetchone()
    stored = row[0]
    assert stored != DEFAULT_PASSWORD
    assert DEFAULT_PASSWORD not in stored
    assert stored.startswith("pbkdf2_sha256$")
    assert verify_password(stored, DEFAULT_PASSWORD) is True
    assert verify_password(stored, "wrong-password") is False


def test_login_success_returns_bearer_token(
    client: TestClient, account_factory: Callable[..., dict]
) -> None:
    account_factory()
    response = client.post(
        "/api/v1/auth/login",
        json={"username": "testuser", "password": DEFAULT_PASSWORD},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["token_type"] == "bearer"
    assert data["expires_in"] == 86400
    assert isinstance(data["access_token"], str) and len(data["access_token"]) >= 32


def test_login_wrong_password_returns_401(
    client: TestClient, account_factory: Callable[..., dict]
) -> None:
    account_factory()
    response = client.post(
        "/api/v1/auth/login",
        json={"username": "testuser", "password": "wrong-password"},
    )
    assert response.status_code == 401


def test_login_unknown_user_returns_same_error_as_wrong_password(
    client: TestClient, account_factory: Callable[..., dict]
) -> None:
    account_factory()
    wrong_password = client.post(
        "/api/v1/auth/login",
        json={"username": "testuser", "password": "wrong-password"},
    )
    unknown_user = client.post(
        "/api/v1/auth/login",
        json={"username": "ghost-user", "password": DEFAULT_PASSWORD},
    )
    assert wrong_password.status_code == 401
    assert unknown_user.status_code == 401
    # Identical detail: the API must not reveal which part was wrong.
    assert wrong_password.json()["detail"] == unknown_user.json()["detail"]


def test_token_is_stored_hashed_not_plaintext(
    client: TestClient, db_path: Path, account_factory: Callable[..., dict],
    auth_factory: Callable[..., dict],
) -> None:
    account_factory()
    headers = auth_factory()
    token = headers["Authorization"].removeprefix("Bearer ")

    with open_connection(db_path) as conn:
        rows = conn.execute("SELECT token_hash FROM sessions").fetchall()
    assert len(rows) == 1
    stored = rows[0][0]
    assert stored != token
    assert token not in stored
    assert stored == hash_token(token)


def test_valid_token_authenticates_profile(
    client: TestClient, account_factory: Callable[..., dict],
    auth_factory: Callable[..., dict],
) -> None:
    account = account_factory()
    headers = auth_factory()
    response = client.get("/api/v1/profile", headers=headers)
    assert response.status_code == 200
    assert response.json()["account_id"] == account["account_id"]


def test_profile_without_token_returns_401(client: TestClient) -> None:
    response = client.get("/api/v1/profile")
    assert response.status_code == 401
    assert response.headers.get("www-authenticate") == "Bearer"


def test_profile_with_invalid_token_returns_401(client: TestClient) -> None:
    response = client.get(
        "/api/v1/profile",
        headers={"Authorization": "Bearer not-a-real-token"},
    )
    assert response.status_code == 401


def test_expired_token_returns_401(
    client: TestClient, db_path: Path, account_factory: Callable[..., dict]
) -> None:
    account = account_factory()
    token = generate_session_token()
    SessionRepository(db_path).create_session(
        account_id=account["account_id"],
        token_hash=hash_token(token),
        created_at="2020-01-01T00:00:00+00:00",
        expires_at="2020-01-02T00:00:00+00:00",
    )
    response = client.get(
        "/api/v1/profile", headers={"Authorization": f"Bearer {token}"}
    )
    assert response.status_code == 401


def test_logout_invalidates_token(
    client: TestClient, account_factory: Callable[..., dict],
    auth_factory: Callable[..., dict],
) -> None:
    account_factory()
    headers = auth_factory()
    assert client.get("/api/v1/profile", headers=headers).status_code == 200

    logout = client.post("/api/v1/auth/logout", headers=headers)
    assert logout.status_code == 204

    assert client.get("/api/v1/profile", headers=headers).status_code == 401
    assert client.post("/api/v1/auth/logout", headers=headers).status_code == 401


def test_logout_without_token_returns_401(client: TestClient) -> None:
    response = client.post("/api/v1/auth/logout")
    assert response.status_code == 401


def test_login_sessions_are_persisted_per_login(
    client: TestClient, db_path: Path, account_factory: Callable[..., dict],
    auth_factory: Callable[..., dict],
) -> None:
    account_factory()
    auth_factory()
    auth_factory()
    with open_connection(db_path) as conn:
        count = conn.execute("SELECT COUNT(*) FROM sessions").fetchone()[0]
    assert count == 2
