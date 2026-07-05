/**
 * NotificationsPage 组件测试——验证通知管理页面渲染（用户通知 + 系统管理 Tab）。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { NotificationsPage } from './NotificationsPage';
import { renderWithProviders } from '@/test/test-utils';

const mockListNotifications = vi.fn().mockResolvedValue({
  items: [
    { notificationId: 'ntf_001', title: '资产审核通过', read: false, createdAt: '2026-07-01T00:00:00Z' },
  ],
  nextCursor: null,
  hasMore: false,
});
const mockMarkNotificationRead = vi.fn().mockResolvedValue({});
const mockListOutboxEvents = vi.fn().mockResolvedValue([]);
const mockGetOutboxPendingCount = vi.fn().mockResolvedValue({ count: 0 });
const mockListWebhookDeliveries = vi.fn().mockResolvedValue([]);
const mockRetryWebhookDelivery = vi.fn().mockResolvedValue({});

vi.mock('../api', () => ({
  listNotifications: (...args: unknown[]) => mockListNotifications(...args),
  markNotificationRead: (...args: unknown[]) => mockMarkNotificationRead(...args),
  listOutboxEvents: (...args: unknown[]) => mockListOutboxEvents(...args),
  getOutboxPendingCount: (...args: unknown[]) => mockGetOutboxPendingCount(...args),
  listWebhookDeliveries: (...args: unknown[]) => mockListWebhookDeliveries(...args),
  retryWebhookDelivery: (...args: unknown[]) => mockRetryWebhookDelivery(...args),
}));

describe('NotificationsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders page title', async () => {
    renderWithProviders(<NotificationsPage />);
    await waitFor(() => {
      expect(screen.getByText(/通知中心|admin\.notifications\.title/i)).toBeInTheDocument();
    });
  });

  it('renders user notifications tab', async () => {
    renderWithProviders(<NotificationsPage />);
    await waitFor(() => {
      expect(screen.getByText(/用户通知|admin\.notifications\.userTab/i)).toBeInTheDocument();
    });
  });
});
