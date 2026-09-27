"""FastAPI dependencies (no global state, no open database connections)."""

from __future__ import annotations

from mrr.services.players import PlayerService


def get_player_service() -> PlayerService:
    """Build a PlayerService for one request.

    The database path comes from the config (`MRR_DATABASE_PATH`).
    Tests override this dependency with a service bound to a temp database.
    """
    return PlayerService()
