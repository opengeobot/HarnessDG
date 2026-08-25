// 资源详情页（09 §8）— resolve 路由 + 三 Tab（介绍/文件/交流反馈）+ 下载弹窗 + 右侧信息卡
// 时间：2026-08-21  作者：AxeXie
import React, { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, type AccessRequest, type Feedback, type FileNode, type Page, type Repository } from '../api/client';
import { errMsg } from '../App';
import { useAuth } from '../context/AuthContext';
import { useToast, DataState } from '../components/ui';
import DownloadModal from '../components/DownloadModal';

const VIS_LABEL: Record<string, string> = { public: '公开', organization: '组织', private: '私有' };
const TYPE_LABEL: Record<string, string> = { model: '模型市场', dataset: '数据市场', studio: '工作空间' };
const TYPE_PATH: Record<string, string> = { model: '/models', dataset: '/datasets', studio: '/studios' };
const META_LABEL: Record<string, string> = {
  task: '任务', framework: '框架', license: '开源协议', architecture: '结构', language: '语种',
  tags: '标签', capabilities: '能力', apiStatus: '推理 Api 状态', parameterCount: '参数量',
  parameterUnit: '参数单位', deployable: '支持部署', mcpCompatible: 'MCP 兼容',
  estimatedRows: '预估行数', dataFormats: '数据格式', sensitivityLevel: '敏感级别',
  previewPolicy: '预览策略', scenes: '场景', runtimeType: '运行时',
};

/** 客户端重算 ETag（与 shared ETags.ofVersion 同规则，04 §10）。 */
const etagOfVersion = (v: number) => '"' + (v + 0x5f000000).toString(16) + '"';

