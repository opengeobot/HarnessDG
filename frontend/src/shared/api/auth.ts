/**
 * 功能: 认证相关 API 调用，对齐 OpenAPI /auth/* 与 /me、/me/password 端点。
 *       所有请求经统一 apiClient；认证端点由 client 自动开启 withCredentials 传递安全 Cookie。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { apiClient } from './client';
import type {
  ChangePasswordRequest,
  CurrentPrincipal,
  LoginRequest,
  TokenPair,
} from '@/shared/types';

/** 本地用户登录，签发访问 JWT 并下发 refresh Cookie */
export function login(payload: LoginRequest): Promise<TokenPair> {
  return apiClient.post<TokenPair>('/auth/login', payload);
}

/** 使用 refresh Cookie 轮换访问 JWT */
export function refreshToken(): Promise<TokenPair> {
  return apiClient.post<TokenPair>('/auth/refresh');
}

/** 登出并吊销当前 Token Family */
export function logout(): Promise<void> {
  return apiClient.post<void>('/auth/logout');
}

/** 获取当前主体、角色与粗粒度 Scope */
export function getCurrentPrincipal(): Promise<CurrentPrincipal> {
  return apiClient.get<CurrentPrincipal>('/me');
}

/** 修改当前用户密码 */
export function changeCurrentUserPassword(
  payload: ChangePasswordRequest,
): Promise<void> {
  return apiClient.put<void>('/me/password', payload);
}
