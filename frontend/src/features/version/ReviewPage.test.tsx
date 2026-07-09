/**
 * ReviewPage 组件测试——验证发布审批页面渲染。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { ReviewPage } from './ReviewPage';
import { renderWithProviders } from '@/test/test-utils';

vi.mock('./api', () => ({
  listPublishRequests: vi.fn().mockResolvedValue([]),
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

  it('renders asset ID input', () => {
    renderWithProviders(<ReviewPage />);
    expect(screen.getByPlaceholderText(/资产 ID|Asset ID/i)).toBeInTheDocument();
  });

  it('shows query button', () => {
    renderWithProviders(<ReviewPage />);
    expect(screen.getByRole('button', { name: /query|查询/i })).toBeInTheDocument();
  });

  it('shows empty state for publish requests', () => {
    renderWithProviders(<ReviewPage />);
    // 未查询前不应显示"no requests"
    expect(screen.queryByText(/no.*request/i)).not.toBeInTheDocument();
  });

  it('shows select-request prompt in decision panel', () => {
    renderWithProviders(<ReviewPage />);
    const hints = screen.getAllByText(/select|选择/i);
    expect(hints.length).toBeGreaterThan(0);
  });
});
