from fastapi import APIRouter

from mrr.api.v1 import auth, health, player_state, players, profile

router = APIRouter()
router.include_router(health.router)
router.include_router(players.router)
router.include_router(auth.router)
router.include_router(profile.router)
router.include_router(player_state.router)
