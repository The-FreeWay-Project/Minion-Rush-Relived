"""Service layer: account business logic (registration).

No FastAPI dependency. Passwords are hashed via ``mrr.security`` before they
reach the database; plaintext passwords are never stored or logged.
"""

from __future__ import annotations

import secrets
import sqlite3
from pathlib import Path

from mrr.repositories.accounts import Account, AccountRepository
from mrr.repositories.players import PlayerRepository
from mrr.security import hash_password

__all__ = [
    "AccountService",
    "AccountValidationError",
    "AccountAlreadyExistsError",
]


class AccountValidationError(ValueError):
    """Raised when account input fails validation."""


class AccountAlreadyExistsError(AccountValidationError):
    """Raised when a username is already taken."""


class AccountService:
    """Registration and account lookup."""

    def __init__(self, database_path: str | Path | None = None) -> None:
        self._accounts = AccountRepository(database_path)
        self._players = PlayerRepository(database_path)

    @property
    def database_path(self) -> Path:
        return self._accounts.database_path

    def register(self, *, username: str, password: str) -> Account:
        """Create an account (hashed password) plus a default owned player."""
        username = self._validate(username=username, password=password)
        password_hash = hash_password(password)
        try:
            account = self._accounts.create_account(
                username=username, password_hash=password_hash
            )
        except sqlite3.IntegrityError as exc:
            raise AccountAlreadyExistsError(
                f"username {username!r} already exists"
            ) from exc
        self._create_default_player(account)
        return account

    def get_account(self, account_id: int) -> Account | None:
        return self._accounts.get_account_by_id(account_id)

    @staticmethod
    def _validate(*, username: str, password: str) -> str:
        if not isinstance(username, str) or not username.strip():
            raise AccountValidationError("username must be a non-empty string")
        if not isinstance(password, str) or not password.strip():
            raise AccountValidationError("password must be a non-empty string")
        return username.strip()

    def _create_default_player(self, account: Account) -> None:
        """Attach one player to a freshly registered account."""
        player_id = f"player-{account.id:04d}"
        if self._players.get_player_by_player_id(player_id) is not None:
            player_id = f"{player_id}-{secrets.token_hex(3)}"
        try:
            self._players.create_player(
                player_id=player_id,
                display_name=account.username,
                account_id=account.id,
            )
        except sqlite3.IntegrityError:
            # Extremely unlikely collision — the account stays usable and
            # the profile simply has no player yet.
            pass
