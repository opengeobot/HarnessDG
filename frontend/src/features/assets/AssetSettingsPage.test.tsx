/**
 * AssetSettingsPage 组件测试——元数据编辑、生命周期操作。
 */
import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { AssetSettingsPage } from './AssetSettingsPage';
import { renderWithProviders } from '@/test/test-utils';
import type { AssetView } from './types';

const mockAsset: AssetView = {
  assetId: 'ast_001',
  coordinate: 'acme/proj/model-a',
  type: 'MODEL',
  namespace: 'acme',
  name: 'model-a',
  displayName: 'Model Alpha',
  description: 'Test model',
  visibility: 'INTERNAL',
  status: 'ACTIVE',
  owners: ['prn_admin'],
  tags: [],
  tagIds: [],
  updatedAt: '2026-07-10T00:00:00Z',
  createdAt: '2026-07-01T00:00:00Z',
  rowVersion: 1,
};

const mockGetAsset = vi.fn().mockResolvedValue(mockAsset);
const mockUpdateAsset = vi.fn().mockResolvedValue(mockAsset);
const mockDeprecate = vi.fn().mockResolvedValue({});
const mockArchive = vi.fn().mockResolvedValue({});
const mockRestore = vi.fn().mockResolvedValue({});
const mockDelete = vi.fn().mockResolvedValue({});

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
  updateAsset: (...args: unknown[]) => mockUpdateAsset(...args),
  deprecateAsset: (...args: unknown[]) => mockDeprecate(...args),
  archiveAsset: (...args: unknown[]) => mockArchive(...args),
  restoreAsset: (...args: unknown[]) => mockRestore(...args),
  deleteAsset: (...args: unknown[]) => mockDelete(...args),
}));

describe('AssetSettingsPage', () => {
  it('渲染设置页标题', async () => {
    renderWithProviders(<AssetSettingsPage />);
    expect(await screen.findByText(/资产设置|settings\.title/i)).toBeInTheDocument();
  });

  it('渲染基本信息卡片', async () => {
    renderWithProviders(<AssetSettingsPage />);
    expect(await screen.findByText('ast_001')).toBeInTheDocument();
    expect(screen.getByText('MODEL')).toBeInTheDocument();
    expect(screen.getByText('acme')).toBeInTheDocument();
  });

  it('ACTIVE 状态显示弃用和归档按钮', async () => {
    renderWithProviders(<AssetSettingsPage />);
    // 等待页面加载完成
    await screen.findByText('ast_001');
    expect(screen.getByRole('button', { name: /弃\s*用/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /归\s*档/ })).toBeInTheDocument();
  });

  it('ACTIVE 状态显示删除按钮', async () => {
    renderWithProviders(<AssetSettingsPage />);
    expect(await screen.findByText(/删除|delete/i)).toBeInTheDocument();
  });

  it('渲染元数据编辑表单', async () => {
    renderWithProviders(<AssetSettingsPage />);
    expect(await screen.findByText('编辑元数据')).toBeInTheDocument();
    expect(screen.getByText(/保\s*存/)).toBeInTheDocument();
  });
});
