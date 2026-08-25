// 个人中心（09 §9）— 左栏 6 子页：概览/我的模型/数据集/工作空间/收藏/点赞
// 时间：2026-08-21  作者：AxeXie
import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, type Page, type Repository } from '../api/client';
import { errMsg } from '../App';
import { useAuth } from '../context/AuthContext';
import { Pagination, ConfirmDialog, DataState, useToast } from '../components/ui';
import RepoFormModal from '../components/RepoFormModal';

const TYPE_LABEL: Record<string, string> = { model: '模型', dataset: '数据集', studio: '工作空间' };

type SubPage = 'overview' | 'model' | 'dataset' | 'studio' | 'favorites' | 'likes';

const MENU: Array<{ key: SubPage; label: string; icon: string }> = [
  { key: 'overview', label: '概览', icon: '📊' },
  { key: 'model', label: '我的模型', icon: '🧠' },
  { key: 'dataset', label: '我的数据集', icon: '🗃️' },
  { key: 'studio', label: '我的工作空间', icon: '🎨' },
  { key: 'favorites', label: '我的收藏', icon: '★' },
  { key: 'likes', label: '我的点赞', icon: '♥' },
];

export default function MyPage() {
  const nav = useNavigate();
  const { user, loading, openLogin } = useAuth();
  const [sub, setSub] = useState<SubPage>('overview');

  // 未登录守卫：跳首页并弹 AuthModal（09 §9.1）
  useEffect(() => {
    if (!loading && !user) {
      openLogin(() => nav('/my'));
      nav('/');
    }
  }, [loading, user, nav, openLogin]);

  if (loading) return <div className="page-loading">加载中…</div>;
  if (!user) return null;

  return (
    <div className="my-layout">
      <aside className="my-side card">
        <div className="my-profile">
          <span className="avatar me-avatar">{(user.nickname || user.username).slice(0, 1).toUpperCase()}</span>
          <div>
            <div className="me-name">{user.nickname || user.username}</div>
            <div className="me-id">@{user.username}</div>
          </div>
        </div>
        <nav className="my-menu">
          {MENU.map((m) => (
            <div key={m.key} className={`my-menu-item ${sub === m.key ? 'active' : ''}`}
                 onClick={() => setSub(m.key)}>
              <span>{m.icon}</span>{m.label}
            </div>
          ))}
        </nav>
      </aside>
      <div className="my-main">
        {sub === 'overview' ? <Overview onGoto={setSub} /> : <RepoSubPage sub={sub} />}
      </div>
    </div>
  );
}

/** 概览：5 统计卡（tab+type pageSize=1 取 total 组装）+ 最近更新 6 条（09 §9.2）。 */
function Overview({ onGoto }: { onGoto: (s: SubPage) => void }) {
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [recent, setRecent] = useState<Repository[]>([]);
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading');

  useEffect(() => {
    (async () => {
      try {
        const entries: Array<[string, number]> = [];
        for (const t of ['model', 'dataset', 'studio']) {
          const d = await api.meRepositories('created', 1, 1, t);
          entries.push([t, d.total]);
        }
        const fav = await api.meRepositories('favorites', 1, 1);
        entries.push(['favorites', fav.total]);
        const likes = await api.meRepositories('likes', 1, 1);
        entries.push(['likes', likes.total]);
        setCounts(Object.fromEntries(entries));
        const r = await api.meRepositories('created', 1, 6);
        setRecent(r.items);
        setState('ready');
      } catch {
        setState('error');
      }
    })();
  }, []);

  const cards: Array<{ key: SubPage; label: string; count: number }> = [
    { key: 'model', label: '我的模型', count: counts['model'] ?? 0 },
    { key: 'dataset', label: '我的数据集', count: counts['dataset'] ?? 0 },
    { key: 'studio', label: '我的工作空间', count: counts['studio'] ?? 0 },
    { key: 'favorites', label: '我的收藏', count: counts['favorites'] ?? 0 },
    { key: 'likes', label: '我的点赞', count: counts['likes'] ?? 0 },
  ];

  return (
    <DataState state={state === 'error' ? 'error' : state === 'loading' ? 'loading' : 'ready'}>
      <div className="stat-cards">
        {cards.map((c) => (
          <div key={c.key} className="card card-pad stat-card" onClick={() => onGoto(c.key)}>
            <div className="stat-card-num">{c.count}</div>
            <div className="stat-card-label">{c.label}</div>
          </div>
        ))}
      </div>
      <div className="card card-pad" style={{ marginTop: 16 }}>
        <h3 style={{ marginTop: 0 }}>最近更新</h3>
        {recent.length === 0 ? (
          <div className="empty-state" style={{ padding: 24 }}>还没有创建过仓库</div>
        ) : (
          <table className="repo-table">
            <thead>
              <tr><th>仓库</th><th>类型</th><th>下载/访问</th><th>点赞</th><th>更新时间</th></tr>
            </thead>
            <tbody>
              {recent.map((r) => <RepoRow key={r.id} repo={r} />)}
            </tbody>
          </table>
        )}
      </div>
    </DataState>
  );
}

function RepoRow({ repo }: { repo: Repository }) {
  const nav = useNavigate();
  return (
    <tr className="repo-row" onClick={() => nav(`/resources/${repo.type}/${repo.namespace}/${repo.name}`)}>
      <td>
        <b>{repo.displayName || repo.name}</b>
        <span className="repo-ns" style={{ marginLeft: 8 }}>@{repo.namespace}/{repo.name}</span>
      </td>
      <td>{TYPE_LABEL[repo.type] ?? repo.type}</td>
      <td>{repo.type === 'studio' ? repo.stats.visits : repo.stats.downloads}</td>
      <td>{repo.stats.likes}</td>
      <td className="req-meta">{new Date(repo.updatedAt).toLocaleDateString('zh-CN')}</td>
    </tr>
  );
}

