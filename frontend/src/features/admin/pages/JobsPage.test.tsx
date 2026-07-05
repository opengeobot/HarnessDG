/**
 * JobsPage 组件测试——验证任务管理页面渲染与任务列表。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { JobsPage } from './JobsPage';
import { renderWithProviders } from '@/test/test-utils';

const mockListJobs = vi.fn().mockResolvedValue({
  items: [
    { jobId: 'job_001', type: 'ASSET_PROVISION', status: 'SUCCEEDED', createdAt: '2026-07-01T00:00:00Z', updatedAt: '2026-07-01T00:01:00Z' },
    { jobId: 'job_002', type: 'WEBHOOK_DELIVERY', status: 'DEAD', createdAt: '2026-07-01T00:02:00Z', updatedAt: '2026-07-01T00:03:00Z' },
  ],
  nextCursor: null,
  hasMore: false,
});
const mockRetryJob = vi.fn().mockResolvedValue({});
const mockCancelJob = vi.fn().mockResolvedValue({});

vi.mock('../api', () => ({
  listJobs: (...args: unknown[]) => mockListJobs(...args),
  retryJob: (...args: unknown[]) => mockRetryJob(...args),
  cancelJob: (...args: unknown[]) => mockCancelJob(...args),
}));

describe('JobsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders page title', async () => {
    renderWithProviders(<JobsPage />);
    await waitFor(() => {
      expect(screen.getByText(/任务管理|admin\.jobs\.title/i)).toBeInTheDocument();
    });
  });

  it('renders job list', async () => {
    renderWithProviders(<JobsPage />);
    await waitFor(() => {
      expect(screen.getByText('job_001')).toBeInTheDocument();
      expect(screen.getByText('job_002')).toBeInTheDocument();
    });
  });
});
