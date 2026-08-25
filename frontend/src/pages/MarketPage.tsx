// 市场列表页统一布局（09 §4/§5/§6/§7）— typeKey 参数化：筛选/排序/分页全部写 URL
// 时间：2026-08-21  作者：AxeXie
import React, { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, type Page, type Repository, type ResourceTypeSchema, type TaxonomyOption } from '../api/client';
import { errMsg } from '../App';
import FilterSidebar, { type FacetDef, type Filters } from '../components/FilterSidebar';
import { RepoCardRenderer } from '../components/cards';
import { Pagination, DataState } from '../components/ui';
import RepoFormModal from '../components/RepoFormModal';
import { useAuth } from '../context/AuthContext';

const PAGE_SIZE = 12;
const TYPE_META: Record<string, { titleKey: string; unitKey: string; bannerKey: string }> = {
  model: { titleKey: 'market.modelTitle', unitKey: 'market.modelUnit', bannerKey: 'market.modelBanner' },
  dataset: { titleKey: 'market.datasetTitle', unitKey: 'market.datasetUnit', bannerKey: 'market.datasetBanner' },
  studio: { titleKey: 'market.studioTitle', unitKey: 'market.studioUnit', bannerKey: 'market.studioBanner' },
};

const SORTS: Record<string, Array<{ v: string; labelKey: string }>> = {
  // 09 §5.3 / §6.2：综合 / 下载量 / 喜欢数
  model: [
    { v: 'relevance-v1', labelKey: 'market.sortRelevance' },
    { v: 'downloads-desc', labelKey: 'market.sortDownloads' },
    { v: 'likes-desc', labelKey: 'market.sortLikes' },
  ],
  dataset: [
    { v: 'relevance-v1', labelKey: 'market.sortRelevance' },
    { v: 'downloads-desc', labelKey: 'market.sortDownloads' },
    { v: 'likes-desc', labelKey: 'market.sortLikes' },
  ],
  // studio 排序 4 项（09 §7.2）：综合 / 最近更新 / 访问数 / 喜欢数
  studio: [
    { v: 'relevance-v1', labelKey: 'market.sortRelevance' },
    { v: 'updatedAt-desc', labelKey: 'market.sortUpdated' },
    { v: 'visits-desc', labelKey: 'market.sortVisits' },
    { v: 'likes-desc', labelKey: 'market.sortLikes' },
  ],
};

const CHIP_KEY: Record<string, string> = {
  task: 'market.chipTask', framework: 'market.chipFramework', license: 'market.chipLicense',
  architecture: 'market.chipArchitecture', language: 'market.chipLanguage', apiStatus: 'market.chipApiStatus',
  tag: 'market.chipTag', scene: 'market.chipScene', org: 'market.chipOrg', gated: 'market.chipGated',
  mcp: 'market.chipMcp', deployable: 'market.chipDeployable', capability: 'market.chipCapability',
};

export default function MarketPage({ typeKey }: { typeKey: string }) {
  const { requireLogin } = useAuth();
  const { t } = useTranslation();
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
    const list: Array<{ key: string; labelKey: string; value: string; display: string }> = [];
    const displayOf = (taxName: string | undefined, key: string): string => {
      if (!taxName) return key;
      return taxonomies[taxName]?.find((o) => o.key === key)?.displayName ?? key;
    };
    const facetOf = (key: string): FacetDef | undefined =>
      (schema?.facets as FacetDef[] | undefined)?.find((f) => f.key === key);
    if (filters.task) list.push({ key: 'task', labelKey: CHIP_KEY.task, value: filters.task, display: displayOf(facetOf('task')?.taxonomy ?? undefined, filters.task) });
    for (const k of ['framework', 'license', 'architecture', 'language', 'apiStatus', 'tag', 'scene'] as const) {
      const v = filters[k];
      if (v) list.push({ key: k, labelKey: CHIP_KEY[k], value: v, display: displayOf(facetOf(k)?.taxonomy ?? undefined, v) });
    }
    if (filters.org) list.push({ key: 'org', labelKey: CHIP_KEY.org, value: filters.org, display: filters.org });
    for (const k of ['gated', 'mcp', 'deployable'] as const) {
      if (filters[k]) list.push({ key: k, labelKey: CHIP_KEY[k], value: 'true', display: t(CHIP_KEY[k]) });
    }
    for (const c of filters.capability ?? []) {
      list.push({ key: 'capability', labelKey: CHIP_KEY.capability, value: c, display: displayOf('capability', c) });
    }
    return list;
  }, [filters, schema, taxonomies, t]);

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
  const unit = t(meta.unitKey);
  const sorts = SORTS[typeKey] ?? SORTS.model;
  const capabilityOptions = taxonomies['capability'] ?? [];
  const hasCapability = typeKey === 'model' && capabilityOptions.length > 0;

  return (
    <div className="market-page">
      {/* Banner */}
      <div className="market-banner">
        <div>
          <div className="market-banner-title">{t(meta.titleKey)}</div>
          <div className="market-banner-sub">{t(meta.bannerKey)}</div>
        </div>
        {typeKey === 'studio' ? (
          <div className="studio-guide">
            <div className="studio-guide-steps">
              {(t('market.guideSteps', { returnObjects: true }) as string[]).map((s, i) => (
                <span key={s} className="studio-guide-step"><b>{i + 1}</b>{s}</span>
              ))}
            </div>
            <button className="btn btn-primary" onClick={() => requireLogin(() => setFormOpen(true))}>
              {t('market.createCta')}
            </button>
          </div>
        ) : (
          <button className="btn btn-primary" onClick={() => requireLogin(() => setFormOpen(true))}>
            {t('market.publish', { unit })}
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
                  {t(c.labelKey)}{t('common.kvSep')}{c.display}
                  <b onClick={() => removeChip(c)}>✕</b>
                </span>
              ))}
              <a className="sel-chip-clear" onClick={clearChips}>{t('market.clearAll')}</a>
            </div>
          )}

          {/* 工具栏 */}
          <div className="toolbar">
            <input type="search" placeholder={t('market.searchPlaceholder', { unit, n: data?.total ?? 0 })}
                   defaultValue={keyword}
                   onKeyDown={(e) => {
                     if (e.key === 'Enter') {
                       updateParams({ keyword: (e.target as HTMLInputElement).value || undefined });
                     }
                   }} />
            {hasCapability && (
              <div className="cap-dropdown">
                <button className="btn btn-ghost btn-sm" onClick={() => setCapOpen(!capOpen)}>
                  {(filters.capability ?? []).length > 0
                    ? t('market.capabilityCount', { n: filters.capability!.length })
                    : `${t('market.chipCapability')} ▾`}
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
                    <button className="btn btn-ghost btn-sm" onClick={() => setCapOpen(false)}>{t('market.collapse')}</button>
                  </div>
                )}
              </div>
            )}
            <select value={sort} onChange={(e) => updateParams({ sort: e.target.value })}>
              {sorts.map((s) => <option key={s.v} value={s.v}>{t(s.labelKey)}</option>)}
            </select>
          </div>

          {/* 数据区六态 */}
          <DataState state={state} errMsg={errMsgText} onRetry={load}>
            {featured && featured.items.length > 0 && (
              <div style={{ marginBottom: 20 }}>
                <h3 style={{ margin: '0 0 10px' }}>{t('market.featured')}</h3>
                <div className="repo-grid">
                  {featured.items.map((r) => <RepoCardRenderer key={r.id} repo={r} />)}
                </div>
              </div>
            )}
            {featured && featured.items.length > 0 && (
              <h3 style={{ margin: '0 0 10px' }}>{t('market.all')}</h3>
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
