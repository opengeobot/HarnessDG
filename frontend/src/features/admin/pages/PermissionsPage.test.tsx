/**
 * PermissionsPage 组件测试——权限清单表格渲染。
 */
import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { PermissionsPage } from './PermissionsPage';
import { renderWithProviders } from '@/test/test-utils';
import type { PermissionView } from '../types';

const mockPermissions: PermissionView[] = [
  { permissionCode: 'asset:read', resource: 'asset', action: 'read', i18nKey: 'perm.asset.read' },
  { permissionCode: 'asset:write', resource: 'asset', action: 'write', i18nKey: 'perm.asset.write' },
  { permissionCode: 'user:read', resource: 'user', action: 'read', i18nKey: 'perm.user.read' },
];

const mockListPermissions = vi.fn().mockResolvedValue(mockPermissions);

vi.mock('../api', () => ({
  listPermissions: (...args: unknown[]) => mockListPermissions(...args),
}));

describe('PermissionsPage', () => {
  it('渲染权限表格列头', async () => {
    renderWithProviders(<PermissionsPage />);
    expect(screen.getByText(/权限清单|permissions/i)).toBeInTheDocument();
    expect(await screen.findByText('asset:read')).toBeInTheDocument();
  });

  it('权限码以蓝色 Tag 展示', async () => {
    renderWithProviders(<PermissionsPage />);
    const tag = await screen.findByText('asset:read');
    expect(tag.closest('.ant-tag')).toHaveClass('ant-tag-blue');
  });

  it('标注"只读"提示文本', () => {
    renderWithProviders(<PermissionsPage />);
    expect(screen.getByText(/只读|readonly/i)).toBeInTheDocument();
  });

  it('显示所有权限码', async () => {
    renderWithProviders(<PermissionsPage />);
    expect(await screen.findByText('asset:write')).toBeInTheDocument();
    expect(await screen.findByText('user:read')).toBeInTheDocument();
  });
});
