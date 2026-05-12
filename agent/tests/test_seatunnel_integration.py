"""
功能：SeaTunnel 集成测试
时间：2026-05-12
作者：AxeXie

测试流程：
1. 创建数据源配置（使用 mock）
2. 生成 .conf 文件
3. 验证 .conf 格式正确（HOCON 语法检查）
4. 提交到 SeaTunnel（mock 模式）
5. 查询任务状态

使用 pytest + unittest.mock 进行 mock。
"""
import pytest
import re
from unittest.mock import AsyncMock, patch, MagicMock
from app.services.datasource_connector import DataSourceConnector, datasource_connector
from app.services.pipeline_generator import PipelineGenerator, pipeline_generator
from app.services.seatunnel_client import SeaTunnelClient


# ============================================================
# 测试 1: DataSourceConnector 数据源配置
# ============================================================

class TestDataSourceConnector:
    """数据源适配器测试"""

    def test_mysql_connection_config(self):
        """MySQL 数据源配置测试"""
        config = datasource_connector.get_connection_config(
            source_type="mysql",
            host="db.example.com",
            port=3306,
            database="testdb",
            user="root",
            password="secret",
        )

        assert config["source_type"] == "mysql"
        assert config["jdbc_url"] == "jdbc:mysql://db.example.com:3306/testdb"
        assert config["driver"] == "com.mysql.cj.jdbc.Driver"
        assert config["host"] == "db.example.com"
        assert config["port"] == 3306
        assert config["database"] == "testdb"
        assert config["user"] == "root"
        assert config["password"] == "secret"
        assert config["seatunnel_plugin"] == "Jdbc"

    def test_postgresql_connection_config(self):
        """PostgreSQL 数据源配置测试"""
        config = datasource_connector.get_connection_config(
            source_type="postgresql",
            host="pg.example.com",
            port=5432,
            database="mydb",
        )

        assert config["source_type"] == "postgresql"
        assert config["jdbc_url"] == "jdbc:postgresql://pg.example.com:5432/mydb"
        assert config["driver"] == "org.postgresql.Driver"
        assert config["port"] == 5432

    def test_oracle_connection_config(self):
        """Oracle 数据源配置测试"""
        config = datasource_connector.get_connection_config(
            source_type="oracle",
            host="oracle.example.com",
            port=1521,
            database="ORCL",
        )

        assert config["source_type"] == "oracle"
        assert config["jdbc_url"] == "jdbc:oracle:thin:@oracle.example.com:1521:ORCL"
        assert config["driver"] == "oracle.jdbc.OracleDriver"

    def test_sqlserver_connection_config(self):
        """SQL Server 数据源配置测试"""
        config = datasource_connector.get_connection_config(
            source_type="sqlserver",
            host="sqlserver.example.com",
            port=1433,
            database="master",
        )

        assert config["source_type"] == "sqlserver"
        assert "databaseName=master" in config["jdbc_url"]
        assert config["driver"] == "com.microsoft.sqlserver.jdbc.SQLServerDriver"

    def test_clickhouse_connection_config(self):
        """ClickHouse 数据源配置测试"""
        config = datasource_connector.get_connection_config(
            source_type="clickhouse",
            host="clickhouse.example.com",
            port=8123,
            database="default",
        )

        assert config["source_type"] == "clickhouse"
        assert config["jdbc_url"] == "jdbc:clickhouse://clickhouse.example.com:8123/default"
        assert config["driver"] == "com.clickhouse.jdbc.ClickHouseDriver"

    def test_hive_connection_config(self):
        """Hive 数据源配置测试"""
        config = datasource_connector.get_connection_config(
            source_type="hive",
            host="hive.example.com",
            port=10000,
            database="default",
        )

        assert config["source_type"] == "hive"
        assert config["jdbc_url"] == "jdbc:hive2://hive.example.com:10000/default"
        assert config["driver"] == "org.apache.hive.jdbc.HiveDriver"

    def test_kafka_connection_config(self):
        """Kafka 数据源配置测试（非 JDBC）"""
        config = datasource_connector.get_connection_config(
            source_type="kafka",
            host="kafka.example.com",
            port=9092,
            topic="my_topic",
        )

        assert config["source_type"] == "kafka"
        assert config["jdbc_url"] is None
        assert config["bootstrap_servers"] == "kafka.example.com:9092"
        assert config["topic"] == "my_topic"
        assert config["seatunnel_plugin"] == "Kafka"

    def test_file_connection_config(self):
        """File 数据源配置测试"""
        config = datasource_connector.get_connection_config(
            source_type="file",
            host="localhost",
            path="/data/input",
        )

        assert config["source_type"] == "file"
        assert config["jdbc_url"] is None
        assert config["path"] == "/data/input"
        assert config["file_type"] == "FILE"
        assert config["seatunnel_plugin"] == "LocalFile"

    def test_csv_connection_config(self):
        """CSV 数据源配置测试"""
        config = datasource_connector.get_connection_config(
            source_type="csv",
            host="localhost",
            path="/data/csv",
        )

        assert config["source_type"] == "csv"
        assert config["file_type"] == "CSV"

    def test_default_port(self):
        """测试默认端口"""
        config = datasource_connector.get_connection_config(
            source_type="mysql",
            host="localhost",
            database="testdb",
        )

        assert config["port"] == 3306  # MySQL 默认端口

    def test_unsupported_source_type(self):
        """测试不支持的数据源类型"""
        with pytest.raises(ValueError) as exc_info:
            datasource_connector.get_connection_config(
                source_type="unknown_db",
                host="localhost",
                database="test",
            )

        assert "不支持的数据源类型" in str(exc_info.value)
        assert "unknown_db" in str(exc_info.value)

    def test_is_jdbc_type(self):
        """测试 JDBC 类型判断"""
        assert datasource_connector.is_jdbc_type("mysql") is True
        assert datasource_connector.is_jdbc_type("postgresql") is True
        assert datasource_connector.is_jdbc_type("kafka") is False
        assert datasource_connector.is_jdbc_type("file") is False
        assert datasource_connector.is_jdbc_type("unknown") is False

    def test_get_supported_types(self):
        """测试获取支持的数据源类型列表"""
        types = datasource_connector.get_supported_types()
        assert "mysql" in types
        assert "postgresql" in types
        assert "kafka" in types
        assert "file" in types
        assert "csv" in types


