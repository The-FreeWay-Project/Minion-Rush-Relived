import sqlite3
from pathlib import Path

import pytest

from mrr.db import connect, init_schema, open_connection


def test_connection_creates_parent_directory(tmp_path: Path) -> None:
    db_path = tmp_path / "nested" / "dir" / "mrr.db"
    with open_connection(db_path) as conn:
        assert db_path.exists()
        assert conn.execute("PRAGMA foreign_keys").fetchone()[0] == 1


def test_connection_is_closed_after_use(tmp_path: Path) -> None:
    db_path = tmp_path / "mrr.db"
    with open_connection(db_path) as conn:
        held = conn
    with pytest.raises(sqlite3.ProgrammingError):
        held.execute("SELECT 1")


def test_schema_creates_players_table(tmp_path: Path) -> None:
    db_path = tmp_path / "mrr.db"
    with open_connection(db_path) as conn:
        init_schema(conn)
    with open_connection(db_path) as conn:
        rows = conn.execute(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'players'"
        ).fetchall()
    assert rows == [("players",)]


def test_players_columns(tmp_path: Path) -> None:
    db_path = tmp_path / "mrr.db"
    with open_connection(db_path) as conn:
        init_schema(conn)
        columns = [row[1] for row in conn.execute("PRAGMA table_info(players)")]
    assert columns == ["id", "player_id", "display_name", "level", "coins", "created_at"]


def test_schema_init_is_repeatable(tmp_path: Path) -> None:
    db_path = tmp_path / "mrr.db"
    with open_connection(db_path) as conn:
        init_schema(conn)
    with open_connection(db_path) as conn:
        conn.execute(
            "INSERT INTO players (player_id, display_name, created_at) VALUES (?, ?, ?)",
            ("test-player-1", "Synthetic Test Player", "2026-01-01T00:00:00Z"),
        )
    with open_connection(db_path) as conn:
        init_schema(conn)  # second run must not fail or drop data
        count = conn.execute("SELECT COUNT(*) FROM players").fetchone()[0]
    assert count == 1


def test_schema_init_keeps_data_in_own_file(tmp_path: Path) -> None:
    db_path = tmp_path / "isolated.db"
    with open_connection(db_path) as conn:
        init_schema(conn)
        tables = [
            row[0]
            for row in conn.execute("SELECT name FROM sqlite_master WHERE type = 'table'").fetchall()
            if not row[0].startswith("sqlite_")
        ]
    assert tables == ["players"]
    assert db_path.exists()
