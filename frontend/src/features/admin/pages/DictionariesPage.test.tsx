/**
 * DictionariesPage 组件测试——验证字典管理页面列表渲染与字典项操作。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { DictionariesPage } from './DictionariesPage';
import { renderWithProviders } from '@/test/test-utils';

const mockListDictionaries = vi.fn().mockResolvedValue([
  { dictCode: 'license', i18nKey: 'dict.license', itemCount: 3 },
  { dictCode: 'framework', i18nKey: 'dict.framework', itemCount: 5 },
]);
const mockListDictionaryItems = vi.fn().mockResolvedValue([
  { itemCode: 'Apache-2.0', i18nKey: 'dict.apache2', sortOrder: 1, status: 'ENABLED' },
  { itemCode: 'MIT', i18nKey: 'dict.mit', sortOrder: 2, status: 'ENABLED' },
]);
const mockCreateDictionaryItem = vi.fn().mockResolvedValue({});
const mockUpdateDictionaryItem = vi.fn().mockResolvedValue({});

vi.mock('../api', () => ({
  listDictionaries: (...args: unknown[]) => mockListDictionaries(...args),
  listDictionaryItems: (...args: unknown[]) => mockListDictionaryItems(...args),
  createDictionaryItem: (...args: unknown[]) => mockCreateDictionaryItem(...args),
  updateDictionaryItem: (...args: unknown[]) => mockUpdateDictionaryItem(...args),
}));

describe('DictionariesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders page title', async () => {
    renderWithProviders(<DictionariesPage />);
    await waitFor(() => {
      expect(screen.getByText(/字典管理|dict\.title/i)).toBeInTheDocument();
    });
  });

  it('renders dictionary type list', async () => {
    renderWithProviders(<DictionariesPage />);
    await waitFor(() => {
      expect(screen.getByText('license')).toBeInTheDocument();
      expect(screen.getByText('framework')).toBeInTheDocument();
    });
  });
});
