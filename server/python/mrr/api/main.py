from fastapi import FastAPI

from mrr.api.v1 import router as v1_router

app = FastAPI(
    title="MRR",
    description="Minion Rush Relived server foundation.",
    version="0.1.0",
    docs_url="/api/docs",
    openapi_url="/api/openapi.json",
)

app.include_router(v1_router, prefix="/api/v1")
