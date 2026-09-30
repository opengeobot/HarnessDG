/**
 * UploadPage 组件测试——上传表单、必填校验、文件选择、complete 契约载荷。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UploadPage } from './UploadPage';
import { renderWithProviders } from '@/test/test-utils';

/** 与被测代码同源的 Web Crypto SHA-256（jsdom 29 实证 crypto.subtle 可用）。 */
async function sha256Hex(text: string): Promise<string> {
  const bytes = new TextEncoder().encode(text);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

const mockPost = vi.fn().mockResolvedValue({
  sessionId: 'sess_001',
  assetId: 'ast_001',
  versionId: 'ver_001',
  status: 'OPEN',
  totalBytes: 1024,
  fileCount: 1,
  expiresAt: '2026-07-11T00:00:00Z',
});
const mockGet = vi.fn().mockResolvedValue({ url: 'https://presigned.example.com/part1' });

vi.mock('@/shared/api', () => ({
  apiClient: {
    post: (...args: unknown[]) => mockPost(...args),
    get: (...args: unknown[]) => mockGet(...args),
  },
}));

describe('UploadPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows deep-link hint when params missing', () => {
    renderWithProviders(<UploadPage />, { route: '/upload' });
    expect(screen.getByText(/assetId|versionId|查询参数/i)).toBeInTheDocument();
  });

  it('渲染 assetId 和 versionId 自查询参数', () => {
    renderWithProviders(<UploadPage />, { route: '/upload?assetId=ast_001&versionId=ver_001' });
    expect(screen.getByText('ast_001')).toBeInTheDocument();
    expect(screen.getByText('ver_001')).toBeInTheDocument();
  });

  it('无文件时创建会话按钮禁用', () => {
    renderWithProviders(<UploadPage />, { route: '/upload?assetId=ast_001&versionId=ver_001' });
    const btn = screen.getByRole('button', { name: /创建上传会话/ });
    expect(btn).toBeDisabled();
  });

  it('选择文件后显示文件数量和大小', async () => {
    const user = userEvent.setup();
    renderWithProviders(<UploadPage />, { route: '/upload?assetId=ast_001&versionId=ver_001' });

    const fileInput = document.querySelector('input[type="file"]') as HTMLElement;
    const file = new File(['hello world'], 'test.txt', { type: 'text/plain' });
    await user.upload(fileInput, file);

    expect(screen.getByText(/1 个文件|1 file/i)).toBeInTheDocument();
  });

  it('URL 含 sessionId 参数时自动恢复会话', async () => {
    mockGet.mockResolvedValueOnce({
      sessionId: 'sess_restore_001',
      assetId: 'ast_001',
      versionId: 'ver_001',
      status: 'OPEN',
      totalBytes: 2048,
      fileCount: 2,
      files: [],
      expiresAt: '2026-07-12T00:00:00Z',
    });

    renderWithProviders(<UploadPage />, {
      route: '/upload?sessionId=sess_restore_001&assetId=ast_001&versionId=ver_001',
    });

    expect(mockGet).toHaveBeenCalled();
  });

  it('complete 载荷携带 files 真实元数据（path/sha256/size/mediaType，对齐契约）', async () => {
    const user = userEvent.setup();
    const content = 'hello upload contract';
    mockGet.mockResolvedValueOnce({
      sessionId: 'sess_001',
      assetId: 'ast_001',
      versionId: 'ver_001',
      status: 'OPEN',
      totalBytes: 1024,
      fileCount: 1,
      parts: [{ partNumber: 1, status: 'COMPLETED', etag: 'etag-1' }],
      expiresAt: '2026-07-12T00:00:00Z',
    });

    renderWithProviders(<UploadPage />, {
      route: '/upload?sessionId=sess_001&assetId=ast_001&versionId=ver_001',
    });
    await waitFor(() => expect(screen.getByText('sess_001')).toBeInTheDocument());

    const fileInput = document.querySelector('input[type="file"]') as HTMLElement;
    const file = new File([content], 'bundle.txt', { type: 'text/plain' });
    await user.upload(fileInput, file);

    await user.click(screen.getByRole('button', { name: /完成上传/ }));

    const expectedSha256 = await sha256Hex(content);
    await waitFor(() =>
      expect(mockPost).toHaveBeenCalledWith(
        '/upload-sessions/sess_001/complete',
        expect.objectContaining({
          parts: [{ partNumber: 1, etag: 'etag-1' }],
          files: [
            expect.objectContaining({
              path: 'bundle.txt',
              sha256: expectedSha256,
              size: file.size,
              mediaType: 'text/plain',
            }),
          ],
        }),
      ),
    );
  });

  it('恢复会话后本地无文件时诚实阻断 complete（不伪造 sha256）', async () => {
    const user = userEvent.setup();
    mockGet.mockResolvedValueOnce({
      sessionId: 'sess_002',
      assetId: 'ast_001',
      versionId: 'ver_001',
      status: 'OPEN',
      totalBytes: 1024,
      fileCount: 1,
      parts: [{ partNumber: 1, status: 'COMPLETED', etag: 'etag-1' }],
      expiresAt: '2026-07-12T00:00:00Z',
    });

    renderWithProviders(<UploadPage />, {
      route: '/upload?sessionId=sess_002&assetId=ast_001&versionId=ver_001',
    });
    await waitFor(() => expect(screen.getByText('sess_002')).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: /完成上传/ }));

    expect(await screen.findByText(/请先选择本地文件/)).toBeInTheDocument();
    expect(mockPost).not.toHaveBeenCalled();
  });
});
