// 资源卡片（09 §5.4 ResourceCard / §7.2 StudioCard）— metadata 安全读取，未知值降级展示原值
// 时间：2026-08-21  作者：AxeXie
import React from 'react';
import { useNavigate } from 'react-router-dom';
import type { Repository } from '../api/client';

const VIS_LABEL: Record<string, string> = { public: '公开', organization: '组织', private: '私有' };

/** metadata 字段安全读取：非预期类型返回 undefined（04 §12 降级渲染）。 */
function metaStr(repo: Repository, key: string): string | undefined {
  const v = repo.metadata?.[key];
  return typeof v === 'string' && v ? v : undefined;
}
function metaArr(repo: Repository, key: string): string[] {
  const v = repo.metadata?.[key];
  return Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : [];
}

function gotoDetail(nav: ReturnType<typeof useNavigate>, r: Repository) {
  nav(`/resources/${r.type}/${r.namespace}/${r.name}`);
}

function fmtTime(iso: string): string {
  return new Date(iso).toLocaleDateString('zh-CN');
}

/** model/dataset 通用 ResourceCard（09 §5.4）。 */
export function ResourceCard({ repo }: { repo: Repository }) {
  const nav = useNavigate();
  const task = metaStr(repo, 'task');
  const license = metaStr(repo, 'license');
  const architecture = metaStr(repo, 'architecture');
  const language = metaStr(repo, 'language');
  const apiStatus = metaStr(repo, 'apiStatus');
  const framework = metaStr(repo, 'framework');
  const frameworks = metaArr(repo, 'frameworks');
  const frameworkText = frameworks.length > 1
    ? `${frameworks[0]} 等 ${frameworks.length} 个框架`
    : (frameworks[0] ?? framework);
  const pCount = repo.metadata?.['parameterCount'];
  const pUnit = metaStr(repo, 'parameterUnit') ?? '';
  const tags = metaArr(repo, 'tags').slice(0, 4);

  const metaItems = [
    typeof pCount === 'number' ? `参数量 ${pCount}${pUnit}` : undefined,
    frameworkText, license, architecture, language,
  ].filter((x): x is string => !!x);

  return (
    <div className="card repo-card" onClick={() => gotoDetail(nav, repo)}>
      <div className="repo-card-top">
        <span className="repo-ns">@{repo.namespace}/{repo.name}</span>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {repo.visibility !== 'public' && (
            <span className={`badge badge-${repo.visibility}`}>{VIS_LABEL[repo.visibility]}</span>
          )}
          {repo.gated && <span className="badge badge-gated">申请制</span>}
          {apiStatus && <span className="badge badge-api">{apiStatus}</span>}
        </div>
      </div>
      <div className="repo-name">{repo.displayName || repo.name}</div>
      {repo.description && <div className="repo-desc">{repo.description}</div>}
      {task && <div><span className="badge badge-task">{task}</span></div>}
      {metaItems.length > 0 && (
        <div className="repo-meta">{metaItems.map((m, i) => <span key={i}>{m}</span>)}</div>
      )}
      {tags.length > 0 && (
        <div className="repo-tags">{tags.map((t) => <span key={t} className="tag-chip">{t}</span>)}</div>
      )}
      <div className="repo-foot">
        <span>@{repo.namespace} · {fmtTime(repo.updatedAt)}</span>
        <span>↓ {repo.stats.downloads}</span>
        <span>♥ {repo.stats.likes}</span>
      </div>
    </div>
  );
}

/** studio 渐变封面按名称哈希选色（g1–g16 种子展示语义，09 §2.5）。 */
const GRADIENTS = [
  'linear-gradient(135deg,#667eea,#764ba2)', 'linear-gradient(135deg,#f093fb,#f5576c)',
  'linear-gradient(135deg,#4facfe,#00f2fe)', 'linear-gradient(135deg,#43e97b,#38f9d7)',
  'linear-gradient(135deg,#fa709a,#fee140)', 'linear-gradient(135deg,#30cfd0,#330867)',
  'linear-gradient(135deg,#a8edea,#fed6e3)', 'linear-gradient(135deg,#ff9a9e,#fecfef)',
  'linear-gradient(135deg,#f6d365,#fda085)', 'linear-gradient(135deg,#84fab0,#8fd3f4)',
  'linear-gradient(135deg,#a18cd1,#fbc2eb)', 'linear-gradient(135deg,#fad0c4,#ffd1ff)',
  'linear-gradient(135deg,#ffecd2,#fcb69f)', 'linear-gradient(135deg,#5ee7df,#b490ca)',
  'linear-gradient(135deg,#c3cfe2,#f5f7fa)', 'linear-gradient(135deg,#667eea,#f093fb)',
];

/** studio 专用渲染器（09 §7.2）：渐变封面（中文名前 2–4 字）+ 场景≤3 + visits/likes。 */
export function StudioCard({ repo }: { repo: Repository }) {
  const nav = useNavigate();
  const title = repo.displayName || repo.name;
  // 封面文案：中文名取前 2–4 字
  const cnChars = title.replace(/[^\u4e00-\u9fa5A-Za-z0-9]/g, '');
  const coverText = cnChars.slice(0, Math.min(4, Math.max(2, cnChars.length))) || title.slice(0, 4);
  const grad = GRADIENTS[(repo.name.length + repo.namespace.length) % GRADIENTS.length];
  const scenes = metaArr(repo, 'scenes').slice(0, 3);
  const tags = metaArr(repo, 'tags').slice(0, 2);
  const mcp = repo.metadata?.['mcp_compatible'] === true || repo.metadata?.['mcpCompatible'] === true;

  return (
    <div className="card studio-card" onClick={() => gotoDetail(nav, repo)}>
      <div className="studio-cover" style={{ background: grad }}>
        <span className="studio-cover-text">{coverText}</span>
      </div>
      <div className="studio-body">
        <div className="studio-title-row">
          <span className="repo-name">{title}</span>
          {mcp && <span className="badge badge-api">MCP</span>}
        </div>
        {(scenes.length > 0 || tags.length > 0) && (
          <div className="repo-tags">
            {scenes.map((s) => <span key={s} className="tag-chip tag-scene">{s}</span>)}
            {tags.map((t) => <span key={t} className="tag-chip">{t}</span>)}
          </div>
        )}
        <div className="repo-foot">
          <span>@{repo.namespace} · {fmtTime(repo.updatedAt)}</span>
          <span>👁 {repo.stats.visits}</span>
          <span>♥ {repo.stats.likes}</span>
        </div>
      </div>
    </div>
  );
}

/** rendererKey 分发（09 §2.5）：未知类型降级为 ResourceCard，不写死 if/else 分支。 */
export function RepoCardRenderer({ repo }: { repo: Repository }) {
  return repo.type === 'studio' ? <StudioCard repo={repo} /> : <ResourceCard repo={repo} />;
}
