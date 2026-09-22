/**
 * RoleBindingsPage 组件测试——验证角色绑定页面渲染与列表。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { RoleBindingsPage } from './RoleBindingsPage';
import { renderWithProviders } from '@/test/test-utils';

const mockListRoleBindings = vi.fn().mockResolvedValue([
  {
    bindingId: 'rb_001',
    principalId: 'prn_user',
    roleId: 'role_admin',
    scopeType: 'PLATFORM',
    scopeId: null,
    createdAt: '2026-07-01T00:00:00Z',
  },
]);
const mockListRoles = vi.fn().mockResolvedValue([
  { roleId: 'role_admin', roleCode: 'admin', roleName: 'Admin', roleType: 'SYSTEM', permissionCodes: [], version: 1 },
]);
const mockCreateRoleBinding = vi.fn();
const mockDeleteRoleBinding = vi.fn();

vi.mock('../api', () => ({
  listRoleBindings: (...args: unknown[]) => mockListRoleBindings(...args),
  listRoles: (...args: unknown[]) => mockListRoles(...args),
  createRoleBinding: (...args: unknown[]) => mockCreateRoleBinding(...args),
  deleteRoleBinding: (...args: unknown[]) => mockDeleteRoleBinding(...args),
}));

describe('RoleBindingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders page title', async () => {
    renderWithProviders(<RoleBindingsPage />);
    await waitFor(() => {
      expect(screen.getByText(/角色绑定|roleBindings\.title/i)).toBeInTheDocument();
    });
  });

  it('renders binding list', async () => {
    renderWithProviders(<RoleBindingsPage />);
    await waitFor(() => {
      expect(screen.getByText('rb_001')).toBeInTheDocument();
      expect(screen.getByText('prn_user')).toBeInTheDocument();
    });
  });
});
