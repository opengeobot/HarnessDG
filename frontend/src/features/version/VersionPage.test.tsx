/**
 * VersionPage 组件测试——验证版本中心页面渲染与交互。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { VersionPage } from './VersionPage';
import { renderWithProviders } from '@/test/test-utils';

vi.mock('./api', () => ({
  listVersions: vi.fn().mockResolvedValue({ items: [], nextCursor: null, hasMore: false }),
  listArtifacts: vi.fn().mockResolvedValue([]),
  getValidationReport: vi.fn().mockResolvedValue({ status: 'NOT_FOUND' }),
  transitionVersion: vi.fn().mockResolvedValue({}),
  issueDownloadTicket: vi.fn().mockResolvedValue({}),
}));

describe('VersionPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows deep-link hint when assetId missing', () => {
    renderWithProviders(<VersionPage />, { route: '/version' });
    expect(screen.getByText(/assetId|查询参数/i)).toBeInTheDocument();
  });

  it('renders asset ID from query params', () => {
    renderWithProviders(<VersionPage />, { route: '/version?assetId=ast_001' });
    expect(screen.getByText('ast_001')).toBeInTheDocument();
  });

  it('shows empty state when no versions loaded', async () => {
    renderWithProviders(<VersionPage />, { route: '/version?assetId=ast_001' });
    expect(await screen.findByText(/暂无版本|no versions/i)).toBeInTheDocument();
  });

  it('shows select-version prompt in detail panel', () => {
    renderWithProviders(<VersionPage />, { route: '/version?assetId=ast_001' });
    const detailHints = screen.getAllByText(/select|选择/i);
    expect(detailHints.length).toBeGreaterThan(0);
  });
});
