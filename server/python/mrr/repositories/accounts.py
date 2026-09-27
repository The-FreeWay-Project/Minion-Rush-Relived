"""Repository layer for the ``accounts`` table.

Database access only — no FastAPI, no HTTP concepts. All queries use bound
parameters. Password hashes are opaque strings here; hashing lives in
``mrr.security``.
"""

from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from mrr.config import load_settings
from mrr.db import open_connection

__all__ = ["Account", "AccountRepository"]

_TABLE_COLUMNS = "id, username, password_hash, created_at"


@dataclass(frozen=True)
class Account:
    """Internal data object for one row of the ``accounts`` table."""

    id: int
    username: str
    password_hash: str  # never expose outside the backend
    created_at: str


def _to_account(row: tuple) -> Account:
    return Account(
        id=row[0],
        username=row[1],
        password_hash=row[2],
        created_at=row[3],
    )


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


class AccountRepository:
    """CRUD access to the ``accounts`` table (short-lived connections)."""

    def __init__(self, database_path: str | Path | None = None) -> None:
        if database_path is None:
            database_path = load_settings().database_path
        self.database_path = Path(database_path)

    def create_account(self, *, username: str, password_hash: str) -> Account:
        """Insert one account and return the stored row.

        Raises ``sqlite3.IntegrityError`` when ``username`` already exists.
        """
        created_at = _utc_now()
        with open_connection(self.database_path) as conn:
            cursor = conn.execute(
                "INSERT INTO accounts (username, password_hash, created_at) VALUES (?, ?, ?)",
                (username, password_hash, created_at),
            )
            row = conn.execute(
                f"SELECT {_TABLE_COLUMNS} FROM accounts WHERE id = ?",
                (cursor.lastrowid,),
            ).fetchone()
        return _to_account(row)

    def get_account_by_id(self, id: int) -> Account | None:
        """Load an account by its internal primary key."""
        with open_connection(self.database_path) as conn:
            row = conn.execute(
                f"SELECT {_TABLE_COLUMNS} FROM accounts WHERE id = ?",
                (id,),
            ).fetchone()
        return _to_account(row) if row else None

    def get_account_by_username(self, username: str) -> Account | None:
        """Load an account by its unique username."""
        with open_connection(self.database_path) as conn:
            row = conn.execute(
                f"SELECT {_TABLE_COLUMNS} FROM accounts WHERE username = ?",
                (username,),
            ).fetchone()
        return _to_account(row) if row else None
