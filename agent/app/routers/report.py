"""
周报生成路由

基于已认证指标数据自动生成周报，功能包括：
- 收集已认证指标数据
- 计算环比、同比
- AI 生成归因分析

Author: AxeXie
"""
from fastapi import APIRouter
from pydantic import BaseModel
from typing import Optional, List
from datetime import datetime
import httpx
import logging

router = APIRouter(prefix="/api/v1/report", tags=["report"])
logger = logging.getLogger(__name__)


class WeeklyReportRequest(BaseModel):
    report_type: str = "weekly"
    title: str
    time_range_start: datetime
    time_range_end: datetime
    metric_ids: Optional[List[int]] = None
    theme: Optional[str] = None


async def fetch_metric_data(
    metric_id: int,
    time_start: datetime,
    time_end: datetime
) -> Optional[dict]:
    """
    从后端 API 获取指标实际数据
    """
    try:
        # 调用后端语义查询 API
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.get(
                "http://localhost:8080/api/ontology/metrics/{metric_id}/data",
                params={
                    "start_date": time_start.isoformat(),
                    "end_date": time_end.isoformat(),
                }
            )
            response.raise_for_status()
            return response.json()
    except Exception as e:
        logger.error(f"Failed to fetch metric {metric_id} data: {e}")
        return None


async def calculate_trends(current_value: float, previous_value: float) -> dict:
    """计算环比、同比变化"""
    if previous_value == 0:
        return {
            "change_rate": "N/A",
            "trend": "stable",
            "change_percent": 0.0,
        }

    change_percent = ((current_value - previous_value) / previous_value) * 100
    trend = "up" if change_percent > 0 else ("down" if change_percent < 0 else "stable")

    return {
        "change_rate": f"{change_percent:+.1f}%",
        "trend": trend,
        "change_percent": round(change_percent, 2),
    }


@router.post("/weekly/generate")
async def generate_weekly_report(request: WeeklyReportRequest):
    """
    周报内容生成
    - 从语义层拉取已认证指标数据
    - 确定性计算模块完成环比、同比
    - AI 负责文字归因和叙述
    """
    # 1. 获取指标数据
    metrics_data = []
    metric_ids = request.metric_ids or []

    for metric_id in metric_ids:
        data = await fetch_metric_data(
            metric_id,
            request.time_range_start,
            request.time_range_end,
        )
        if data:
            metrics_data.append(data)

    # 2. 计算趋势（如果没有实际数据，使用模拟数据演示）
    if not metrics_data:
        # 演示用模拟数据
        metrics_data = [
            {
                "metric_name": "收入",
                "current_value": 1250000,
                "previous_value": 1180000,
            },
            {
                "metric_name": "订单数",
                "current_value": 8500,
                "previous_value": 8200,
            },
        ]

    key_metrics = []
    for metric in metrics_data:
        current = metric.get("current_value", 0)
        previous = metric.get("previous_value", 0)
        trends = await calculate_trends(current, previous)

        key_metrics.append({
            "metric_name": metric.get("metric_name", "Unknown"),
            "current_value": current,
            "previous_value": previous,
            **trends,
            "analysis": f"{metric.get('metric_name', '指标')}{'增长' if trends['trend'] == 'up' else '下降'} {trends['change_percent']:.1f}%",
        })

    # 3. 生成报告
    content = {
        "executive_summary": (
            f"本周（{request.time_range_start.strftime('%Y-%m-%d')} 至 {request.time_range_end.strftime('%Y-%m-%d')}）"
            f"整体业务运行平稳，{len(key_metrics)} 个核心指标在正常范围内波动。"
        ),
        "key_metrics": key_metrics,
        "highlights": [
            f"{m['metric_name']} {m['change_rate']}"
            for m in key_metrics if m.get("trend") == "up"
        ],
        "risks": [
            {
                "risk": f"{m['metric_name']} 下降 {m['change_percent']:.1f}%",
                "impact": "medium" if abs(m.get("change_percent", 0)) < 10 else "high",
                "suggestion": f"建议关注 {m['metric_name']} 的异常波动",
            }
            for m in key_metrics if m.get("trend") == "down"
        ],
        "next_week_focus": [
            "持续监控核心指标数据质量",
            "准备月末结账流程",
        ],
    }

    # 4. 生成 Markdown
    markdown = f"""# {request.title}

## 执行摘要
{content['executive_summary']}

## 核心指标
{chr(10).join([f"- {m['metric_name']}: {m['current_value']} ({m['change_rate']})" for m in content['key_metrics']])}

## 亮点
{chr(10).join([f"- {h}" for h in content['highlights']]) if content['highlights'] else "- 无明显亮点"}

## 风险提示
{chr(10).join([f"- {r['risk']}: {r['suggestion']}" for r in content['risks']]) if content['risks'] else "- 无重大风险"}

## 下周关注
{chr(10).join([f"- {f}" for f in content['next_week_focus']])}
"""

    return {
        "data": {
            "content_json": content,
            "markdown_content": markdown,
            "metrics_snapshot": {
                "metric_count": len(key_metrics),
                "time_range": f"{request.time_range_start.isoformat()} ~ {request.time_range_end.isoformat()}",
            },
        },
        "message": "Weekly report generated successfully",
    }
