/**
 * AccessPage 组件测试——主体信息、Scope 列表、API Token 占位。
 */
import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { AccessPage } from './AccessPage';
import { renderWithProviders } from '@/test/test-utils';

vi.mock('@/app/auth', () => ({
  useAuth: () => ({
    status: 'authenticated',
    principal: {
      principalId: 'prn_admin',
      principalType: 'USER',
      subject: 'admin',
      displayName: 'Administrator',
      organizationId: null,
      roles: ['ROLE_ADMIN'],
      scopes: ['asset:read', 'asset:manage', 'admin:all'],
      locale: 'zh-CN',
    },
    scopes: new Set(['asset:read', 'asset:manage', 'admin:all']),
    login: vi.fn(),
    logout: vi.fn(),
    reloadPrincipal: vi.fn(),
  }),
}));

describe('AccessPage', () => {
  it('渲染页面标题', () => {
    renderWithProviders(<AccessPage />);
    expect(screen.getByText(/访问与凭据|access\.title/i)).toBeInTheDocument();
  });

  it('显示主体信息', () => {
    renderWithProviders(<AccessPage />);
    expect(screen.getByText('prn_admin')).toBeInTheDocument();
    expect(screen.getByText('USER')).toBeInTheDocument();
    expect(screen.getByText('admin')).toBeInTheDocument();
    expect(screen.getByText('Administrator')).toBeInTheDocument();
  });

  it('显示角色 Tag', () => {
    renderWithProviders(<AccessPage />);
    expect(screen.getByText('ROLE_ADMIN')).toBeInTheDocument();
  });

  it('显示 Scope 列表', () => {
    renderWithProviders(<AccessPage />);
    expect(screen.getByText('asset:read')).toBeInTheDocument();
    expect(screen.getByText('asset:manage')).toBeInTheDocument();
    expect(screen.getByText('admin:all')).toBeInTheDocument();
  });

  it('显示 API Token 占位', () => {
    renderWithProviders(<AccessPage />);
    expect(screen.getByText('API Token')).toBeInTheDocument();
    expect(screen.getByText(/即将|comingSoon|coming soon/i)).toBeInTheDocument();
  });
});
