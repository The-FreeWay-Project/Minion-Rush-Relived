"""Repository layer for the ``player_state`` save-state table.

Database access only; all queries use bound parameters.
"""

from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from pathlib import Path

from mrr.config import load_settings
from mrr.db import open_connection

__all__ = ["PlayerState", "PlayerStateRepository"]

_TABLE_COLUMNS = "player_id, experience, level, coins, updated_at"


@dataclass(frozen=True)
class PlayerState:
    """Internal data object for one row of the ``player_state`` table."""

    player_id: int  # foreign key to players.id (internal integer id)
    experience: int
    level: int
    coins: int
    updated_at: str


def _to_state(row: tuple) -> PlayerState:
    return PlayerState(
        player_id=row[0],
        experience=row[1],
        level=row[2],
        coins=row[3],
        updated_at=row[4],
    )


class PlayerStateRepository:
    """Access to the ``player_state`` table (short-lived connections)."""

    def __init__(self, database_path: str | Path | None = None) -> None:
        if database_path is None:
            database_path = load_settings().database_path
        self.database_path = Path(database_path)

    def get_state(self, player_id: int) -> PlayerState | None:
        """Load the save state for a player, or None when it does not exist."""
        with open_connection(self.database_path) as conn:
            row = conn.execute(
                f"SELECT {_TABLE_COLUMNS} FROM player_state WHERE player_id = ?",
                (player_id,),
            ).fetchone()
        return _to_state(row) if row else None

    def create_state(
        self,
        *,
        player_id: int,
        experience: int = 0,
        level: int = 1,
        coins: int = 0,
        updated_at: str,
    ) -> PlayerState:
        """Insert a save-state row.

        Raises ``sqlite3.IntegrityError`` when the row already exists.
        """
        with open_connection(self.database_path) as conn:
            conn.execute(
                "INSERT INTO player_state (player_id, experience, level, coins, updated_at) "
                "VALUES (?, ?, ?, ?, ?)",
                (player_id, experience, level, coins, updated_at),
            )
            row = conn.execute(
                f"SELECT {_TABLE_COLUMNS} FROM player_state WHERE player_id = ?",
                (player_id,),
            ).fetchone()
        return _to_state(row)

    def get_or_create_state(self, *, player_id: int, updated_at: str) -> PlayerState:
        """Return the save state, creating it with defaults when missing."""
        existing = self.get_state(player_id)
        if existing is not None:
            return existing
        return self.create_state(player_id=player_id, updated_at=updated_at)

    def upsert_state(
        self,
        *,
        player_id: int,
        experience: int,
        level: int,
        coins: int,
        updated_at: str,
    ) -> PlayerState:
        """Insert or replace the save state for a player."""
        with open_connection(self.database_path) as conn:
            conn.execute(
                "INSERT INTO player_state (player_id, experience, level, coins, updated_at) "
                "VALUES (?, ?, ?, ?, ?) "
                "ON CONFLICT(player_id) DO UPDATE SET "
                "experience = excluded.experience, level = excluded.level, "
                "coins = excluded.coins, updated_at = excluded.updated_at",
                (player_id, experience, level, coins, updated_at),
            )
            row = conn.execute(
                f"SELECT {_TABLE_COLUMNS} FROM player_state WHERE player_id = ?",
                (player_id,),
            ).fetchone()
        return _to_state(row)
