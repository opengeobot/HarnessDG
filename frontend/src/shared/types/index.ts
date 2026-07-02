/**
 * 功能: shared/types 统一导出
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
export type {
  ApiResponse,
  ApiErrorBody,
  CursorPage,
  PageResult,
} from './api';
export type { Scope } from './permission';
export type {
  PrincipalType,
  CurrentPrincipal,
  TokenPair,
  LoginRequest,
  ChangePasswordRequest,
} from './auth';
