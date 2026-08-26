// 仓库创建/编辑弹窗（09 §5.5 字段映射）— 字段由 ResourceTypeSchema 驱动，禁止硬编码枚举
// 时间：2026-08-21  作者：AxeXie
import React, { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  api,
  type Repository, type ResourceTypeSchema, type TaxonomyOption, type OrganizationListItem,
} from '../api/client';
import { errMsg } from '../App';
import { useAuth } from '../context/AuthContext';

interface FormMeta {
  [key: string]: string | number | boolean | string[];
}

const TYPE_KEY: Record<string, string> = { model: 'repoForm.typeModel', dataset: 'repoForm.typeDataset', studio: 'repoForm.typeStudio' };
const FIELD_KEY: Record<string, string> = {
  task: 'repoForm.fieldTask', framework: 'repoForm.fieldFramework', license: 'repoForm.fieldLicense', architecture: 'repoForm.fieldArchitecture',
  language: 'repoForm.fieldLanguage', tags: 'repoForm.fieldTags', capabilities: 'repoForm.fieldCapabilities', apiStatus: 'repoForm.fieldApiStatus',
  parameterCount: 'repoForm.fieldParameterCount', parameterUnit: 'repoForm.fieldParameterUnit', deployable: 'repoForm.fieldDeployable',
  mcpCompatible: 'repoForm.fieldMcpCompatible', estimatedRows: 'repoForm.fieldEstimatedRows', dataFormats: 'repoForm.fieldDataFormats',
  sensitivityLevel: 'repoForm.fieldSensitivityLevel', previewPolicy: 'repoForm.fieldPreviewPolicy', scenes: 'repoForm.fieldScenes',
  runtimeType: 'repoForm.fieldRuntimeType',
};
const VIS_KEY: Record<string, string> = { public: 'repoForm.visPublic', organization: 'repoForm.visOrg', private: 'repoForm.visPrivate' };

/** 仅启用选项可选（字典统一：禁用项不出现在创建/编辑表单，与筛选面一致）。 */
const activeOnly = (opts: TaxonomyOption[]) => opts.filter((o) => o.status === 'active');

/** 按当前语言择显：中文优先 displayName，英文优先 displayNameEn（缺省回退）。 */
function labelOf(o: TaxonomyOption, zh: boolean): string {
  if (zh) return o.displayName || o.displayNameEn || o.key;
  return o.displayNameEn || o.displayName || o.key;
}

interface MetaProp { type?: string; }

