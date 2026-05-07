import httpx
import logging
from app.config import settings
from app.models.schemas import ChatRequest, ChatResponse, IntentRequest, IntentResponse

logger = logging.getLogger(__name__)


class QwenPawClient:
    """QwenPaw Agent 客户端 - 代理请求到已部署的 QwenPaw 服务"""

    def __init__(self):
        self.endpoint = settings.qwenpaw_endpoint
        self.client = httpx.AsyncClient(timeout=60.0)

    async def chat(self, request: ChatRequest, trace_id: str = "") -> ChatResponse:
        """发送对话请求到 QwenPaw"""
        headers = {settings.trace_header: trace_id} if trace_id else {}

        try:
            response = await self.client.post(
                f"{self.endpoint}/v1/chat/completions",
                json={
                    "model": "qwenpaw",
                    "messages": [
                        {"role": "system", "content": self._build_system_prompt(request.context)},
                        {"role": "user", "content": request.message},
                    ],
                    "session_id": request.session_id,
                },
                headers=headers,
            )
            response.raise_for_status()
            data = response.json()

            # 解析 QwenPaw 返回结果
            reply_content = data.get("choices", [{}])[0].get("message", {}).get("content", "")
            metadata = data.get("metadata", {})

            return ChatResponse(
                reply=reply_content,
                intent=metadata.get("intent"),
                sql=metadata.get("sql"),
                data=metadata.get("data"),
                session_id=request.session_id,
            )
        except httpx.HTTPStatusError as e:
            logger.error(f"QwenPaw HTTP error: {e.response.status_code}")
            return ChatResponse(
                reply="Agent 服务暂时不可用，请稍后再试。",
                session_id=request.session_id,
            )
        except Exception as e:
            logger.error(f"QwenPaw request failed: {e}")
            return ChatResponse(
                reply="请求处理异常，请稍后再试。",
                session_id=request.session_id,
            )

    async def recognize_intent(self, request: IntentRequest, trace_id: str = "") -> IntentResponse:
        """意图识别"""
        headers = {settings.trace_header: trace_id} if trace_id else {}

        try:
            response = await self.client.post(
                f"{self.endpoint}/v1/intent",
                json={"query": request.query},
                headers=headers,
            )
            response.raise_for_status()
            data = response.json()
            return IntentResponse(
                intent=data.get("intent", "unknown"),
                confidence=data.get("confidence", 0.0),
                entities=data.get("entities", {}),
            )
        except Exception as e:
            logger.error(f"Intent recognition failed: {e}")
            return IntentResponse(intent="unknown", confidence=0.0)

    def _build_system_prompt(self, context: dict) -> str:
        """构建系统提示词，注入本体上下文"""
        base_prompt = (
            "你是 AODO (AI Ontology Data OS) 的数据助手。"
            "你能够帮助用户查询业务指标、分析数据趋势、解释数据含义。"
            "回答应当简洁、专业，如果涉及SQL查询请一并返回。"
        )
        if context.get("ontology"):
            base_prompt += f"\n\n当前本体上下文: {context['ontology']}"
        return base_prompt

    async def close(self):
        await self.client.aclose()


qwenpaw_client = QwenPawClient()
