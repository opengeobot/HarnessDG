/**
 * 功能: 权限 Provider 组件。从认证会话读取真实 Scope 集合并向子树提供，
 *       未认证时为空集。移除骨架期的全 Scope 默认注入，遵循 AGENTS 授权约束。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useMemo, type ReactNode } from 'react';
import { useAuth } from '@/app/auth';
import type { Scope } from '@/shared/types';
import { PermissionContext, type PermissionContextValue } from './context';

export function PermissionProvider({ children }: { children: ReactNode }) {
  const { scopes } = useAuth();
  const value = useMemo<PermissionContextValue>(
    () => ({ scopes: new Set<Scope>(scopes as ReadonlySet<Scope>) }),
    [scopes],
  );
  return (
    <PermissionContext.Provider value={value}>
      {children}
    </PermissionContext.Provider>
  );
}
