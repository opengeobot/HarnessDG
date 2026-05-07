from pydantic import BaseModel
from typing import Optional


class ChatRequest(BaseModel):
    session_id: str
    message: str
    context: dict = {}


class ChatResponse(BaseModel):
    reply: str
    intent: Optional[str] = None
    sql: Optional[str] = None
    data: Optional[dict] = None
    session_id: str


class IntentRequest(BaseModel):
    query: str


class IntentResponse(BaseModel):
    intent: str
    confidence: float
    entities: dict = {}
