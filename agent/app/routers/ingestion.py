"""
数据接入配置生成路由

生成 SeaTunnel 接入配置，支持多种数据源类型和同步模式。

Author: AxeXie
"""
from fastapi import APIRouter
from pydantic import BaseModel
from typing import Optional, List, Dict
from app.services.seatunnel_client import seatunnel_client

router = APIRouter(prefix="/api/v1/ingestion", tags=["ingestion"])


class IngestionConfigRequest(BaseModel):
    source_id: int
    source_type: str = "mysql"
    target_entity_code: str
    sync_mode: str = "full"
    source_table: str
    field_mapping: Optional[List[Dict[str, str]]] = None
    auto_execute: bool = False


@router.post("/generate-config")
async def generate_ingestion_config(request: IngestionConfigRequest):
    """
    生成 SeaTunnel 接入配置
    """
    field_mappings = request.field_mapping or []

    seatunnel_config = {
        "env": {
            "job.mode": "BATCH" if request.sync_mode == "full" else "STREAMING",
            "parallelism": 1
        },
        "source": {
            "plugin": "Jdbc" if request.source_type in ["mysql", "postgresql"] else request.source_type,
            "url": f"jdbc:{request.source_type}://host:3306/database",
            "driver": f"com.{request.source_type}.jdbc.Driver",
            "table": request.source_table,
            "query": f"SELECT * FROM {request.source_table}"
        },
        "sink": {
            "plugin": "Jdbc",
            "url": "jdbc:postgresql://localhost:5432/harnessdg",
            "table": f"ods_{request.target_entity_code}",
            "primary_keys": ["id"]
        },
        "transform": []
    }

    result = {
        "data": {
            "seatunnel_config": seatunnel_config,
            "field_mapping": field_mappings,
            "sync_mode": request.sync_mode
        },
        "message": "Ingestion config generated successfully"
    }

    # 如果要求自动执行，提交到 SeaTunnel
    if request.auto_execute:
        job_result = await seatunnel_client.submit_job(seatunnel_config)
        result["data"]["job_submission"] = job_result

    return result


@router.post("/execute")
async def execute_ingestion_job(config: Dict):
    """
    执行 SeaTunnel 作业
    """
    return await seatunnel_client.submit_job(config)


@router.get("/job-status/{job_id}")
async def get_job_status(job_id: str):
    """
    查询作业状态
    """
    return await seatunnel_client.get_job_status(job_id)


@router.post("/stop-job/{job_id}")
async def stop_job(job_id: str):
    """
    停止作业
    """
    return await seatunnel_client.stop_job(job_id)
