/**
 * AssetDetailPage 组件测试——验证资产详情页渲染与 SafeMarkdown 集成。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { AssetDetailPage } from './AssetDetailPage';
import { renderWithProviders } from '@/test/test-utils';

const mockAsset = {
  assetId: 'ast_001',
  type: 'MODEL' as const,
  namespace: 'org/team',
  name: 'test-model',
  displayName: 'Test Model',
  description: 'A test model',
  visibility: 'PUBLIC' as const,
  status: 'ACTIVE' as const,
  owners: ['team-1'],
  tags: ['nlp'],
  tagIds: ['tag-001'],
  license: 'Apache-2.0',
  organizationId: 'org_001',
  projectId: 'prj_001',
  createdAt: '2026-07-01T00:00:00Z',
  updatedAt: '2026-07-01T00:00:00Z',
  card: {
    readme: '# Hello\n\nThis is a **test** model.',
    assetYaml: 'name: test-model\nversion: 1.0.0',
    sourceCommit: 'abc123',
    untrustedContent: false,
  },
};

const mockGetAsset = vi.fn().mockResolvedValue(mockAsset);
const mockDeprecateAsset = vi.fn().mockResolvedValue(mockAsset);
const mockArchiveAsset = vi.fn().mockResolvedValue(mockAsset);
const mockRestoreAsset = vi.fn().mockResolvedValue(mockAsset);
const mockListThreads = vi.fn().mockResolvedValue({ items: [], nextCursor: null, hasMore: false });
const mockCreateThread = vi.fn().mockResolvedValue({});

vi.mock('./api', () => ({
  getAsset: (...args: unknown[]) => mockGetAsset(...args),
  deprecateAsset: (...args: unknown[]) => mockDeprecateAsset(...args),
  archiveAsset: (...args: unknown[]) => mockArchiveAsset(...args),
  restoreAsset: (...args: unknown[]) => mockRestoreAsset(...args),
  listThreads: (...args: unknown[]) => mockListThreads(...args),
  createThread: (...args: unknown[]) => mockCreateThread(...args),
  searchAssets: vi.fn().mockResolvedValue({ items: [], nextCursor: null, hasMore: false }),
  createAsset: vi.fn().mockResolvedValue({}),
  updateAsset: vi.fn().mockResolvedValue({}),
  deleteAsset: vi.fn().mockResolvedValue({}),
  getAssetFacets: vi.fn().mockResolvedValue({}),
  listComments: vi.fn().mockResolvedValue([]),
  createComment: vi.fn().mockResolvedValue({}),
  editComment: vi.fn().mockResolvedValue({}),
  retractComment: vi.fn().mockResolvedValue({}),
}));

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useParams: () => ({ assetId: 'ast_001' }),
    useNavigate: () => vi.fn(),
  };
});

describe('AssetDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders asset display name', async () => {
    renderWithProviders(<AssetDetailPage />, { route: '/assets/ast_001' });
    await waitFor(() => {
      expect(screen.getByText('Test Model')).toBeInTheDocument();
    });
  });

  it('renders README with SafeMarkdown', async () => {
    renderWithProviders(<AssetDetailPage />, { route: '/assets/ast_001' });
    await waitFor(() => {
      expect(screen.getByText('README')).toBeInTheDocument();
    });
  });

  it('shows coordinate', async () => {
    renderWithProviders(<AssetDetailPage />, { route: '/assets/ast_001' });
    await waitFor(() => {
      expect(screen.getByText('org/team/MODEL/test-model')).toBeInTheDocument();
    });
  });
});