# ============================================================
# 测试 2: PipelineGenerator HOCON 配置生成
# ============================================================

class TestPipelineGeneratorHOCON:
    """Pipeline 生成器 HOCON 格式测试"""

    def test_generate_conf_mysql(self):
        """测试 MySQL 数据源的 HOCON 配置生成"""
        generator = PipelineGenerator()
        ods_layer = {"table_name": "ods_sales_order"}

        conf = generator.generate_conf(
            entity_code="sales_order",
            source_type="mysql",
            source_table="orders",
            ods_layer=ods_layer,
            source_host="db.example.com",
            source_port=3306,
            source_database="testdb",
            source_user="root",
            source_password="secret",
            target_host="localhost",
            target_port=5432,
            target_database="harnessdg",
            target_user="harness",
            target_password="harness_dev",
        )

        # 验证 HOCON 格式基本结构
        assert "env {" in conf
        assert "source {" in conf
        assert "sink {" in conf
        assert 'job.mode = "BATCH"' in conf
        assert "parallelism = 1" in conf

        # 验证 source 配置
        assert "jdbc:mysql://db.example.com:3306/testdb" in conf
        assert "com.mysql.cj.jdbc.Driver" in conf
        assert 'user = "root"' in conf
        assert 'password = "secret"' in conf
        assert "SELECT * FROM orders" in conf

        # 验证 sink 配置
        assert "jdbc:postgresql://localhost:5432/harnessdg" in conf
        assert "org.postgresql.Driver" in conf
        assert 'table = "ods_sales_order"' in conf

    def test_generate_conf_postgresql(self):
        """测试 PostgreSQL 数据源的 HOCON 配置生成"""
        generator = PipelineGenerator()
        ods_layer = {"table_name": "ods_user"}

        conf = generator.generate_conf(
            entity_code="user",
            source_type="postgresql",
            source_table="users",
            ods_layer=ods_layer,
            source_host="pg.example.com",
            source_port=5432,
            source_database="mydb",
        )

        assert "jdbc:postgresql://pg.example.com:5432/mydb" in conf
        assert "org.postgresql.Driver" in conf

    def test_generate_conf_kafka(self):
        """测试 Kafka 数据源的 HOCON 配置生成"""
        generator = PipelineGenerator()
        ods_layer = {"table_name": "ods_events"}

        conf = generator.generate_conf(
            entity_code="events",
            source_type="kafka",
            source_table="",
            ods_layer=ods_layer,
            source_host="kafka.example.com",
            source_port=9092,
            source_database="",
            topic="user_events",
        )

        assert "Kafka {" in conf
        assert "bootstrap.servers" in conf
        assert 'topic = "user_events"' in conf
        assert "format = json" in conf

    def test_generate_conf_file(self):
        """测试 File 数据源的 HOCON 配置生成"""
        generator = PipelineGenerator()
        ods_layer = {"table_name": "ods_logs"}

        conf = generator.generate_conf(
            entity_code="logs",
            source_type="file",
            source_table="",
            ods_layer=ods_layer,
            source_host="localhost",
            path="/data/logs",
        )

        assert "LocalFile {" in conf
        assert 'path = "/data/logs"' in conf
        assert 'file_format_type = "FILE"' in conf

    def test_generate_conf_csv(self):
        """测试 CSV 数据源的 HOCON 配置生成"""
        generator = PipelineGenerator()
        ods_layer = {"table_name": "ods_data"}

        conf = generator.generate_conf(
            entity_code="data",
            source_type="csv",
            source_table="",
            ods_layer=ods_layer,
            source_host="localhost",
            path="/data/csv",
        )

        assert 'file_format_type = "CSV"' in conf

    def test_hocon_format_validation(self):
        """测试 HOCON 格式基本语法验证"""
        generator = PipelineGenerator()
        ods_layer = {"table_name": "ods_test"}

        conf = generator.generate_conf(
            entity_code="test",
            source_type="mysql",
            source_table="test_table",
            ods_layer=ods_layer,
            source_host="localhost",
            source_database="testdb",
        )

        # 验证 HOCON 块结构完整性
        # 检查 env/source/sink 块是否正确闭合
        assert conf.count("{") == conf.count("}")

        # 检查引号配对
        double_quotes = conf.count('"')
        assert double_quotes % 2 == 0, "引号未配对"

        # 检查必要关键字
        for keyword in ["env", "source", "sink", "job.mode", "url", "driver", "table"]:
            assert keyword in conf, f"缺少关键字: {keyword}"

    def test_generate_full_pipeline_with_conf(self):
        """测试完整 Pipeline 生成（包含 HOCON 配置）"""
        generator = PipelineGenerator()

        pipeline = generator.generate_full_pipeline(
            entity_code="sales_order",
            metric_code="total_revenue",
            dimensions=["date", "channel"],
            data_domain="sales",
            schedule_cron="0 2 * * *",
            source_type="mysql",
            source_table="orders",
            source_host="db.example.com",
            source_port=3306,
            source_database="testdb",
            source_user="root",
            source_password="secret",
        )

        # 验证 Pipeline 基本结构
        assert pipeline["pipeline_id"] == "pipeline_sales_order_total_revenue"
        assert pipeline["entity_code"] == "sales_order"
        assert pipeline["metric_code"] == "total_revenue"
        assert "seatunnel_config" in pipeline
        assert "seatunnel_conf_text" in pipeline
        assert pipeline["status"] == "generated"

        # 验证 seatunnel_config 包含 conf_text
        seatunnel_config = pipeline["seatunnel_config"]
        assert "conf_text" in seatunnel_config

        # 验证 seatunnel_conf_text 是 HOCON 格式字符串
        conf_text = pipeline["seatunnel_conf_text"]
        assert isinstance(conf_text, str)
        assert "env {" in conf_text
        assert "source {" in conf_text
        assert "sink {" in conf_text

        # 验证数据分层
        assert "ods" in pipeline["layers"]
        assert "dwd" in pipeline["layers"]
        assert "dws" in pipeline["layers"]
        assert "ads" in pipeline["layers"]

        # 验证质量规则
        assert len(pipeline["quality_rules"]) > 0


