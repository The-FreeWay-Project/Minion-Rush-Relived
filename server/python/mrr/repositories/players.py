"""Repository layer for the ``players`` table.

Database access only — no FastAPI, no HTTP status codes, no request/response
objects. All queries use bound parameters.
"""

from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from mrr.config import load_settings
from mrr.db import open_connection

__all__ = ["Player", "PlayerRepository"]

_TABLE_COLUMNS = "id, player_id, display_name, level, coins, created_at"


@dataclass(frozen=True)
class Player:
    """Internal data object for one row of the ``players`` table."""

    id: int
    player_id: str
    display_name: str
    level: int
    coins: int
    created_at: str


def _to_player(row: tuple) -> Player:
    return Player(
        id=row[0],
        player_id=row[1],
        display_name=row[2],
        level=row[3],
        coins=row[4],
        created_at=row[5],
    )


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


class PlayerRepository:
    """CRUD access to the ``players`` table.

    Every public method opens a short-lived connection via
    ``open_connection()`` (commit on success, rollback on error) and closes it
    again — there is never a global open connection.
    """

    def __init__(self, database_path: str | Path | None = None) -> None:
        if database_path is None:
            database_path = load_settings().database_path
        self.database_path = Path(database_path)

    def create_player(
        self,
        *,
        player_id: str,
        display_name: str,
        level: int = 1,
        coins: int = 0,
        created_at: str | None = None,
    ) -> Player:
        """Insert one player and return the stored row.

        Raises ``sqlite3.IntegrityError`` when ``player_id`` already exists.
        """
        if created_at is None:
            created_at = _utc_now()
        with open_connection(self.database_path) as conn:
            cursor = conn.execute(
                "INSERT INTO players (player_id, display_name, level, coins, created_at) "
                "VALUES (?, ?, ?, ?, ?)",
                (player_id, display_name, level, coins, created_at),
            )
            row = conn.execute(
                f"SELECT {_TABLE_COLUMNS} FROM players WHERE id = ?",
                (cursor.lastrowid,),
            ).fetchone()
        return _to_player(row)

    def get_player_by_id(self, id: int) -> Player | None:
        """Load a player by its internal primary key."""
        with open_connection(self.database_path) as conn:
            row = conn.execute(
                f"SELECT {_TABLE_COLUMNS} FROM players WHERE id = ?",
                (id,),
            ).fetchone()
        return _to_player(row) if row else None

    def get_player_by_player_id(self, player_id: str) -> Player | None:
        """Load a player by its unique ``player_id``."""
        with open_connection(self.database_path) as conn:
            row = conn.execute(
                f"SELECT {_TABLE_COLUMNS} FROM players WHERE player_id = ?",
                (player_id,),
            ).fetchone()
        return _to_player(row) if row else None

    def delete_player(self, player_id: str) -> bool:
        """Delete one player by ``player_id``. Returns True when a row was removed."""
        with open_connection(self.database_path) as conn:
            cursor = conn.execute(
                "DELETE FROM players WHERE player_id = ?",
                (player_id,),
            )
        return cursor.rowcount > 0

    def list_players(self, limit: int = 100, offset: int = 0) -> list[Player]:
        """Return players ordered by internal id."""
        with open_connection(self.database_path) as conn:
            rows = conn.execute(
                f"SELECT {_TABLE_COLUMNS} FROM players ORDER BY id LIMIT ? OFFSET ?",
                (limit, offset),
            ).fetchall()
        return [_to_player(row) for row in rows]
