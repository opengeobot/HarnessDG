/**
 * IdempotencyPage 组件测试——验证幂等记录页面渲染与列表。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { IdempotencyPage } from './IdempotencyPage';
import { renderWithProviders } from '@/test/test-utils';

const mockListIdempotencyRecords = vi.fn().mockResolvedValue([
  {
    idempotencyKey: 'idmp_001',
    principalId: 'prn_admin',
    method: 'POST',
    path: '/api/v1/assets',
    requestDigest: 'abc123',
    createdAt: '2026-07-01T00:00:00Z',
  },
]);

vi.mock('../api', () => ({
  listIdempotencyRecords: (...args: unknown[]) => mockListIdempotencyRecords(...args),
}));

describe('IdempotencyPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders page title', async () => {
    renderWithProviders(<IdempotencyPage />);
    await waitFor(() => {
      expect(screen.getByText(/幂等记录|idempotency\.title/i)).toBeInTheDocument();
    });
  });

  it('renders idempotency record list', async () => {
    renderWithProviders(<IdempotencyPage />);
    await waitFor(() => {
      expect(screen.getByText('idmp_001')).toBeInTheDocument();
      expect(screen.getByText('prn_admin')).toBeInTheDocument();
    });
  });
});
