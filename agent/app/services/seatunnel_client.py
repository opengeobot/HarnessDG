"""
功能：SeaTunnel 客户端服务 - 支持重试机制和超时配置
时间：2026-05-12
作者：AxeXie

基于 httpx AsyncClient 实现，支持：
- 从 config.py 读取 endpoint、超时、重试配置
- 指数退避重试机制（最多 3 次）
- 完善的错误处理（HTTPError 和通用异常分别捕获）
"""
import asyncio
import httpx
import logging
from typing import Optional, Dict, Any
from app.config import settings

logger = logging.getLogger(__name__)


class SeaTunnelClient:
    """SeaTunnel API 客户端 - 支持重试机制和超时配置"""

    def __init__(self):
        # 从配置读取 endpoint
        self.endpoint = settings.seatunnel_endpoint
        # 超时配置
        self.timeout = settings.seatunnel_timeout
        # 重试配置
        self.max_retries = settings.seatunnel_max_retries
        # 创建异步 HTTP 客户端
        self.client = httpx.AsyncClient(
            timeout=httpx.Timeout(self.timeout, connect=10.0, read=self.timeout),
        )

    async def submit_job(self, config: Dict[str, Any]) -> Dict[str, Any]:
        """
        提交 SeaTunnel 作业（带重试机制）

        Args:
            config: SeaTunnel 配置（HOCON 字符串或 JSON 对象）

        Returns:
            作业提交结果，包含 job_id
        """
        # 判断配置类型：字符串作为 HOCON 内容，字典作为 JSON
        if isinstance(config, str):
            request_data = {"content": config}
        else:
            request_data = config

        async def _do_submit() -> Dict[str, Any]:
            """执行单次提交请求"""
            response = await self.client.post(
                f"{self.endpoint}/hazelcast/rest/maps/submit-job",
                json=request_data,
                headers={"Content-Type": "application/json"},
            )
            response.raise_for_status()
            return response.json()

        try:
            result = await self._retry_with_backoff(_do_submit)

            job_id = result.get("jobId")
            logger.info(f"SeaTunnel job submitted successfully: {job_id}")
            return {
                "success": True,
                "job_id": job_id,
                "message": "Job submitted successfully",
            }
        except httpx.HTTPStatusError as e:
            logger.error(f"SeaTunnel HTTP error: {e.response.status_code} - {e.response.text}")
            return {
                "success": False,
                "job_id": None,
                "message": f"HTTP error: {e.response.status_code}",
            }
        except Exception as e:
            logger.error(f"SeaTunnel job submission failed after retries: {e}")
            return {
                "success": False,
                "job_id": None,
                "message": f"Error: {str(e)}",
            }

    async def get_job_status(self, job_id: str) -> Dict[str, Any]:
        """
        查询作业状态（带重试机制）

        Args:
            job_id: 作业 ID

        Returns:
            作业状态信息
        """
        async def _do_get_status() -> Dict[str, Any]:
            """执行单次状态查询"""
            response = await self.client.get(
                f"{self.endpoint}/hazelcast/rest/maps/job-status/{job_id}"
            )
            response.raise_for_status()
            return response.json()

        try:
            result = await self._retry_with_backoff(_do_get_status)
            result["success"] = True
            return result
        except httpx.HTTPStatusError as e:
            logger.error(f"Failed to get job status: HTTP {e.response.status_code}")
            return {
                "success": False,
                "message": f"HTTP error: {e.response.status_code}",
            }
        except Exception as e:
            logger.error(f"Failed to get job status: {e}")
            return {"success": False, "message": str(e)}

    async def stop_job(self, job_id: str) -> Dict[str, Any]:
        """
        停止作业（带重试机制）

        Args:
            job_id: 作业 ID

        Returns:
            停止结果
        """
        async def _do_stop() -> Dict[str, Any]:
            """执行单次停止请求"""
            response = await self.client.post(
                f"{self.endpoint}/hazelcast/rest/maps/stop-job/{job_id}"
            )
            response.raise_for_status()
            return {"success": True, "message": "Job stopped"}

        try:
            result = await self._retry_with_backoff(_do_stop)
            return result
        except httpx.HTTPStatusError as e:
            logger.error(f"Failed to stop job: HTTP {e.response.status_code}")
            return {
                "success": False,
                "message": f"HTTP error: {e.response.status_code}",
            }
        except Exception as e:
            logger.error(f"Failed to stop job: {e}")
            return {"success": False, "message": str(e)}

    async def _retry_with_backoff(self, func, *args, **kwargs) -> Any:
        """
        带指数退避的重试机制

        Args:
            func: 要执行的异步函数
            *args: 函数位置参数
            **kwargs: 函数关键字参数

        Returns:
            函数执行结果

        Raises:
            最后一次尝试的异常
        """
        last_exception = None

        for attempt in range(self.max_retries):
            try:
                return await func(*args, **kwargs)
            except httpx.HTTPStatusError as e:
                # HTTP 错误（4xx/5xx），5xx 可重试
                if e.response.status_code >= 500 and attempt < self.max_retries - 1:
                    wait_time = 2 ** attempt  # 指数退避：1s, 2s, 4s
                    logger.warning(
                        f"SeaTunnel request failed with {e.response.status_code}, "
                        f"retry {attempt + 1}/{self.max_retries} after {wait_time}s"
                    )
                    await asyncio.sleep(wait_time)
                    last_exception = e
                else:
                    # 4xx 错误或已达最大重试次数，直接抛出
                    raise
            except httpx.RequestError as e:
                # 网络错误（连接超时、DNS 解析失败等），可重试
                if attempt < self.max_retries - 1:
                    wait_time = 2 ** attempt
                    logger.warning(
                        f"SeaTunnel request error: {e}, "
                        f"retry {attempt + 1}/{self.max_retries} after {wait_time}s"
                    )
                    await asyncio.sleep(wait_time)
                    last_exception = e
                else:
                    raise
            except Exception:
                # 其他未知异常，直接抛出不重试
                raise

        # 所有重试耗尽，抛出最后一次异常
        raise last_exception

    async def close(self):
        """关闭客户端"""
        await self.client.aclose()


# 单例实例
seatunnel_client = SeaTunnelClient()
