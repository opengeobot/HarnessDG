/**
 * DependenciesPage 组件测试——系统依赖健康 + 指标摘要。
 */
import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { DependenciesPage } from './DependenciesPage';
import { renderWithProviders } from '@/test/test-utils';
import type { SystemDependencySummary } from '@/shared/api';
import type { MetricsSummary } from '../types';

const mockDeps: SystemDependencySummary = {
  status: 'UP',
  dependencies: [
    { name: 'PostgreSQL', status: 'UP', latencyMs: 3 },
    { name: 'MinIO', status: 'UP', latencyMs: 12 },
    { name: 'Gitea', status: 'DEGRADED', latencyMs: 250 },
  ],
};

const mockMetrics: MetricsSummary = {
  generatedAt: '2026-07-10T12:00:00Z',
  api: { totalRequests: 15000, errorRate: 0.002 },
  jobs: { running: 2, pending: 5 },
  dependencies: { total: 3, healthy: 2 },
};

const mockFetchDeps = vi.fn().mockResolvedValue(mockDeps);
const mockGetMetrics = vi.fn().mockResolvedValue(mockMetrics);

vi.mock('@/shared/api', () => ({
  fetchSystemDependencies: (...args: unknown[]) => mockFetchDeps(...args),
}));

vi.mock('../api', () => ({
  getMetricsSummary: (...args: unknown[]) => mockGetMetrics(...args),
}));

describe('DependenciesPage', () => {
  it('渲染页面标题', () => {
    renderWithProviders(<DependenciesPage />);
    expect(screen.getByText(/系统依赖|dependencies/i)).toBeInTheDocument();
  });

  it('渲染依赖健康表格列头', async () => {
    renderWithProviders(<DependenciesPage />);
    // 等待数据加载
    expect(await screen.findByText('PostgreSQL')).toBeInTheDocument();
    expect(screen.getByText('MinIO')).toBeInTheDocument();
    expect(screen.getByText('Gitea')).toBeInTheDocument();
  });

  it('显示依赖状态 Tag', async () => {
    renderWithProviders(<DependenciesPage />);
    // 多个 UP 状态，用 getAllByText
    expect((await screen.findAllByText('UP')).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('DEGRADED')).toBeInTheDocument();
  });

  it('渲染指标摘要卡片', async () => {
    renderWithProviders(<DependenciesPage />);
    expect(await screen.findByText(/指标摘要|metricsSummary/i)).toBeInTheDocument();
    // generatedAt
    expect(await screen.findByText('2026-07-10T12:00:00Z')).toBeInTheDocument();
  });

  it('显示延迟数值', async () => {
    renderWithProviders(<DependenciesPage />);
    expect(await screen.findByText('3')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('250')).toBeInTheDocument();
  });
});
