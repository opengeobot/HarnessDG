/**
 * 功能: 认证与当前主体相关类型，对齐 OpenAPI 契约 (CurrentPrincipal / TokenPair /
 *       LoginRequest / ChangePasswordRequest)。字段使用 camelCase 与后端一致。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */

/** 访问主体类型（对齐 OpenAPI PrincipalType） */
export type PrincipalType = 'USER' | 'AGENT' | 'SERVICE' | 'API_CLIENT' | 'WORKER';

/** 当前主体信息（GET /me 与登录响应携带） */
export interface CurrentPrincipal {
  principalId: string;
  userId?: string | null;
  principalType: PrincipalType;
  subject: string;
  displayName?: string | null;
  organizationId?: string | null;
  roles: string[];
  scopes: string[];
  locale: string;
  forcePasswordChange?: boolean;
}

/** 登录/刷新签发的令牌对（accessToken 仅内存持有） */
export interface TokenPair {
  tokenType: 'Bearer';
  accessToken: string;
  refreshToken?: string;
  expiresIn: number;
  refreshExpiresIn: number;
  principal: CurrentPrincipal;
}

/** 登录请求体 */
export interface LoginRequest {
  username: string;
  password: string;
}

/** 修改当前用户密码请求体 */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
