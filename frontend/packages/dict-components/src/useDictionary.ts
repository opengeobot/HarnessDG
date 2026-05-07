/**
 * 功能：字典数据通用 Hook，自动处理加载、缓存、多语言
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import type { DictionaryItem } from '@harnessdg/shared';

interface UseDictionaryOptions {
  parentId?: string;
  tree?: boolean;
  staleTime?: number;
}

interface UseDictionaryResult {
  items: DictionaryItem[];
  loading: boolean;
  error: Error | null;
  getLabel: (code: string) => string;
  getColor: (code: string) => string;
  getIcon: (code: string) => string;
  refresh: () => void;
}

const DICT_STALE_TIME = 5 * 60 * 1000;
const DICT_CACHE_TIME = 30 * 60 * 1000;

async function fetchDictionary(groupCode: string, tree?: boolean): Promise<DictionaryItem[]> {
  const url = tree
    ? `/api/v1/dictionary/tree/${groupCode}`
    : `/api/v1/dictionary/items/${groupCode}`;
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to fetch dictionary: ${groupCode}`);
  }
  const result = await response.json();
  return result.data ?? [];
}

export function useDictionary(
  groupCode: string,
  options: UseDictionaryOptions = {},
): UseDictionaryResult {
  const { i18n } = useTranslation();
  const locale = i18n.language || 'zh_CN';

  const { data, isLoading, error, refetch } = useQuery<DictionaryItem[]>({
    queryKey: ['dictionary', groupCode, options.tree, options.parentId],
    queryFn: () => fetchDictionary(groupCode, options.tree),
    staleTime: options.staleTime ?? DICT_STALE_TIME,
    gcTime: DICT_CACHE_TIME,
    refetchOnWindowFocus: false,
    refetchOnMount: false,
    retry: 2,
  });

  const items = data ?? [];

  const getLabel = (code: string): string => {
    const item = items.find((i) => i.code === code);
    if (!item) return code;
    return item.label[locale] || item.label['zh_CN'] || code;
  };

  const getColor = (code: string): string => {
    const item = items.find((i) => i.code === code);
    return item?.color ?? '';
  };

  const getIcon = (code: string): string => {
    const item = items.find((i) => i.code === code);
    return item?.icon ?? '';
  };

  return {
    items,
    loading: isLoading,
    error: error as Error | null,
    getLabel,
    getColor,
    getIcon,
    refresh: () => refetch(),
  };
}
