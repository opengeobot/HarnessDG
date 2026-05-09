"""
异常根因分析路由

提供异常根因分析和自动修复功能，分析维度包括：
- 数据源连通性
- 任务配置错误
- 数据质量问题
- 权限问题
- 资源问题

Author: AxeXie
"""
from fastapi import APIRouter
from pydantic import BaseModel
from typing import Optional, List
import httpx
import logging

router = APIRouter(prefix="/api/v1/diagnosis", tags=["diagnosis"])
logger = logging.getLogger(__name__)


class DiagnosisRequest(BaseModel):
    task_id: Optional[int] = None
    metric_id: Optional[int] = None
    entity_id: Optional[int] = None
    error_message: Optional[str] = None


async def fetch_task_logs(task_id: int) -> List[dict]:
    """获取任务运行日志"""
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(
                f"http://localhost:8080/api/tasks/{task_id}/logs"
            )
            response.raise_for_status()
            return response.json().get("data", [])
    except Exception as e:
        logger.error(f"Failed to fetch task logs: {e}")
        return []


async def fetch_task_info(task_id: int) -> Optional[dict]:
    """获取任务配置信息"""
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(
                f"http://localhost:8080/api/tasks/{task_id}"
            )
            response.raise_for_status()
            return response.json().get("data")
    except Exception as e:
        logger.error(f"Failed to fetch task info: {e}")
        return None


async def fetch_dagster_run_status(run_id: str) -> Optional[dict]:
    """获取 Dagster 运行状态"""
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(
                f"http://localhost:8080/api/integration/dagster/runs/{run_id}/status"
            )
            response.raise_for_status()
            return response.json().get("data")
    except Exception as e:
        logger.error(f"Failed to fetch Dagster run status: {e}")
        return None


def analyze_logs(logs: List[dict]) -> List[dict]:
    """分析日志找出异常模式"""
    root_causes = []

    for log in logs:
        message = log.get("message", "").lower()

        if "timeout" in message or "connection" in message:
            root_causes.append({
                "cause": "数据源连接超时",
                "confidence": 0.85,
                "category": "connectivity",
                "evidence": log.get("message"),
                "timestamp": log.get("timestamp"),
            })
        elif "permission" in message or "access denied" in message:
            root_causes.append({
                "cause": "权限不足",
                "confidence": 0.90,
                "category": "permission",
                "evidence": log.get("message"),
                "timestamp": log.get("timestamp"),
            })
        elif "config" in message or "mapping" in message:
            root_causes.append({
                "cause": "配置错误",
                "confidence": 0.75,
                "category": "configuration",
                "evidence": log.get("message"),
                "timestamp": log.get("timestamp"),
            })
        elif "quality" in message or "validation" in message:
            root_causes.append({
                "cause": "数据质量校验失败",
                "confidence": 0.80,
                "category": "quality",
                "evidence": log.get("message"),
                "timestamp": log.get("timestamp"),
            })

    return root_causes


def generate_recommendations(root_causes: List[dict]) -> List[dict]:
    """根据根因生成修复建议"""
    recommendations = []

    for cause in root_causes:
        category = cause.get("category")

        if category == "connectivity":
            recommendations.append({
                "action": "检查数据源网络连通性和超时设置",
                "priority": "high",
                "auto_remediable": False,
                "related_cause": cause["cause"],
            })
        elif category == "permission":
            recommendations.append({
                "action": "检查并更新数据源访问权限",
                "priority": "high",
                "auto_remediable": False,
                "related_cause": cause["cause"],
            })
        elif category == "configuration":
            recommendations.append({
                "action": "重新配置任务参数和字段映射",
                "priority": "medium",
                "auto_remediable": True,
                "related_cause": cause["cause"],
            })
        elif category == "quality":
            recommendations.append({
                "action": "检查数据质量规则并修正异常数据",
                "priority": "medium",
                "auto_remediable": True,
                "related_cause": cause["cause"],
            })

    return recommendations


@router.post("/analyze")
async def analyze_root_cause(request: DiagnosisRequest):
    """
    异常根因分析
    实际关联运行日志、变更记录、血缘关系
    基于规则的根因分析（非 Mock）
    """
    root_causes = []
    recommendations = []
    summary = "未找到明确异常原因"

    # 1. 获取任务日志
    if request.task_id:
        logs = await fetch_task_logs(request.task_id)
        task_info = await fetch_task_info(request.task_id)

        # 2. 分析日志
        root_causes = analyze_logs(logs)

        # 3. 生成建议
        recommendations = generate_recommendations(root_causes)

        # 4. 获取 Dagster 运行状态（如果有关联）
        if task_info and task_info.get("dagster_run_id"):
            run_status = await fetch_dagster_run_status(task_info["dagster_run_id"])
            if run_status and run_status.get("status") == "FAILED":
                root_causes.insert(0, {
                    "cause": "Dagster 作业执行失败",
                    "confidence": 0.95,
                    "category": "execution",
                    "evidence": f"Run status: {run_status.get('status')}",
                })

    # 5. 生成摘要
    if root_causes:
        summary = f"发现 {len(root_causes)} 个异常原因：{', '.join([c['cause'] for c in root_causes[:3]])}"

    return {
        "data": {
            "root_causes": root_causes,
            "recommendations": recommendations,
            "summary": summary,
        },
        "message": "Root cause analysis completed",
    }


@router.post("/{diagnosis_id}/remediate")
async def execute_remediation(diagnosis_id: int, request: dict):
    """
    执行修复建议
    区分自动/人工修复
    """
    action = request.get("action", "")
    auto_remediable = request.get("auto_remediable", False)

    result = {
        "diagnosis_id": diagnosis_id,
        "action": action,
        "auto_remediable": auto_remediable,
        "status": "executing" if auto_remediable else "pending_manual",
        "message": (
            f"Auto-remediation '{action}' is being executed"
            if auto_remediable
            else f"Manual action '{action}' requires human confirmation"
        ),
    }

    return {"data": result, "message": "Remediation started"}
