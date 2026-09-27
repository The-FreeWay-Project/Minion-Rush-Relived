"""Database schema for MRR.

Contains only local/synthetic test data: test players, dev accounts, their
sessions and their save state. No real player or account data belongs here.

Schema evolution is idempotent: ``init_schema`` can run repeatedly against
fresh and pre-existing databases without dropping data.
"""

from __future__ import annotations

import sqlite3

SCHEMA_STATEMENTS: tuple[str, ...] = (
    # 1) accounts first: players/sessions reference it.
    """
    CREATE TABLE IF NOT EXISTS accounts (
        id INTEGER PRIMARY KEY,
        username TEXT UNIQUE NOT NULL,
        password_hash TEXT NOT NULL,
        created_at TEXT NOT NULL
    )
    """,
    # 2) players (account_id added by migration for pre-0.2 databases)
    """
    CREATE TABLE IF NOT EXISTS players (
        id INTEGER PRIMARY KEY,
        player_id TEXT UNIQUE NOT NULL,
        display_name TEXT NOT NULL,
        level INTEGER NOT NULL DEFAULT 1,
        coins INTEGER NOT NULL DEFAULT 0,
        created_at TEXT NOT NULL,
        account_id INTEGER REFERENCES accounts(id)
    )
    """,
    # 3) sessions (token stored hashed only)
    """
    CREATE TABLE IF NOT EXISTS sessions (
        id INTEGER PRIMARY KEY,
        account_id INTEGER NOT NULL REFERENCES accounts(id),
        token_hash TEXT UNIQUE NOT NULL,
        created_at TEXT NOT NULL,
        expires_at TEXT NOT NULL
    )
    """,
    # 4) save state; deleting a player removes its state
    """
    CREATE TABLE IF NOT EXISTS player_state (
        player_id INTEGER PRIMARY KEY REFERENCES players(id) ON DELETE CASCADE,
        experience INTEGER NOT NULL DEFAULT 0,
        level INTEGER NOT NULL DEFAULT 1,
        coins INTEGER NOT NULL DEFAULT 0,
        updated_at TEXT NOT NULL
    )
    """,
)


def _has_column(conn: sqlite3.Connection, table: str, column: str) -> bool:
    return any(row[1] == column for row in conn.execute(f"PRAGMA table_info({table})"))


def init_schema(conn: sqlite3.Connection) -> None:
    """Create missing tables and apply additive migrations.

    Safe to run repeatedly — never drops or rewrites existing data.
    """
    for statement in SCHEMA_STATEMENTS:
        conn.execute(statement)

    # Migration: players.account_id (added in MRR v0.2). Runs after
    # `accounts` exists so the FK reference validates.
    if not _has_column(conn, "players", "account_id"):
        conn.execute(
            "ALTER TABLE players ADD COLUMN account_id INTEGER REFERENCES accounts(id)"
        )

    conn.commit()
