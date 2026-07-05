/**
 * CreateAssetModal 组件测试——验证创建资产弹窗的表单渲染、校验与提交。
 */
import React from 'react';
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

// Mock ControlledSelect to avoid real API calls (use createElement to avoid JSX issues in mock factory)
vi.mock('@/shared/components/ControlledSelect', () => ({
  ControlledSelect: (props: Record<string, unknown>) =>
    React.createElement(
      'div',
      { 'data-testid': `controlled-select-${(props.placeholder as string) ?? ''}` },
      (props.mode as string) === 'multiple' ? 'multi-select' : 'select',
      (props.disabled as boolean) ? ' (disabled)' : '',
    ),
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
    expect(screen.getByText(/模型/)).toBeInTheDocument();
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

  it('MODEL 类型时渲染受控选择器替代自由输入', () => {
    renderWithProviders(<CreateAssetModal {...defaultProps} />);
    // framework and task are now ControlledSelect components (zh i18n placeholders)
    expect(screen.getByTestId('controlled-select-请选择框架')).toBeInTheDocument();
    expect(screen.getByTestId('controlled-select-请选择任务')).toBeInTheDocument();
    // architecture remains as Input
    expect(screen.getByPlaceholderText('decoder-only')).toBeInTheDocument();
  });

  it('渲染组织/项目/Owner团队/标签/字典受控选择器', () => {
    renderWithProviders(<CreateAssetModal {...defaultProps} />);
    expect(screen.getByTestId('controlled-select-请选择组织')).toBeInTheDocument();
    expect(screen.getByTestId('controlled-select-请选择受控标签')).toBeInTheDocument();
    expect(screen.getByTestId('controlled-select-请选择许可证')).toBeInTheDocument();
    // Owner team 使用受控选择器（multi-select 模式，需先选组织才可用）
    expect(screen.getByTestId('controlled-select-请选择 Owner 团队（需先选组织）')).toBeInTheDocument();
    // Project 也是受控选择器
    expect(screen.getByTestId('controlled-select-请选择项目（需先选组织）')).toBeInTheDocument();
  });
});
