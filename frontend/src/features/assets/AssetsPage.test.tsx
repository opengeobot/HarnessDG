/**
 * AssetsPage 组件测试——验证资产列表页渲染、搜索与过滤交互。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { AssetsPage } from './AssetsPage';
import { renderWithProviders } from '@/test/test-utils';

// Mock API 模块
vi.mock('./api', () => ({
  searchAssets: vi.fn().mockResolvedValue({ items: [], hasMore: false, nextCursor: null }),
  deleteAsset: vi.fn().mockResolvedValue(undefined),
}));

// Mock ControlledSelect 避免测试中发起真实 API 请求
vi.mock('@/shared/components/ControlledSelect', () => ({
  ControlledSelect: (props: { placeholder?: string; value?: string; onChange?: (v: string) => void }) => (
    <select
      data-testid="controlled-select"
      value={props.value ?? ''}
      onChange={(e) => props.onChange?.(e.target.value)}
    >
      <option value="">{props.placeholder ?? ''}</option>
    </select>
  ),
}));

// Mock react-router-dom 的 Link 组件（保留 Router 等真实实现）
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
      <a href={to}>{children}</a>
    ),
  };
});

describe('AssetsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('渲染页面标题和操作按钮', () => {
    renderWithProviders(<AssetsPage />);
    expect(screen.getByText('资产目录')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /登\s*记\s*资\s*产|registerAsset/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /刷\s*新|refresh/i })).toBeInTheDocument();
  });

  it('渲染类型过滤 Segmented（全部/模型/数据集）', () => {
    renderWithProviders(<AssetsPage />);
    // Segmented 选项
    expect(screen.getByText('全部')).toBeInTheDocument();
    expect(screen.getByText('模型')).toBeInTheDocument();
    expect(screen.getByText('数据集')).toBeInTheDocument();
  });

  it('渲染搜索输入框', () => {
    renderWithProviders(<AssetsPage />);
    expect(screen.getByPlaceholderText(/搜索|search/i)).toBeInTheDocument();
  });

  it('数据为空时展示 Empty 组件', async () => {
    renderWithProviders(<AssetsPage />);
    // antd Table 空状态使用 i18n 的 assets.emptyText
    const empty = await screen.findByText(/暂无资产/);
    expect(empty).toBeInTheDocument();
  });

  it('渲染表格列头', () => {
    renderWithProviders(<AssetsPage />);
    expect(screen.getByText('名称')).toBeInTheDocument();
    expect(screen.getByText('类型')).toBeInTheDocument();
    expect(screen.getByText('可见性')).toBeInTheDocument();
    expect(screen.getByText('状态')).toBeInTheDocument();
  });

  it('渲染分类过滤面板（语言 + 敏感等级）', () => {
    renderWithProviders(<AssetsPage />);
    expect(screen.getByText('语言')).toBeInTheDocument();
    expect(screen.getByText('敏感等级')).toBeInTheDocument();
  });
});
