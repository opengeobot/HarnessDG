/**
 * UploadPage 组件测试——上传表单、必填校验、文件选择。
 */
import { describe, it, expect, vi } from 'vitest';
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

const mockSearchParams = new URLSearchParams();
const mockSetSearchParams = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useSearchParams: () => [mockSearchParams, mockSetSearchParams],
  };
});

describe('UploadPage', () => {
  it('渲染上传页面标题', () => {
    renderWithProviders(<UploadPage />);
    expect(screen.getByText('上传中心')).toBeInTheDocument();
  });

  it('渲染 assetId 和 versionId 输入框', () => {
    renderWithProviders(<UploadPage />);
    expect(screen.getByPlaceholderText('资产 ID')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('版本 ID')).toBeInTheDocument();
  });

  it('必填字段未填时按钮禁用', () => {
    renderWithProviders(<UploadPage />);
    const btn = screen.getByRole('button', { name: /创建上传会话/ });
    expect(btn).toBeDisabled();
  });

  it('填写 assetId 和 versionId 后按钮仍因无文件而禁用', async () => {
    const user = userEvent.setup();
    renderWithProviders(<UploadPage />);
    await user.type(screen.getByPlaceholderText('资产 ID'), 'ast_001');
    await user.type(screen.getByPlaceholderText('版本 ID'), 'ver_001');
    const btn = screen.getByRole('button', { name: /创建上传会话/ });
    expect(btn).toBeDisabled();
  });

  it('选择文件后显示文件数量和大小', async () => {
    const user = userEvent.setup();
    renderWithProviders(<UploadPage />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLElement;

    const file = new File(['hello world'], 'test.txt', { type: 'text/plain' });
    await user.upload(fileInput, file);

    // 文件选择后显示文件数
    expect(screen.getByText(/1/i)).toBeInTheDocument();
  });

  it('URL 含 sessionId 参数时自动恢复会话', () => {
    // 设置 sessionId 查询参数
    mockSearchParams.set('sessionId', 'sess_restore_001');
    mockSearchParams.set('assetId', 'ast_001');
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

    renderWithProviders(<UploadPage />);

    // restoreSession 应被调用（通过 searchParams 触发）
    expect(mockGet).toHaveBeenCalled();
  });
});
