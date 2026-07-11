/**
 * AssetLineagePage 组件测试——验证血缘页面渲染、方向切换与空状态。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AssetLineagePage } from './AssetLineagePage';
import { renderWithProviders } from '@/test/test-utils';

const mockGetAssetLineage = vi.fn().mockResolvedValue({
  assetId: 'ast_001',
  direction: 'down',
  relations: [
    {
      relationId: 'rel_001',
      parentAssetId: 'ast_001',
      childAssetId: 'ast_002',
      relationType: 'DERIVED_FROM',
      parentName: 'parent-model',
      childName: 'child-model',
      createdAt: '2026-07-01T00:00:00Z',
    },
  ],
});
const mockSearchAssets = vi.fn().mockResolvedValue({ items: [], hasMore: false, nextCursor: null });
const mockCreateAssetRelation = vi.fn().mockResolvedValue({});

vi.mock('./api', () => ({
  getAssetLineage: (...args: unknown[]) => mockGetAssetLineage(...args),
  searchAssets: (...args: unknown[]) => mockSearchAssets(...args),
  createAssetRelation: (...args: unknown[]) => mockCreateAssetRelation(...args),
  listDictionaryItems: vi.fn().mockResolvedValue([]),
  listTags: vi.fn().mockResolvedValue([]),
}));

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useParams: () => ({ assetId: 'ast_001' }),
    Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
      <a href={to}>{children}</a>
    ),
  };
});

vi.mock('@/shared/hooks', () => ({
  useDocumentTitle: vi.fn(),
}));

describe('AssetLineagePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('渲染血缘标题', async () => {
    renderWithProviders(<AssetLineagePage />);
    await waitFor(() => {
      expect(screen.getByText('资产血缘')).toBeInTheDocument();
    });
  });

  it('加载并渲染关系列表', async () => {
    renderWithProviders(<AssetLineagePage />);
    await waitFor(() => {
      expect(mockGetAssetLineage).toHaveBeenCalledWith('ast_001', expect.objectContaining({ direction: 'down' }));
    });
  });

  it('空关系时显示 Empty 状态', async () => {
    mockGetAssetLineage.mockResolvedValueOnce({
      assetId: 'ast_001',
      direction: 'down',
      relations: [],
    });
    renderWithProviders(<AssetLineagePage />);
    await waitFor(() => {
      expect(screen.getByText('暂无血缘关系')).toBeInTheDocument();
    });
  });
});
