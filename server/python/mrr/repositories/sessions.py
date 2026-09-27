"""Repository layer for the ``sessions`` table.

Only the SHA-256 hash of a session token is stored — never the token itself.
Database access only; all queries use bound parameters.
"""

from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from pathlib import Path

from mrr.config import load_settings
from mrr.db import open_connection

__all__ = ["Session", "SessionRepository"]

_TABLE_COLUMNS = "id, account_id, token_hash, created_at, expires_at"


@dataclass(frozen=True)
class Session:
    """Internal data object for one row of the ``sessions`` table."""

    id: int
    account_id: int
    token_hash: str
    created_at: str
    expires_at: str


def _to_session(row: tuple) -> Session:
    return Session(
        id=row[0],
        account_id=row[1],
        token_hash=row[2],
        created_at=row[3],
        expires_at=row[4],
    )


class SessionRepository:
    """Access to the ``sessions`` table (short-lived connections)."""

    def __init__(self, database_path: str | Path | None = None) -> None:
        if database_path is None:
            database_path = load_settings().database_path
        self.database_path = Path(database_path)

    def create_session(
        self,
        *,
        account_id: int,
        token_hash: str,
        created_at: str,
        expires_at: str,
    ) -> Session:
        """Insert one session.

        Raises ``sqlite3.IntegrityError`` when ``token_hash`` already exists.
        """
        with open_connection(self.database_path) as conn:
            cursor = conn.execute(
                "INSERT INTO sessions (account_id, token_hash, created_at, expires_at) "
                "VALUES (?, ?, ?, ?)",
                (account_id, token_hash, created_at, expires_at),
            )
            row = conn.execute(
                f"SELECT {_TABLE_COLUMNS} FROM sessions WHERE id = ?",
                (cursor.lastrowid,),
            ).fetchone()
        return _to_session(row)

    def get_session_by_token_hash(self, token_hash: str) -> Session | None:
        """Load a session by the hash of its bearer token."""
        with open_connection(self.database_path) as conn:
            row = conn.execute(
                f"SELECT {_TABLE_COLUMNS} FROM sessions WHERE token_hash = ?",
                (token_hash,),
            ).fetchone()
        return _to_session(row) if row else None

    def delete_session_by_token_hash(self, token_hash: str) -> bool:
        """Invalidate one session. Returns True when a row was removed."""
        with open_connection(self.database_path) as conn:
            cursor = conn.execute(
                "DELETE FROM sessions WHERE token_hash = ?",
                (token_hash,),
            )
        return cursor.rowcount > 0
