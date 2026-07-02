/**
 * 功能: 权限上下文定义 (Context 对象与类型)，与 Provider 组件分离以满足 Fast Refresh。
 *       Scope 来源于真实认证会话（/me 或登录响应），未认证时为空集。
 *       安全判断必须在后端完成，前端仅用于按钮/路由提示，遵循设计第 11.1、15.7 节。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { createContext } from 'react';
import type { Scope } from '@/shared/types';

export interface PermissionContextValue {
  scopes: ReadonlySet<Scope>;
}

export const PermissionContext = createContext<PermissionContextValue | null>(
  null,
);