# ============================================================
# 测试 3: SeaTunnelClient 带重试机制
# ============================================================

class TestSeaTunnelClient:
    """SeaTunnel 客户端测试（mock 模式）"""

    @pytest.fixture
    def mock_settings(self):
        """模拟配置"""
        with patch("app.services.seatunnel_client.settings") as mock_settings:
            mock_settings.seatunnel_endpoint = "http://localhost:8080"
            mock_settings.seatunnel_timeout = 60
            mock_settings.seatunnel_max_retries = 3
            yield mock_settings

    @pytest.fixture
    def client(self, mock_settings):
        """创建 SeaTunnel 客户端实例"""
        return SeaTunnelClient()

    @pytest.mark.asyncio
    async def test_submit_job_success(self, client):
        """测试作业提交成功"""
        mock_response = MagicMock()
        mock_response.json.return_value = {"jobId": "test-job-123"}
        mock_response.raise_for_status = MagicMock()

        client.client.post = AsyncMock(return_value=mock_response)

        result = await client.submit_job({"content": "env {}"})

        assert result["success"] is True
        assert result["job_id"] == "test-job-123"
        assert result["message"] == "Job submitted successfully"

    @pytest.mark.asyncio
    async def test_submit_job_with_conf_string(self, client):
        """测试使用 HOCON 字符串提交作业"""
        mock_response = MagicMock()
        mock_response.json.return_value = {"jobId": "test-job-456"}
        mock_response.raise_for_status = MagicMock()

        client.client.post = AsyncMock(return_value=mock_response)

        hocon_conf = """
        env {
          job.mode = "BATCH"
        }
        source {
          Jdbc {
            url = "jdbc:mysql://localhost:3306/testdb"
          }
        }
        """
        result = await client.submit_job(hocon_conf)

        assert result["success"] is True
        assert result["job_id"] == "test-job-456"

        # 验证请求数据格式
        call_args = client.client.post.call_args
        assert call_args[1]["json"]["content"] == hocon_conf

    @pytest.mark.asyncio
    async def test_submit_job_http_error(self, client):
        """测试作业提交 HTTP 错误"""
        import httpx
        mock_response = MagicMock()
        mock_response.status_code = 500
        mock_response.text = "Internal Server Error"

        client.client.post = AsyncMock(
            side_effect=httpx.HTTPStatusError(
                "Server Error",
                request=MagicMock(),
                response=mock_response,
            )
        )

        result = await client.submit_job({"content": "env {}"})

        assert result["success"] is False
        assert "HTTP error: 500" in result["message"]

    @pytest.mark.asyncio
    async def test_submit_job_retry_on_5xx(self, client):
        """测试 5xx 错误时自动重试"""
        import httpx

        call_count = 0

        async def mock_post(*args, **kwargs):
            nonlocal call_count
            call_count += 1
            if call_count < 3:
                # 前两次调用返回 500 错误
                mock_response = MagicMock()
                mock_response.status_code = 500
                mock_response.text = "Server Error"
                raise httpx.HTTPStatusError(
                    "Server Error",
                    request=MagicMock(),
                    response=mock_response,
                )
            # 第三次调用成功
            mock_response = MagicMock()
            mock_response.json.return_value = {"jobId": "retry-job-789"}
            mock_response.raise_for_status = MagicMock()
            return mock_response

        client.client.post = mock_post
        client.max_retries = 3

        result = await client.submit_job({"content": "env {}"})

        assert result["success"] is True
        assert result["job_id"] == "retry-job-789"
        assert call_count == 3  # 重试了 3 次

    @pytest.mark.asyncio
    async def test_get_job_status_success(self, client):
        """测试查询作业状态成功"""
        mock_response = MagicMock()
        mock_response.json.return_value = {
            "jobId": "test-job-123",
            "jobStatus": "FINISHED",
        }
        mock_response.raise_for_status = MagicMock()

        client.client.get = AsyncMock(return_value=mock_response)

        result = await client.get_job_status("test-job-123")

        assert result["success"] is True
        assert result["jobStatus"] == "FINISHED"

    @pytest.mark.asyncio
    async def test_get_job_status_not_found(self, client):
        """测试查询不存在的作业状态"""
        import httpx
        mock_response = MagicMock()
        mock_response.status_code = 404

        client.client.get = AsyncMock(
            side_effect=httpx.HTTPStatusError(
                "Not Found",
                request=MagicMock(),
                response=mock_response,
            )
        )

        result = await client.get_job_status("non-existent-job")

        assert result["success"] is False
        assert "HTTP error: 404" in result["message"]

    @pytest.mark.asyncio
    async def test_stop_job_success(self, client):
        """测试停止作业成功"""
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()

        client.client.post = AsyncMock(return_value=mock_response)

        result = await client.stop_job("test-job-123")

        assert result["success"] is True
        assert result["message"] == "Job stopped"

    @pytest.mark.asyncio
    async def test_retry_exhausted(self, client):
        """测试重试耗尽后失败"""
        import httpx

        call_count = 0

        async def mock_post(*args, **kwargs):
            nonlocal call_count
            call_count += 1
            mock_response = MagicMock()
            mock_response.status_code = 500
            mock_response.text = "Server Error"
            raise httpx.HTTPStatusError(
                "Server Error",
                request=MagicMock(),
                response=mock_response,
            )

        client.client.post = mock_post
        client.max_retries = 2

        result = await client.submit_job({"content": "env {}"})

        assert result["success"] is False
        assert call_count == 2  # 最多重试 2 次

    @pytest.mark.asyncio
    async def test_no_retry_on_4xx(self, client):
        """测试 4xx 错误不重试"""
        import httpx

        call_count = 0

        async def mock_post(*args, **kwargs):
            nonlocal call_count
            call_count += 1
            mock_response = MagicMock()
            mock_response.status_code = 400
            mock_response.text = "Bad Request"
            raise httpx.HTTPStatusError(
                "Bad Request",
                request=MagicMock(),
                response=mock_response,
            )

        client.client.post = mock_post
        client.max_retries = 3

        result = await client.submit_job({"content": "env {}"})

        assert result["success"] is False
        assert call_count == 1  # 4xx 错误不重试

    @pytest.mark.asyncio
    async def test_retry_on_network_error(self, client):
        """测试网络错误时重试"""
        import httpx

        call_count = 0

        async def mock_post(*args, **kwargs):
            nonlocal call_count
            call_count += 1
            if call_count < 2:
                raise httpx.ConnectError("Connection refused")
            mock_response = MagicMock()
            mock_response.json.return_value = {"jobId": "network-retry-job"}
            mock_response.raise_for_status = MagicMock()
            return mock_response

        client.client.post = mock_post
        client.max_retries = 3

        result = await client.submit_job({"content": "env {}"})

        assert result["success"] is True
        assert result["job_id"] == "network-retry-job"
        assert call_count == 2


