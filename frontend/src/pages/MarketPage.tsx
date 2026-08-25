// 市场列表页统一布局（09 §4/§5/§6/§7）— typeKey 参数化：筛选/排序/分页全部写 URL
// 时间：2026-08-21  作者：AxeXie
import React, { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, type Page, type Repository, type ResourceTypeSchema, type TaxonomyOption } from '../api/client';
import { errMsg } from '../App';
import FilterSidebar, { type FacetDef, type Filters } from '../components/FilterSidebar';
import { RepoCardRenderer } from '../components/cards';
import { Pagination, DataState } from '../components/ui';
import RepoFormModal from '../components/RepoFormModal';
import { useAuth } from '../context/AuthContext';

const PAGE_SIZE = 12;
const TYPE_META: Record<string, { title: string; unit: string; banner: string }> = {
  model: { title: '模型市场', unit: '模型', banner: '发布你的模型，让社区使用与部署。' },
  dataset: { title: '数据市场', unit: '数据集', banner: '开放高质量数据集，加速模型训练。' },
  studio: { title: '工作空间', unit: '工作空间', banner: '把你的创意做成可交互的应用。' },
};

const SORTS: Record<string, Array<{ v: string; label: string }>> = {
  // 09 §5.3 / §6.2：综合 / 下载量 / 喜欢数
  model: [
    { v: 'relevance-v1', label: '综合排序' },
    { v: 'downloads-desc', label: '下载量排序' },
    { v: 'likes-desc', label: '喜欢数排序' },
  ],
  dataset: [
    { v: 'relevance-v1', label: '综合排序' },
    { v: 'downloads-desc', label: '下载量排序' },
    { v: 'likes-desc', label: '喜欢数排序' },
  ],
  // studio 排序 4 项（09 §7.2）：综合 / 最近更新 / 访问数 / 喜欢数
  studio: [
    { v: 'relevance-v1', label: '综合排序' },
    { v: 'updatedAt-desc', label: '最近更新' },
    { v: 'visits-desc', label: '访问数排序' },
    { v: 'likes-desc', label: '喜欢数排序' },
  ],
};

const CHIP_LABEL: Record<string, string> = {
  task: '任务', framework: '框架', license: '协议', architecture: '结构', language: '语种',
  apiStatus: 'Api 状态', tag: '标签', scene: '场景', org: '组织', gated: '仅看申请制',
  mcp: 'MCP 兼容', deployable: '可部署', capability: '能力',
};

