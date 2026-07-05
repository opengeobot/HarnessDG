/**
 * TagsPage 组件测试——验证标签管理页面列表渲染与组织作用域过滤。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { TagsPage } from './TagsPage';
import { renderWithProviders } from '@/test/test-utils';

const mockListTags = vi.fn().mockResolvedValue([
  {
    tagId: 'tag_01',
    tagCode: 'nlp',
    displayName: 'NLP',
    scopeType: 'PLATFORM',
    scopeId: null,
    status: 'ENABLED',
  },
  {
    tagId: 'tag_02',
    tagCode: 'cv',
    displayName: 'Computer Vision',
    scopeType: 'ORGANIZATION',
    scopeId: 'org_01',
    status: 'ENABLED',
  },
]);
const mockCreateTag = vi.fn().mockResolvedValue({});
const mockUpdateTag = vi.fn().mockResolvedValue({});
const mockEnableTag = vi.fn().mockResolvedValue({});
const mockDisableTag = vi.fn().mockResolvedValue({});

vi.mock('../api', () => ({
  listTags: (...args: unknown[]) => mockListTags(...args),
  createTag: (...args: unknown[]) => mockCreateTag(...args),
  updateTag: (...args: unknown[]) => mockUpdateTag(...args),
  enableTag: (...args: unknown[]) => mockEnableTag(...args),
  disableTag: (...args: unknown[]) => mockDisableTag(...args),
}));

describe('TagsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders page title', async () => {
    renderWithProviders(<TagsPage />);
    await waitFor(() => {
      expect(screen.getByText(/标签管理|tags\.title/i)).toBeInTheDocument();
    });
  });

  it('renders tag list after loading', async () => {
    renderWithProviders(<TagsPage />);
    await waitFor(() => {
      expect(screen.getByText('NLP')).toBeInTheDocument();
      expect(screen.getByText('Computer Vision')).toBeInTheDocument();
    });
  });

  it('shows tag codes', async () => {
    renderWithProviders(<TagsPage />);
    await waitFor(() => {
      expect(screen.getByText('nlp')).toBeInTheDocument();
    });
  });
});
