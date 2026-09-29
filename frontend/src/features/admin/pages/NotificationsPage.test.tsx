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

  // TASK-P0BR-066 / D-08：Outbox 与 Webhook 投递端点契约要求 system:observe，
  // 仅持 notification:read 的主体不应看到「系统管理」Tab，也不应触发对应取数。
  it('仅 notification:read 的主体不渲染「系统管理」Tab 且不取数', async () => {
    renderWithProviders(<NotificationsPage />, {
      authContext: { scopes: new Set(['notification:read']) },
    });
    await waitFor(() => {
      expect(screen.getByText(/用户通知|admin\.notifications\.userTab/i)).toBeInTheDocument();
    });
    expect(screen.queryByText(/系统管理|admin\.notifications\.adminTab/i)).not.toBeInTheDocument();
    expect(mockListOutboxEvents).not.toHaveBeenCalled();
    expect(mockListWebhookDeliveries).not.toHaveBeenCalled();
    expect(mockGetOutboxPendingCount).not.toHaveBeenCalled();
  });

  it('持 system:observe 的主体可见「系统管理」Tab', async () => {
    renderWithProviders(<NotificationsPage />, {
      authContext: { scopes: new Set(['notification:read', 'system:observe']) },
    });
    await waitFor(() => {
      expect(screen.getByText(/系统管理|admin\.notifications\.adminTab/i)).toBeInTheDocument();
    });
  });
});