# ============================================================
# 测试 4: 端到端集成测试（mock 模式）
# ============================================================

class TestSeatunnelEndToEnd:
    """SeaTunnel 端到端集成测试"""

    @pytest.mark.asyncio
    async def test_full_pipeline_flow(self):
        """测试完整流程：生成配置 -> 提交作业 -> 查询状态"""

        # 1. 生成 Pipeline 配置
        generator = PipelineGenerator()
        pipeline = generator.generate_full_pipeline(
            entity_code="test_entity",
            metric_code="test_metric",
            dimensions=["dim1", "dim2"],
            data_domain="test",
            source_type="mysql",
            source_table="test_table",
            source_host="db.example.com",
            source_port=3306,
            source_database="testdb",
        )

        assert pipeline["status"] == "generated"
        conf_text = pipeline["seatunnel_conf_text"]
        assert isinstance(conf_text, str)
        assert "env {" in conf_text

        # 2. Mock 提交作业
        with patch("app.services.seatunnel_client.settings") as mock_settings:
            mock_settings.seatunnel_endpoint = "http://localhost:8080"
            mock_settings.seatunnel_timeout = 60
            mock_settings.seatunnel_max_retries = 3

            client = SeaTunnelClient()

            mock_response = MagicMock()
            mock_response.json.return_value = {"jobId": "e2e-job-001"}
            mock_response.raise_for_status = MagicMock()
            client.client.post = AsyncMock(return_value=mock_response)

            result = await client.submit_job(conf_text)

            assert result["success"] is True
            assert result["job_id"] == "e2e-job-001"

            # 3. Mock 查询作业状态
            mock_status_response = MagicMock()
            mock_status_response.json.return_value = {
                "jobId": "e2e-job-001",
                "jobStatus": "RUNNING",
            }
            mock_status_response.raise_for_status = MagicMock()
            client.client.get = AsyncMock(return_value=mock_status_response)

            status = await client.get_job_status("e2e-job-001")

            assert status["success"] is True
            assert status["jobStatus"] == "RUNNING"
