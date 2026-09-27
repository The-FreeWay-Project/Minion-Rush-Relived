"""FastAPI dependencies (no global state, no open database connections).

All services are built per request from the configured database path.
Tests override ``get_database_path`` (or individual service factories) to
bind the app to a temporary SQLite database.
"""

from __future__ import annotations

from pathlib import Path
from typing import Annotated

from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from mrr.config import Settings, load_settings
from mrr.repositories.accounts import Account
from mrr.services.accounts import AccountService
from mrr.services.authentication import AuthenticationError, AuthenticationService
from mrr.services.player_state import PlayerStateService
from mrr.services.players import PlayerService

_bearer = HTTPBearer(auto_error=False)

_401_HEADERS = {"WWW-Authenticate": "Bearer"}


def get_settings() -> Settings:
    return load_settings()


def get_database_path(settings: Settings = Depends(get_settings)) -> Path:
    return settings.database_path


def get_player_service(
    database_path: Path = Depends(get_database_path),
) -> PlayerService:
    return PlayerService(database_path)


def get_account_service(
    database_path: Path = Depends(get_database_path),
) -> AccountService:
    return AccountService(database_path)


def get_authentication_service(
    database_path: Path = Depends(get_database_path),
    settings: Settings = Depends(get_settings),
) -> AuthenticationService:
    return AuthenticationService(
        database_path, session_ttl_seconds=settings.session_ttl_seconds
    )


def get_player_state_service(
    database_path: Path = Depends(get_database_path),
) -> PlayerStateService:
    return PlayerStateService(database_path)


def get_bearer_credentials(
    credentials: Annotated[
        HTTPAuthorizationCredentials | None, Depends(_bearer)
    ],
) -> HTTPAuthorizationCredentials | None:
    """Raw bearer credentials from the Authorization header (may be None)."""
    return credentials


def get_current_account(
    credentials: Annotated[
        HTTPAuthorizationCredentials | None, Depends(_bearer)
    ],
    auth_service: AuthenticationService = Depends(get_authentication_service),
) -> Account:
    """Authenticated account behind ``Authorization: Bearer <token>``.

    Raises 401 for a missing, invalid or expired session token. The token
    itself is never echoed back or logged.
    """
    if credentials is None:
        raise HTTPException(
            status_code=401, detail="Not authenticated", headers=_401_HEADERS
        )
    try:
        return auth_service.authenticate(credentials.credentials)
    except AuthenticationError as exc:
        raise HTTPException(
            status_code=401, detail=str(exc), headers=_401_HEADERS
        ) from exc


def get_optional_account(
    credentials: Annotated[
        HTTPAuthorizationCredentials | None, Depends(_bearer)
    ],
    auth_service: AuthenticationService = Depends(get_authentication_service),
) -> Account | None:
    """Like ``get_current_account`` but returns None when no token is sent.

    An *invalid* token still raises 401.
    """
    if credentials is None:
        return None
    try:
        return auth_service.authenticate(credentials.credentials)
    except AuthenticationError as exc:
        raise HTTPException(
            status_code=401, detail=str(exc), headers=_401_HEADERS
        ) from exc
