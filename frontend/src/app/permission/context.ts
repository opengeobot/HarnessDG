/**
 * 功能: 权限上下文定义 (Context 对象与类型)，与 Provider 组件分离以满足 Fast Refresh。
 *       安全判断必须在后端完成，前端仅用于按钮/路由提示，遵循设计第 11.1、15.7 节。
 * 时间: 2026-06-29
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

/**
 * 骨架阶段授予全部 Scope，便于本地浏览所有占位页面。
 * P1 接入后端后，应由会话信息填充真实 Scope。
 */
export const ALL_SCOPES: readonly Scope[] = [
  'asset:read',
  'asset:preview',
  'asset:download',
  'asset:write',
  'asset:submit',
  'asset:publish',
  'asset:admin',
];