/** 仓库子页：表格 10 条/页 + 创建 + 编辑（PATCH+If-Match）+ 删除（ConfirmDialog）。 */
function RepoSubPage({ sub }: { sub: SubPage }) {
  const toast = useToast();
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Page<Repository> | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'empty' | 'error'>('loading');
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Repository | null>(null);
  const [deleting, setDeleting] = useState<Repository | null>(null);
  const [delBusy, setDelBusy] = useState(false);
  const pageSize = 10;

  const tab = sub === 'favorites' ? 'favorites' : sub === 'likes' ? 'likes' : 'created';
  const type = sub === 'model' || sub === 'dataset' || sub === 'studio' ? sub : undefined;

  const load = useCallback(() => {
    setState('loading');
    api.meRepositories(tab, page, pageSize, type)
      .then((d) => { setData(d); setState(d.items.length === 0 ? 'empty' : 'ready'); })
      .catch(() => setState('error'));
  }, [tab, type, page]);

  useEffect(() => { setPage(1); }, [tab, type]);
  useEffect(() => { load(); }, [load]);

  async function confirmDelete() {
    if (!deleting) return;
    setDelBusy(true);
    try {
      await api.deleteRepo(deleting.id, deleting.etag ?? `W/"${deleting.version}"`);
      toast.show('删除请求已受理，正在异步清理');
      setDeleting(null);
      load();
    } catch (e) {
      toast.show(errMsg(e), 'err');
    } finally {
      setDelBusy(false);
    }
  }

  const createType = type ?? 'model';
  const title = MENU.find((m) => m.key === sub)?.label ?? '';

  return (
    <>
      <div className="dir-head">
        <span className="dir-title">{title}</span>
        {tab === 'created' && (
          <button className="btn btn-primary btn-sm" onClick={() => setFormOpen(true)}>
            + 创建{TYPE_LABEL[createType]}
          </button>
        )}
      </div>
      <div className="card card-pad">
        <DataState state={state} onRetry={load}>
          <table className="repo-table">
            <thead>
              <tr>
                <th>仓库</th><th>类型</th><th>可见性</th><th>下载/访问</th><th>点赞</th><th>更新时间</th>
                {tab === 'created' && <th style={{ width: 140 }}>操作</th>}
              </tr>
            </thead>
            <tbody>
              {(data?.items ?? []).map((r) => (
                <EditableRow key={r.id} repo={r} tab={tab}
                             onEdit={() => setEditing(r)} onDelete={() => setDeleting(r)} />
              ))}
            </tbody>
          </table>
          {data && (
            <Pagination page={page} pageSize={pageSize} total={data.total} onChange={setPage} />
          )}
        </DataState>
      </div>

      {formOpen && (
        <RepoFormModal typeKey={createType} onClose={() => setFormOpen(false)}
                         onSaved={(saved, created) => {
                           setFormOpen(false);
                           toast.show(created
                             ? (saved.lifecycleStatus === 'provisioning' ? '创建受理，资源准备中' : '创建成功')
                             : '保存成功');
                           load();
                         }} />
      )}
      {editing && (
        <RepoFormModal typeKey={editing.type} initial={editing}
                        onClose={() => setEditing(null)}
                        onSaved={() => { setEditing(null); toast.show('保存成功'); load(); }} />
      )}
      {deleting && (
        <ConfirmDialog title="删除仓库"
                        message={`确认删除 ${deleting.namespace}/${deleting.name}？删除为异步流程，进入清理状态后不可恢复。`}
                        confirmText="删除" busy={delBusy}
                        onConfirm={confirmDelete} onCancel={() => setDeleting(null)} />
      )}
    </>
  );
}

function EditableRow({ repo, tab, onEdit, onDelete }: {
  repo: Repository; tab: string; onEdit: () => void; onDelete: () => void;
}) {
  const nav = useNavigate();
  const goto = () => nav(`/resources/${repo.type}/${repo.namespace}/${repo.name}`);
  return (
    <tr>
      <td className="repo-row" onClick={goto}>
        <b>{repo.displayName || repo.name}</b>
        <span className="repo-ns" style={{ marginLeft: 8 }}>@{repo.namespace}/{repo.name}</span>
        {repo.lifecycleStatus === 'provisioning' && (
          <span className="badge badge-pending" style={{ marginLeft: 6 }}>资源准备中</span>
        )}
      </td>
      <td onClick={goto}>{TYPE_LABEL[repo.type] ?? repo.type}</td>
      <td onClick={goto}>
        <span className={`badge badge-${repo.visibility}`}>
          {repo.visibility === 'public' ? '公开' : repo.visibility === 'organization' ? '组织' : '私有'}
        </span>
        {repo.gated && <span className="badge badge-gated" style={{ marginLeft: 4 }}>申请制</span>}
      </td>
      <td className="req-meta" onClick={goto}>{repo.type === 'studio' ? repo.stats.visits : repo.stats.downloads}</td>
      <td className="req-meta" onClick={goto}>{repo.stats.likes}</td>
      <td className="req-meta" onClick={goto}>{new Date(repo.updatedAt).toLocaleDateString('zh-CN')}</td>
      {tab === 'created' && (
        <td>
          <div className="req-actions">
            <button className="btn btn-ghost btn-sm" onClick={onEdit}>编辑</button>
            <button className="btn btn-danger btn-sm" onClick={onDelete}>删除</button>
          </div>
        </td>
      )}
    </tr>
  );
}
