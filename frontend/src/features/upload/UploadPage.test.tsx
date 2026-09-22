/**
 * UploadPage 组件测试——上传表单、必填校验、文件选择。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UploadPage } from './UploadPage';
import { renderWithProviders } from '@/test/test-utils';

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
});
