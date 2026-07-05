/**
 * AuditLogsPage 组件测试——验证审计日志页面渲染与日志列表。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { AuditLogsPage } from './AuditLogsPage';
import { renderWithProviders } from '@/test/test-utils';

const mockListAuditLogs = vi.fn().mockResolvedValue({
  items: [
    { auditId: 'aud_001', principalId: 'user_001', action: 'LOGIN', resourceType: 'session', resourceId: 'ses_1', result: 'SUCCEEDED', createdAt: '2026-07-01T00:00:00Z' },
    { auditId: 'aud_002', principalId: 'user_002', action: 'CREATE_ASSET', resourceType: 'asset', resourceId: 'ast_1', result: 'FAILED', createdAt: '2026-07-01T00:01:00Z' },
  ],
  nextCursor: null,
  hasMore: false,
});

vi.mock('../api', () => ({
  listAuditLogs: (...args: unknown[]) => mockListAuditLogs(...args),
}));

describe('AuditLogsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders page title', async () => {
    renderWithProviders(<AuditLogsPage />);
    await waitFor(() => {
      expect(screen.getByText(/审计日志|admin\.auditLogs\.title/i)).toBeInTheDocument();
    });
  });

  it('renders audit log list', async () => {
    renderWithProviders(<AuditLogsPage />);
    await waitFor(() => {
      expect(screen.getByText('user_001')).toBeInTheDocument();
      expect(screen.getByText('user_002')).toBeInTheDocument();
    });
  });
});
