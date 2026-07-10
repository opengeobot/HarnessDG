/**
 * DownloadStats 组件测试——验证 i18n 下载徽章与 tooltip。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DownloadStats } from './DownloadStats';
import { renderWithProviders } from '@/test/test-utils';

const mockGet = vi.fn();

vi.mock('@/shared/api', () => ({
  apiClient: {
    get: (...args: unknown[]) => mockGet(...args),
  },
}));

describe('DownloadStats', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders download badge with i18n tooltip', async () => {
    mockGet.mockResolvedValue({ assetId: 'ast_001', totalDownloads: 15 });
    const user = userEvent.setup();

    renderWithProviders(<DownloadStats assetId="ast_001" />);

    await waitFor(() => {
      expect(screen.getByText('15')).toBeInTheDocument();
    });

    await user.hover(screen.getByText('15'));
    await waitFor(() => {
      expect(screen.getByText(/15.*authorized downloads|已授权下载.*15/i)).toBeInTheDocument();
    });
  });

  it('renders nothing when totalDownloads is zero', async () => {
    mockGet.mockResolvedValue({ assetId: 'ast_001', totalDownloads: 0 });

    renderWithProviders(<DownloadStats assetId="ast_001" />);

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalled();
    });
    expect(screen.queryByText('0')).not.toBeInTheDocument();
  });
});
