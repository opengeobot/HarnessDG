/**
 * AlertsPage 组件测试——验证系统告警页面渲染与列表。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AlertsPage } from './AlertsPage';
import { renderWithProviders } from '@/test/test-utils';

const mockListAlerts = vi.fn().mockResolvedValue([
  {
    id: 1,
    alertId: 'alt_001',
    alertType: 'DEAD_JOB',
    severity: 'CRITICAL',
    title: 'Dead job detected',
    detail: 'job_001 exceeded retry limit',
    status: 'FIRING',
    firedAt: '2026-07-01T00:00:00Z',
    resolvedAt: null,
  },
]);

const mockAcknowledgeAlert = vi.fn().mockResolvedValue({
  id: 1,
  alertId: 'alt_001',
  alertType: 'DEAD_JOB',
  severity: 'CRITICAL',
  title: 'Dead job detected',
  detail: 'job_001 exceeded retry limit',
  status: 'RESOLVED',
  firedAt: '2026-07-01T00:00:00Z',
  resolvedAt: '2026-07-01T01:00:00Z',
});

vi.mock('../api', () => ({
  listAlerts: (...args: unknown[]) => mockListAlerts(...args),
  acknowledgeAlert: (...args: unknown[]) => mockAcknowledgeAlert(...args),
}));

describe('AlertsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders page title', async () => {
    renderWithProviders(<AlertsPage />);
    await waitFor(() => {
      expect(screen.getByText(/系统告警|alerts\.title/i)).toBeInTheDocument();
    });
  });

  it('renders alert list', async () => {
    renderWithProviders(<AlertsPage />);
    await waitFor(() => {
      expect(screen.getByText('alt_001')).toBeInTheDocument();
      expect(screen.getByText('Dead job detected')).toBeInTheDocument();
    });
  });

  it('shows acknowledge button for firing alerts', async () => {
    renderWithProviders(<AlertsPage />);
    await waitFor(() => {
      expect(screen.getByText('alt_001')).toBeInTheDocument();
    });
    const acknowledgeBtn = screen.getByRole('button', { name: '确认' });
    await userEvent.click(acknowledgeBtn);
    await waitFor(() => {
      expect(mockAcknowledgeAlert).toHaveBeenCalledWith('alt_001');
    });
  });
});
