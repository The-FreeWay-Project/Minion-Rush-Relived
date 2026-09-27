"""Protected profile endpoint: the authenticated account and its player."""

from __future__ import annotations

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from mrr.api.deps import get_current_account, get_player_service
from mrr.repositories.accounts import Account
from mrr.services.players import Player, PlayerService

router = APIRouter(tags=["profile"])


class ProfilePlayerResponse(BaseModel):
    player_id: str
    display_name: str
    level: int
    coins: int


class ProfileResponse(BaseModel):
    account_id: int
    username: str
    player: ProfilePlayerResponse | None


@router.get("/profile", response_model=ProfileResponse)
def get_profile(
    account: Account = Depends(get_current_account),
    service: PlayerService = Depends(get_player_service),
) -> ProfileResponse:
    player = service.get_primary_player_for_account(account.id)
    return ProfileResponse(
        account_id=account.id,
        username=account.username,
        player=_to_profile_player(player) if player else None,
    )


def _to_profile_player(player: Player) -> ProfilePlayerResponse:
    return ProfilePlayerResponse(
        player_id=player.player_id,
        display_name=player.display_name,
        level=player.level,
        coins=player.coins,
    )
