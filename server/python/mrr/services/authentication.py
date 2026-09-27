"""Service layer: login, session issuance and token authentication.

No FastAPI dependency. Session tokens are random and stored only as SHA-256
hashes; the login error message never reveals whether the username or the
password was wrong.
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from pathlib import Path

from mrr.config import DEFAULT_SESSION_TTL_SECONDS
from mrr.repositories.accounts import Account, AccountRepository
from mrr.repositories.sessions import SessionRepository
from mrr.security import generate_session_token, hash_token, verify_password

__all__ = ["AuthenticationService", "AuthenticationError"]

_INVALID_CREDENTIALS = "Invalid username or password"
_INVALID_TOKEN = "Invalid or expired session token"


class AuthenticationError(Exception):
    """Raised for invalid credentials or an invalid/expired session token."""


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class AuthenticationService:
    """Login, session creation, bearer-token authentication and logout."""

    def __init__(
        self,
        database_path: str | Path | None = None,
        session_ttl_seconds: int = DEFAULT_SESSION_TTL_SECONDS,
    ) -> None:
        self._accounts = AccountRepository(database_path)
        self._sessions = SessionRepository(database_path)
        self._session_ttl_seconds = session_ttl_seconds

    @property
    def session_ttl_seconds(self) -> int:
        return self._session_ttl_seconds

    def login(self, *, username: str, password: str) -> tuple[str, int]:
        """Verify credentials and issue a session token.

        Returns ``(access_token, expires_in_seconds)``.
        Raises ``AuthenticationError`` with an identical message for unknown
        usernames and wrong passwords.
        """
        if not isinstance(username, str) or not username.strip():
            raise AuthenticationError(_INVALID_CREDENTIALS)
        if not isinstance(password, str) or not password:
            raise AuthenticationError(_INVALID_CREDENTIALS)

        account = self._accounts.get_account_by_username(username.strip())
        if account is None or not verify_password(account.password_hash, password):
            raise AuthenticationError(_INVALID_CREDENTIALS)

        token = generate_session_token()
        now = _utc_now()
        expires_at = now + timedelta(seconds=self._session_ttl_seconds)
        self._sessions.create_session(
            account_id=account.id,
            token_hash=hash_token(token),
            created_at=now.isoformat(timespec="seconds"),
            expires_at=expires_at.isoformat(timespec="seconds"),
        )
        return token, self._session_ttl_seconds

    def authenticate(self, token: str) -> Account:
        """Resolve a bearer token to its account.

        Raises ``AuthenticationError`` for missing, unknown or expired
        sessions.
        """
        if not isinstance(token, str) or not token:
            raise AuthenticationError(_INVALID_TOKEN)

        session = self._sessions.get_session_by_token_hash(hash_token(token))
        if session is None:
            raise AuthenticationError(_INVALID_TOKEN)

        try:
            expires_at = datetime.fromisoformat(session.expires_at)
        except ValueError:
            self._sessions.delete_session_by_token_hash(session.token_hash)
            raise AuthenticationError(_INVALID_TOKEN) from None
        if expires_at.tzinfo is None:
            expires_at = expires_at.replace(tzinfo=timezone.utc)
        if expires_at <= _utc_now():
            self._sessions.delete_session_by_token_hash(session.token_hash)
            raise AuthenticationError(_INVALID_TOKEN)

        account = self._accounts.get_account_by_id(session.account_id)
        if account is None:
            raise AuthenticationError(_INVALID_TOKEN)
        return account

    def logout(self, token: str) -> bool:
        """Invalidate the session behind ``token``. Returns True on success."""
        if not isinstance(token, str) or not token:
            return False
        return self._sessions.delete_session_by_token_hash(hash_token(token))
