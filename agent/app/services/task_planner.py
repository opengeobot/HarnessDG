"""
功能：任务计划生成服务
时间：2026-05-09
作者：AxeXie
"""
import logging
from typing import Optional
from app.models.schemas import IntentResponse

logger = logging.getLogger(__name__)


class TaskPlanner:
    """任务计划生成器 - 将意图识别结果转化为标准化执行计划"""

    # 任务类型到执行计划的映射
    EXECUTION_PLANS = {
        "build_metric": {
            "steps": [
                "validate_definition",
                "generate_asset",
                "create_quality_rules",
                "create_job",
                "submit_approval",
                "publish_metric",
            ],
            "required_fields": ["entity", "metric", "dimensions", "time_scope"],
            "approval_required": True,
        },
        "ask_data": {
            "steps": [
                "parse_query",
                "resolve_ontology",
                "generate_sql",
                "execute_query",
                "format_result",
            ],
            "required_fields": ["query"],
            "approval_required": False,
        },
        "generate_report": {
            "steps": [
                "select_metrics",
                "fetch_data",
                "calculate_trends",
                "generate_analysis",
                "format_report",
            ],
            "required_fields": ["report_type", "time_range", "metrics"],
            "approval_required": False,
        },
        "data_ingestion": {
            "steps": [
                "validate_source",
                "generate_pipeline",
                "create_mapping",
                "submit_approval",
                "execute_ingestion",
                "verify_result",
            ],
            "required_fields": ["source_type", "target_entity", "sync_mode"],
            "approval_required": True,
        },
        "diagnose_exception": {
            "steps": [
                "collect_logs",
                "analyze_root_cause",
                "generate_suggestions",
                "execute_remediation",
            ],
            "required_fields": ["target_type", "target_id"],
            "approval_required": False,
        },
    }

    def generate_plan(
        self,
        intent: IntentResponse,
        context: Optional[dict] = None,
    ) -> dict:
        """
        根据意图生成执行计划

        Args:
            intent: 意图识别结果
            context: 额外上下文信息

        Returns:
            标准化执行计划 JSON
        """
        intent_type = intent.intent
        plan_template = self.EXECUTION_PLANS.get(intent_type)

        if not plan_template:
            logger.warning(f"Unknown intent type: {intent_type}")
            return self._default_plan(intent_type, context)

        # 构建执行计划
        execution_plan = {
            "intent": intent_type,
            "confidence": intent.confidence,
            "entities": intent.entities,
            "execution_plan": plan_template["steps"],
            "required_fields": plan_template["required_fields"],
            "approval_required": plan_template["approval_required"],
            "context": context or {},
        }

        # 验证必需字段
        missing_fields = self._validate_required_fields(
            execution_plan["required_fields"],
            intent.entities,
        )
        execution_plan["missing_fields"] = missing_fields

        logger.info(f"Generated execution plan for intent: {intent_type}")
        return execution_plan

    def _validate_required_fields(
        self,
        required_fields: list[str],
        entities: dict,
    ) -> list[str]:
        """验证必需字段是否完整"""
        return [field for field in required_fields if field not in entities]

    def _default_plan(
        self,
        intent_type: str,
        context: Optional[dict] = None,
    ) -> dict:
        """生成默认执行计划（未知意图）"""
        return {
            "intent": intent_type,
            "confidence": 0.0,
            "entities": {},
            "execution_plan": ["parse_request", "execute_generic", "return_result"],
            "required_fields": [],
            "approval_required": False,
            "missing_fields": [],
            "context": context or {},
        }


# 单例实例
task_planner = TaskPlanner()
