"""Database schema for MRR.

Currently holds only a minimal table for *synthetic local test players*.
No real player or account data belongs in here.
"""

from __future__ import annotations

import sqlite3

SCHEMA_STATEMENTS: tuple[str, ...] = (
    """
    CREATE TABLE IF NOT EXISTS players (
        id INTEGER PRIMARY KEY,
        player_id TEXT UNIQUE NOT NULL,
        display_name TEXT NOT NULL,
        level INTEGER NOT NULL DEFAULT 1,
        coins INTEGER NOT NULL DEFAULT 0,
        created_at TEXT NOT NULL
    )
    """,
)


def init_schema(conn: sqlite3.Connection) -> None:
    """Create missing tables. Safe to run repeatedly (never drops data)."""
    for statement in SCHEMA_STATEMENTS:
        conn.execute(statement)
    conn.commit()
