/**
 * 功能: 系统健康检查 API 示例。演示 features 如何经统一 client 访问后端，
 *       骨架阶段不在页面发起真实请求，仅提供契约层调用示例。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { apiClient } from './client';

/** 后端健康检查返回（占位契约，P1 与 OpenAPI 对齐） */
export interface HealthStatus {
  status: 'UP' | 'DOWN';
}

/** 查询系统健康状态 */
export function fetchSystemHealth(): Promise<HealthStatus> {
  return apiClient.get<HealthStatus>('/system/health');
}
