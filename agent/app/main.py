import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.routers.agent import router as agent_router
from app.services.qwenpaw_client import qwenpaw_client

logging.basicConfig(level=getattr(logging, settings.log_level))
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(f"Agent service starting, QwenPaw endpoint: {settings.qwenpaw_endpoint}")
    yield
    await qwenpaw_client.close()
    logger.info("Agent service shutdown")


app = FastAPI(
    title="HarnessDG Agent Service",
    description="AI Agent gateway proxying to QwenPaw for intent recognition, data query and explanation",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(agent_router)

# Phase 2 新增路由
from app.routers import quality, diagnosis, report, ingestion

app.include_router(quality.router)
app.include_router(diagnosis.router)
app.include_router(report.router)
app.include_router(ingestion.router)


@app.get("/")
async def root():
    return {"service": "harnessdg-agent", "version": "1.0.0"}
