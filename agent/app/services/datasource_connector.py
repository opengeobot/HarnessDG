"""
功能：数据源适配器服务 - 根据数据源类型返回对应的连接配置
时间：2026-05-12
作者：AxeXie

根据 source_type 返回对应的 JDBC URL 格式、driver class、默认端口等配置。
支持 MySQL、PostgreSQL、Hive、Oracle、SQL Server、ClickHouse、Kafka、File/CSV 等数据源。
"""
import logging
from typing import Optional, Dict, Any

logger = logging.getLogger(__name__)


# 数据源类型定义 - 包含 JDBC URL 模板、驱动类、默认端口
DATA_SOURCE_REGISTRY: Dict[str, Dict[str, Any]] = {
    "mysql": {
        "jdbc_url_template": "jdbc:mysql://{host}:{port}/{database}",
        "driver": "com.mysql.cj.jdbc.Driver",
        "default_port": 3306,
        "seatunnel_plugin": "Jdbc",
    },
    "postgresql": {
        "jdbc_url_template": "jdbc:postgresql://{host}:{port}/{database}",
        "driver": "org.postgresql.Driver",
        "default_port": 5432,
        "seatunnel_plugin": "Jdbc",
    },
    "hive": {
        "jdbc_url_template": "jdbc:hive2://{host}:{port}/{database}",
        "driver": "org.apache.hive.jdbc.HiveDriver",
        "default_port": 10000,
        "seatunnel_plugin": "Jdbc",
    },
    "oracle": {
        "jdbc_url_template": "jdbc:oracle:thin:@{host}:{port}:{database}",
        "driver": "oracle.jdbc.OracleDriver",
        "default_port": 1521,
        "seatunnel_plugin": "Jdbc",
    },
    "sqlserver": {
        "jdbc_url_template": "jdbc:sqlserver://{host}:{port};databaseName={database}",
        "driver": "com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "default_port": 1433,
        "seatunnel_plugin": "Jdbc",
    },
    "clickhouse": {
        "jdbc_url_template": "jdbc:clickhouse://{host}:{port}/{database}",
        "driver": "com.clickhouse.jdbc.ClickHouseDriver",
        "default_port": 8123,
        "seatunnel_plugin": "Jdbc",
    },
    "kafka": {
        "jdbc_url_template": None,  # Kafka 非 JDBC 数据源
        "driver": None,
        "default_port": 9092,
        "seatunnel_plugin": "Kafka",
    },
    "file": {
        "jdbc_url_template": None,  # 文件系统非 JDBC
        "driver": None,
        "default_port": None,
        "seatunnel_plugin": "LocalFile",
    },
    "csv": {
        "jdbc_url_template": None,  # CSV 文件非 JDBC
        "driver": None,
        "default_port": None,
        "seatunnel_plugin": "LocalFile",
    },
}


class DataSourceConnector:
    """数据源适配器 - 根据数据源类型返回对应的连接配置"""

    def get_connection_config(
        self,
        source_type: str,
        host: str,
        port: Optional[int] = None,
        database: str = "",
        **kwargs,
    ) -> dict:
        """
        返回数据源连接配置，包含 jdbc_url、driver、host、port、database

        Args:
            source_type: 数据源类型（mysql, postgresql, hive, oracle, sqlserver, clickhouse, kafka, file, csv）
            host: 主机地址
            port: 端口号（可选，使用默认端口如果不提供）
            database: 数据库名称
            **kwargs: 额外参数
                - user: 用户名
                - password: 密码
                - path: 文件路径（用于 file/csv 类型）
                - topic: Kafka topic（用于 kafka 类型）

        Returns:
            数据源连接配置字典

        Raises:
            ValueError: 不支持的数据源类型
        """
        source_type_lower = source_type.lower().strip()

        if source_type_lower not in DATA_SOURCE_REGISTRY:
            supported = ", ".join(DATA_SOURCE_REGISTRY.keys())
            raise ValueError(
                f"不支持的数据源类型: {source_type}。支持的类型: {supported}"
            )

        config = DATA_SOURCE_REGISTRY[source_type_lower]
        effective_port = port or config["default_port"]

        # 基础配置
        result = {
            "source_type": source_type_lower,
            "host": host,
            "port": effective_port,
            "database": database,
            "seatunnel_plugin": config["seatunnel_plugin"],
        }

        # 根据数据源类型构建特定配置
        if config["jdbc_url_template"]:
            # JDBC 类型数据源
            jdbc_url = config["jdbc_url_template"].format(
                host=host,
                port=effective_port,
                database=database,
            )
            result["jdbc_url"] = jdbc_url
            result["driver"] = config["driver"]
        elif source_type_lower == "kafka":
            # Kafka 非 JDBC 数据源
            bootstrap_servers = kwargs.get(
                "bootstrap_servers", f"{host}:{effective_port}"
            )
            topic = kwargs.get("topic", "default_topic")
            result["jdbc_url"] = None
            result["bootstrap_servers"] = bootstrap_servers
            result["topic"] = topic
        elif source_type_lower in ("file", "csv"):
            # 文件系统数据源
            file_path = kwargs.get("path", "/data/input")
            result["jdbc_url"] = None
            result["path"] = file_path
            result["file_type"] = source_type_lower.upper()

        # 添加认证信息（如果提供）
        if kwargs.get("user"):
            result["user"] = kwargs["user"]
        if kwargs.get("password"):
            result["password"] = kwargs["password"]

        logger.info(
            f"获取数据源配置: type={source_type_lower}, host={host}, port={effective_port}"
        )
        return result

    def get_supported_types(self) -> list[str]:
        """返回所有支持的数据源类型列表"""
        return list(DATA_SOURCE_REGISTRY.keys())

    def is_jdbc_type(self, source_type: str) -> bool:
        """判断数据源类型是否为 JDBC 类型"""
        source_type_lower = source_type.lower().strip()
        if source_type_lower not in DATA_SOURCE_REGISTRY:
            return False
        return DATA_SOURCE_REGISTRY[source_type_lower]["jdbc_url_template"] is not None


# 单例实例
datasource_connector = DataSourceConnector()
