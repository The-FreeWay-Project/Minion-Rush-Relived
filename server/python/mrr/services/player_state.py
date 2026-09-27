"""Service layer: persistent save state for a player.

Ownership rule: state is always addressed through the caller's own account —
there is no way to read or write another account's state through this
service.
"""

from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path

from mrr.repositories.players import Player, PlayerRepository
from mrr.repositories.player_state import PlayerState, PlayerStateRepository

__all__ = ["PlayerStateService", "PlayerStateNotFoundError"]


class PlayerStateNotFoundError(LookupError):
    """Raised when the account has no player to attach state to."""


class PlayerStateService:
    """Read and update the save state of an account's primary player."""

    def __init__(self, database_path: str | Path | None = None) -> None:
        self._states = PlayerStateRepository(database_path)
        self._players = PlayerRepository(database_path)

    @property
    def database_path(self) -> Path:
        return self._states.database_path

    def get_state(self, account_id: int) -> PlayerState:
        """Return the account's state, creating default state when missing."""
        player = self._resolve_player(account_id)
        return self._states.get_or_create_state(
            player_id=player.id, updated_at=_utc_now()
        )

    def update_state(
        self,
        account_id: int,
        *,
        experience: int,
        level: int,
        coins: int,
    ) -> PlayerState:
        """Replace the account's state values."""
        player = self._resolve_player(account_id)
        return self._states.upsert_state(
            player_id=player.id,
            experience=experience,
            level=level,
            coins=coins,
            updated_at=_utc_now(),
        )

    def _resolve_player(self, account_id: int) -> Player:
        player = self._players.get_primary_player_for_account(account_id)
        if player is None:
            raise PlayerStateNotFoundError("account has no player")
        return player


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")
