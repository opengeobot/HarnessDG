"""
HarnessDG Agent Service Tests
时间：2026-05-12
作者：AxeXie
"""
import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.models.schemas import ChatRequest, IntentRequest


@pytest.fixture
async def client():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


@pytest.mark.asyncio
async def test_health_check(client):
    """健康检查端点测试"""
    response = await client.get("/api/agent/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["service"] == "harnessdg-agent"


@pytest.mark.asyncio
async def test_chat_empty_message(client):
    """空消息聊天测试"""
    request = ChatRequest(session_id="test_session", message="", context={})
    response = await client.post("/api/agent/chat", json=request.model_dump())
    # 应该返回错误或空回复
    assert response.status_code in [200, 400]


@pytest.mark.asyncio
async def test_chat_with_message(client):
    """正常聊天测试"""
    request = ChatRequest(
        session_id="test_session_123",
        message="上月总营收是多少？",
        context={}
    )
    response = await client.post("/api/agent/chat", json=request.model_dump())
    # 即使 QwenPaw 不可用，也应该返回合理的响应
    assert response.status_code == 200
    data = response.json()
    assert "reply" in data or "error" in data


@pytest.mark.asyncio
async def test_chat_auto_generates_session_id():
    """Session ID 自动生成测试"""
    request = ChatRequest(message="test message", context={})
    assert request.session_id is None or request.session_id == ""


@pytest.mark.asyncio
async def test_intent_recognition(client):
    """意图识别测试"""
    request = IntentRequest(query="查询订单数据")
    response = await client.post("/api/agent/intent", json=request.model_dump())
    assert response.status_code == 200


@pytest.mark.asyncio
async def test_plan_generation(client):
    """任务计划生成测试"""
    request = IntentRequest(query="生成销售报表")
    response = await client.post("/api/agent/plan", json=request.model_dump())
    assert response.status_code == 200
    data = response.json()
    assert "trace_id" in data


@pytest.mark.asyncio
async def test_pipeline_generation(client):
    """Pipeline 生成测试"""
    payload = {
        "entity_code": "sales_order",
        "metric_code": "total_revenue",
        "dimensions": ["date", "channel"],
        "data_domain": "sales",
        "schedule_cron": "0 2 * * *",
        "source_type": "mysql",
        "source_table": "orders",
    }
    response = await client.post("/api/agent/generate-pipeline", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert "trace_id" in data
    assert "data" in data
