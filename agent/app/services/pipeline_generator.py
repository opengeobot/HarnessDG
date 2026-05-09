"""
功能：全自动 Pipeline 生成服务
时间：2026-05-09
作者：AxeXie

基于本体定义（Entity/Metric/Dimension）自动生成完整的数据 Pipeline，包括：
- 数据接入配置（SeaTunnel Pipeline）
- 数据分层模型（ODS -> DWD -> DWS -> ADS）
- 调度依赖关系（Dagster DAG）
- 质量规则集
"""
import logging
from typing import Dict, List, Optional, Any
from app.models.schemas import IntentResponse

logger = logging.getLogger(__name__)


class PipelineGenerator:
    """全自动 Pipeline 生成器"""

    # 数据分层模板
    LAYER_TEMPLATES = {
        "ods": {
            "description": "Operational Data Store - 原始数据层",
            "transform": [],
            "quality_rules": ["not_null", "unique_key"],
        },
        "dwd": {
            "description": "Data Warehouse Detail - 明细数据层",
            "transform": ["clean", "standardize"],
            "quality_rules": ["not_null", "valid_format", "range_check"],
        },
        "dws": {
            "description": "Data Warehouse Summary - 汇总数据层",
            "transform": ["aggregate", "join"],
            "quality_rules": ["completeness", "consistency"],
        },
        "ads": {
            "description": "Application Data Store - 应用数据层",
            "transform": ["filter", "sort", "limit"],
            "quality_rules": ["threshold_check"],
        },
    }

    def generate_full_pipeline(
        self,
        entity_code: str,
        metric_code: str,
        dimensions: List[str],
        data_domain: str,
        schedule_cron: str = "0 2 * * *",
        source_type: str = "mysql",
        source_table: str = "",
    ) -> Dict[str, Any]:
        """
        基于本体定义生成完整 Pipeline

        Args:
            entity_code: 实体代码
            metric_code: 指标代码
            dimensions: 维度列表
            data_domain: 数据域
            schedule_cron: 调度 CRON 表达式
            source_type: 数据源类型
            source_table: 源表名

        Returns:
            完整 Pipeline 配置
        """
        logger.info(f"Generating full pipeline for entity={entity_code}, metric={metric_code}")

        # 1. 生成数据分层模型
        layers = self._generate_data_layers(entity_code, metric_code, dimensions, source_table)

        # 2. 生成 SeaTunnel 接入配置
        seatunnel_config = self._generate_seatunnel_config(
            entity_code, source_type, source_table, layers["ods"]
        )

        # 3. 生成 Dagster DAG 配置
        dagster_config = self._generate_dagster_config(
            entity_code, metric_code, layers, schedule_cron
        )

        # 4. 生成质量规则集
        quality_rules = self._generate_quality_rules(entity_code, metric_code, dimensions)

        # 5. 组装完整 Pipeline
        pipeline = {
            "pipeline_id": f"pipeline_{entity_code}_{metric_code}",
            "entity_code": entity_code,
            "metric_code": metric_code,
            "data_domain": data_domain,
            "layers": layers,
            "seatunnel_config": seatunnel_config,
            "dagster_config": dagster_config,
            "quality_rules": quality_rules,
            "schedule": {
                "cron": schedule_cron,
                "timezone": "UTC",
            },
            "status": "generated",
            "ready_to_deploy": True,
        }

        logger.info(f"Pipeline generated successfully: {pipeline['pipeline_id']}")
        return pipeline

    def _generate_data_layers(
        self,
        entity_code: str,
        metric_code: str,
        dimensions: List[str],
        source_table: str,
    ) -> Dict[str, Any]:
        """生成数据分层模型"""
        layers = {}

        for layer_name, layer_template in self.LAYER_TEMPLATES.items():
            table_name = f"{layer_name}_{entity_code}"
            if layer_name == "ads":
                table_name = f"{layer_name}_{metric_code}"

            layers[layer_name] = {
                "table_name": table_name,
                "description": layer_template["description"],
                "transforms": layer_template["transform"],
                "source_table": source_table if layer_name == "ods" else f"{self._get_previous_layer(layer_name)}_{entity_code}",
                "quality_rules": layer_template["quality_rules"],
            }

        return layers

    def _get_previous_layer(self, current_layer: str) -> str:
        """获取上一层名称"""
        layer_order = ["ods", "dwd", "dws", "ads"]
        idx = layer_order.index(current_layer)
        if idx == 0:
            return "source"
        return layer_order[idx - 1]

    def _generate_seatunnel_config(
        self,
        entity_code: str,
        source_type: str,
        source_table: str,
        ods_layer: Dict[str, Any],
    ) -> Dict[str, Any]:
        """生成 SeaTunnel 接入配置"""
        return {
            "env": {
                "job.mode": "BATCH",
                "parallelism": 1,
                "checkpoint_interval": 10000,
            },
            "source": {
                "plugin": "Jdbc" if source_type in ["mysql", "postgresql"] else source_type,
                "url": f"jdbc:{source_type}://host:3306/database",
                "driver": f"com.{source_type}.jdbc.Driver",
                "table": source_table or entity_code,
                "query": f"SELECT * FROM {source_table or entity_code}",
            },
            "sink": {
                "plugin": "Jdbc",
                "url": "jdbc:postgresql://localhost:5432/harnessdg",
                "table": ods_layer["table_name"],
                "primary_keys": ["id"],
            },
            "transform": [],
        }

    def _generate_dagster_config(
        self,
        entity_code: str,
        metric_code: str,
        layers: Dict[str, Any],
        schedule_cron: str,
    ) -> Dict[str, Any]:
        """生成 Dagster DAG 配置"""
        assets = []
        dependencies = []

        # 为每一层创建 Asset
        for layer_name, layer_config in layers.items():
            asset_key = f"asset_{layer_name}_{entity_code if layer_name != 'ads' else metric_code}"
            assets.append({
                "key": asset_key,
                "group_name": entity_code,
                "description": f"{layer_name} layer for {entity_code}",
            })

            # 建立依赖关系
            if layer_name != "ods":
                prev_layer = self._get_previous_layer(layer_name)
                prev_asset = f"asset_{prev_layer}_{entity_code if prev_layer != 'ods' else metric_code}"
                dependencies.append({
                    "asset": asset_key,
                    "depends_on": prev_asset,
                })

        # 创建 Job
        job_name = f"job_{entity_code}_{metric_code}"

        # 创建 Schedule
        schedule_name = f"schedule_{entity_code}_{metric_code}"

        return {
            "assets": assets,
            "dependencies": dependencies,
            "job": {
                "name": job_name,
                "asset_keys": [a["key"] for a in assets],
                "description": f"Job for {entity_code} - {metric_code}",
            },
            "schedule": {
                "name": schedule_name,
                "job_name": job_name,
                "cron_schedule": schedule_cron,
                "timezone": "UTC",
            },
        }

    def _generate_quality_rules(
        self,
        entity_code: str,
        metric_code: str,
        dimensions: List[str],
    ) -> List[Dict[str, Any]]:
        """生成质量规则集"""
        rules = [
            {
                "rule_type": "not_null",
                "target": "id",
                "description": f"Primary key must not be null",
                "severity": "critical",
            },
            {
                "rule_type": "unique",
                "target": "id",
                "description": f"Primary key must be unique",
                "severity": "critical",
            },
        ]

        # 为维度字段添加规则
        for dim in dimensions:
            rules.append({
                "rule_type": "not_null",
                "target": dim,
                "description": f"Dimension {dim} must not be null",
                "severity": "warning",
            })

        # 指标规则
        rules.append({
            "rule_type": "range_check",
            "target": metric_code,
            "description": f"Metric {metric_code} must be within valid range",
            "severity": "critical",
            "params": {
                "min_value": 0,
            },
        })

        return rules


# 单例实例
pipeline_generator = PipelineGenerator()
