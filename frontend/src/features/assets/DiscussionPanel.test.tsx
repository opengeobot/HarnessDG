/**
 * DiscussionPanel 组件测试——验证讨论面板渲染、线程列表与评论。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
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

vi.mock('@/app/permission', () => ({
  usePermission: () => ({
    hasScope: (scope: string) => scope === 'asset:moderate',
    hasAllScopes: () => true,
    scopes: new Set(['asset:moderate']),
  }),
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
}));

describe('DiscussionPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders discussion title', async () => {
    renderWithProviders(<DiscussionPanel assetId="ast_001" />);
    await waitFor(() => {
      expect(screen.getByText(/讨论|discussion\.title/i)).toBeInTheDocument();
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
});
