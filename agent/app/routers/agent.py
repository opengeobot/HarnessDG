import uuid
from fastapi import APIRouter, Header
from typing import Optional
from app.models.schemas import ChatRequest, ChatResponse, IntentRequest, IntentResponse
from app.services.qwenpaw_client import qwenpaw_client
from app.services.task_planner import task_planner
from app.services.pipeline_generator import pipeline_generator

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


@router.post("/plan")
async def generate_plan(
    request: IntentRequest,
    x_trace_id: Optional[str] = Header(None, alias="X-Trace-Id"),
):
    """生成任务执行计划"""
    trace_id = x_trace_id or str(uuid.uuid4())

    # 先进行意图识别
    intent = await qwenpaw_client.recognize_intent(request, trace_id)

    # 基于意图生成执行计划
    plan = task_planner.generate_plan(intent, context={"query": request.query})

    return {
        "trace_id": trace_id,
        **plan,
    }


@router.post("/generate-pipeline")
async def generate_pipeline(
    request: dict,
    x_trace_id: Optional[str] = Header(None, alias="X-Trace-Id"),
):
    """
    全自动 Pipeline 生成
    基于本体定义生成完整的数据 Pipeline
    """
    trace_id = x_trace_id or str(uuid.uuid4())

    pipeline = pipeline_generator.generate_full_pipeline(
        entity_code=request.get("entity_code"),
        metric_code=request.get("metric_code"),
        dimensions=request.get("dimensions", []),
        data_domain=request.get("data_domain", "default"),
        schedule_cron=request.get("schedule_cron", "0 2 * * *"),
        source_type=request.get("source_type", "mysql"),
        source_table=request.get("source_table", ""),
    )

    return {
        "trace_id": trace_id,
        "data": pipeline,
        "message": "Pipeline generated successfully",
    }


@router.get("/health")
async def health():
    """健康检查"""
    return {"status": "ok", "service": "harnessdg-agent"}
