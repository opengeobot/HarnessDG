// 列表页左侧筛选栏（09 §4/§5.2/§6.1/§7.2）— 分组折叠、组内单选可取消，选项全部数据驱动
// 时间：2026-08-21  作者：AxeXie
import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, type TaxonomyOption, type OrganizationListItem } from '../api/client';

/** 契约 ResourceTypeSchema.facets 项（key/dataType/filterable/sortable/taxonomy）。 */
export interface FacetDef {
  key: string;
  dataType?: string;
  filterable?: boolean;
  sortable?: boolean;
  taxonomy?: string | null;
}

/** 列表筛选状态（与 04 §6.3 请求参数同名）。 */
export interface Filters {
  task?: string;
  framework?: string;
  license?: string;
  architecture?: string;
  language?: string;
  apiStatus?: string;
  tag?: string;
  scene?: string;
  capability?: string[];
  org?: string;
  gated?: boolean;
  mcp?: boolean;
  deployable?: boolean;
}

const GROUP_KEY: Record<string, string> = {
  task: 'filter.groupTask', framework: 'filter.groupFramework', license: 'filter.groupLicense', architecture: 'filter.groupArchitecture',
  language: 'filter.groupLanguage', tag: 'filter.groupTag', scene: 'filter.groupScene', apiStatus: 'filter.groupApiStatus',
};
const BOOL_KEY: Record<string, string> = {
  deployable: 'filter.boolDeployable', mcpCompatible: 'filter.boolMcp',
};
/** boolean facet key → 列表查询参数名（04 §6.3）。 */
const BOOL_PARAM: Record<string, 'deployable' | 'mcp'> = {
  deployable: 'deployable', mcpCompatible: 'mcp',
};

function CollapsibleGroup({ title, children }: { title: string; children: React.ReactNode }) {
  const [open, setOpen] = useState(true);
  return (
    <div className="filter-group">
      <div className="filter-group-head" onClick={() => setOpen(!open)}>
        <span>{title}</span>
        <span className="filter-caret">{open ? '▾' : '▸'}</span>
      </div>
      {open && <div className="filter-group-body">{children}</div>}
    </div>
  );
}

/** 仅展示启用选项（字典统一：disabled 项不出现在筛选面）。 */
const activeOnly = (opts: TaxonomyOption[]) => opts.filter((o) => o.status === 'active');

/** 按当前语言择显：中文优先 displayName，英文优先 displayNameEn（缺省回退）。 */
function labelOf(o: TaxonomyOption, zh: boolean): string {
  if (zh) return o.displayName || o.displayNameEn || o.key;
  return o.displayNameEn || o.displayName || o.key;
}

/** 任务树组：分类折叠 + 子任务 chip 单选可取消，映射 task 参数（09 §5.2）。 */
function TaskTreeGroup({ options, value, onPick }: {
  options: TaxonomyOption[]; value?: string; onPick: (v?: string) => void;
}) {
  const { t, i18n } = useTranslation();
  const zh = i18n.language.startsWith('zh');
  const [filter, setFilter] = useState('');
  const opts = activeOnly(options);
  const roots = opts.filter((o) => !o.parentKey);
  const childrenOf = (key: string) => opts.filter((o) => o.parentKey === key);
  // 无层级时（平铺字典）直接列 chips
  const flat = roots.length === opts.length;
  const match = (o: TaxonomyOption) =>
    !filter || labelOf(o, zh).includes(filter) || o.key.includes(filter);
  return (
    <>
      <input className="filter-search" placeholder={t('filter.searchTask')}
             value={filter} onChange={(e) => setFilter(e.target.value)} />
      {flat ? (
        <div className="chip-wrap">
          {opts.filter(match).map((o) => (
              <span key={o.key}
                    className={`filter-chip ${value === o.key ? 'active' : ''}`}
                    onClick={() => onPick(value === o.key ? undefined : o.key)}>
                {labelOf(o, zh)}
              </span>
            ))}
        </div>
      ) : roots.map((root) => {
        const kids = childrenOf(root.key).filter(match);
        if (filter && kids.length === 0) return null;
        return (
          <details key={root.key} className="task-cat" open={!!filter}>
            <summary>{labelOf(root, zh)}{t('filter.catCount', { n: childrenOf(root.key).length })}</summary>
            <div className="chip-wrap">
              {kids.map((o) => (
                <span key={o.key}
                      className={`filter-chip ${value === o.key ? 'active' : ''}`}
                      onClick={() => onPick(value === o.key ? undefined : o.key)}>
                  {labelOf(o, zh)}
                </span>
              ))}
            </div>
          </details>
        );
      })}
    </>
  );
}