export default function MarketPage({ typeKey }: { typeKey: string }) {
  const { requireLogin } = useAuth();
  const [params, setParams] = useSearchParams();

  const [schema, setSchema] = useState<ResourceTypeSchema | null>(null);
  const [taxonomies, setTaxonomies] = useState<Record<string, TaxonomyOption[]>>({});
  const [data, setData] = useState<Page<Repository> | null>(null);
  const [featured, setFeatured] = useState<Page<Repository> | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'empty' | 'error' | 'forbidden'>('loading');
  const [errMsgText, setErrMsgText] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [capOpen, setCapOpen] = useState(false);

  // URL → 状态（单一事实来源为 URL searchParams）
  const keyword = params.get('keyword') ?? '';
  const sort = params.get('sort') ?? SORTS[typeKey]?.[0]?.v ?? 'relevance-v1';
  const page = Math.max(1, parseInt(params.get('page') ?? '1', 10) || 1);
  const filters: Filters = useMemo(() => ({
    task: params.get('task') ?? undefined,
    framework: params.get('framework') ?? undefined,
    license: params.get('license') ?? undefined,
    architecture: params.get('architecture') ?? undefined,
    language: params.get('language') ?? undefined,
    apiStatus: params.get('apiStatus') ?? undefined,
    tag: params.get('tag') ?? undefined,
    scene: params.get('scene') ?? undefined,
    org: params.get('org') ?? undefined,
    gated: params.get('gated') === 'true' || undefined,
    mcp: params.get('mcp') === 'true' || undefined,
    deployable: params.get('deployable') === 'true' || undefined,
    capability: params.getAll('capability').length ? params.getAll('capability') : undefined,
  }), [params]);

  // 加载 schema（facets）与 taxonomies（数据驱动）
  useEffect(() => {
    api.resourceTypeSchema(typeKey).then(setSchema).catch(() => {});
    api.metadataOptions().then((o) => setTaxonomies(o.taxonomies)).catch(() => {});
  }, [typeKey]);

  // 顶栏「创建」导航带 ?create=1 → 直接打开创建弹窗（已由 TopBar 登录后进入）
  useEffect(() => {
    if (params.get('create')) {
      setFormOpen(true);
      const next = new URLSearchParams(params);
      next.delete('create');
      setParams(next, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [typeKey]);

  function updateParams(patch: Record<string, string | undefined>, resetPage = true) {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(patch)) {
      next.delete(k);
      if (v !== undefined && v !== '') next.set(k, v);
    }
    if (resetPage) next.delete('page');
    setParams(next, { replace: true });
  }

  function onFilterChange(patch: Partial<Filters>) {
    const urlPatch: Record<string, string | undefined> = {};
    for (const [k, v] of Object.entries(patch)) {
      if (k === 'capability') {
        urlPatch['capability'] = Array.isArray(v) && v.length ? '__arr__' : undefined;
      } else {
        urlPatch[k] = v === undefined ? undefined : String(v);
      }
    }
    // capability 多值单独处理（重复参数）
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(urlPatch)) {
      if (k === 'capability') continue;
      next.delete(k);
      if (v !== undefined) next.set(k, v);
    }
    if ('capability' in patch) {
      next.delete('capability');
      for (const c of patch.capability ?? []) next.append('capability', c);
    }
    next.delete('page');
    setParams(next, { replace: true });
  }

  // 拉取列表
  const load = React.useCallback(() => {
    setState('loading');
    const q: Record<string, unknown> = {
      type: typeKey, page, pageSize: PAGE_SIZE, sort,
    };
    if (keyword.trim()) q['keyword'] = keyword.trim();
    for (const k of ['task', 'framework', 'license', 'architecture', 'language', 'apiStatus', 'tag', 'scene', 'org'] as const) {
      if (filters[k]) q[k] = filters[k];
    }
    for (const k of ['gated', 'mcp', 'deployable'] as const) {
      if (filters[k]) q[k] = filters[k];
    }
    if (filters.capability?.length) q['capability'] = filters.capability;
    api.listRepositories(q as never)
      .then((d) => {
        setData(d);
        setState(d.items.length === 0 ? 'empty' : 'ready');
      })
      .catch((e) => {
        setErrMsgText(errMsg(e));
        setState('forbidden');
      });
    // studio 双板块：精选（featured=true 命中项）+ 全部，共用同一分页器（09 §7.2）
    if (typeKey === 'studio') {
      api.listRepositories({ ...(q as object), featured: true, page: 1, pageSize: 6 } as never)
        .then(setFeatured)
        .catch(() => setFeatured(null));
    } else {
      setFeatured(null);
    }
  }, [typeKey, page, sort, keyword, filters]);

  useEffect(() => { load(); }, [load]);

  // 已选 chips
  const chips = useMemo(() => {
    const list: Array<{ key: string; label: string; value: string; display: string }> = [];
    const displayOf = (taxName: string | undefined, key: string): string => {
      if (!taxName) return key;
      return taxonomies[taxName]?.find((o) => o.key === key)?.displayName ?? key;
    };
    const facetOf = (key: string): FacetDef | undefined =>
      (schema?.facets as FacetDef[] | undefined)?.find((f) => f.key === key);
    if (filters.task) list.push({ key: 'task', label: CHIP_LABEL.task, value: filters.task, display: displayOf(facetOf('task')?.taxonomy ?? undefined, filters.task) });
    for (const k of ['framework', 'license', 'architecture', 'language', 'apiStatus', 'tag', 'scene'] as const) {
      const v = filters[k];
      if (v) list.push({ key: k, label: CHIP_LABEL[k], value: v, display: displayOf(facetOf(k)?.taxonomy ?? undefined, v) });
    }
    if (filters.org) list.push({ key: 'org', label: CHIP_LABEL.org, value: filters.org, display: filters.org });
    for (const k of ['gated', 'mcp', 'deployable'] as const) {
      if (filters[k]) list.push({ key: k, label: CHIP_LABEL[k], value: 'true', display: CHIP_LABEL[k] });
    }
    for (const c of filters.capability ?? []) {
      list.push({ key: 'capability', label: CHIP_LABEL.capability, value: c, display: displayOf('capability', c) });
    }
    return list;
  }, [filters, schema, taxonomies]);

  function removeChip(chip: { key: string; value: string }) {
    if (chip.key === 'capability') {
      onFilterChange({ capability: (filters.capability ?? []).filter((c) => c !== chip.value) });
    } else {
      onFilterChange({ [chip.key]: undefined } as Partial<Filters>);
    }
  }

  function clearChips() {
    const next = new URLSearchParams();
    if (keyword) next.set('keyword', keyword);
    if (sort !== SORTS[typeKey][0]?.v) next.set('sort', sort);
    setParams(next, { replace: true });
  }

  const meta = TYPE_META[typeKey] ?? TYPE_META.model;
  const sorts = SORTS[typeKey] ?? SORTS.model;
  const capabilityOptions = taxonomies['capability'] ?? [];
  const hasCapability = typeKey === 'model' && capabilityOptions.length > 0;

  return (
    <div className="market-page">
      {/* Banner */}
      <div className="market-banner">
        <div>
          <div className="market-banner-title">{meta.title}</div>
          <div className="market-banner-sub">{meta.banner}</div>
        </div>
        {typeKey === 'studio' ? (
          <div className="studio-guide">
            <div className="studio-guide-steps">
              {['创建空间', '上传应用', '配置运行时', '发布分享'].map((s, i) => (
                <span key={s} className="studio-guide-step"><b>{i + 1}</b>{s}</span>
              ))}
            </div>
            <button className="btn btn-primary" onClick={() => requireLogin(() => setFormOpen(true))}>
              我要创建
            </button>
          </div>
        ) : (
          <button className="btn btn-primary" onClick={() => requireLogin(() => setFormOpen(true))}>
            发布{meta.unit}
          </button>
        )}
      </div>

      <div className="market-layout">
        <FilterSidebar typeKey={typeKey}
                       facets={(schema?.facets as FacetDef[] | undefined) ?? []}
                       taxonomies={taxonomies}
                       filters={filters}
                       onChange={onFilterChange} />

        <div className="market-main">
          {/* chips 行 */}
          {chips.length > 0 && (
            <div className="chip-row">
              {chips.map((c) => (
                <span key={c.key + c.value} className="sel-chip">
                  {c.label}：{c.display}
                  <b onClick={() => removeChip(c)}>✕</b>
                </span>
              ))}
              <a className="sel-chip-clear" onClick={clearChips}>清空全部</a>
            </div>
          )}

          {/* 工具栏 */}
          <div className="toolbar">
            <input type="search" placeholder={`搜索${meta.unit}（共 ${data?.total ?? 0} 个）`}
                   defaultValue={keyword}
                   onKeyDown={(e) => {
                     if (e.key === 'Enter') {
                       updateParams({ keyword: (e.target as HTMLInputElement).value || undefined });
                     }
                   }} />
            {hasCapability && (
              <div className="cap-dropdown">
                <button className="btn btn-ghost btn-sm" onClick={() => setCapOpen(!capOpen)}>
                  能力{(filters.capability ?? []).length > 0 ? `（${filters.capability!.length}）` : ''} ▾
                </button>
                {capOpen && (
                  <div className="cap-menu">
                    {capabilityOptions.map((o) => (
                      <label key={o.key} className="filter-check">
                        <input type="checkbox"
                               checked={(filters.capability ?? []).includes(o.key)}
                               onChange={() => {
                                 const cur = filters.capability ?? [];
                                 onFilterChange({
                                   capability: cur.includes(o.key)
                                     ? cur.filter((c) => c !== o.key) : [...cur, o.key],
                                 });
                               }} />
                        {o.displayName}
                      </label>
                    ))}
                    <button className="btn btn-ghost btn-sm" onClick={() => setCapOpen(false)}>收起</button>
                  </div>
                )}
              </div>
            )}
            <select value={sort} onChange={(e) => updateParams({ sort: e.target.value })}>
              {sorts.map((s) => <option key={s.v} value={s.v}>{s.label}</option>)}
            </select>
          </div>

          {/* 数据区六态 */}
          <DataState state={state} errMsg={errMsgText} onRetry={load}>
            {featured && featured.items.length > 0 && (
              <div style={{ marginBottom: 20 }}>
                <h3 style={{ margin: '0 0 10px' }}>✨ 精选</h3>
                <div className="repo-grid">
                  {featured.items.map((r) => <RepoCardRenderer key={r.id} repo={r} />)}
                </div>
              </div>
            )}
            {featured && featured.items.length > 0 && (
              <h3 style={{ margin: '0 0 10px' }}>全部</h3>
            )}
            <div className="repo-grid">
              {(data?.items ?? []).map((r) => <RepoCardRenderer key={r.id} repo={r} />)}
            </div>
            {data && (
              <Pagination page={page} pageSize={PAGE_SIZE} total={data.total}
                          onChange={(p) => updateParams({ page: String(p) }, false)} />
            )}
          </DataState>
        </div>
      </div>

      {formOpen && (
        <RepoFormModal typeKey={typeKey} onClose={() => setFormOpen(false)}
                         onSaved={() => { setFormOpen(false); load(); }} />
      )}
    </div>
  );
}
