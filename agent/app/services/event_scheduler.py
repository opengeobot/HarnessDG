"""
功能：事件驱动调度服务
时间：2026-05-09
作者：AxeXie

实现基于事件的自动任务触发：
- 数据到达触发同步任务
- 质量异常触发告警和修复
- 指标波动触发归因分析
- 外部 Webhook 触发工作流
"""
import logging
from typing import Dict, Any, List, Optional
from datetime import datetime

logger = logging.getLogger(__name__)


class EventBus:
    """事件总线 - 管理和路由所有平台事件"""

    def __init__(self):
        self.listeners: Dict[str, List[callable]] = {}
        self.event_history: List[Dict[str, Any]] = []

    def register_listener(self, event_type: str, callback: callable):
        """注册事件监听器"""
        if event_type not in self.listeners:
            self.listeners[event_type] = []
        self.listeners[event_type].append(callback)
        logger.info(f"Registered listener for event type: {event_type}")

    def emit(self, event_type: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        """
        发布事件并触发所有注册的监听器

        Args:
            event_type: 事件类型
            payload: 事件负载

        Returns:
            事件处理结果
        """
        event = {
            "event_type": event_type,
            "payload": payload,
            "timestamp": datetime.utcnow().isoformat(),
            "event_id": f"evt_{hash(str(payload))}",
        }

        # 记录事件历史
        self.event_history.append(event)

        # 触发监听器
        results = []
        if event_type in self.listeners:
            for callback in self.listeners[event_type]:
                try:
                    result = callback(event)
                    results.append({"listener": callback.__name__, "result": result})
                except Exception as e:
                    logger.error(f"Listener {callback.__name__} failed: {e}")
                    results.append({"listener": callback.__name__, "error": str(e)})

        logger.info(f"Event {event_type} emitted with {len(results)} listeners triggered")
        return {
            "event_id": event["event_id"],
            "listeners_triggered": len(results),
            "results": results,
        }

    def get_event_history(self, limit: int = 100) -> List[Dict[str, Any]]:
        """获取事件历史"""
        return self.event_history[-limit:]


class EventDrivenScheduler:
    """事件驱动调度器"""

    def __init__(self):
        self.event_bus = EventBus()
        self._setup_default_listeners()

    def _setup_default_listeners(self):
        """设置默认事件监听器"""
        # 数据到达事件
        self.event_bus.register_listener("data_arrived", self._on_data_arrived)

        # 质量异常事件
        self.event_bus.register_listener("quality_exception", self._on_quality_exception)

        # 指标波动事件
        self.event_bus.register_listener("metric_fluctuation", self._on_metric_fluctuation)

        # 任务失败事件
        self.event_bus.register_listener("task_failed", self._on_task_failed)

        # Webhook 事件
        self.event_bus.register_listener("webhook_triggered", self._on_webhook_triggered)

    def _on_data_arrived(self, event: Dict[str, Any]) -> Dict[str, Any]:
        """数据到达触发同步任务"""
        payload = event.get("payload", {})
        entity_code = payload.get("entity_code")
        data_count = payload.get("data_count", 0)

        return {
            "action": "trigger_sync",
            "entity_code": entity_code,
            "data_count": data_count,
            "message": f"Sync task triggered for {entity_code} with {data_count} records",
        }

    def _on_quality_exception(self, event: Dict[str, Any]) -> Dict[str, Any]:
        """质量异常触发告警和修复"""
        payload = event.get("payload", {})
        rule_type = payload.get("rule_type")
        severity = payload.get("severity", "warning")

        action = "alert"
        if severity == "critical":
            action = "auto_remediate"

        return {
            "action": action,
            "rule_type": rule_type,
            "severity": severity,
            "message": f"Quality exception handled: {rule_type} ({severity})",
        }

    def _on_metric_fluctuation(self, event: Dict[str, Any]) -> Dict[str, Any]:
        """指标波动触发归因分析"""
        payload = event.get("payload", {})
        metric_code = payload.get("metric_code")
        fluctuation_percent = payload.get("fluctuation_percent", 0)

        return {
            "action": "trigger_attribution_analysis",
            "metric_code": metric_code,
            "fluctuation_percent": fluctuation_percent,
            "message": f"Attribution analysis triggered for {metric_code} ({fluctuation_percent}%)",
        }

    def _on_task_failed(self, event: Dict[str, Any]) -> Dict[str, Any]:
        """任务失败触发诊断"""
        payload = event.get("payload", {})
        task_id = payload.get("task_id")
        error_message = payload.get("error_message", "")

        return {
            "action": "trigger_diagnosis",
            "task_id": task_id,
            "error_message": error_message,
            "message": f"Diagnosis triggered for failed task {task_id}",
        }

    def _on_webhook_triggered(self, event: Dict[str, Any]) -> Dict[str, Any]:
        """Webhook 触发工作流"""
        payload = event.get("payload", {})
        webhook_source = payload.get("source")
        webhook_data = payload.get("data", {})

        return {
            "action": "trigger_workflow",
            "source": webhook_source,
            "data": webhook_data,
            "message": f"Workflow triggered by webhook: {webhook_source}",
        }

    def trigger_event(self, event_type: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        """
        对外接口：触发事件

        Args:
            event_type: 事件类型
            payload: 事件负载

        Returns:
            事件处理结果
        """
        logger.info(f"Triggering event: {event_type}")
        return self.event_bus.emit(event_type, payload)

    def get_event_history(self, limit: int = 100) -> List[Dict[str, Any]]:
        """获取事件历史"""
        return self.event_bus.get_event_history(limit)


# 单例实例
event_driven_scheduler = EventDrivenScheduler()
