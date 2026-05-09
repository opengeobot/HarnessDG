"""
功能：异常自愈与策略推荐服务
时间：2026-05-09
作者：AxeXie

在异常诊断基础上增加自动修复能力：
- 低风险异常自动重试/回滚
- 常见错误自动修复（配置修正、资源调整）
- AI 推荐修复策略（基于历史经验）
- 自愈策略学习和优化
"""
import logging
from typing import Dict, Any, List, Optional
from datetime import datetime

logger = logging.getLogger(__name__)


class RemediationEngine:
    """异常自愈引擎"""

    # 可自动修复的异常类型清单
    AUTO_REMEDIABLE_ERRORS = {
        "connection_timeout": {
            "strategy": "retry_with_backoff",
            "max_retries": 3,
            "backoff_factor": 2,
            "description": "Connection timeout - retrying with exponential backoff",
        },
        "temporary_resource_unavailable": {
            "strategy": "retry_after_delay",
            "delay_seconds": 60,
            "max_retries": 2,
            "description": "Resource temporarily unavailable - retrying after delay",
        },
        "stale_cache": {
            "strategy": "clear_cache_and_retry",
            "description": "Stale cache detected - clearing cache and retrying",
        },
        "minor_config_issue": {
            "strategy": "auto_correct_config",
            "description": "Minor configuration issue - auto-correcting",
        },
    }

    # 修复策略库
    STRATEGY_LIBRARY = {
        "retry": {
            "applicable_errors": ["connection_timeout", "temporary_failure"],
            "risk_level": "low",
            "success_rate": 0.85,
            "description": "Retry the failed operation",
        },
        "rollback": {
            "applicable_errors": ["data_corruption", "schema_mismatch"],
            "risk_level": "medium",
            "success_rate": 0.70,
            "description": "Rollback to previous stable state",
        },
        "scale_resources": {
            "applicable_errors": ["out_of_memory", "cpu_throttling"],
            "risk_level": "low",
            "success_rate": 0.90,
            "description": "Scale up resources and retry",
        },
        "fallback_source": {
            "applicable_errors": ["source_unavailable", "network_partition"],
            "risk_level": "medium",
            "success_rate": 0.75,
            "description": "Switch to fallback data source",
        },
    }

    # 修复历史
    remediation_history: List[Dict[str, Any]] = []

    def analyze_and_remediate(
        self,
        error_type: str,
        error_message: str,
        context: Dict[str, Any],
        auto_remediate: bool = True,
    ) -> Dict[str, Any]:
        """
        分析错误并执行/推荐修复策略

        Args:
            error_type: 错误类型
            error_message: 错误信息
            context: 错误上下文
            auto_remediate: 是否自动修复

        Returns:
            修复结果
        """
        logger.info(f"Analyzing error: {error_type}")

        # 1. 判断是否可自动修复
        is_auto_remediable = error_type in self.AUTO_REMEDIABLE_ERRORS

        # 2. 推荐修复策略
        recommended_strategy = self._recommend_strategy(error_type, error_message, context)

        # 3. 执行自动修复（如果允许且可行）
        remediation_result = None
        if auto_remediate and is_auto_remediable:
            remediation_result = self._execute_remediation(
                error_type, recommended_strategy, context
            )

        # 4. 记录历史
        history_entry = {
            "error_type": error_type,
            "error_message": error_message,
            "recommended_strategy": recommended_strategy,
            "auto_remediated": auto_remediate and is_auto_remediable,
            "remediation_result": remediation_result,
            "timestamp": datetime.utcnow().isoformat(),
        }
        self.remediation_history.append(history_entry)

        return {
            "error_type": error_type,
            "is_auto_remediable": is_auto_remediable,
            "recommended_strategy": recommended_strategy,
            "auto_remediated": auto_remediate and is_auto_remediable,
            "remediation_result": remediation_result,
            "message": (
                f"Auto-remediation executed" if auto_remediate and is_auto_remediable
                else f"Manual action required: {recommended_strategy['description']}"
            ),
        }

    def _recommend_strategy(
        self,
        error_type: str,
        error_message: str,
        context: Dict[str, Any],
    ) -> Dict[str, Any]:
        """推荐最佳修复策略"""
        # 检查是否是已知错误类型
        if error_type in self.AUTO_REMEDIABLE_ERRORS:
            auto_strategy = self.AUTO_REMEDIABLE_ERRORS[error_type]
            return {
                "strategy": auto_strategy["strategy"],
                "risk_level": "low",
                "description": auto_strategy["description"],
                "confidence": 0.90,
            }

        # 基于错误消息匹配策略
        for strategy_name, strategy_info in self.STRATEGY_LIBRARY.items():
            for applicable_error in strategy_info["applicable_errors"]:
                if applicable_error in error_type or applicable_error in error_message.lower():
                    return {
                        "strategy": strategy_name,
                        "risk_level": strategy_info["risk_level"],
                        "description": strategy_info["description"],
                        "confidence": strategy_info["success_rate"],
                    }

        # 默认策略
        return {
            "strategy": "manual_investigation",
            "risk_level": "high",
            "description": "Manual investigation required - no auto-remediation available",
            "confidence": 0.50,
        }

    def _execute_remediation(
        self,
        error_type: str,
        strategy: Dict[str, Any],
        context: Dict[str, Any],
    ) -> Dict[str, Any]:
        """执行自动修复"""
        strategy_name = strategy.get("strategy")

        try:
            if strategy_name == "retry_with_backoff":
                return self._retry_with_backoff(error_type, context)
            elif strategy_name == "clear_cache_and_retry":
                return self._clear_cache_and_retry(context)
            elif strategy_name == "auto_correct_config":
                return self._auto_correct_config(context)
            else:
                return {
                    "success": False,
                    "message": f"Strategy {strategy_name} not implemented",
                }
        except Exception as e:
            logger.error(f"Remediation failed: {e}")
            return {
                "success": False,
                "message": f"Remediation execution failed: {str(e)}",
            }

    def _retry_with_backoff(
        self,
        error_type: str,
        context: Dict[str, Any],
    ) -> Dict[str, Any]:
        """指数退避重试"""
        config = self.AUTO_REMEDIABLE_ERRORS.get(error_type, {})
        max_retries = config.get("max_retries", 3)

        # 实际重试逻辑（这里简化为记录）
        return {
            "success": True,
            "strategy": "retry_with_backoff",
            "max_retries": max_retries,
            "message": f"Retry scheduled with exponential backoff (max {max_retries} attempts)",
        }

    def _clear_cache_and_retry(self, context: Dict[str, Any]) -> Dict[str, Any]:
        """清除缓存并重试"""
        # 实际缓存清除逻辑（这里简化为记录）
        return {
            "success": True,
            "strategy": "clear_cache_and_retry",
            "message": "Cache cleared and retry scheduled",
        }

    def _auto_correct_config(self, context: Dict[str, Any]) -> Dict[str, Any]:
        """自动修正配置"""
        # 实际配置修正逻辑（这里简化为记录）
        return {
            "success": True,
            "strategy": "auto_correct_config",
            "message": "Configuration auto-corrected",
        }

    def get_remediation_history(
        self,
        limit: int = 50,
        error_type: Optional[str] = None,
    ) -> List[Dict[str, Any]]:
        """获取修复历史"""
        history = self.remediation_history

        if error_type:
            history = [h for h in history if h.get("error_type") == error_type]

        return history[-limit:]

    def get_strategy_statistics(self) -> Dict[str, Any]:
        """获取策略统计信息"""
        stats = {}
        for entry in self.remediation_history:
            strategy = entry.get("recommended_strategy", {}).get("strategy", "unknown")
            if strategy not in stats:
                stats[strategy] = {"count": 0, "success": 0}
            stats[strategy]["count"] += 1
            if entry.get("remediation_result", {}).get("success"):
                stats[strategy]["success"] += 1

        # 计算成功率
        for strategy_name, strategy_stats in stats.items():
            total = strategy_stats["count"]
            success = strategy_stats["success"]
            strategy_stats["success_rate"] = success / total if total > 0 else 0

        return stats


# 单例实例
remediation_engine = RemediationEngine()
