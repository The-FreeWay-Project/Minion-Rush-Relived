"""SQLite database helpers (Python standard library only)."""

from __future__ import annotations

import sqlite3
from collections.abc import Iterator
from contextlib import contextmanager
from pathlib import Path

from mrr.db.schema import init_schema

__all__ = ["connect", "open_connection", "init_schema"]


def connect(database_path: str | Path) -> sqlite3.Connection:
    """Open a SQLite connection to ``database_path``.

    Creates the parent directory when it does not exist and enables
    foreign key enforcement for this connection.
    """
    path = Path(database_path)
    path.parent.mkdir(parents=True, exist_ok=True)

    conn = sqlite3.connect(path)
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


@contextmanager
def open_connection(database_path: str | Path) -> Iterator[sqlite3.Connection]:
    """Yield a connection, committing on success and always closing it.

    No connection is kept open globally; callers get a short-lived one.
    """
    conn = connect(database_path)
    try:
        yield conn
        conn.commit()
    except BaseException:
        conn.rollback()
        raise
    finally:
        conn.close()
