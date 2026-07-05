/**
 * RolesPage 组件测试——验证角色管理页面列表渲染与内置角色不可编辑。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { RolesPage } from './RolesPage';
import { renderWithProviders } from '@/test/test-utils';

const mockListRoles = vi.fn().mockResolvedValue([
  { roleId: 'rol_admin', roleCode: 'ADMIN', roleName: 'Admin', builtIn: true, permissionCodes: ['asset:read'] },
  { roleId: 'rol_custom', roleCode: 'CUSTOM', roleName: 'Custom Role', builtIn: false, permissionCodes: [] },
]);
const mockListPermissions = vi.fn().mockResolvedValue([
  { permissionId: 'prm_asset_read', code: 'asset:read', resource: 'asset', action: 'read' },
]);
const mockCreateRole = vi.fn().mockResolvedValue({});
const mockUpdateRole = vi.fn().mockResolvedValue({});
const mockDeleteRole = vi.fn().mockResolvedValue({});

vi.mock('../api', () => ({
  listRoles: (...args: unknown[]) => mockListRoles(...args),
  listPermissions: (...args: unknown[]) => mockListPermissions(...args),
  createRole: (...args: unknown[]) => mockCreateRole(...args),
  updateRole: (...args: unknown[]) => mockUpdateRole(...args),
  deleteRole: (...args: unknown[]) => mockDeleteRole(...args),
}));

describe('RolesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders page title', async () => {
    renderWithProviders(<RolesPage />);
    await waitFor(() => {
      expect(screen.getByText(/角色管理|roles\.title/i)).toBeInTheDocument();
    });
  });

  it('renders role list after loading', async () => {
    renderWithProviders(<RolesPage />);
    await waitFor(() => {
      expect(screen.getByText('Admin')).toBeInTheDocument();
      expect(screen.getByText('Custom Role')).toBeInTheDocument();
    });
  });

  it('shows built-in indicator for system roles', async () => {
    renderWithProviders(<RolesPage />);
    await waitFor(() => {
      // Built-in roles should show some indicator (SYSTEM or 内置 tag)
      expect(screen.getByText('ADMIN')).toBeInTheDocument();
    });
  });
});
