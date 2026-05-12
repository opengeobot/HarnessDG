/**
 * 功能：字典数据通用 Hook，封装 dictStore 与 i18n，提供响应式字典读取
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { useEffect, useMemo, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import type { DictionaryItem } from '@harnessdg/shared';
import { useDictStore } from '@/stores/dictStore';

export interface UseDictionaryResult {
  items: DictionaryItem[];
  loading: boolean;
  error: string | null;
  getLabel: (code: string) => string;
  getColor: (code: string) => string;
  getIcon: (code: string) => string;
  refresh: () => void;
}

export function useDictionary(groupCode: string): UseDictionaryResult {
  const { i18n } = useTranslation();
  const locale = i18n.language || 'zh_CN';

  // 缓存选择器函数以避免无限循环
  const selectItems = useCallback((s: any) => s.cache[groupCode]?.items ?? [], [groupCode]);
  const selectLoading = useCallback((s: any) => !!s.loading[groupCode], [groupCode]);
  const selectError = useCallback((s: any) => s.error[groupCode] ?? null, [groupCode]);
  const selectLoadGroup = useCallback((s: any) => s.loadGroup, []);

  const items = useDictStore(selectItems);
  const loading = useDictStore(selectLoading);
  const error = useDictStore(selectError);
  const loadGroup = useDictStore(selectLoadGroup);

  useEffect(() => {
    if (groupCode) loadGroup(groupCode);
  }, [groupCode, loadGroup]);

  return useMemo<UseDictionaryResult>(() => {
    const getItem = (code: string) => items.find((i: DictionaryItem) => i.code === code);
    return {
      items,
      loading,
      error,
      getLabel: (code) => {
        const item = getItem(code);
        if (!item) return code;
        return (item.label as any)?.[locale] || (item.label as any)?.zh_CN || code;
      },
      getColor: (code) => getItem(code)?.color ?? '',
      getIcon: (code) => getItem(code)?.icon ?? '',
      refresh: () => loadGroup(groupCode, true),
    };
  }, [items, loading, error, locale, groupCode, loadGroup]);
}

/** 批量订阅多个字典分组，确保都已加载 */
export function useDictionaryBatch(groupCodes: string[]): Record<string, UseDictionaryResult> {
  const loadBatch = useDictStore((s) => s.loadBatch);
  const codeKey = groupCodes.join('|');

  useEffect(() => {
    if (groupCodes.length > 0) loadBatch(groupCodes);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [codeKey]);

  const results: Record<string, UseDictionaryResult> = {};
  // 逐个调用 useDictionary 以复用 selector；但 hooks 不能在循环中调用，这里改用 store 直读
  const { i18n } = useTranslation();
  const locale = i18n.language || 'zh_CN';
  const cache = useDictStore((s) => s.cache);
  const loading = useDictStore((s) => s.loading);
  const error = useDictStore((s) => s.error);
  const loadGroup = useDictStore((s) => s.loadGroup);

  groupCodes.forEach((code) => {
    const items = cache[code]?.items ?? [];
    const getItem = (c: string) => items.find((i: DictionaryItem) => i.code === c);
    results[code] = {
      items,
      loading: !!loading[code],
      error: error[code] ?? null,
      getLabel: (c) => {
        const item = getItem(c);
        if (!item) return c;
        return (item.label as any)?.[locale] || (item.label as any)?.zh_CN || c;
      },
      getColor: (c) => getItem(c)?.color ?? '',
      getIcon: (c) => getItem(c)?.icon ?? '',
      refresh: () => loadGroup(code, true),
    };
  });
  return results;
}
