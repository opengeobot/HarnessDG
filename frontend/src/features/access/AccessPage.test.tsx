/**
 * AccessPage 组件测试——主体信息、Scope 列表、PAT 管理。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { AccessPage } from './AccessPage';
import { renderWithProviders } from '@/test/test-utils';

const mockListPats = vi.fn().mockResolvedValue([]);

vi.mock('./api', () => ({
  listPats: () => mockListPats(),
  createPat: vi.fn(),
  revokePat: vi.fn(),
}));

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
      scopes: ['asset:read', 'asset:manage', 'token:create'],
      locale: 'zh-CN',
    },
    scopes: new Set(['asset:read', 'asset:manage', 'token:create']),
    login: vi.fn(),
    logout: vi.fn(),
    reloadPrincipal: vi.fn(),
  }),
}));

describe('AccessPage', () => {
  beforeEach(() => {
    mockListPats.mockClear();
  });

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

  it('显示 Scope 列表', () => {
    renderWithProviders(<AccessPage />);
    expect(screen.getByText('asset:read')).toBeInTheDocument();
    expect(screen.getByText('token:create')).toBeInTheDocument();
  });

  it('显示 API Token 管理区域', () => {
    renderWithProviders(<AccessPage />);
    expect(screen.getByText('API Token')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /创建 Token|Create Token/i })).toBeInTheDocument();
  });
});
