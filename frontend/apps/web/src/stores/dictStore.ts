/**
 * 功能：字典全局状态管理 - Zustand 实现，负责字典缓存、批量加载、过期重载
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { create } from 'zustand';
import type { DictionaryItem } from '@harnessdg/shared';
import { dictApi } from '@/services';

const TTL_MS = 5 * 60 * 1000;

interface CacheEntry {
  items: DictionaryItem[];
  expireAt: number;
}

interface DictState {
  cache: Record<string, CacheEntry>;
  loading: Record<string, boolean>;
  error: Record<string, string | null>;

  /** 加载单个分组字典项（若缓存未过期则跳过） */
  loadGroup: (groupCode: string, force?: boolean) => Promise<DictionaryItem[]>;
  /** 批量加载多个分组字典项，去重合并 */
  loadBatch: (groupCodes: string[], force?: boolean) => Promise<void>;
  /** 同步获取当前缓存的字典项（不触发加载） */
  getItems: (groupCode: string) => DictionaryItem[];
  /** 同步获取字典项显示文本 */
  getLabel: (groupCode: string, itemCode: string, locale?: string) => string;
  /** 同步获取字典项颜色 */
  getColor: (groupCode: string, itemCode: string) => string;
  /** 同步获取字典项图标 */
  getIcon: (groupCode: string, itemCode: string) => string;
  /** 手动失效某分组缓存，写操作后需调用 */
  invalidate: (groupCode: string) => void;
  /** 清空全部缓存 */
  invalidateAll: () => void;
}

function extractItems(resp: any): DictionaryItem[] {
  // R<List<DictItemDTO>> 响应经 axios 拦截器返回的结构为 { code, message, data, ... }
  if (resp && Array.isArray(resp.data)) return resp.data as DictionaryItem[];
  if (Array.isArray(resp)) return resp as DictionaryItem[];
  return [];
}

export const useDictStore = create<DictState>((set, get) => ({
  cache: {},
  loading: {},
  error: {},

  loadGroup: async (groupCode, force = false) => {
    if (!groupCode) return [];
    const state = get();
    const entry = state.cache[groupCode];
    const now = Date.now();
    if (!force && entry && entry.expireAt > now) {
      return entry.items;
    }
    // 并发去重：若已在加载则等待
    if (state.loading[groupCode]) {
      return entry?.items ?? [];
    }
    set((s) => ({ loading: { ...s.loading, [groupCode]: true }, error: { ...s.error, [groupCode]: null } }));
    try {
      const resp = await dictApi.listItems(groupCode);
      const items = extractItems(resp);
      set((s) => ({
        cache: { ...s.cache, [groupCode]: { items, expireAt: Date.now() + TTL_MS } },
        loading: { ...s.loading, [groupCode]: false },
      }));
      return items;
    } catch (e: any) {
      set((s) => ({
        loading: { ...s.loading, [groupCode]: false },
        error: { ...s.error, [groupCode]: e?.message ?? 'load failed' },
      }));
      return [];
    }
  },

  loadBatch: async (groupCodes, force = false) => {
    const state = get();
    const now = Date.now();
    const missing = groupCodes.filter((code) => {
      if (!code) return false;
      if (state.loading[code]) return false;
      const entry = state.cache[code];
      return force || !entry || entry.expireAt <= now;
    });
    if (missing.length === 0) return;

    const loadingPatch: Record<string, boolean> = {};
    missing.forEach((c) => (loadingPatch[c] = true));
    set((s) => ({ loading: { ...s.loading, ...loadingPatch } }));

    try {
      const resp: any = await dictApi.batchItems(missing);
      const dataMap: Record<string, DictionaryItem[]> = (resp?.data ?? resp) ?? {};
      const newCache: Record<string, CacheEntry> = {};
      const doneLoading: Record<string, boolean> = {};
      missing.forEach((code) => {
        newCache[code] = {
          items: Array.isArray(dataMap[code]) ? dataMap[code] : [],
          expireAt: Date.now() + TTL_MS,
        };
        doneLoading[code] = false;
      });
      set((s) => ({
        cache: { ...s.cache, ...newCache },
        loading: { ...s.loading, ...doneLoading },
      }));
    } catch (e: any) {
      const doneLoading: Record<string, boolean> = {};
      const errorPatch: Record<string, string> = {};
      missing.forEach((code) => {
        doneLoading[code] = false;
        errorPatch[code] = e?.message ?? 'load failed';
      });
      set((s) => ({
        loading: { ...s.loading, ...doneLoading },
        error: { ...s.error, ...errorPatch },
      }));
    }
  },

  getItems: (groupCode) => get().cache[groupCode]?.items ?? [],

  getLabel: (groupCode, itemCode, locale) => {
    const items = get().cache[groupCode]?.items ?? [];
    const item = items.find((i) => i.code === itemCode);
    if (!item) return itemCode;
    const lang = locale || localStorage.getItem('harnessdg_locale') || 'zh_CN';
    return item.label?.[lang] || item.label?.zh_CN || itemCode;
  },

  getColor: (groupCode, itemCode) => {
    const item = (get().cache[groupCode]?.items ?? []).find((i) => i.code === itemCode);
    return item?.color ?? '';
  },

  getIcon: (groupCode, itemCode) => {
    const item = (get().cache[groupCode]?.items ?? []).find((i) => i.code === itemCode);
    return item?.icon ?? '';
  },

  invalidate: (groupCode) => {
    set((s) => {
      const nextCache = { ...s.cache };
      delete nextCache[groupCode];
      return { cache: nextCache };
    });
  },

  invalidateAll: () => set({ cache: {}, loading: {}, error: {} }),
}));

/** 启动时预加载的高频字典分组（v2.md §9.5.3） */
export const PRELOAD_DICT_GROUPS = [
  'task_type',
  'task_status',
  'asset_status',
  'priority_level',
  'data_domain',
  'entity_type',
  'metric_type',
  'dimension_type',
  'relation_type',
  'agg_method',
];

/** 在应用启动时调用，预加载高频字典 */
export function preloadDictionaries() {
  return useDictStore.getState().loadBatch(PRELOAD_DICT_GROUPS);
}
