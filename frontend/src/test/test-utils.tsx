/**
 * 测试工具——提供 i18next、QueryClient、MemoryRouter、AuthProvider、PermissionProvider 等测试上下文包装器。
 */
import type { ReactNode } from 'react';
import { render, type RenderOptions } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { App as AntdApp } from 'antd';
import { vi } from 'vitest';
import i18n from '@/shared/i18n';
import { AuthContext, type AuthContextValue } from '@/app/auth/context';
import { PermissionProvider } from '@/app/permission';

interface TestWrapperOptions {
  route?: string;
  /** 可选：覆盖默认 mock 认证上下文（已认证 + 全 Scope） */
  authContext?: Partial<AuthContextValue>;
}

/** 默认 mock 认证上下文：已认证 + 常用 Scope 全集，供需要 usePermission 的组件测试。 */
const defaultMockAuth: AuthContextValue = {
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
  scopes: new Set([
    'asset:read', 'asset:write', 'asset:manage', 'asset:create', 'asset:update',
    'asset:upload', 'asset:download', 'asset:discuss', 'asset:moderate',
    'asset:review', 'asset:delete', 'asset:deprecate',
    'user:read', 'user:manage', 'authorization:read', 'authorization:manage',
    'agent:register', 'agent:authorize', 'dictionary:read', 'dictionary:manage',
    'tag:read', 'tag:manage', 'system:configure', 'system:observe',
    'job:read', 'job:manage', 'audit:read', 'notification:read',
    'project:view', 'project:manage', 'organization:manage',
    'token:create', 'mcp:invoke',
  ]),
  login: vi.fn().mockResolvedValue({} as any),
  logout: vi.fn().mockResolvedValue(undefined),
  reloadPrincipal: vi.fn().mockResolvedValue(undefined),
};

/** 创建带 i18n + QueryClient + Router + antd App + Mock Auth + Permission 的测试包装器 */
function createWrapper({ route = '/', authContext }: TestWrapperOptions = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const mockAuth = { ...defaultMockAuth, ...authContext };

  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <I18nextProvider i18n={i18n}>
          <AntdApp>
            <MemoryRouter initialEntries={[route]}>
              <AuthContext.Provider value={mockAuth}>
                <PermissionProvider>{children}</PermissionProvider>
              </AuthContext.Provider>
            </MemoryRouter>
          </AntdApp>
        </I18nextProvider>
      </QueryClientProvider>
    );
  };
}

/** render 的增强版：自动包裹测试上下文 */
export function renderWithProviders(
  ui: React.ReactElement,
  options?: RenderOptions & TestWrapperOptions,
) {
  const { route, authContext, ...renderOptions } = options ?? {};
  return render(ui, { wrapper: createWrapper({ route, authContext }), ...renderOptions });
}
