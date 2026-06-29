/**
 * 功能: shared/api 统一导出
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
export { apiClient, API_BASE_URL } from './client';
export { ApiError, isApiError } from './errors';
export { fetchSystemDependencies } from './endpoints';
export type {
  DependencyHealth,
  DependencyStatus,
  SystemDependencySummary,
} from './endpoints';
