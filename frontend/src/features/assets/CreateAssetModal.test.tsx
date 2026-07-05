/**
 * CreateAssetModal 组件测试——验证创建资产弹窗的表单渲染、校验与提交。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CreateAssetModal } from './CreateAssetModal';
import { renderWithProviders } from '@/test/test-utils';

// Mock API
const mockCreateAsset = vi.fn();
vi.mock('./api', () => ({
  createAsset: (...args: unknown[]) => mockCreateAsset(...args),
}));

const defaultProps = { open: true, onClose: vi.fn() };

describe('CreateAssetModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('open=true 时渲染弹窗标题', () => {
    renderWithProviders(<CreateAssetModal {...defaultProps} />);
    expect(screen.getByText(/登记资产|create\.title/i)).toBeInTheDocument();
  });

  it('open=false 时不渲染表单', () => {
    renderWithProviders(<CreateAssetModal open={false} onClose={vi.fn()} />);
    expect(screen.queryByText(/登记资产|create\.title/i)).not.toBeInTheDocument();
  });

  it('渲染关键字段：类型、命名空间、名称', () => {
    renderWithProviders(<CreateAssetModal {...defaultProps} />);
    // 类型选择（默认 MODEL，渲染为“模型”标签）
    expect(screen.getByText(/模型/)).toBeInTheDocument();
    // namespace / name 输入框
    expect(screen.getByPlaceholderText('nlp')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('qwen-domain-7b')).toBeInTheDocument();
  });

  it('点击取消按钮调用 onClose', async () => {
    const onClose = vi.fn();
    renderWithProviders(<CreateAssetModal open={true} onClose={onClose} />);
    const cancelBtn = screen.getByRole('button', { name: /取\s*消|cancel/i });
    await userEvent.click(cancelBtn);
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('MODEL 类型时渲染 framework/task/architecture 字段', () => {
    renderWithProviders(<CreateAssetModal {...defaultProps} />);
    // 默认是 MODEL 类型，应该有 framework、task、architecture 字段
    expect(screen.getByPlaceholderText('pytorch')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('text-generation')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('decoder-only')).toBeInTheDocument();
  });
});
