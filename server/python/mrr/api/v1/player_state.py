"""Protected save-state endpoints for the authenticated account's player."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from mrr.api.deps import get_current_account, get_player_state_service
from mrr.repositories.accounts import Account
from mrr.repositories.player_state import PlayerState
from mrr.services.player_state import PlayerStateNotFoundError, PlayerStateService

router = APIRouter(prefix="/player", tags=["player-state"])


class PlayerStateRequest(BaseModel):
    experience: int = Field(ge=0)
    level: int = Field(ge=1)
    coins: int = Field(ge=0)


class PlayerStateResponse(BaseModel):
    player_id: int
    experience: int
    level: int
    coins: int
    updated_at: str


@router.get("/state", response_model=PlayerStateResponse)
def get_player_state(
    account: Account = Depends(get_current_account),
    service: PlayerStateService = Depends(get_player_state_service),
) -> PlayerStateResponse:
    try:
        return _to_response(service.get_state(account.id))
    except PlayerStateNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@router.put("/state", response_model=PlayerStateResponse)
def put_player_state(
    payload: PlayerStateRequest,
    account: Account = Depends(get_current_account),
    service: PlayerStateService = Depends(get_player_state_service),
) -> PlayerStateResponse:
    try:
        state = service.update_state(
            account.id,
            experience=payload.experience,
            level=payload.level,
            coins=payload.coins,
        )
    except PlayerStateNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    return _to_response(state)


def _to_response(state: PlayerState) -> PlayerStateResponse:
    return PlayerStateResponse(
        player_id=state.player_id,
        experience=state.experience,
        level=state.level,
        coins=state.coins,
        updated_at=state.updated_at,
    )
