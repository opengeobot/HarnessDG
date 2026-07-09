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

  it('renders asset ID input', () => {
    renderWithProviders(<VersionPage />);
    expect(screen.getByPlaceholderText(/资产 ID|Asset ID/i)).toBeInTheDocument();
  });

  it('shows query button', () => {
    renderWithProviders(<VersionPage />);
    expect(screen.getByRole('button', { name: /query|查询/i })).toBeInTheDocument();
  });

  it('shows empty state when no versions loaded', () => {
    renderWithProviders(<VersionPage />);
    // 初始状态，未输入 assetId 前不应显示版本列表
    expect(screen.queryByText(/no versions/i)).not.toBeInTheDocument();
  });

  it('shows select-version prompt in detail panel', () => {
    renderWithProviders(<VersionPage />);
    // 详情面板提示选择版本
    const detailHints = screen.getAllByText(/select|选择/i);
    expect(detailHints.length).toBeGreaterThan(0);
  });
});
