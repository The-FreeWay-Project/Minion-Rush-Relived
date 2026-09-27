"""Shared fixtures for MRR API tests.

Every test client runs against a temporary SQLite database — the developer's
``data/mrr.db`` is never touched.
"""

from __future__ import annotations

from pathlib import Path
from typing import Callable

import pytest
from fastapi.testclient import TestClient

from mrr.api.deps import get_database_path
from mrr.api.main import app
from mrr.db import init_schema, open_connection

DEFAULT_PASSWORD = "test-password"


@pytest.fixture()
def db_path(tmp_path: Path) -> Path:
    path = tmp_path / "test.db"
    with open_connection(path) as conn:
        init_schema(conn)
    return path


@pytest.fixture()
def client(db_path: Path):
    app.dependency_overrides[get_database_path] = lambda: db_path
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.pop(get_database_path, None)


@pytest.fixture()
def account_factory(client: TestClient) -> Callable[..., dict]:
    def _register(
        username: str = "testuser", password: str = DEFAULT_PASSWORD
    ) -> dict:
        response = client.post(
            "/api/v1/auth/register",
            json={"username": username, "password": password},
        )
        assert response.status_code == 201, response.text
        return response.json()

    return _register


@pytest.fixture()
def auth_factory(client: TestClient) -> Callable[..., dict]:
    def _login(
        username: str = "testuser", password: str = DEFAULT_PASSWORD
    ) -> dict[str, str]:
        response = client.post(
            "/api/v1/auth/login",
            json={"username": username, "password": password},
        )
        assert response.status_code == 200, response.text
        return {"Authorization": f"Bearer {response.json()['access_token']}"}

    return _login
