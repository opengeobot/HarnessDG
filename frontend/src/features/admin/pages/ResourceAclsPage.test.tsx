/**
 * ResourceAclsPage 组件测试——验证资源 ACL 页面渲染与列表。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { ResourceAclsPage } from './ResourceAclsPage';
import { renderWithProviders } from '@/test/test-utils';

const mockListResourceAcls = vi.fn().mockResolvedValue([
  {
    aclId: 'acl_001',
    principalId: 'prn_user',
    resourceType: 'ASSET',
    resourceId: 'ast_001',
    permissionCodes: ['asset:read'],
    createdAt: '2026-07-01T00:00:00Z',
  },
]);
const mockListPermissions = vi.fn().mockResolvedValue([
  { permissionCode: 'asset:read', resource: 'asset', action: 'read', i18nKey: 'perm.asset.read' },
]);
const mockCreateResourceAcl = vi.fn();
const mockDeleteResourceAcl = vi.fn();

vi.mock('../api', () => ({
  listResourceAcls: (...args: unknown[]) => mockListResourceAcls(...args),
  listPermissions: (...args: unknown[]) => mockListPermissions(...args),
  createResourceAcl: (...args: unknown[]) => mockCreateResourceAcl(...args),
  deleteResourceAcl: (...args: unknown[]) => mockDeleteResourceAcl(...args),
}));

describe('ResourceAclsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders page title', async () => {
    renderWithProviders(<ResourceAclsPage />);
    await waitFor(() => {
      expect(screen.getByText(/资源 ACL|resourceAcls\.title/i)).toBeInTheDocument();
    });
  });

  it('renders ACL list', async () => {
    renderWithProviders(<ResourceAclsPage />);
    await waitFor(() => {
      expect(screen.getByText('acl_001')).toBeInTheDocument();
      expect(screen.getByText('asset:read')).toBeInTheDocument();
    });
  });
});
