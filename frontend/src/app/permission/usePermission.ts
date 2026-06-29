/**
 * 功能: usePermission Hook 最小骨架。统一表达前端权限点判断。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { useContext } from 'react';
import type { Scope } from '@/shared/types';
import { PermissionContext } from './context';

export interface UsePermissionResult {
  /** 是否具备指定 Scope（前端提示用，安全以后端为准） */
  hasScope: (scope: Scope) => boolean;
  /** 是否具备全部指定 Scope */
  hasAllScopes: (scopes: Scope[]) => boolean;
}

export function usePermission(): UsePermissionResult {
  const context = useContext(PermissionContext);
  if (!context) {
    throw new Error('usePermission 必须在 PermissionProvider 内使用');
  }
  const { scopes } = context;
  return {
    hasScope: (scope) => scopes.has(scope),
    hasAllScopes: (required) => required.every((scope) => scopes.has(scope)),
  };
}
