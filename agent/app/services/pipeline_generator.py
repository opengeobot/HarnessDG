"""
功能：全自动 Pipeline 生成服务
时间：2026-05-09
作者：AxeXie

基于本体定义（Entity/Metric/Dimension）自动生成完整的数据 Pipeline，包括：
- 数据接入配置（SeaTunnel Pipeline，支持 HOCON .conf 格式）
- 数据分层模型（ODS -> DWD -> DWS -> ADS）
- 调度依赖关系（Dagster DAG）
- 质量规则集
"""
import logging
from typing import Dict, List, Optional, Any
from app.models.schemas import IntentResponse
from app.services.datasource_connector import datasource_connector

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
        source_host: str = "localhost",
        source_port: Optional[int] = None,
        source_database: str = "",
        source_user: str = "",
        source_password: str = "",
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
            source_host: 数据源主机地址
            source_port: 数据源端口
            source_database: 数据源数据库名
            source_user: 数据源用户名
            source_password: 数据源密码

        Returns:
            完整 Pipeline 配置
        """
        logger.info(f"Generating full pipeline for entity={entity_code}, metric={metric_code}")

        # 1. 生成数据分层模型
        layers = self._generate_data_layers(entity_code, metric_code, dimensions, source_table)

        # 2. 生成 SeaTunnel HOCON 配置文件
        seatunnel_conf = self._generate_seatunnel_config(
            entity_code, source_type, source_table, layers["ods"],
            source_host, source_port, source_database, source_user, source_password,
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
            "seatunnel_config": seatunnel_conf,
            "seatunnel_conf_text": self.generate_conf(
                entity_code, source_type, source_table, layers["ods"],
                source_host, source_port, source_database, source_user, source_password,
            ),
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

    def generate_conf(
        self,
        entity_code: str,
        source_type: str,
        source_table: str,
        ods_layer: Dict[str, Any],
        source_host: str = "localhost",
        source_port: Optional[int] = None,
        source_database: str = "",
        source_user: str = "",
        source_password: str = "",
        target_host: str = "localhost",
        target_port: int = 5432,
        target_database: str = "harnessdg",
        target_user: str = "harness",
        target_password: str = "harness_dev",
        job_mode: str = "BATCH",
        parallelism: int = 1,
        **kwargs,
    ) -> str:
        """
        生成 SeaTunnel HOCON 格式的 .conf 配置文件

        Args:
            entity_code: 实体代码
            source_type: 数据源类型
            source_table: 源表名
            ods_layer: ODS 层配置
            source_host: 数据源主机
            source_port: 数据源端口
            source_database: 数据源数据库
            source_user: 数据源用户名
            source_password: 数据源密码
            target_host: 目标主机
            target_port: 目标端口
            target_database: 目标数据库
            target_user: 目标用户名
            target_password: 目标密码
            job_mode: 作业模式（BATCH/STREAM）
            parallelism: 并行度
            **kwargs: 额外参数（topic 用于 Kafka, path 用于 File/CSV）

        Returns:
            HOCON 格式的配置文件字符串
        """
        # 使用 DataSourceConnector 获取源端配置
        try:
            source_config = datasource_connector.get_connection_config(
                source_type=source_type,
                host=source_host,
                port=source_port,
                database=source_database,
                user=source_user,
                password=source_password,
                **kwargs,
            )
        except ValueError as e:
            logger.error(f"数据源配置获取失败: {e}")
            source_config = {}

        # 获取目标端配置（默认 PostgreSQL）
        target_config = datasource_connector.get_connection_config(
            source_type="postgresql",
            host=target_host,
            port=target_port,
            database=target_database,
            user=target_user,
            password=target_password,
        )

        # 确定源表名
        table_name = source_table or entity_code
        ods_table_name = ods_layer.get("table_name", f"ods_{entity_code}")

        # 构建 HOCON 配置字符串
        conf_lines = []

        # env 配置
        conf_lines.append("env {")
        conf_lines.append(f'  job.mode = "{job_mode}"')
        conf_lines.append(f"  parallelism = {parallelism}")
        if job_mode == "BATCH":
            conf_lines.append("  checkpoint_interval = 10000")
        conf_lines.append("}")

        # source 配置
        conf_lines.append("source {")
        source_plugin = source_config.get("seatunnel_plugin", "Jdbc")

        if datasource_connector.is_jdbc_type(source_type):
            jdbc_url = source_config.get("jdbc_url", f"jdbc:{source_type}://{source_host}:{source_port or 3306}/{source_database}")
            driver = source_config.get("driver", "")

            conf_lines.append(f"  Jdbc {{")
            conf_lines.append(f'    url = "{jdbc_url}"')
            conf_lines.append(f'    driver = "{driver}"')
            if source_user:
                conf_lines.append(f'    user = "{source_user}"')
            if source_password:
                conf_lines.append(f'    password = "{source_password}"')
            conf_lines.append(f'    query = "SELECT * FROM {table_name}"')
            conf_lines.append("  }")
        elif source_type.lower() == "kafka":
            bootstrap_servers = source_config.get("bootstrap_servers", f"{source_host}:{source_port or 9092}")
            topic = source_config.get("topic", "default_topic")
            conf_lines.append(f"  Kafka {{")
            conf_lines.append(f'    bootstrap.servers = "{bootstrap_servers}"')
            conf_lines.append(f'    topic = "{topic}"')
            conf_lines.append('    format = json')
            conf_lines.append("  }")
        elif source_type.lower() in ("file", "csv"):
            file_path = source_config.get("path", "/data/input")
            file_type = source_config.get("file_type", "CSV")
            conf_lines.append(f"  LocalFile {{")
            conf_lines.append(f'    path = "{file_path}"')
            conf_lines.append(f'    file_format_type = "{file_type}"')
            conf_lines.append("  }")

        conf_lines.append("}")

        # sink 配置
        conf_lines.append("sink {")
        if target_config.get("jdbc_url"):
            conf_lines.append("  Jdbc {")
            conf_lines.append(f'    url = "{target_config["jdbc_url"]}"')
            conf_lines.append(f'    driver = "{target_config["driver"]}"')
            if target_user:
                conf_lines.append(f'    user = "{target_user}"')
            if target_password:
                conf_lines.append(f'    password = "{target_password}"')
            conf_lines.append(f'    table = "{ods_table_name}"')
            conf_lines.append("  }")
        conf_lines.append("}")

        return "\n".join(conf_lines)

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
        source_host: str = "localhost",
        source_port: Optional[int] = None,
        source_database: str = "",
        source_user: str = "",
        source_password: str = "",
    ) -> Dict[str, Any]:
        """
        生成 SeaTunnel 接入配置（JSON 格式，保持向后兼容）

        内部调用 generate_conf() 生成 HOCON 格式配置
        """
        # 生成 HOCON 格式配置
        conf_text = self.generate_conf(
            entity_code=entity_code,
            source_type=source_type,
            source_table=source_table,
            ods_layer=ods_layer,
            source_host=source_host,
            source_port=source_port,
            source_database=source_database,
            source_user=source_user,
            source_password=source_password,
        )

        # 同时返回 JSON 结构（向后兼容）
        ods_table_name = ods_layer.get("table_name", f"ods_{entity_code}")
        table_name = source_table or entity_code

        # 获取源端连接配置
        try:
            source_conn_config = datasource_connector.get_connection_config(
                source_type=source_type,
                host=source_host,
                port=source_port,
                database=source_database,
                user=source_user,
                password=source_password,
            )
        except ValueError:
            source_conn_config = {}

        return {
            "conf_text": conf_text,
            "env": {
                "job.mode": "BATCH",
                "parallelism": 1,
                "checkpoint_interval": 10000,
            },
            "source": {
                "plugin": source_conn_config.get("seatunnel_plugin", "Jdbc"),
                "url": source_conn_config.get("jdbc_url", f"jdbc:{source_type}://{source_host}:{source_port or 3306}/{source_database}"),
                "driver": source_conn_config.get("driver", ""),
                "table": table_name,
                "query": f"SELECT * FROM {table_name}",
            },
            "sink": {
                "plugin": "Jdbc",
                "url": f"jdbc:postgresql://{source_host}:5432/harnessdg",
                "table": ods_table_name,
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