/** 组织组：GET /organizations 匿名列表 + repoCounts[typeKey] 计数 + 每页 6 小分页（09 §5.2）。 */
function OrgGroup({ typeKey, value, onPick }: {
  typeKey: string; value?: string; onPick: (slug?: string) => void;
}) {
  const { t } = useTranslation();
  const [orgs, setOrgs] = useState<OrganizationListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const pageSize = 6;

  useEffect(() => {
    api.listOrganizations(page, pageSize)
      .then((data) => { setOrgs(data.items); setTotal(data.total); })
      .catch(() => {});
  }, [page]);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const unitKey = typeKey === 'dataset' ? 'filter.unitDataset' : typeKey === 'studio' ? 'filter.unitStudio' : 'filter.unitModel';
  return (
    <>
      {orgs.length === 0 ? (
        <div className="filter-empty">{t('filter.noOrg')}</div>
      ) : orgs.map((o) => (
        <div key={o.id}
             className={`filter-org-item ${value === o.slug ? 'active' : ''}`}
             onClick={() => onPick(value === o.slug ? undefined : o.slug)}>
          <span className="filter-org-name">{o.name}</span>
          <span className="filter-org-count">
            {t('filter.orgCount', { n: o.repoCounts?.[typeKey] ?? 0, unit: t(unitKey) })}
          </span>
        </div>
      ))}
      {totalPages > 1 && (
        <div className="filter-mini-pager">
          <button className="btn btn-ghost btn-sm" disabled={page <= 1} onClick={() => setPage(page - 1)}>‹</button>
          <span>{page}/{totalPages}</span>
          <button className="btn btn-ghost btn-sm" disabled={page >= totalPages} onClick={() => setPage(page + 1)}>›</button>
        </div>
      )}
    </>
  );
}

export default function FilterSidebar({ typeKey, facets, taxonomies, filters, onChange }: {
  typeKey: string;
  facets: FacetDef[];
  taxonomies: Record<string, TaxonomyOption[]>;
  filters: Filters;
  onChange: (patch: Partial<Filters>) => void;
}) {
  const { t, i18n } = useTranslation();
  const zh = i18n.language.startsWith('zh');
  const filterable = (facets ?? []).filter((f) => f.filterable !== false);
  const taskFacet = filterable.find((f) => f.key === 'task' && f.taxonomy && taxonomies[f.taxonomy]);
  const boolFacets = filterable.filter((f) => f.dataType === 'boolean' && BOOL_KEY[f.key]);
  const sceneFacet = filterable.find((f) => f.key === 'scene' && f.taxonomy && taxonomies[f.taxonomy]);
  // 普通 taxonomy 单选组（排除 task/scene/capability，capability 在工具栏做多选下拉）
  const singleGroups = filterable.filter((f) =>
    f.dataType !== 'boolean' && f.key !== 'task' && f.key !== 'scene' && f.key !== 'capability'
    && f.taxonomy && taxonomies[f.taxonomy]);
  const showOrg = typeKey === 'model' || typeKey === 'dataset';
  const [sceneExpanded, setSceneExpanded] = useState(false);

  return (
    <aside className="filter-sidebar">
      {taskFacet && (
        <CollapsibleGroup title={t('filter.groupTask')}>
          <TaskTreeGroup options={taxonomies[taskFacet.taxonomy!]}
                         value={filters.task}
                         onPick={(v) => onChange({ task: v })} />
        </CollapsibleGroup>
      )}

      {singleGroups.map((f) => (
        <CollapsibleGroup key={f.key} title={GROUP_KEY[f.key] ? t(GROUP_KEY[f.key]) : f.key}>
          <div className="filter-opt-list">
            {activeOnly(taxonomies[f.taxonomy!]).map((o) => (
              <div key={o.key}
                   className={`filter-opt ${(filters as Record<string, unknown>)[f.key] === o.key ? 'active' : ''}`}
                   onClick={() => {
                     const cur = (filters as Record<string, unknown>)[f.key];
                     onChange({ [f.key]: cur === o.key ? undefined : o.key } as Partial<Filters>);
                   }}>
                {labelOf(o, zh)}
              </div>
            ))}
          </div>
        </CollapsibleGroup>
      ))}

      {sceneFacet && (
        <CollapsibleGroup title={t('filter.groupScene')}>
          <div className="chip-wrap">
            {activeOnly(taxonomies[sceneFacet.taxonomy!])
              .slice(0, sceneExpanded ? undefined : 4)
              .map((o) => (
                <span key={o.key}
                      className={`filter-chip ${filters.scene === o.key ? 'active' : ''}`}
                      onClick={() => onChange({ scene: filters.scene === o.key ? undefined : o.key })}>
                  {labelOf(o, zh)}
                </span>
              ))}
          </div>
          {activeOnly(taxonomies[sceneFacet.taxonomy!]).length > 4 && (
            <a className="filter-more" onClick={() => setSceneExpanded(!sceneExpanded)}>
              {sceneExpanded ? t('filter.collapse') : t('filter.expandMore', { n: activeOnly(taxonomies[sceneFacet.taxonomy!]).length - 4 })}
            </a>
          )}
        </CollapsibleGroup>
      )}

      {boolFacets.length > 0 && (
        <CollapsibleGroup title={t('filter.capGroup')}>
          {boolFacets.map((f) => {
            const param = BOOL_PARAM[f.key];
            const checked = param ? !!filters[param] : false;
            return (
              <label key={f.key} className="filter-check">
                <input type="checkbox" checked={checked}
                       onChange={() => onChange({ [param]: !checked } as Partial<Filters>)} />
                {BOOL_KEY[f.key] ? t(BOOL_KEY[f.key]) : f.key}
              </label>
            );
          })}
        </CollapsibleGroup>
      )}

      {typeKey === 'dataset' && (
        <CollapsibleGroup title={t('filter.otherGroup')}>
          <label className="filter-check">
            <input type="checkbox" checked={!!filters.gated}
                   onChange={() => onChange({ gated: !filters.gated })} />
            {t('filter.gatedOnly')}
          </label>
        </CollapsibleGroup>
      )}

      {showOrg && (
        <CollapsibleGroup title={t('filter.orgGroup')}>
          <OrgGroup typeKey={typeKey} value={filters.org} onPick={(slug) => onChange({ org: slug })} />
        </CollapsibleGroup>
      )}
    </aside>
  );
}
