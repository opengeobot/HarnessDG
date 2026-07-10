/**
 * AssetAccessPage 组件测试——ACL 列表、授权表单。
 */
import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { AssetAccessPage } from './AssetAccessPage';
import { renderWithProviders } from '@/test/test-utils';
import type { AssetView, ResourceAclView } from './types';

const mockAsset: AssetView = {
  assetId: 'ast_001',
  coordinate: 'acme/proj/model-a',
  type: 'MODEL',
  namespace: 'acme',
  name: 'model-a',
  displayName: 'Model Alpha',
  description: null,
  visibility: 'INTERNAL',
  status: 'ACTIVE',
  owners: ['prn_admin'],
  tags: [],
  tagIds: [],
  updatedAt: '2026-07-10T00:00:00Z',
  createdAt: '2026-07-01T00:00:00Z',
  rowVersion: 1,
};

const mockAcls: ResourceAclView[] = [
  {
    aclId: 'acl_1',
    principalId: 'prn_user1',
    resourceType: 'ASSET',
    resourceId: 'ast_001',
    permissionCodes: ['asset:read', 'asset:update'],
    createdAt: '2026-07-05T00:00:00Z',
  },
];

const mockGetAsset = vi.fn().mockResolvedValue(mockAsset);
const mockListAccess = vi.fn().mockResolvedValue(mockAcls);
const mockCreateAccess = vi.fn().mockResolvedValue({});
const mockDeleteAccess = vi.fn().mockResolvedValue({});

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useParams: () => ({ assetId: 'ast_001' }),
    useNavigate: () => vi.fn(),
  };
});

vi.mock('./api', () => ({
  getAsset: (...args: unknown[]) => mockGetAsset(...args),
  listAssetAccess: (...args: unknown[]) => mockListAccess(...args),
  createAssetAccess: (...args: unknown[]) => mockCreateAccess(...args),
  deleteAssetAccess: (...args: unknown[]) => mockDeleteAccess(...args),
}));

describe('AssetAccessPage', () => {
  it('渲染 ACL 管理标题', async () => {
    renderWithProviders(<AssetAccessPage />);
    expect(await screen.findByText('访问授权')).toBeInTheDocument();
  });

  it('渲染资产名称链接', async () => {
    renderWithProviders(<AssetAccessPage />);
    expect(await screen.findByText('Model Alpha')).toBeInTheDocument();
  });

  it('渲染 ACL 列表中的主体 ID', async () => {
    renderWithProviders(<AssetAccessPage />);
    expect(await screen.findByText('prn_user1')).toBeInTheDocument();
  });

  it('渲染权限码 Tag', async () => {
    renderWithProviders(<AssetAccessPage />);
    expect(await screen.findByText('asset:read')).toBeInTheDocument();
    expect(screen.getByText('asset:update')).toBeInTheDocument();
  });

  it('渲染授权表单', async () => {
    renderWithProviders(<AssetAccessPage />);
    expect(await screen.findByText('授予权限')).toBeInTheDocument();
  });
});
