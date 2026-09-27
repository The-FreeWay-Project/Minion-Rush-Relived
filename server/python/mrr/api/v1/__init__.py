from fastapi import APIRouter

from mrr.api.v1 import health, players

router = APIRouter()
router.include_router(health.router)
router.include_router(players.router)
