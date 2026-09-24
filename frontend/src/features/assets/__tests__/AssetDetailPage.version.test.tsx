/**
 * 功能: AssetDetailPage 版本切换器回退语义与文件树 DVC 字段渲染回归测试。
 *       对齐 REQ-DST-DETAIL-001：latestPublished 由服务端计算，前端禁止猜测 latest。
 * 时间: 2026-09-24
 * 作者: AxeXie
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { AssetDetailPage } from '../AssetDetailPage';
import { renderWithProviders } from '@/test/test-utils';

const baseAsset = {
  assetId: 'ast_001',
  coordinate: 'aih://org/team/model/test-model',
  type: 'MODEL',
  namespace: 'org/team',
  name: 'test-model',
  displayName: 'Test Model',
  description: '',
  visibility: 'PUBLIC',
  status: 'ACTIVE',
  owners: ['team-1'],
  tags: [],
  tagIds: [],
  license: 'Apache-2.0',
  organizationId: 'org_001',
  projectId: null,
  createdAt: '2026-07-01T00:00:00Z',
  updatedAt: '2026-07-01T00:00:00Z',
  card: null,
};

const mockGetAsset = vi.fn().mockResolvedValue(baseAsset);

vi.mock('../api', () => ({
  getAsset: (...args: unknown[]) => mockGetAsset(...args),
  deprecateAsset: vi.fn().mockResolvedValue({}),
  archiveAsset: vi.fn().mockResolvedValue({}),
  restoreAsset: vi.fn().mockResolvedValue({}),
  listThreads: vi.fn().mockResolvedValue({ items: [], nextCursor: null, hasMore: false }),
  createThread: vi.fn().mockResolvedValue({}),
  listComments: vi.fn().mockResolvedValue([]),
  createComment: vi.fn().mockResolvedValue({}),
  editComment: vi.fn().mockResolvedValue({}),
  retractComment: vi.fn().mockResolvedValue({}),
  getDiscussionSubscription: vi.fn().mockResolvedValue(false),
  setDiscussionSubscription: vi.fn().mockResolvedValue(undefined),
}));

const mockListVersions = vi.fn();
const mockListArtifacts = vi.fn().mockResolvedValue([]);

vi.mock('@/features/version/api', () => ({
  listVersions: (...args: unknown[]) => mockListVersions(...args),
  listArtifacts: (...args: unknown[]) => mockListArtifacts(...args),
  getDvcConfig: vi.fn().mockResolvedValue({}),
  getDvcCredentials: vi.fn().mockResolvedValue({}),
  issueDownloadTicket: vi.fn().mockResolvedValue({}),
  submitPublishRequest: vi.fn().mockResolvedValue({}),
  createDraftVersion: vi.fn().mockResolvedValue({}),
}));

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useParams: () => ({ assetId: 'ast_001' }),
    useNavigate: () => vi.fn(),
  };
});

function version(overrides: Record<string, unknown>) {
  return {
    versionId: 'ver_x',
    assetId: 'ast_001',
    version: '1.0.0',
    status: 'DRAFT',
    createdBy: 'user_001',
    createdAt: '2026-07-01T00:00:00Z',
    updatedAt: '2026-07-01T00:00:00Z',
    ...overrides,
  };
}

describe('AssetDetailPage version switcher fallback', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetAsset.mockResolvedValue(baseAsset);
    mockListArtifacts.mockResolvedValue([]);
  });

  it('falls back to the most recent PUBLISHED version instead of versions[0]', async () => {
    // 最新创建的是 DRAFT；已发布版本 createdAt 更早——不得默认选中 DRAFT
    mockListVersions.mockResolvedValue({
      items: [
        version({ versionId: 'ver_draft', version: '2.0.0', status: 'DRAFT', createdAt: '2026-09-01T00:00:00Z' }),
        version({ versionId: 'ver_pub_2', version: '1.2.0', status: 'PUBLISHED', publishedAt: '2026-08-01T00:00:00Z', createdAt: '2026-07-20T00:00:00Z' }),
        version({ versionId: 'ver_pub_1', version: '1.1.0', status: 'PUBLISHED', publishedAt: '2026-07-10T00:00:00Z', createdAt: '2026-07-05T00:00:00Z' }),
      ],
      nextCursor: null,
      hasMore: false,
    });
    renderWithProviders(<AssetDetailPage />, { route: '/assets/ast_001' });
    await waitFor(() => {
      expect(screen.getByText('1.2.0 (PUBLISHED)')).toBeInTheDocument();
    });
    expect(screen.queryByText('2.0.0 (DRAFT)')).not.toBeInTheDocument();
  });

  it('does not auto-select when no PUBLISHED version exists', async () => {
    mockListVersions.mockResolvedValue({
      items: [version({ versionId: 'ver_draft', version: '2.0.0', status: 'DRAFT' })],
      nextCursor: null,
      hasMore: false,
    });
    renderWithProviders(<AssetDetailPage />, { route: '/assets/ast_001' });
    await waitFor(() => {
      expect(screen.getByText('Test Model')).toBeInTheDocument();
    });
    // 未选中任何版本：下拉不显示 DRAFT 选项文本，且未发起该版本的 artifacts 查询
    expect(screen.queryByText('2.0.0 (DRAFT)')).not.toBeInTheDocument();
    expect(mockListArtifacts).not.toHaveBeenCalled();
  });

  it('honors explicit ?version= selection even for DRAFT', async () => {
    mockListVersions.mockResolvedValue({
      items: [version({ versionId: 'ver_draft', version: '2.0.0', status: 'DRAFT' })],
      nextCursor: null,
      hasMore: false,
    });
    renderWithProviders(<AssetDetailPage />, { route: '/assets/ast_001?version=ver_draft' });
    await waitFor(() => {
      expect(screen.getByText('2.0.0 (DRAFT)')).toBeInTheDocument();
    });
  });

  it('renders dvcHash tag for DVC-tracked artifacts in files tab', async () => {
    mockListVersions.mockResolvedValue({
      items: [version({ versionId: 'ver_pub', version: '1.0.0', status: 'PUBLISHED', publishedAt: '2026-07-01T00:00:00Z' })],
      nextCursor: null,
      hasMore: false,
    });
    mockListArtifacts.mockResolvedValue([
      {
        artifactId: 'art_001',
        versionId: 'ver_pub',
        path: 'data/train.parquet',
        dvcFile: 'data/train.parquet.dvc',
        dvcHash: 'md5:abcdef1234567890',
        sha256: 'sha256deadbeef',
        size: 1024,
        mediaType: 'application/octet-stream',
      },
    ]);
    renderWithProviders(<AssetDetailPage />, { route: '/assets/ast_001?tab=files&version=ver_pub' });
    await waitFor(() => {
      expect(screen.getByText('train.parquet')).toBeInTheDocument();
    });
    expect(screen.getByText(/DVC md5:abcdef/)).toBeInTheDocument();
  });
});