export default function DetailPage() {
  const { typeKey, namespace, name, id } = useParams();
  const nav = useNavigate();
  const { user, requireLogin } = useAuth();
  const toast = useToast();

  const [repo, setRepo] = useState<Repository | null>(null);
  const [err, setErr] = useState('');
  const [tab, setTab] = useState<'intro' | 'files' | 'feedback'>('intro');
  const [dlOpen, setDlOpen] = useState(false);
  const [liked, setLiked] = useState(false);
  const [favorited, setFavorited] = useState(false);
  const [related, setRelated] = useState<Page<Repository> | null>(null);

  // README 纯文本降级渲染（无 Markdown 库，受控 pre）
  const [readme, setReadme] = useState<string | null>(null);

  // 文件 Tab
  const [files, setFiles] = useState<FileNode[]>([]);
  const [filesErr, setFilesErr] = useState('');
  const [curPath, setCurPath] = useState('');

  // 反馈 Tab
  const [feedbacks, setFeedbacks] = useState<Feedback[]>([]);
  const [fbCursor, setFbCursor] = useState<string | null>(null);
  const [fbText, setFbText] = useState('');
  const [fbBusy, setFbBusy] = useState(false);

  const loadRepo = useCallback(async () => {
    setErr('');
    try {
      const r = typeKey && namespace && name
        ? await api.resolveRepo(typeKey, namespace, name)
        : await api.getRepository(id!);
      setRepo(r);
    } catch (e) {
      setErr(errMsg(e));
    }
  }, [typeKey, namespace, name, id]);

  useEffect(() => { loadRepo(); }, [loadRepo]);

  // related 相关工作空间
  useEffect(() => {
    if (!repo) return;
    api.relatedRepositories(repo.id).then(setRelated).catch(() => {});
  }, [repo?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  // 介绍 Tab：尝试读取 README.md（经下载会话预签名 URL fetch，失败降级）
  useEffect(() => {
    if (!repo || tab !== 'intro') return;
    let cancelled = false;
    (async () => {
      try {
        const list = await api.listFiles(repo.id, 'main');
        const md = list.items.find((f) => f.type === 'file' && f.name.toLowerCase() === 'readme.md');
        if (!md) { setReadme(null); return; }
        const session = await api.createDownloadSession(repo.id, md.id);
        const resp = await fetch(session.url);
        const text = await resp.text();
        if (!cancelled) setReadme(text);
      } catch {
        if (!cancelled) setReadme(null);
      }
    })();
    return () => { cancelled = true; };
  }, [repo?.id, tab]); // eslint-disable-line react-hooks/exhaustive-deps

  // 文件 Tab
  const loadFiles = useCallback(async () => {
    if (!repo) return;
    setFilesErr('');
    try {
      const page = await api.listFiles(repo.id, 'main', curPath || undefined);
      setFiles(page.items ?? []);
    } catch (e) {
      setFilesErr(errMsg(e));
      setFiles([]);
    }
  }, [repo?.id, curPath]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (tab === 'files' && repo) loadFiles(); }, [tab, repo?.id, loadFiles]); // eslint-disable-line react-hooks/exhaustive-deps

  // 反馈 Tab
  const loadFeedbacks = useCallback(async (cursor?: string) => {
    if (!repo) return;
    try {
      const d = await api.listFeedbacks(repo.id, cursor ?? undefined);
      setFeedbacks((prev) => cursor ? [...prev, ...d.items] : d.items);
      setFbCursor(d.nextCursor);
    } catch { /* 匿名可读，失败忽略 */ }
  }, [repo?.id]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (tab === 'feedback' && repo) loadFeedbacks(); }, [tab, repo?.id, loadFeedbacks]); // eslint-disable-line react-hooks/exhaustive-deps

  /** 本地乐观计数乐观更新（服务端 stats 异步重算，09 §8.2 按钮状态以响应为准）。 */
  function bumpStat(field: 'likes' | 'favorites', delta: number) {
    setRepo((prev) => prev ? { ...prev, stats: { ...prev.stats, [field]: prev.stats[field] + delta } } : prev);
  }

  async function doLike() {
    requireLogin(() => {
      (async () => {
        try {
          if (liked) { await api.unlike(repo!.id); setLiked(false); bumpStat('likes', -1); }
          else { await api.like(repo!.id); setLiked(true); bumpStat('likes', 1); }
          loadRepo();
        } catch (e) { toast.show(errMsg(e), 'err'); }
      })();
    });
  }

  async function doFavorite() {
    requireLogin(() => {
      (async () => {
        try {
          if (favorited) { await api.unfavorite(repo!.id); setFavorited(false); bumpStat('favorites', -1); }
          else { await api.favorite(repo!.id); setFavorited(true); bumpStat('favorites', 1); }
          loadRepo();
        } catch (e) { toast.show(errMsg(e), 'err'); }
      })();
    });
  }

  async function submitFeedback() {
    if (!fbText.trim()) return;
    setFbBusy(true);
    try {
      await api.createFeedback(repo!.id, fbText.trim());
      setFbText('');
      await loadFeedbacks();
      toast.show('反馈已发布');
    } catch (e) {
      toast.show(errMsg(e), 'err');
    } finally {
      setFbBusy(false);
    }
  }

  async function downloadFile(f: FileNode) {
    try {
      const session = await api.createDownloadSession(repo!.id, f.id);
      window.open(session.url, '_blank');
    } catch (e) {
      toast.show('下载失败：' + errMsg(e), 'err');
    }
  }

  if (err) return <div className="form-error">{err}</div>;
  if (!repo) return <div className="loading">加载中…</div>;

  const metaEntries = Object.entries(repo.metadata ?? {}).filter(([, v]) => v !== null && v !== undefined && v !== '');

  return (
    <div className="detail-layout">
      <div className="detail-main">
        {/* 面包屑 */}
        <div className="breadcrumb">
          <Link to="/">首页</Link> /
          <Link to={TYPE_PATH[repo.type] ?? '/'}>{TYPE_LABEL[repo.type] ?? repo.type}</Link> /
          <span>{repo.namespace}/{repo.name}</span>
        </div>

        <div className="card card-pad">
          <div className="detail-head">
            <div>
              <h1 className="detail-title">{repo.displayName || repo.name}</h1>
              <div className="detail-ns">
                {repo.namespace}/{repo.name}
                <span className={`badge badge-${repo.visibility}`} style={{ marginLeft: 10 }}>
                  {VIS_LABEL[repo.visibility]}
                </span>
                {repo.gated && <span className="badge badge-gated" style={{ marginLeft: 6 }}>申请制</span>}
                {repo.lifecycleStatus === 'provisioning' && (
                  <span className="badge badge-pending" style={{ marginLeft: 6 }}>资源准备中</span>
                )}
              </div>
            </div>
          </div>

          {/* 按钮组：下载 / 点赞 / 收藏 / 分享 + 规划中按钮（保留位置，禁用态 + 角标，禁止 Toast 冒充成功，09 §8.2） */}
          <div className="detail-actions">
            <button className="btn btn-primary" onClick={() => setDlOpen(true)}>⬇ 下载</button>
            <button className={`btn btn-ghost ${liked ? 'btn-active' : ''}`} onClick={doLike}>
              ♥ 点赞 {repo.stats.likes}
            </button>
            <button className={`btn btn-ghost ${favorited ? 'btn-active' : ''}`} onClick={doFavorite}>
              ★ 收藏 {repo.stats.favorites}
            </button>
            {repo.type === 'studio' && (
              <PlannedButton label="🚀 在线体验" note="v1 不交付真实运行时（publish_status 仅 draft/metadata_only），在线体验规划中。" />
            )}
            {repo.type === 'studio' && repo.metadata?.['deployable'] === true && (
              <PlannedButton label="部署" note="部署运行时门禁未就绪，该能力规划中。" />
            )}
            {repo.type === 'model' && <PlannedButton label="Notebook 快速开发" note="Notebook 开发环境规划中，v1 未交付。" />}
            {repo.type === 'model' && <PlannedButton label="训练" note="训练任务规划中，v1 未交付。" />}
            {repo.type === 'model' && <PlannedButton label="评测" note="评测任务规划中，v1 未交付。" />}
            <PlannedButton label="分享" note="分享能力规划中，可先复制浏览器地址栏链接。" />
          </div>

          {repo.description && <p className="detail-desc">{repo.description}</p>}

          {/* 标签行 + 统计行 */}
          {Array.isArray(repo.metadata?.['tags']) && (repo.metadata!['tags'] as string[]).length > 0 && (
            <div className="repo-tags" style={{ marginBottom: 8 }}>
              {(repo.metadata!['tags'] as string[]).slice(0, 8).map((t) => (
                <span key={t} className="tag-chip">{t}</span>
              ))}
            </div>
          )}
          <div className="stat-row">
            <span>浏览 <b>{repo.stats.visits}</b></span>
            <span>下载 <b>{repo.stats.downloads}</b></span>
            <span>点赞 <b>{repo.stats.likes}</b></span>
            <span>收藏 <b>{repo.stats.favorites}</b></span>
            <span>文件 <b>{repo.stats.fileCount}</b></span>
          </div>
        </div>

        {/* 三 Tab */}
        <div className="tabs">
          <div className={`tab ${tab === 'intro' ? 'active' : ''}`} onClick={() => setTab('intro')}>介绍</div>
          <div className={`tab ${tab === 'files' ? 'active' : ''}`} onClick={() => setTab('files')}>文件</div>
          <div className={`tab ${tab === 'feedback' ? 'active' : ''}`} onClick={() => setTab('feedback')}>交流反馈</div>
        </div>

        {tab === 'intro' && (
          <div className="card card-pad">
            <h3 style={{ marginTop: 0 }}>元数据</h3>
            {metaEntries.length > 0 ? (
              <div className="meta-grid">
                {metaEntries.map(([k, v]) => (
                  <div key={k} className="meta-item">
                    <span className="meta-key">{META_LABEL[k] ?? k}</span>
                    <span className="meta-val">
                      {Array.isArray(v) ? v.join('、') : typeof v === 'boolean' ? (v ? '是' : '否') : String(v)}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="empty-state" style={{ padding: 24 }}>暂无元数据</div>
            )}

            <h3>README</h3>
            {readme === null ? (
              <div className="empty-state" style={{ padding: 24 }}>暂无 README</div>
            ) : (
              <pre className="readme-text">{readme}</pre>
            )}
          </div>
        )}

        {tab === 'files' && (
          <div className="card card-pad">
            {filesErr ? (
              <div className="form-error" style={{ display: 'inline-block' }}>{filesErr}</div>
            ) : (
              <>
                {curPath && (
                  <a style={{ cursor: 'pointer' }} onClick={() => setCurPath(curPath.split('/').slice(0, -1).join('/'))}>
                    ← 返回上级
                  </a>
                )}
                <div className="file-list" style={{ marginTop: 8 }}>
                  {files.map((f) => (
                    <div key={f.id} className="file-item"
                         onClick={() => (f.type === 'directory' ? setCurPath(f.path) : downloadFile(f))}>
                      <span className="file-icon">{f.type === 'directory' ? '📁' : '📄'}</span>
                      <span className="file-name">{f.name}</span>
                      {f.type === 'file' && f.size != null && (
                        <span className="file-size">{f.size > 1048576 ? (f.size / 1048576).toFixed(1) + ' MB' : (f.size / 1024).toFixed(1) + ' KB'}</span>
                      )}
                      {f.type === 'file' && <span className="btn btn-ghost btn-sm">下载</span>}
                    </div>
                  ))}
                  {files.length === 0 && (
                    <div className="empty-state" style={{ padding: 24 }}>该分支下暂无文件</div>
                  )}
                </div>
              </>
            )}
          </div>
        )}

        {tab === 'feedback' && (
          <div className="card card-pad">
            {user ? (
              <>
                <textarea className="reason-input" placeholder="发表你的看法或问题…"
                          value={fbText} onChange={(e) => setFbText(e.target.value)} />
                <div style={{ margin: '10px 0 16px' }}>
                  <button className="btn btn-primary btn-sm" disabled={fbBusy || !fbText.trim()}
                          onClick={submitFeedback}>
                    {fbBusy ? '发布中…' : '发布反馈'}
                  </button>
                </div>
              </>
            ) : (
              <div className="empty-state" style={{ padding: 16 }}>
                登录后参与交流 <a onClick={() => requireLogin(() => {})} style={{ cursor: 'pointer' }}>去登录</a>
              </div>
            )}
            <DataState state={feedbacks.length === 0 ? 'empty' : 'ready'}>
              <div className="fb-list">
                {feedbacks.map((f) => (
                  <div key={f.id} className="fb-item">
                    <div className="fb-head">
                      <span className="avatar" style={{ width: 26, height: 26, fontSize: 12 }}>
                        {f.author.slice(0, 1).toUpperCase()}
                      </span>
                      <b>{f.author}</b>
                      <span className="req-meta">{new Date(f.createdAt).toLocaleString('zh-CN')}</span>
                    </div>
                    <div className="fb-content">{f.content}</div>
                  </div>
                ))}
              </div>
              {fbCursor && (
                <div style={{ textAlign: 'center', marginTop: 12 }}>
                  <button className="btn btn-ghost btn-sm" onClick={() => loadFeedbacks(fbCursor)}>加载更多</button>
                </div>
              )}
            </DataState>
          </div>
        )}

        {/* gated 申请入口 + 所有者/维护者审批管理（09 §6.3） */}
        {repo.gated && (
          <div className="card card-pad" style={{ marginTop: 16 }}>
            <h3 style={{ marginTop: 0 }}>申请制资源</h3>
            <p className="detail-desc" style={{ margin: '0 0 10px' }}>
              该资源需维护者审批后方可下载/访问。
            </p>
            <GatedAdmin repoId={repo.id} />
            <GatedApply repoId={repo.id} />
          </div>
        )}
      </div>

      {/* 右侧信息卡 */}
      <aside className="detail-side">
        <div className="card card-pad">
          <h3 style={{ marginTop: 0 }}>资源信息</h3>
          <div className="info-rows">
            <div><span>类型</span><b>{repo.type}</b></div>
            <div><span>可见性</span><b>{VIS_LABEL[repo.visibility]}</b></div>
            {typeof repo.metadata?.['license'] === 'string' && (
              <div><span>协议</span><b>{repo.metadata!['license'] as string}</b></div>
            )}
            <div><span>创建时间</span><b>{new Date(repo.createdAt).toLocaleDateString('zh-CN')}</b></div>
            <div><span>更新时间</span><b>{new Date(repo.updatedAt).toLocaleDateString('zh-CN')}</b></div>
          </div>
        </div>
        {related && related.items.length > 0 && (
          <div className="card card-pad" style={{ marginTop: 16 }}>
            <h3 style={{ marginTop: 0 }}>相关工作空间</h3>
            <div className="related-list">
              {related.items.map((r) => (
                <div key={r.id} className="related-item"
                     onClick={() => nav(`/resources/${r.type}/${r.namespace}/${r.name}`)}>
                  <span className="repo-name" style={{ fontSize: 14 }}>{r.displayName || r.name}</span>
                  <span className="repo-ns">@{r.namespace}/{r.name}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </aside>

      {dlOpen && <DownloadModal repo={repo} onClose={() => setDlOpen(false)} />}
    </div>
  );
}

/** 规划中能力按钮（09 §8.2 / UX-001）：保留位置，点击展示说明面板，禁止成功语气 Toast。 */
function PlannedButton({ label, note }: { label: string; note: string }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button className="btn btn-ghost" onClick={() => setOpen(true)}>
        {label}<span className="badge badge-pending" style={{ marginLeft: 4 }}>规划中</span>
      </button>
      {open && (
        <div className="modal-mask" onClick={() => setOpen(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3 style={{ marginTop: 0 }}>{label} · 规划中</h3>
            <p className="detail-desc">{note}</p>
            <div style={{ textAlign: 'right' }}>
              <button className="btn btn-primary btn-sm" onClick={() => setOpen(false)}>知道了</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

const REQ_STATUS_LABEL: Record<string, string> = {
  pending: '待审批', approved: '已批准', rejected: '已拒绝',
  revoked: '已吊销', expired: '已过期', withdrawn: '已撤回',
};

/** 所有者/维护者审批面板（09 §6.3）：非维护者 GET 403 时整体隐藏（服务端判定，不特判用户名）。 */
function GatedAdmin({ repoId }: { repoId: string }) {
  const { user } = useAuth();
  const toast = useToast();
  const [requests, setRequests] = useState<AccessRequest[] | null>(null);
  const [busyId, setBusyId] = useState('');

  const load = useCallback(() => {
    if (!user) return;
    api.repoAccessRequests(repoId).then((d) => setRequests(d.items)).catch(() => setRequests(null));
  }, [repoId, user?.username]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { load(); }, [load]);

  async function act(req: AccessRequest, action: 'approve' | 'reject' | 'revoke') {
    setBusyId(req.id + action);
    try {
      const etag = etagOfVersion(req.version);
      if (action === 'approve') await api.approveAccess(repoId, req.id, etag);
      else if (action === 'reject') await api.rejectAccess(repoId, req.id, etag);
      else await api.revokeAccess(repoId, req.id, etag);
      toast.show(action === 'approve' ? '已批准访问申请' : action === 'reject' ? '已拒绝访问申请' : '已吊销访问授权');
      load();
    } catch (e) {
      toast.show(errMsg(e), 'err');
    } finally {
      setBusyId('');
    }
  }

  if (!user || requests === null) return null;
  return (
    <div style={{ marginBottom: 14 }}>
      <h4 style={{ margin: '0 0 8px' }}>待审批与授权管理（维护者）</h4>
      {requests.length === 0 ? (
        <div className="empty-state" style={{ padding: 12 }}>暂无访问申请</div>
      ) : (
        requests.map((r) => (
          <div key={r.id} className="fb-item" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <span className="req-meta">申请人 {r.requesterId.slice(0, 8)}…</span>
              <span className={`badge ${r.status === 'pending' ? 'badge-pending' : r.status === 'approved' ? 'badge-public' : 'badge-private'}`} style={{ marginLeft: 8 }}>
                {REQ_STATUS_LABEL[r.status] ?? r.status}
              </span>
              {r.reason && <div className="fb-content">{r.reason}</div>}
            </div>
            <div className="req-actions">
              {r.status === 'pending' && (
                <>
                  <button className="btn btn-primary btn-sm" disabled={busyId === r.id + 'approve'}
                          onClick={() => act(r, 'approve')}>批准</button>
                  <button className="btn btn-danger btn-sm" disabled={busyId === r.id + 'reject'}
                          onClick={() => act(r, 'reject')}>拒绝</button>
                </>
              )}
              {r.status === 'approved' && (
                <button className="btn btn-ghost btn-sm" disabled={busyId === r.id + 'revoke'}
                        onClick={() => act(r, 'revoke')}>吊销</button>
              )}
            </div>
          </div>
        ))
      )}
    </div>
  );
}

/** gated 访问申请（requireLogin 拦截）；已申请过则展示自身申请状态（09 §6.3）。 */
function GatedApply({ repoId }: { repoId: string }) {
  const { user, requireLogin } = useAuth();
  const toast = useToast();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [mine, setMine] = useState<AccessRequest | null | undefined>(undefined);

  // 查询本人对该仓库的既有申请（myAccessRequests 跨仓库，前端过滤 repositoryId）
  useEffect(() => {
    if (!user) { setMine(undefined); return; }
    api.myAccessRequests()
      .then((d) => setMine(d.items.find((r) => r.repositoryId === repoId) ?? null))
      .catch(() => setMine(null));
  }, [repoId, user?.username]); // eslint-disable-line react-hooks/exhaustive-deps

  function submit() {
    requireLogin(() => {
      (async () => {
        setBusy(true);
        try {
          const created = await api.requestAccess(repoId, reason.trim() || '申请访问该资源');
          toast.show('申请已提交，等待维护者审批');
          setReason('');
          setMine(created);
        } catch (e) {
          toast.show(errMsg(e), 'err');
        } finally {
          setBusy(false);
        }
      })();
    });
  }

  if (!user) {
    return <button className="btn btn-primary" onClick={() => requireLogin(() => {})}>登录后申请访问</button>;
  }
  if (mine) {
    const canReapply = mine.status === 'rejected' || mine.status === 'revoked'
      || mine.status === 'expired' || mine.status === 'withdrawn';
    return (
      <div className="empty-state" style={{ padding: 12, textAlign: 'left' }}>
        我的申请状态：
        <span className={`badge ${mine.status === 'pending' ? 'badge-pending' : mine.status === 'approved' ? 'badge-public' : 'badge-private'}`} style={{ margin: '0 8px' }}>
          {REQ_STATUS_LABEL[mine.status] ?? mine.status}
        </span>
        {mine.status === 'pending' && '等待维护者审批中'}
        {mine.status === 'approved' && '已获批，可查看与下载文件'}
        {canReapply && (
          <button className="btn btn-ghost btn-sm" style={{ marginLeft: 8 }} onClick={() => setMine(null)}>重新申请</button>
        )}
      </div>
    );
  }
  return (
    <>
      <textarea className="reason-input" placeholder="简述访问用途（可选）"
                value={reason} onChange={(e) => setReason(e.target.value)} />
      <div style={{ marginTop: 12 }}>
        <button className="btn btn-primary" disabled={busy} onClick={submit}>
          {busy ? '提交中…' : '提交申请'}
        </button>
      </div>
    </>
  );
}
