/**
 * CreateAssetPage 组件测试——验证创建资产独立页面渲染、表单与导航。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { CreateAssetPage } from './CreateAssetPage';
import { renderWithProviders } from '@/test/test-utils';

vi.mock('./api', () => ({
  createAsset: vi.fn().mockResolvedValue({ assetId: 'ast_new', namespace: 'org1', name: 'model1' }),
  listDictionaryItems: vi.fn().mockResolvedValue([]),
  listTags: vi.fn().mockResolvedValue([]),
  searchAssets: vi.fn().mockResolvedValue({ items: [], hasMore: false, nextCursor: null }),
}));

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

const mockNavigate = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
      <a href={to}>{children}</a>
    ),
  };
});

vi.mock('@/shared/hooks', () => ({
  useDocumentTitle: vi.fn(),
}));

/** antd Button 在两个汉字之间插入空格，归一化匹配且限定 button 元素 */
const btnText = (text: string) => (_: string | null, el: Element | null) =>
  el?.tagName === 'BUTTON' && (el.textContent?.replace(/\s+/g, '') ?? '') === text;

describe('CreateAssetPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('渲染页面标题', () => {
    renderWithProviders(<CreateAssetPage />);
    expect(screen.getByText('登记资产')).toBeInTheDocument();
  });

  it('渲染创建和取消按钮', () => {
    renderWithProviders(<CreateAssetPage />);
    expect(screen.getByText(btnText('创建'))).toBeInTheDocument();
    expect(screen.getByText(btnText('取消'))).toBeInTheDocument();
  });

  it('取消按钮点击后导航回列表', async () => {
    const { default: userEvent } = await import('@testing-library/user-event');
    const user = userEvent.setup();
    renderWithProviders(<CreateAssetPage />);
    const cancelBtn = screen.getByText(btnText('取消'));
    await user.click(cancelBtn);
    expect(mockNavigate).toHaveBeenCalledWith('/assets');
  });
});
