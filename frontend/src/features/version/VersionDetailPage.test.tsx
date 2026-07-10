/**
 * VersionDetailPage 组件测试——版本详情、校验报告、工件列表。
 */
import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { VersionDetailPage } from './VersionDetailPage';
import { renderWithProviders } from '@/test/test-utils';
import type { VersionView, ArtifactView } from './types';

const mockVersion: VersionView = {
  versionId: 'ver_001',
  assetId: 'ast_001',
  version: '1.0.0',
  status: 'PUBLISHED',
  sourceCommit: 'abc123def456',
  manifestDigest: 'sha256:deadbeef',
  gitTag: 'v1.0.0',
  publishedAt: '2026-07-05T10:00:00Z',
  publishedBy: 'admin',
  notes: 'Initial release',
  createdBy: 'admin',
  createdAt: '2026-07-01T00:00:00Z',
  updatedAt: '2026-07-05T10:00:00Z',
};

const mockArtifacts: ArtifactView[] = [
  { artifactId: 'art_1', versionId: 'ver_001', path: 'model/weights.bin', sha256: 'aabbccdd', size: 1048576, mediaType: 'application/octet-stream' },
];

const mockValidationReport = {
  status: 'PASSED',
  policyVersion: '1.0',
  findings: [
    { ruleId: 'R001', status: 'PASS', severity: 'INFO', resourcePath: 'model/weights.bin', message: 'OK' },
  ],
};

const mockGetVersion = vi.fn().mockResolvedValue(mockVersion);
const mockListArtifacts = vi.fn().mockResolvedValue(mockArtifacts);
const mockGetReport = vi.fn().mockResolvedValue(mockValidationReport);

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useParams: () => ({ assetId: 'ast_001', versionId: 'ver_001' }),
    useNavigate: () => vi.fn(),
  };
});

vi.mock('./api', () => ({
  getVersion: (...args: unknown[]) => mockGetVersion(...args),
  listArtifacts: (...args: unknown[]) => mockListArtifacts(...args),
  getValidationReport: (...args: unknown[]) => mockGetReport(...args),
}));

vi.mock('@/features/assets/PreviewPanel', () => ({
  PreviewPanel: () => <div data-testid="preview-panel">Preview</div>,
}));

describe('VersionDetailPage', () => {
  it('渲染版本详情标题', async () => {
    renderWithProviders(<VersionDetailPage />);
    const matches = await screen.findAllByText(/1\.0\.0/);
    expect(matches.length).toBeGreaterThanOrEqual(1);
  });

  it('显示版本状态 Tag', async () => {
    renderWithProviders(<VersionDetailPage />);
    const tags = await screen.findAllByText('PUBLISHED');
    expect(tags.length).toBeGreaterThanOrEqual(1);
  });

  it('显示版本 ID 和 commit', async () => {
    renderWithProviders(<VersionDetailPage />);
    expect(await screen.findByText('ver_001')).toBeInTheDocument();
    expect(screen.getByText('abc123def456')).toBeInTheDocument();
  });

  it('显示校验报告状态', async () => {
    renderWithProviders(<VersionDetailPage />);
    expect(await screen.findByText('PASSED')).toBeInTheDocument();
  });

  it('显示工件列表', async () => {
    renderWithProviders(<VersionDetailPage />);
    const items = await screen.findAllByText('model/weights.bin');
    expect(items.length).toBeGreaterThanOrEqual(1);
  });
});
