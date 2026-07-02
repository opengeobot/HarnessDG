/**
 * 功能: shared/api 统一导出
 * 时间: 2026-07-01
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
export {
  login,
  refreshToken,
  logout,
  getCurrentPrincipal,
  changeCurrentUserPassword,
} from './auth';
export {
  getAccessToken,
  setAccessToken,
  setRefreshHandler,
  setUnauthorizedHandler,
} from './tokenHolder';
