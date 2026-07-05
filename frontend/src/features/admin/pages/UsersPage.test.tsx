/**
 * UsersPage 组件测试——验证用户管理页面的列表渲染、创建表单与状态展示。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { UsersPage } from './UsersPage';
import { renderWithProviders } from '@/test/test-utils';

const mockListUsers = vi.fn().mockResolvedValue({
  items: [
    {
      userId: 'usr_01',
      username: 'admin',
      displayName: 'Administrator',
      email: 'admin@test.com',
      status: 'ACTIVE',
      locale: 'zh-CN',
    },
  ],
  page: 1,
  pageSize: 20,
  total: 1,
});
const mockCreateUser = vi.fn().mockResolvedValue({ userId: 'usr_02' });
const mockEnableUser = vi.fn().mockResolvedValue({});
const mockDisableUser = vi.fn().mockResolvedValue({});
const mockResetUserPassword = vi.fn().mockResolvedValue({});
const mockUpdateUser = vi.fn().mockResolvedValue({});

vi.mock('../api', () => ({
  listUsers: (...args: unknown[]) => mockListUsers(...args),
  createUser: (...args: unknown[]) => mockCreateUser(...args),
  enableUser: (...args: unknown[]) => mockEnableUser(...args),
  disableUser: (...args: unknown[]) => mockDisableUser(...args),
  resetUserPassword: (...args: unknown[]) => mockResetUserPassword(...args),
  updateUser: (...args: unknown[]) => mockUpdateUser(...args),
}));

describe('UsersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders page title', async () => {
    renderWithProviders(<UsersPage />);
    await waitFor(() => {
      expect(screen.getByText(/用户管理|users\.title/i)).toBeInTheDocument();
    });
  });

  it('renders user list after loading', async () => {
    renderWithProviders(<UsersPage />);
    await waitFor(() => {
      expect(screen.getByText('admin')).toBeInTheDocument();
      expect(screen.getByText('Administrator')).toBeInTheDocument();
    });
  });

  it('shows ACTIVE status tag', async () => {
    renderWithProviders(<UsersPage />);
    await waitFor(() => {
      expect(screen.getByText('ACTIVE')).toBeInTheDocument();
    });
  });

  it('renders create user button', async () => {
    renderWithProviders(<UsersPage />);
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /创建|create/i })).toBeInTheDocument();
    });
  });
});
