import uuid
from fastapi import APIRouter, Header
from typing import Optional
from app.models.schemas import ChatRequest, ChatResponse, IntentRequest, IntentResponse
from app.services.qwenpaw_client import qwenpaw_client

router = APIRouter(prefix="/api/agent", tags=["agent"])


@router.post("/chat", response_model=ChatResponse)
async def chat(
    request: ChatRequest,
    x_trace_id: Optional[str] = Header(None, alias="X-Trace-Id"),
):
    """AI 数据问答入口"""
    trace_id = x_trace_id or str(uuid.uuid4())
    if not request.session_id:
        request.session_id = f"session_{uuid.uuid4().hex[:12]}"
    return await qwenpaw_client.chat(request, trace_id)


@router.post("/intent", response_model=IntentResponse)
async def recognize_intent(
    request: IntentRequest,
    x_trace_id: Optional[str] = Header(None, alias="X-Trace-Id"),
):
    """意图识别"""
    trace_id = x_trace_id or str(uuid.uuid4())
    return await qwenpaw_client.recognize_intent(request, trace_id)


@router.get("/health")
async def health():
    """健康检查"""
    return {"status": "ok", "service": "harnessdg-agent"}
