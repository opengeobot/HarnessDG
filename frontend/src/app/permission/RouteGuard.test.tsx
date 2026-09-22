/**
 * RouteGuard 组件测试——认证重定向、加载态、权限校验。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/test-utils';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    Navigate: (props: { to: string; replace?: boolean; state?: unknown }) => {
      mockNavigate(props);
      return null;
    },
  };
});

const mockUseAuth = vi.fn();
const mockUsePermission = vi.fn();

vi.mock('@/app/auth', () => ({
  useAuth: (...args: unknown[]) => mockUseAuth(...args),
}));

vi.mock('./usePermission', () => ({
  usePermission: (...args: unknown[]) => mockUsePermission(...args),
}));

// 延迟导入
const { RouteGuard } = await import('./RouteGuard');

describe('RouteGuard', () => {
  beforeEach(() => {
    mockUseAuth.mockReturnValue({
      status: 'authenticated',
      principal: { principalId: 'prn_1', roles: [] },
      scopes: new Set(['asset:read', 'asset:manage']),
      login: vi.fn(),
      logout: vi.fn(),
      reloadPrincipal: vi.fn(),
    });
    mockUsePermission.mockReturnValue({
      hasScope: () => true,
      hasAllScopes: () => true,
    });
  });

  it('anonymous 状态重定向到 /login', () => {
    mockUseAuth.mockReturnValue({
      status: 'anonymous',
      principal: null,
      scopes: new Set(),
      login: vi.fn(),
      logout: vi.fn(),
      reloadPrincipal: vi.fn(),
    });
    renderWithProviders(<RouteGuard><div>Protected</div></RouteGuard>);
    expect(screen.queryByText('Protected')).not.toBeInTheDocument();
    expect(mockNavigate).toHaveBeenCalledWith(
      expect.objectContaining({ to: '/login', replace: true }),
    );
  });

  it('initializing 状态显示 Spin 加载态', () => {
    mockUseAuth.mockReturnValue({
      status: 'initializing',
      principal: null,
      scopes: new Set(),
      login: vi.fn(),
      logout: vi.fn(),
      reloadPrincipal: vi.fn(),
    });
    const { container } = renderWithProviders(<RouteGuard><div>Protected</div></RouteGuard>);
    expect(container.querySelector('.ant-spin')).toBeInTheDocument();
    expect(screen.queryByText('Protected')).not.toBeInTheDocument();
  });

  it('已认证有权限时渲染子组件', () => {
    renderWithProviders(
      <RouteGuard requiredScopes={['asset:read']}>
        <div>Protected Content</div>
      </RouteGuard>,
    );
    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });

  it('已认证无权限时显示 403', () => {
    mockUsePermission.mockReturnValue({
      hasScope: () => false,
      hasAllScopes: () => false,
    });
    renderWithProviders(
      <RouteGuard requiredScopes={['admin:all']}>
        <div>Protected Content</div>
      </RouteGuard>,
    );
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
    expect(screen.getByText(/缺少访问权限/)).toBeInTheDocument();
  });

  it('无 requiredScopes 时已认证即可访问', () => {
    mockUsePermission.mockReturnValue({
      hasScope: () => false,
      hasAllScopes: () => false,
    });
    renderWithProviders(
      <RouteGuard><div>Open Content</div></RouteGuard>,
    );
    expect(screen.getByText('Open Content')).toBeInTheDocument();
  });
});
