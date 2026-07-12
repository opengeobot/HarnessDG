/**
 * 功能: PermissionProvider 组件测试——覆盖 AC-P0B-UI-002（权限体验）：
 *       从认证上下文读取 Scope 集合，向子树提供权限判断。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { AuthContext, type AuthContextValue } from '@/app/auth/context';
import { PermissionProvider } from './PermissionProvider';
import { usePermission } from './usePermission';

function createWrapper(authContext: Partial<AuthContextValue> = {}) {
  const defaultAuth: AuthContextValue = {
    status: 'authenticated',
    principal: {
      principalId: 'prn_test',
      userId: 'usr_test',
      principalType: 'USER',
      subject: 'prn_test',
      displayName: 'Test User',
      organizationId: null,
      roles: [],
      scopes: [],
      locale: 'zh-CN',
      forcePasswordChange: false,
    },
    scopes: new Set(['asset:read', 'asset:write', 'user:read']),
    login: vi.fn().mockResolvedValue({} as any),
    logout: vi.fn().mockResolvedValue(undefined),
    reloadPrincipal: vi.fn().mockResolvedValue(undefined),
    ...authContext,
  };

  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <AuthContext.Provider value={defaultAuth}>
        <PermissionProvider>{children}</PermissionProvider>
      </AuthContext.Provider>
    );
  };
}

describe('PermissionProvider', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('从认证上下文读取 Scope 并传递给子组件', () => {
    const { result } = renderHook(() => usePermission(), {
      wrapper: createWrapper({
        scopes: new Set(['asset:read', 'asset:write', 'user:read']),
      }),
    });

    expect(result.current.hasScope('asset:read')).toBe(true);
    expect(result.current.hasScope('asset:write')).toBe(true);
    expect(result.current.hasScope('user:read')).toBe(true);
  });

  it('未认证的 Scope 集合为空', () => {
    const { result } = renderHook(() => usePermission(), {
      wrapper: createWrapper({
        status: 'anonymous',
        principal: null,
        scopes: new Set(),
      }),
    });

    expect(result.current.hasScope('asset:read')).toBe(false);
    expect(result.current.hasAllScopes(['asset:read'])).toBe(false);
  });

  it('hasAllScopes 全部满足时返回 true', () => {
    const { result } = renderHook(() => usePermission(), {
      wrapper: createWrapper({
        scopes: new Set(['asset:read', 'asset:write', 'user:read']),
      }),
    });

    expect(result.current.hasAllScopes(['asset:read', 'asset:write'])).toBe(true);
    expect(result.current.hasAllScopes(['asset:read', 'asset:manage'])).toBe(false);
  });

  it('hasScope 对不存在的 Scope 返回 false', () => {
    const { result } = renderHook(() => usePermission(), {
      wrapper: createWrapper({
        scopes: new Set(['asset:read']),
      }),
    });

    expect(result.current.hasScope('system:configure')).toBe(false);
    expect(result.current.hasScope('asset:read')).toBe(true);
  });

  it('不同权限的用户看到不同 Scope 集合', () => {
    // 管理员
    const { result: adminResult } = renderHook(() => usePermission(), {
      wrapper: createWrapper({
        scopes: new Set(['asset:read', 'asset:write', 'user:manage', 'system:configure']),
      }),
    });

    // 普通用户
    const { result: userResult } = renderHook(() => usePermission(), {
      wrapper: createWrapper({
        scopes: new Set(['asset:read']),
      }),
    });

    expect(adminResult.current.hasScope('user:manage')).toBe(true);
    expect(adminResult.current.hasScope('system:configure')).toBe(true);
    expect(userResult.current.hasScope('user:manage')).toBe(false);
    expect(userResult.current.hasScope('system:configure')).toBe(false);
  });
});
