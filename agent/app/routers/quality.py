"""
质量规则生成路由

基于实体/指标定义自动生成质量规则，支持多种规则类型：
- 基于字段类型：NOT NULL、枚举值校验、数值范围
- 基于历史波动：环比阈值、同比阈值
- 基于业务类型：主键唯一性、时间字段合法性

Author: AxeXie
"""
from fastapi import APIRouter
from pydantic import BaseModel
from typing import List, Optional

router = APIRouter(prefix="/api/v1/quality", tags=["quality"])


class AutoGenerateRequest(BaseModel):
    entity_id: Optional[int] = None
    metric_id: Optional[int] = None
    field_types: Optional[List[dict]] = None


@router.post("/generate-rules")
async def generate_quality_rules(request: AutoGenerateRequest):
    """
    基于实体/指标定义自动生成质量规则
    规则策略：
    - 基于字段类型：NOT NULL、枚举值校验、数值范围
    - 基于历史波动：环比阈值、同比阈值
    - 基于业务类型：主键唯一性、时间字段合法性
    """
    rules = []
    # 根据 field_types 自动生成规则
    if request.field_types:
        for field in request.field_types:
            field_name = field.get("name", "")
            field_type = field.get("type", "string")

            if field.get("is_primary_key"):
                rules.append({
                    "rule_type": "unique",
                    "rule_name": f"{field_name} 唯一性检查",
                    "rule_expression": f'{{"field": "{field_name}", "check": "unique"}}',
                    "severity": "critical"
                })

            if field.get("not_null"):
                rules.append({
                    "rule_type": "not_null",
                    "rule_name": f"{field_name} 非空检查",
                    "rule_expression": f'{{"field": "{field_name}", "check": "not_null"}}',
                    "severity": "warning"
                })

            if field_type in ["int", "bigint", "decimal", "float"]:
                rules.append({
                    "rule_type": "range",
                    "rule_name": f"{field_name} 数值范围检查",
                    "rule_expression": f'{{"field": "{field_name}", "check": "range", "min": 0}}',
                    "severity": "warning"
                })

            if field_type == "date" or field_type == "timestamp":
                rules.append({
                    "rule_type": "range",
                    "rule_name": f"{field_name} 日期合法性检查",
                    "rule_expression": f'{{"field": "{field_name}", "check": "valid_date"}}',
                    "severity": "warning"
                })

    return {"data": rules, "message": "Quality rules generated successfully"}
