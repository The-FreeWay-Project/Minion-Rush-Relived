"""Service layer: business logic for players.

Validation and orchestration only. No FastAPI dependency, no HTTP concepts.
"""

from __future__ import annotations

import sqlite3
from pathlib import Path

from mrr.repositories.players import Player, PlayerRepository

__all__ = [
    "Player",
    "PlayerService",
    "PlayerValidationError",
    "PlayerAlreadyExistsError",
]


class PlayerValidationError(ValueError):
    """Raised when player input fails validation."""


class PlayerAlreadyExistsError(PlayerValidationError):
    """Raised when a ``player_id`` is already taken."""


class PlayerService:
    """Business logic above :class:`PlayerRepository`."""

    def __init__(self, database_path: str | Path | None = None) -> None:
        self._repository = PlayerRepository(database_path)

    @property
    def database_path(self) -> Path:
        return self._repository.database_path

    def create_player(
        self,
        *,
        player_id: str,
        display_name: str,
        level: int = 1,
        coins: int = 0,
    ) -> Player:
        """Validate and create one player."""
        self._validate(
            player_id=player_id,
            display_name=display_name,
            level=level,
            coins=coins,
        )
        try:
            return self._repository.create_player(
                player_id=player_id,
                display_name=display_name,
                level=level,
                coins=coins,
            )
        except sqlite3.IntegrityError as exc:
            if self._repository.get_player_by_player_id(player_id) is not None:
                raise PlayerAlreadyExistsError(
                    f"player_id {player_id!r} already exists"
                ) from exc
            raise

    def get_player(self, player_id: str) -> Player | None:
        """Load a player by its unique ``player_id`` (None when unknown)."""
        if not isinstance(player_id, str) or not player_id.strip():
            raise PlayerValidationError("player_id must be a non-empty string")
        return self._repository.get_player_by_player_id(player_id)

    def get_player_by_id(self, id: int) -> Player | None:
        """Load a player by its internal id (None when unknown)."""
        return self._repository.get_player_by_id(id)

    def delete_player(self, player_id: str) -> bool:
        """Delete a player. Returns True when a row was removed."""
        if not isinstance(player_id, str) or not player_id.strip():
            raise PlayerValidationError("player_id must be a non-empty string")
        return self._repository.delete_player(player_id)

    def get_players_for_account(self, account_id: int) -> list[Player]:
        """All players owned by an account (empty list when none)."""
        return self._repository.get_players_by_account_id(account_id)

    def get_primary_player_for_account(self, account_id: int) -> Player | None:
        """The account's first player, or None (used by the profile)."""
        return self._repository.get_primary_player_for_account(account_id)

    def list_players(self, limit: int = 100, offset: int = 0) -> list[Player]:
        return self._repository.list_players(limit=limit, offset=offset)

    @staticmethod
    def _validate(*, player_id: str, display_name: str, level: int, coins: int) -> None:
        if not isinstance(player_id, str) or not player_id.strip():
            raise PlayerValidationError("player_id must be a non-empty string")
        if not isinstance(display_name, str) or not display_name.strip():
            raise PlayerValidationError("display_name must be a non-empty string")
        if not isinstance(level, int) or level < 1:
            raise PlayerValidationError("level must be an integer >= 1")
        if not isinstance(coins, int) or coins < 0:
            raise PlayerValidationError("coins must be an integer >= 0")
