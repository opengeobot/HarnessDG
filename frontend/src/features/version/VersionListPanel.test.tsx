/**
 * VersionListPanel 组件测试——版本列表渲染、选中交互。
 */
import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { VersionListPanel } from './VersionListPanel';
import { renderWithProviders } from '@/test/test-utils';
import type { VersionView } from './types';

const mockVersions: VersionView[] = [
  {
    versionId: 'ver_001',
    assetId: 'ast_001',
    version: '1.0.0',
    status: 'PUBLISHED',
    sourceCommit: 'abc123def456',
    createdBy: 'admin',
    createdAt: '2026-07-01T00:00:00Z',
    updatedAt: '2026-07-05T00:00:00Z',
  },
  {
    versionId: 'ver_002',
    assetId: 'ast_001',
    version: '1.1.0',
    status: 'DRAFT',
    sourceCommit: null,
    createdBy: 'admin',
    createdAt: '2026-07-06T00:00:00Z',
    updatedAt: '2026-07-06T00:00:00Z',
  },
];

const mockListVersions = vi.fn().mockResolvedValue({ items: mockVersions });

vi.mock('./api', () => ({
  listVersions: (...args: unknown[]) => mockListVersions(...args),
}));

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    Link: ({ children, to }: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
  };
});

describe('VersionListPanel', () => {
  it('渲染版本列表标题', async () => {
    renderWithProviders(<VersionListPanel assetId="ast_001" />);
    expect(await screen.findByText(/版本列表|versionList/i)).toBeInTheDocument();
  });

  it('显示版本号', async () => {
    renderWithProviders(<VersionListPanel assetId="ast_001" />);
    expect(await screen.findByText('1.0.0')).toBeInTheDocument();
    expect(screen.getByText('1.1.0')).toBeInTheDocument();
  });

  it('显示版本状态 Tag', async () => {
    renderWithProviders(<VersionListPanel assetId="ast_001" />);
    expect(await screen.findByText('PUBLISHED')).toBeInTheDocument();
    expect(screen.getByText('DRAFT')).toBeInTheDocument();
  });

  it('显示"查看全部"链接', async () => {
    renderWithProviders(<VersionListPanel assetId="ast_001" />);
    expect(await screen.findByText(/查看全部|viewAll/i)).toBeInTheDocument();
  });

  it('显示 commit 信息', async () => {
    renderWithProviders(<VersionListPanel assetId="ast_001" />);
    // sourceCommit 截取前 12 位
    expect(await screen.findByText('abc123def456')).toBeInTheDocument();
  });
});