export default function RepoFormModal({ typeKey, initial, onClose, onSaved }: {
  typeKey: string;
  /** 编辑模式：传入现有仓库（PATCH+If-Match，name 禁用）。 */
  initial?: Repository | null;
  onClose: () => void;
  onSaved: (repo: Repository, created: boolean) => void;
}) {
  const { user } = useAuth();
  const { t, i18n } = useTranslation();
  const zh = i18n.language.startsWith('zh');
  const editing = !!initial;
  const [schema, setSchema] = useState<ResourceTypeSchema | null>(null);
  const [taxonomies, setTaxonomies] = useState<Record<string, TaxonomyOption[]>>({});
  const [orgs, setOrgs] = useState<OrganizationListItem[]>([]);

  const [namespaceId, setNamespaceId] = useState('');
  const [name, setName] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [description, setDescription] = useState('');
  const [visibility, setVisibility] = useState<'public' | 'organization' | 'private'>('public');
  const [gated, setGated] = useState(false);
  const [tagsText, setTagsText] = useState('');
  const [meta, setMeta] = useState<FormMeta>({});
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  // 加载 schema + taxonomies + 组织（数据驱动：选项全部来自服务端）
  useEffect(() => {
    api.resourceTypeSchema(typeKey).then(setSchema).catch((e) => setErr(errMsg(e)));
    api.metadataOptions().then((o) => setTaxonomies(o.taxonomies)).catch(() => {});
    api.listOrganizations(1, 100).then((p) => setOrgs(p.items)).catch(() => {});
  }, [typeKey]);

  // 编辑回填 / 创建默认值
  useEffect(() => {
    if (!schema) return;
    if (initial) {
      setNamespaceId(initial.namespace); // 仅展示，编辑不可改命名空间
      setName(initial.name);
      setDisplayName(initial.displayName ?? '');
      setDescription(initial.description ?? '');
      setVisibility(initial.visibility);
      setGated(initial.gated);
      const m = (initial.metadata ?? {}) as FormMeta;
      setMeta({ ...m });
      const t = m['tags'];
      setTagsText(Array.isArray(t) ? t.join(', ') : '');
    } else {
      setNamespaceId(user?.namespaceId ?? '');
    }
  }, [schema, initial, user]);

  const properties = useMemo(() => {
    const ms = schema?.metadataSchema as { properties?: Record<string, MetaProp>; required?: string[] } | undefined;
    return ms?.properties ?? {};
  }, [schema]);
  const requiredKeys = useMemo(() => {
    const ms = schema?.metadataSchema as { required?: string[] } | undefined;
    return ms?.required ?? [];
  }, [schema]);

  // metadata key → taxonomy（来自 facets 定义）
  const taxonomyOf = useMemo(() => {
    const map: Record<string, string> = {};
    for (const f of schema?.facets ?? []) {
      const facet = f as { key?: string; taxonomy?: string | null };
      if (facet.key && facet.taxonomy) {
        // facet key 与 metadata key 的差异映射（tag→tags、scene→scenes、capability→capabilities）
        const metaKey = facet.key === 'tag' ? 'tags' : facet.key === 'scene' ? 'scenes'
          : facet.key === 'capability' ? 'capabilities' : facet.key;
        map[metaKey] = facet.taxonomy;
      }
    }
    return map;
  }, [schema]);

  function setField(key: string, v: string | number | boolean | string[]) {
    setMeta((prev) => ({ ...prev, [key]: v }));
  }

  function toggleArr(key: string, v: string) {
    setMeta((prev) => {
      const cur = Array.isArray(prev[key]) ? (prev[key] as string[]) : [];
      const next = cur.includes(v) ? cur.filter((x) => x !== v) : [...cur, v];
      return { ...prev, [key]: next };
    });
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErr(''); setBusy(true);
    try {
      // 组装 metadata：仅含 schema 声明字段（additionalProperties=false），空值剔除
      const metadata: Record<string, unknown> = {};
      const tags = tagsText.split(/[,，]/).map((s) => s.trim()).filter(Boolean);
      if (properties['tags'] && tags.length > 0) metadata['tags'] = tags;
      for (const [key, prop] of Object.entries(properties)) {
        if (key === 'tags' || key === 'coverObjectKey') continue;
        const v = meta[key];
        if (v === undefined || v === '' || (Array.isArray(v) && v.length === 0)) continue;
        metadata[key] = v;
      }
      const schemaVersion = schema?.version ?? 1;
      if (editing && initial) {
        const saved = await api.patchRepo(initial.id, {
          displayName, description, visibility, gated,
          metadata, metadataSchemaVersion: schemaVersion,
        }, initial.etag ?? `W/"${initial.version}"`);
        onSaved(saved, false);
      } else {
        const saved = await api.createRepo({
          namespaceId, name: name.trim(), type: typeKey,
          displayName: displayName.trim() || undefined,
          description: description.trim() || undefined,
          visibility, gated,
          metadata, metadataSchemaVersion: schemaVersion,
        });
        onSaved(saved, true);
      }
    } catch (ex) {
      setErr(errMsg(ex));
      setBusy(false);
    }
  }

  // ---------- metadata 字段渲染（schema 驱动） ----------

  function renderMetaField(key: string, prop: MetaProp) {
    const req = requiredKeys.includes(key);
    const label = FIELD_KEY[key] ? t(FIELD_KEY[key]) : key;
    const taxName = taxonomyOf[key];
    const options = taxName && taxonomies[taxName] ? activeOnly(taxonomies[taxName]) : undefined;

    // 分层 taxonomy（任务树）→ optgroup 下拉
    if (prop.type === 'string' && options && options.some((o) => o.parentKey)) {
      const roots = options.filter((o) => !o.parentKey);
      const val = typeof meta[key] === 'string' ? (meta[key] as string) : '';
      return (
        <div className="field" key={key}>
          <label>{label}{req && ' *'}</label>
          <select value={val} required={req}
                  onChange={(e) => setField(key, e.target.value)}>
            <option value="">{t('repoForm.selectPlease')}</option>
            {roots.map((r) => {
              const kids = options.filter((o) => o.parentKey === r.key);
              return kids.length > 0 ? (
                <optgroup key={r.key} label={labelOf(r, zh)}>
                  {kids.map((k) => <option key={k.key} value={k.key}>{labelOf(k, zh)}</option>)}
                </optgroup>
              ) : (
                <option key={r.key} value={r.key}>{labelOf(r, zh)}</option>
              );
            })}
          </select>
        </div>
      );
    }

    // 单选 taxonomy → 下拉
    if (prop.type === 'string' && options) {
      const val = typeof meta[key] === 'string' ? (meta[key] as string) : '';
      return (
        <div className="field" key={key}>
          <label>{label}{req && ' *'}</label>
          <select value={val} required={req}
                  onChange={(e) => setField(key, e.target.value)}>
            <option value="">{t('repoForm.selectPlease')}</option>
            {options.map((o) => <option key={o.key} value={o.key}>{labelOf(o, zh)}</option>)}
          </select>
        </div>
      );
    }

    // 多选（数组 + taxonomy）→ checkbox 组
    if (prop.type === 'array' && options) {
      const val = Array.isArray(meta[key]) ? (meta[key] as string[]) : [];
      return (
        <div className="field" key={key}>
          <label>{label}</label>
          <div className="chip-wrap">
            {options.map((o) => (
              <span key={o.key}
                    className={`filter-chip ${val.includes(o.key) ? 'active' : ''}`}
                    onClick={() => toggleArr(key, o.key)}>
                {labelOf(o, zh)}
              </span>
            ))}
          </div>
        </div>
      );
    }

    // 数组无 taxonomy（dataFormats）→ 逗号分隔输入
    if (prop.type === 'array') {
      const val = Array.isArray(meta[key]) ? (meta[key] as string[]).join(', ') : '';
      return (
        <div className="field" key={key}>
          <label>{label}</label>
          <input value={val} placeholder={t('repoForm.arrPlaceholder')}
                 onChange={(e) => setField(key, e.target.value.split(/[,，]/).map((s) => s.trim()).filter(Boolean))} />
        </div>
      );
    }

    // 布尔 → 复选
    if (prop.type === 'boolean') {
      return (
        <div className="field" key={key}>
          <label className="filter-check">
            <input type="checkbox" checked={!!meta[key]}
                   onChange={(e) => setField(key, e.target.checked)} />
            {label}
          </label>
        </div>
      );
    }

    // 整数 → 数字输入（参数量带单位）
    if (prop.type === 'integer') {
      return (
        <div className="field" key={key}>
          <label>{label}</label>
          <div style={{ display: 'flex', gap: 8 }}>
            <input type="number" min={0} style={{ flex: 1 }}
                   value={typeof meta[key] === 'number' ? (meta[key] as number) : ''}
                   onChange={(e) => setField(key, e.target.value === '' ? '' : Number(e.target.value))} />
            {key === 'parameterCount' && (
              <select style={{ width: 110 }}
                      value={typeof meta['parameterUnit'] === 'string' ? (meta['parameterUnit'] as string) : ''}
                      onChange={(e) => setField('parameterUnit', e.target.value)}>
                <option value="">{t('repoForm.unitPlaceholder')}</option>
                <option value="B">B</option>
                <option value="M">M</option>
                <option value="B*">B*</option>
              </select>
            )}
          </div>
        </div>
      );
    }

    // 其他字符串 → 文本输入
    return (
      <div className="field" key={key}>
        <label>{label}{req && ' *'}</label>
        <input value={typeof meta[key] === 'string' ? (meta[key] as string) : ''} required={req}
               onChange={(e) => setField(key, e.target.value)} />
      </div>
    );
  }

  const metaKeys = Object.keys(properties).filter((k) => k !== 'tags' && k !== 'coverObjectKey' && k !== 'parameterUnit');

  return (
    <div className="modal-mask" onClick={onClose}>
      <div className="modal" style={{ maxWidth: 640 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <span className="modal-title">
            {editing
              ? t('repoForm.editTitle', { unit: TYPE_KEY[typeKey] ? t(TYPE_KEY[typeKey]) : typeKey })
              : t('repoForm.publishTitle', { unit: TYPE_KEY[typeKey] ? t(TYPE_KEY[typeKey]) : typeKey })}
          </span>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>✕</button>
        </div>
        <form onSubmit={submit}>
          <div className="modal-body form">
            {err && <div className="form-error">{err}</div>}

            <div style={{ display: 'flex', gap: 12 }}>
              <div className="field" style={{ flex: 1 }}>
                <label>{t('repoForm.nsLabel')}</label>
                {editing ? (
                  <input value={initial!.namespace} disabled />
                ) : (
                  <select value={namespaceId} required onChange={(e) => setNamespaceId(e.target.value)}>
                    <option value="">{t('repoForm.selectPlease')}</option>
                    {user && <option value={user.namespaceId}>@{user.username}{t('repoForm.nsPersonal')}</option>}
                    {orgs.map((o) => <option key={o.id} value={o.namespaceId}>@{o.slug}{t('repoForm.nsOrg', { name: o.name })}</option>)}
                  </select>
                )}
              </div>
              <div className="field" style={{ flex: 1 }}>
                <label>{t('repoForm.nameLabel')}</label>
                <input value={name} disabled={editing} required
                       pattern="[a-z0-9][a-z0-9_\-\.]{0,63}"
                       title={t('repoForm.nameTitle')}
                       placeholder={t('repoForm.namePlaceholder')}
                       onChange={(e) => setName(e.target.value)} />
              </div>
            </div>

            <div className="field">
              <label>{t('repoForm.displayLabel')}</label>
              <input value={displayName} maxLength={128}
                     onChange={(e) => setDisplayName(e.target.value)} />
            </div>

            <div className="field">
              <label>{t('repoForm.descLabel')}</label>
              <textarea className="reason-input" value={description} maxLength={4000}
                        onChange={(e) => setDescription(e.target.value)} />
            </div>

            <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap' }}>
              <div className="field">
                <label>{t('repoForm.visLabel')}</label>
                <div style={{ display: 'flex', gap: 14 }}>
                  {(['public', 'organization', 'private'] as const).map((v) => (
                    <label key={v} className="filter-check">
                      <input type="radio" name="visibility" checked={visibility === v}
                             onChange={() => setVisibility(v)} />
                      {t(VIS_KEY[v])}
                    </label>
                  ))}
                </div>
              </div>
              {typeKey !== 'studio' && (
                <div className="field">
                  <label>&nbsp;</label>
                  <label className="filter-check">
                    <input type="checkbox" checked={gated} onChange={(e) => setGated(e.target.checked)} />
                    {t('repoForm.gatedLabel')}
                  </label>
                </div>
              )}
            </div>

            {properties['tags'] && (
              <div className="field">
                <label>{t('repoForm.tagsLabel')}</label>
                <input value={tagsText} placeholder={t('repoForm.tagsPlaceholder')}
                       onChange={(e) => setTagsText(e.target.value)} />
              </div>
            )}

            {metaKeys.map((k) => renderMetaField(k, properties[k]))}
          </div>
          <div className="modal-foot">
            <button type="button" className="btn btn-ghost" onClick={onClose}>{t('repoForm.cancel')}</button>
            <button type="submit" className="btn btn-primary" disabled={busy}>
              {busy ? t('repoForm.submitting') : (editing ? t('repoForm.saveBtn') : t('repoForm.createBtn'))}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
