/**
 * 功能: 权限 Provider 组件。向子树提供当前主体的 Scope 集合（骨架阶段默认全开放）。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { useMemo, type ReactNode } from 'react';
import {
  ALL_SCOPES,
  PermissionContext,
  type PermissionContextValue,
} from './context';

export function PermissionProvider({ children }: { children: ReactNode }) {
  const value = useMemo<PermissionContextValue>(
    () => ({ scopes: new Set(ALL_SCOPES) }),
    [],
  );
  return (
    <PermissionContext.Provider value={value}>
      {children}
    </PermissionContext.Provider>
  );
}
