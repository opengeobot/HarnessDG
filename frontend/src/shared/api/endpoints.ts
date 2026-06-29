/**
 * 功能: 系统诊断 API 调用。对齐 OpenAPI 契约 GET /api/v1/system/dependencies，
 *       演示 features 如何经统一 client 访问后端，骨架阶段不在页面发起真实请求。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { apiClient } from './client';

/** 依赖健康状态（与 OpenAPI DependencyStatus.status 一致） */
export type DependencyHealth = 'UP' | 'DEGRADED' | 'DOWN';

/** 单个依赖组件健康状态 */
export interface DependencyStatus {
  name: string;
  status: DependencyHealth;
  latencyMs?: number;
}

/** 系统依赖与健康摘要（对齐 OpenAPI SystemDependencySummary） */
export interface SystemDependencySummary {
  status: DependencyHealth;
  dependencies: DependencyStatus[];
}

/** 查询系统依赖与健康摘要（运维诊断只读接口） */
export function fetchSystemDependencies(): Promise<SystemDependencySummary> {
  return apiClient.get<SystemDependencySummary>('/system/dependencies');
}
