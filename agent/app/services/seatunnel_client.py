"""
功能：SeaTunnel 客户端服务
时间：2026-05-09
作者：AxeXie
"""
import httpx
import logging
from typing import Optional, Dict, Any
from app.config import settings

logger = logging.getLogger(__name__)


class SeaTunnelClient:
    """SeaTunnel API 客户端 - 实际调用 SeaTunnel 服务"""

    def __init__(self):
        self.endpoint = settings.seatunnel_endpoint
        self.client = httpx.AsyncClient(timeout=60.0)

    async def submit_job(self, config: Dict[str, Any]) -> Dict[str, Any]:
        """
        提交 SeaTunnel 作业

        Args:
            config: SeaTunnel 配置 JSON

        Returns:
            作业提交结果，包含 job_id
        """
        try:
            response = await self.client.post(
                f"{self.endpoint}/hazelcast/rest/maps/submit-job",
                json=config,
                headers={"Content-Type": "application/json"},
            )
            response.raise_for_status()
            result = response.json()

            job_id = result.get("jobId")
            logger.info(f"SeaTunnel job submitted successfully: {job_id}")
            return {
                "success": True,
                "job_id": job_id,
                "message": "Job submitted successfully",
            }
        except httpx.HTTPStatusError as e:
            logger.error(f"SeaTunnel HTTP error: {e.response.status_code}")
            return {
                "success": False,
                "job_id": None,
                "message": f"HTTP error: {e.response.status_code}",
            }
        except Exception as e:
            logger.error(f"SeaTunnel job submission failed: {e}")
            return {
                "success": False,
                "job_id": None,
                "message": f"Error: {str(e)}",
            }

    async def get_job_status(self, job_id: str) -> Dict[str, Any]:
        """
        查询作业状态

        Args:
            job_id: 作业 ID

        Returns:
            作业状态信息
        """
        try:
            response = await self.client.get(
                f"{self.endpoint}/hazelcast/rest/maps/job-status/{job_id}"
            )
            response.raise_for_status()
            return response.json()
        except Exception as e:
            logger.error(f"Failed to get job status: {e}")
            return {"success": False, "message": str(e)}

    async def stop_job(self, job_id: str) -> Dict[str, Any]:
        """
        停止作业

        Args:
            job_id: 作业 ID

        Returns:
            停止结果
        """
        try:
            response = await self.client.post(
                f"{self.endpoint}/hazelcast/rest/maps/stop-job/{job_id}"
            )
            response.raise_for_status()
            return {"success": True, "message": "Job stopped"}
        except Exception as e:
            logger.error(f"Failed to stop job: {e}")
            return {"success": False, "message": str(e)}

    async def close(self):
        """关闭客户端"""
        await self.client.aclose()


# 单例实例
seatunnel_client = SeaTunnelClient()
