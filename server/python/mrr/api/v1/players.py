"""Player endpoints (synthetic test players only).

HTTP boundary: Pydantic models + status codes. All work is delegated to
``PlayerService`` — no SQL and no SQLite connections in this module.

Ownership rule: players that belong to an account are protected — only the
owner may read or delete them. Unowned synthetic test players (created
without a token) stay publicly readable for local development.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from mrr.api.deps import get_optional_account, get_player_service
from mrr.repositories.accounts import Account
from mrr.repositories.players import Player
from mrr.services.players import (
    PlayerAlreadyExistsError,
    PlayerService,
    PlayerValidationError,
)

router = APIRouter(tags=["players"])

_AUTH_HEADERS = {"WWW-Authenticate": "Bearer"}


class CreatePlayerRequest(BaseModel):
    """POST body for creating a synthetic test player."""

    player_id: str = Field(min_length=1, max_length=128)
    display_name: str = Field(min_length=1, max_length=128)
    level: int = Field(default=1, ge=1)
    coins: int = Field(default=0, ge=0)


class PlayerResponse(BaseModel):
    """Player representation returned by the API."""

    id: int
    player_id: str
    display_name: str
    level: int
    coins: int
    created_at: str


def _handle(exc: PlayerValidationError, status_code: int) -> HTTPException:
    return HTTPException(status_code=status_code, detail=str(exc))


def _enforce_ownership(player: Player, account: Account | None) -> None:
    """Protect owned players: owner only (404 for other accounts)."""
    if player.account_id is None:
        return
    if account is None:
        raise HTTPException(
            status_code=401,
            detail="Authentication required for this player",
            headers=_AUTH_HEADERS,
        )
    if account.id != player.account_id:
        raise HTTPException(
            status_code=404, detail=f"player {player.player_id!r} not found"
        )


@router.post("/players", status_code=201, response_model=PlayerResponse)
def create_player(
    payload: CreatePlayerRequest,
    service: PlayerService = Depends(get_player_service),
) -> Player:
    try:
        return service.create_player(
            player_id=payload.player_id,
            display_name=payload.display_name,
            level=payload.level,
            coins=payload.coins,
        )
    except PlayerAlreadyExistsError as exc:
        raise _handle(exc, 409) from exc
    except PlayerValidationError as exc:
        raise _handle(exc, 422) from exc


@router.get("/players", response_model=list[PlayerResponse])
def list_players(service: PlayerService = Depends(get_player_service)) -> list[Player]:
    return service.list_players()


@router.get("/players/{player_id}", response_model=PlayerResponse)
def get_player(
    player_id: str,
    service: PlayerService = Depends(get_player_service),
    account: Account | None = Depends(get_optional_account),
) -> Player:
    try:
        player = service.get_player(player_id)
    except PlayerValidationError as exc:
        raise _handle(exc, 422) from exc
    if player is None:
        raise HTTPException(status_code=404, detail=f"player {player_id!r} not found")
    _enforce_ownership(player, account)
    return player


@router.delete("/players/{player_id}", status_code=204)
def delete_player(
    player_id: str,
    service: PlayerService = Depends(get_player_service),
    account: Account | None = Depends(get_optional_account),
) -> None:
    try:
        player = service.get_player(player_id)
    except PlayerValidationError as exc:
        raise _handle(exc, 422) from exc
    if player is None:
        raise HTTPException(status_code=404, detail=f"player {player_id!r} not found")
    _enforce_ownership(player, account)
    service.delete_player(player_id)
