/**
 * DiscussionPanel 组件测试——验证讨论面板渲染、线程列表、评论与订阅接线。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { DiscussionPanel } from './DiscussionPanel';
import { renderWithProviders } from '@/test/test-utils';

const mockListThreads = vi.fn().mockResolvedValue({
  items: [
    { threadId: 'thr_001', assetId: 'ast_001', title: '讨论线程 1', createdBy: 'user_001', status: 'OPEN', commentCount: 2, createdAt: '2026-07-01T00:00:00Z' },
    { threadId: 'thr_002', assetId: 'ast_001', title: '讨论线程 2', createdBy: 'user_002', status: 'LOCKED', commentCount: 0, createdAt: '2026-07-01T00:01:00Z' },
  ],
  nextCursor: null,
  hasMore: false,
});
const mockCreateThread = vi.fn().mockResolvedValue({});
const mockListComments = vi.fn().mockResolvedValue([]);
const mockCreateComment = vi.fn().mockResolvedValue({});
const mockEditComment = vi.fn().mockResolvedValue({});
const mockRetractComment = vi.fn().mockResolvedValue({});
const mockHideComment = vi.fn().mockResolvedValue({});
const mockUnhideComment = vi.fn().mockResolvedValue({});
const mockLockThread = vi.fn().mockResolvedValue({});
const mockUnlockThread = vi.fn().mockResolvedValue({});
const mockGetDiscussionSubscription = vi.fn().mockResolvedValue(false);
const mockSetDiscussionSubscription = vi.fn().mockResolvedValue(undefined);

vi.mock('@/app/permission', () => ({
  usePermission: () => ({
    hasScope: (scope: string) => scope === 'asset:moderate',
    hasAllScopes: () => true,
    scopes: new Set(['asset:moderate']),
  }),
  PermissionProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('./api', () => ({
  listThreads: (...args: unknown[]) => mockListThreads(...args),
  createThread: (...args: unknown[]) => mockCreateThread(...args),
  listComments: (...args: unknown[]) => mockListComments(...args),
  createComment: (...args: unknown[]) => mockCreateComment(...args),
  editComment: (...args: unknown[]) => mockEditComment(...args),
  retractComment: (...args: unknown[]) => mockRetractComment(...args),
  hideComment: (...args: unknown[]) => mockHideComment(...args),
  unhideComment: (...args: unknown[]) => mockUnhideComment(...args),
  lockThread: (...args: unknown[]) => mockLockThread(...args),
  unlockThread: (...args: unknown[]) => mockUnlockThread(...args),
  getDiscussionSubscription: (...args: unknown[]) => mockGetDiscussionSubscription(...args),
  setDiscussionSubscription: (...args: unknown[]) => mockSetDiscussionSubscription(...args),
}));

describe('DiscussionPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders discussion title', async () => {
    renderWithProviders(<DiscussionPanel assetId="ast_001" />);
    await waitFor(() => {
      // 精确匹配 Card 标题，避免命中「订阅讨论」按钮文本
      expect(screen.getByText('讨论', { exact: true })).toBeInTheDocument();
    });
  });

  it('renders thread list', async () => {
    renderWithProviders(<DiscussionPanel assetId="ast_001" />);
    await waitFor(() => {
      expect(screen.getByText('讨论线程 1')).toBeInTheDocument();
      expect(screen.getByText('讨论线程 2')).toBeInTheDocument();
    });
  });

  it('shows moderator lock action for OPEN threads', async () => {
    renderWithProviders(<DiscussionPanel assetId="ast_001" />);
    await waitFor(() => {
      expect(screen.getAllByText(/锁定|lockThread/i).length).toBeGreaterThan(0);
    });
  });

  /** 等待订阅查询完成：antd loading 按钮会拦截 click。 */
  async function waitButtonReady(btn: HTMLElement) {
    await waitFor(() => {
      expect(btn.className).not.toContain('ant-btn-loading');
    });
  }

  it('loads subscription state and toggles subscribe', async () => {
    mockGetDiscussionSubscription.mockResolvedValue(false);
    renderWithProviders(<DiscussionPanel assetId="ast_001" />);
    const subscribeBtn = await screen.findByRole('button', { name: /订阅讨论/ });
    expect(mockGetDiscussionSubscription).toHaveBeenCalledWith('ast_001');
    await waitButtonReady(subscribeBtn);
    fireEvent.click(subscribeBtn);
    await waitFor(() => {
      expect(mockSetDiscussionSubscription).toHaveBeenCalledWith('ast_001', true);
    });
  });

  it('renders unsubscribe action when already subscribed', async () => {
    mockGetDiscussionSubscription.mockResolvedValue(true);
    renderWithProviders(<DiscussionPanel assetId="ast_001" />);
    const unsubscribeBtn = await screen.findByRole('button', { name: /取消订阅/ });
    await waitButtonReady(unsubscribeBtn);
    fireEvent.click(unsubscribeBtn);
    await waitFor(() => {
      expect(mockSetDiscussionSubscription).toHaveBeenCalledWith('ast_001', false);
    });
  });
});
