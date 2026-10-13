/**
 * ArtifactContentPanel 组件测试——文本内容渲染 / 错误态文案与下载引导 / 不可信数据安全渲染（TASK-P0BR-077）。
 * 时间: 2026-10-10
 * 作者: AxeXie
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { ArtifactContentPanel } from './ArtifactContentPanel';
import { renderWithProviders } from '@/test/test-utils';
import { ApiError } from '@/shared/api/errors';
import type { ArtifactView } from '@/features/version/types';

const mockGetBlob = vi.fn();
const mockIssueDownloadTicket = vi.fn();

vi.mock('@/shared/api', () => ({
  apiClient: {
    get: vi.fn(),
    getBlob: (...args: unknown[]) => mockGetBlob(...args),
  },
  isApiError: (e: unknown) => e instanceof ApiError,
}));

vi.mock('@/features/version/api', () => ({
  issueDownloadTicket: (...args: unknown[]) => mockIssueDownloadTicket(...args),
}));

const csvArtifact: ArtifactView = {
  artifactId: 'art_csv',
  versionId: 'ver_001',
  path: 'data/train/part-000.csv',
  sha256: 'a'.repeat(64),
  size: 18,
  mediaType: 'text/csv',
};

const apiError = (code: string) =>
  new ApiError({ code, message: code, i18nKey: 'error.common.unknown', retryable: false }, 400);

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ArtifactContentPanel', () => {
  it('经内容代理端点取字节流并以 <pre> 纯文本渲染真实内容', async () => {
    mockGetBlob.mockResolvedValue({
      blob: new Blob(['id,name\n1,alpha\n'], { type: 'text/csv' }),
      mediaType: 'text/csv',
    });

    const { container } = renderWithProviders(
      <ArtifactContentPanel assetId="ast_001" versionId="ver_001" artifact={csvArtifact} />,
    );

    await waitFor(() => {
      expect(mockGetBlob).toHaveBeenCalledWith(
        '/assets/ast_001/versions/ver_001/artifacts/art_csv/content',
      );
    });
    const pre = await screen.findByTestId('artifact-content-pre');
    expect(pre).toHaveTextContent('id,name');
    expect(pre.tagName).toBe('PRE');
    // 内容为不可信数据：不得出现任何富语义标签（无 dangerouslySetInnerHTML 渲染路径）
    expect(container.querySelector('table')).toBeNull();
    expect(container.querySelector('h1')).toBeNull();
  });

  it('不可信 HTML 片段仅作为纯文本呈现，不产生元素', async () => {
    mockGetBlob.mockResolvedValue({
      blob: new Blob(['<img src=x onerror="alert(1)"><b>bold</b>'], { type: 'text/plain' }),
      mediaType: 'text/plain',
    });

    const { container } = renderWithProviders(
      <ArtifactContentPanel assetId="ast_001" versionId="ver_001" artifact={{ ...csvArtifact, mediaType: 'text/plain' }} />,
    );

    await waitFor(() => {
      expect(screen.getByTestId('artifact-content-pre')).toBeInTheDocument();
    });
    expect(screen.getByTestId('artifact-content-pre').textContent).toContain('<img src=x');
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('b')).toBeNull();
  });

  it('超限 413 显示超限文案与下载引导', async () => {
    mockGetBlob.mockRejectedValue(apiError('PREVIEW_TOO_LARGE'));

    renderWithProviders(
      <ArtifactContentPanel assetId="ast_001" versionId="ver_001" artifact={csvArtifact} />,
    );

    await waitFor(() => {
      expect(screen.getByText('文件超出在线预览大小限制')).toBeInTheDocument();
    });
    expect(screen.getByText('使用下载获取完整内容')).toBeInTheDocument();
  });

  it('白名单外 415 显示不支持文案', async () => {
    mockGetBlob.mockRejectedValue(apiError('PREVIEW_UNSUPPORTED_FORMAT'));

    renderWithProviders(
      <ArtifactContentPanel assetId="ast_001" versionId="ver_001" artifact={csvArtifact} />,
    );

    await waitFor(() => {
      expect(screen.getByText('该文件类型不支持在线预览')).toBeInTheDocument();
    });
  });

  it('其余失败显示通用错误文案并保留下载引导', async () => {
    mockGetBlob.mockRejectedValue(apiError('PREVIEW_SOURCE_UNAVAILABLE'));

    renderWithProviders(
      <ArtifactContentPanel assetId="ast_001" versionId="ver_001" artifact={csvArtifact} />,
    );

    await waitFor(() => {
      expect(screen.getByText('内容加载失败')).toBeInTheDocument();
    });
    expect(screen.getByText('使用下载获取完整内容')).toBeInTheDocument();
  });

  it('空内容文件显示空态提示', async () => {
    mockGetBlob.mockResolvedValue({ blob: new Blob([''], { type: 'text/csv' }), mediaType: 'text/csv' });

    renderWithProviders(
      <ArtifactContentPanel assetId="ast_001" versionId="ver_001" artifact={csvArtifact} />,
    );

    await waitFor(() => {
      expect(screen.getByText('该文件内容为空')).toBeInTheDocument();
    });
  });

  it('下载引导按钮经既有下载票据通道签发', async () => {
    mockGetBlob.mockRejectedValue(apiError('PREVIEW_TOO_LARGE'));
    mockIssueDownloadTicket.mockResolvedValue({ method: 'GIT_DVC' });

    renderWithProviders(
      <ArtifactContentPanel assetId="ast_001" versionId="ver_001" artifact={csvArtifact} />,
    );

    await waitFor(() => {
      expect(screen.getByText('使用下载获取完整内容')).toBeInTheDocument();
    });
    screen.getByText('使用下载获取完整内容').closest('button')?.click();
    await waitFor(() => {
      expect(mockIssueDownloadTicket).toHaveBeenCalledWith('ver_001', 'art_csv');
    });
  });
});
