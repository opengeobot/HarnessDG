/**
 * ReviewPage 组件测试——验证发布审批页面渲染与 REQUEST_CHANGES 选项。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReviewPage } from './ReviewPage';
import { renderWithProviders } from '@/test/test-utils';

vi.mock('./api', () => ({
  listPublishRequests: vi.fn().mockResolvedValue([]),
  getValidationReport: vi.fn().mockResolvedValue({ status: 'PASSED', findings: [] }),
  listArtifacts: vi.fn().mockResolvedValue([]),
  listVersions: vi.fn().mockResolvedValue({ items: [], nextCursor: null, hasMore: false }),
  listDecisions: vi.fn().mockResolvedValue([]),
}));

vi.mock('@/shared/api', () => ({
  apiClient: {
    post: vi.fn().mockResolvedValue({}),
  },
}));

describe('ReviewPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows deep-link hint when assetId missing', () => {
    renderWithProviders(<ReviewPage />, { route: '/review' });
    expect(screen.getByText(/assetId|查询参数/i)).toBeInTheDocument();
  });

  it('renders asset ID from query params', () => {
    renderWithProviders(<ReviewPage />, { route: '/review?assetId=ast_1' });
    expect(screen.getByText('ast_1')).toBeInTheDocument();
  });

  it('shows select-request prompt in decision panel', () => {
    renderWithProviders(<ReviewPage />, { route: '/review?assetId=ast_1' });
    const hints = screen.getAllByText(/select|选择/i);
    expect(hints.length).toBeGreaterThan(0);
  });

  it('includes REQUEST_CHANGES in decision options when request selected', async () => {
    const { listPublishRequests } = await import('./api');
    vi.mocked(listPublishRequests).mockResolvedValue([
      {
        request_id: 'pub_1',
        version_id: 'ver_1',
        frozen_digest: 'abc',
        frozen_source_commit: 'commit1',
        status: 'SUBMITTED',
        submitted_by: 'usr_1',
        created_at: '2026-07-10T00:00:00Z',
      },
    ]);

    const user = userEvent.setup();
    renderWithProviders(<ReviewPage />, { route: '/review?assetId=ast_1' });

    await user.click(await screen.findByText('ver_1'));

    const select = await screen.findByRole('combobox');
    await user.click(select);
    expect(await screen.findByText(/要求修改|request changes/i)).toBeInTheDocument();
  });
});
