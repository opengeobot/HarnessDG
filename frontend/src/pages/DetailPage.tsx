// 资源详情页（09 §8）— resolve 路由 + 三 Tab（介绍/文件/交流反馈）+ 下载弹窗 + 右侧信息卡
// 时间：2026-08-21  作者：AxeXie
import React, { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, type AccessRequest, type Feedback, type FileNode, type Page, type Repository } from '../api/client';
import { errMsg } from '../App';
import { useAuth } from '../context/AuthContext';
import { useToast, DataState } from '../components/ui';
import DownloadModal from '../components/DownloadModal';

const VIS_KEY: Record<string, string> = {
  public: 'card.visPublic', organization: 'card.visOrganization', private: 'card.visPrivate',
};
const TYPE_KEY: Record<string, string> = {
  model: 'detail.typeModel', dataset: 'detail.typeDataset', studio: 'detail.typeStudio',
};
const TYPE_PATH: Record<string, string> = { model: '/models', dataset: '/datasets', studio: '/studios' };
const META_KEY: Record<string, string> = {
  task: 'detail.metaTask', framework: 'detail.metaFramework', license: 'detail.metaLicense',
  architecture: 'detail.metaArchitecture', language: 'detail.metaLanguage',
  tags: 'detail.metaTags', capabilities: 'detail.metaCapabilities', apiStatus: 'detail.metaApiStatus',
  parameterCount: 'detail.metaParameterCount', parameterUnit: 'detail.metaParameterUnit',
  deployable: 'detail.metaDeployable', mcpCompatible: 'detail.metaMcpCompatible',
  estimatedRows: 'detail.metaEstimatedRows', dataFormats: 'detail.metaDataFormats',
  sensitivityLevel: 'detail.metaSensitivityLevel', previewPolicy: 'detail.metaPreviewPolicy',
  scenes: 'detail.metaScenes', runtimeType: 'detail.metaRuntimeType',
};

/** 日期本地化：随当前语言切换 locale。 */
const dateLocale = (lang: string) => (lang === 'zh' ? 'zh-CN' : 'en-US');

/** 客户端重算 ETag（与 shared ETags.ofVersion 同规则，04 §10）。 */
const etagOfVersion = (v: number) => '"' + (v + 0x5f000000).toString(16) + '"';

export default function DetailPage() {
  const { typeKey, namespace, name, id } = useParams();
  const nav = useNavigate();
  const { t, i18n } = useTranslation();
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
      toast.show(t('detail.feedbackPublished'));
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
      toast.show(t('detail.downloadFailed', { msg: errMsg(e) }), 'err');
    }
  }

  if (err) return <div className="form-error">{err}</div>;
  if (!repo) return <div className="loading">{t('common.loading')}</div>;

  const metaEntries = Object.entries(repo.metadata ?? {}).filter(([, v]) => v !== null && v !== undefined && v !== '');

  return (
    <div className="detail-layout">
      <div className="detail-main">
        {/* 面包屑 */}
        <div className="breadcrumb">
          <Link to="/">{t('nav.home')}</Link> /
          <Link to={TYPE_PATH[repo.type] ?? '/'}>{TYPE_KEY[repo.type] ? t(TYPE_KEY[repo.type]) : repo.type}</Link> /
          <span>{repo.namespace}/{repo.name}</span>
        </div>

        <div className="card card-pad">
          <div className="detail-head">
            <div>
              <h1 className="detail-title">{repo.displayName || repo.name}</h1>
              <div className="detail-ns">
                {repo.namespace}/{repo.name}
                <span className={`badge badge-${repo.visibility}`} style={{ marginLeft: 10 }}>
                  {VIS_KEY[repo.visibility] ? t(VIS_KEY[repo.visibility]) : repo.visibility}
                </span>
                {repo.gated && <span className="badge badge-gated" style={{ marginLeft: 6 }}>{t('card.gated')}</span>}
                {repo.lifecycleStatus === 'provisioning' && (
                  <span className="badge badge-pending" style={{ marginLeft: 6 }}>{t('my.provisioning')}</span>
                )}
              </div>
            </div>
          </div>

          {/* 按钮组：下载 / 点赞 / 收藏 / 分享 + 规划中按钮（保留位置，禁用态 + 角标，禁止 Toast 冒充成功，09 §8.2） */}
          <div className="detail-actions">
            <button className="btn btn-primary" onClick={() => setDlOpen(true)}>{t('detail.download')}</button>
            <button className={`btn btn-ghost ${liked ? 'btn-active' : ''}`} onClick={doLike}>
              {t('detail.like', { n: repo.stats.likes })}
            </button>
            <button className={`btn btn-ghost ${favorited ? 'btn-active' : ''}`} onClick={doFavorite}>
              {t('detail.favorite', { n: repo.stats.favorites })}
            </button>
            {repo.type === 'studio' && (
              <PlannedButton label={t('detail.planTryStudio')} note={t('detail.planTryStudioNote')} />
            )}
            {repo.type === 'studio' && repo.metadata?.['deployable'] === true && (
              <PlannedButton label={t('detail.planDeploy')} note={t('detail.planDeployNote')} />
            )}
            {repo.type === 'model' && <PlannedButton label={t('detail.planNotebook')} note={t('detail.planNotebookNote')} />}
            {repo.type === 'model' && <PlannedButton label={t('detail.planTrain')} note={t('detail.planTrainNote')} />}
            {repo.type === 'model' && <PlannedButton label={t('detail.planEval')} note={t('detail.planEvalNote')} />}
            <PlannedButton label={t('detail.planShare')} note={t('detail.planShareNote')} />
          </div>

          {repo.description && <p className="detail-desc">{repo.description}</p>}

          {/* 标签行 + 统计行 */}
          {Array.isArray(repo.metadata?.['tags']) && (repo.metadata!['tags'] as string[]).length > 0 && (
            <div className="repo-tags" style={{ marginBottom: 8 }}>
              {(repo.metadata!['tags'] as string[]).slice(0, 8).map((tag) => (
                <span key={tag} className="tag-chip">{tag}</span>
              ))}
            </div>
          )}
          <div className="stat-row">
            <span>{t('detail.statVisits')} <b>{repo.stats.visits}</b></span>
            <span>{t('detail.statDownloads')} <b>{repo.stats.downloads}</b></span>
            <span>{t('detail.statLikes')} <b>{repo.stats.likes}</b></span>
            <span>{t('detail.statFavorites')} <b>{repo.stats.favorites}</b></span>
            <span>{t('detail.statFiles')} <b>{repo.stats.fileCount}</b></span>
          </div>
        </div>

        {/* 三 Tab */}
        <div className="tabs">
          <div className={`tab ${tab === 'intro' ? 'active' : ''}`} onClick={() => setTab('intro')}>{t('detail.tabIntro')}</div>
          <div className={`tab ${tab === 'files' ? 'active' : ''}`} onClick={() => setTab('files')}>{t('detail.tabFiles')}</div>
          <div className={`tab ${tab === 'feedback' ? 'active' : ''}`} onClick={() => setTab('feedback')}>{t('detail.tabFeedback')}</div>
        </div>

        {tab === 'intro' && (
          <div className="card card-pad">
            <h3 style={{ marginTop: 0 }}>{t('detail.metadata')}</h3>
            {metaEntries.length > 0 ? (
              <div className="meta-grid">
                {metaEntries.map(([k, v]) => (
                  <div key={k} className="meta-item">
                    <span className="meta-key">{META_KEY[k] ? t(META_KEY[k]) : k}</span>
                    <span className="meta-val">
                      {Array.isArray(v) ? v.join(t('detail.sep'))
                        : typeof v === 'boolean' ? (v ? t('detail.yes') : t('detail.no')) : String(v)}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="empty-state" style={{ padding: 24 }}>{t('detail.noMetadata')}</div>
            )}

            <h3>README</h3>
            {readme === null ? (
              <div className="empty-state" style={{ padding: 24 }}>{t('detail.noReadme')}</div>
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
                    {t('detail.backUp')}
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
                      {f.type === 'file' && <span className="btn btn-ghost btn-sm">{t('detail.fileDownload')}</span>}
                    </div>
                  ))}
                  {files.length === 0 && (
                    <div className="empty-state" style={{ padding: 24 }}>{t('detail.noFiles')}</div>
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
                <textarea className="reason-input" placeholder={t('detail.fbPlaceholder')}
                          value={fbText} onChange={(e) => setFbText(e.target.value)} />
                <div style={{ margin: '10px 0 16px' }}>
                  <button className="btn btn-primary btn-sm" disabled={fbBusy || !fbText.trim()}
                          onClick={submitFeedback}>
                    {fbBusy ? t('detail.fbPublishing') : t('detail.fbPublish')}
                  </button>
                </div>
              </>
            ) : (
              <div className="empty-state" style={{ padding: 16 }}>
                {t('detail.fbLoginPrompt')} <a onClick={() => requireLogin(() => {})} style={{ cursor: 'pointer' }}>{t('detail.fbGoLogin')}</a>
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
                      <span className="req-meta">{new Date(f.createdAt).toLocaleString(dateLocale(i18n.language))}</span>
                    </div>
                    <div className="fb-content">{f.content}</div>
                  </div>
                ))}
              </div>
              {fbCursor && (
                <div style={{ textAlign: 'center', marginTop: 12 }}>
                  <button className="btn btn-ghost btn-sm" onClick={() => loadFeedbacks(fbCursor)}>{t('detail.fbLoadMore')}</button>
                </div>
              )}
            </DataState>
          </div>
        )}

        {/* gated 申请入口 + 所有者/维护者审批管理（09 §6.3） */}
        {repo.gated && (
          <div className="card card-pad" style={{ marginTop: 16 }}>
            <h3 style={{ marginTop: 0 }}>{t('detail.gatedTitle')}</h3>
            <p className="detail-desc" style={{ margin: '0 0 10px' }}>
              {t('detail.gatedDesc')}
            </p>
            <GatedAdmin repoId={repo.id} />
            <GatedApply repoId={repo.id} />
          </div>
        )}
      </div>

      {/* 右侧信息卡 */}
      <aside className="detail-side">
        <div className="card card-pad">
          <h3 style={{ marginTop: 0 }}>{t('detail.sideTitle')}</h3>
          <div className="info-rows">
            <div><span>{t('detail.sideType')}</span><b>{repo.type}</b></div>
            <div><span>{t('detail.sideVisibility')}</span><b>{VIS_KEY[repo.visibility] ? t(VIS_KEY[repo.visibility]) : repo.visibility}</b></div>
            {typeof repo.metadata?.['license'] === 'string' && (
              <div><span>{t('detail.sideLicense')}</span><b>{repo.metadata!['license'] as string}</b></div>
            )}
            <div><span>{t('detail.sideCreated')}</span><b>{new Date(repo.createdAt).toLocaleDateString(dateLocale(i18n.language))}</b></div>
            <div><span>{t('detail.sideUpdated')}</span><b>{new Date(repo.updatedAt).toLocaleDateString(dateLocale(i18n.language))}</b></div>
          </div>
        </div>
        {related && related.items.length > 0 && (
          <div className="card card-pad" style={{ marginTop: 16 }}>
            <h3 style={{ marginTop: 0 }}>{t('detail.relatedTitle')}</h3>
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
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  return (
    <>
      <button className="btn btn-ghost" onClick={() => setOpen(true)}>
        {label}<span className="badge badge-pending" style={{ marginLeft: 4 }}>{t('detail.plannedBadge')}</span>
      </button>
      {open && (
        <div className="modal-mask" onClick={() => setOpen(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3 style={{ marginTop: 0 }}>{t('detail.plannedTitle', { label })}</h3>
            <p className="detail-desc">{note}</p>
            <div style={{ textAlign: 'right' }}>
              <button className="btn btn-primary btn-sm" onClick={() => setOpen(false)}>{t('detail.gotIt')}</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

const REQ_KEY: Record<string, string> = {
  pending: 'detail.reqPending', approved: 'detail.reqApproved', rejected: 'detail.reqRejected',
  revoked: 'detail.reqRevoked', expired: 'detail.reqExpired', withdrawn: 'detail.reqWithdrawn',
};

/** 所有者/维护者审批面板（09 §6.3）：非维护者 GET 403 时整体隐藏（服务端判定，不特判用户名）。 */
function GatedAdmin({ repoId }: { repoId: string }) {
  const { t } = useTranslation();
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
      toast.show(action === 'approve' ? t('detail.gateApproved')
        : action === 'reject' ? t('detail.gateRejected') : t('detail.gateRevoked'));
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
      <h4 style={{ margin: '0 0 8px' }}>{t('detail.gateAdminTitle')}</h4>
      {requests.length === 0 ? (
        <div className="empty-state" style={{ padding: 12 }}>{t('detail.gateNoRequests')}</div>
      ) : (
        requests.map((r) => (
          <div key={r.id} className="fb-item" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <span className="req-meta">{t('detail.gateRequester', { id: r.requesterId.slice(0, 8) })}</span>
              <span className={`badge ${r.status === 'pending' ? 'badge-pending' : r.status === 'approved' ? 'badge-public' : 'badge-private'}`} style={{ marginLeft: 8 }}>
                {REQ_KEY[r.status] ? t(REQ_KEY[r.status]) : r.status}
              </span>
              {r.reason && <div className="fb-content">{r.reason}</div>}
            </div>
            <div className="req-actions">
              {r.status === 'pending' && (
                <>
                  <button className="btn btn-primary btn-sm" disabled={busyId === r.id + 'approve'}
                          onClick={() => act(r, 'approve')}>{t('detail.gateApprove')}</button>
                  <button className="btn btn-danger btn-sm" disabled={busyId === r.id + 'reject'}
                          onClick={() => act(r, 'reject')}>{t('detail.gateReject')}</button>
                </>
              )}
              {r.status === 'approved' && (
                <button className="btn btn-ghost btn-sm" disabled={busyId === r.id + 'revoke'}
                        onClick={() => act(r, 'revoke')}>{t('detail.gateRevoke')}</button>
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
  const { t } = useTranslation();
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
          const created = await api.requestAccess(repoId, reason.trim() || t('detail.applyDefaultReason'));
          toast.show(t('detail.applySubmitted'));
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
    return <button className="btn btn-primary" onClick={() => requireLogin(() => {})}>{t('detail.applyLogin')}</button>;
  }
  if (mine) {
    const canReapply = mine.status === 'rejected' || mine.status === 'revoked'
      || mine.status === 'expired' || mine.status === 'withdrawn';
    return (
      <div className="empty-state" style={{ padding: 12, textAlign: 'left' }}>
        {t('detail.myApplyStatus')}
        <span className={`badge ${mine.status === 'pending' ? 'badge-pending' : mine.status === 'approved' ? 'badge-public' : 'badge-private'}`} style={{ margin: '0 8px' }}>
          {REQ_KEY[mine.status] ? t(REQ_KEY[mine.status]) : mine.status}
        </span>
        {mine.status === 'pending' && t('detail.applyWait')}
        {mine.status === 'approved' && t('detail.applyGranted')}
        {canReapply && (
          <button className="btn btn-ghost btn-sm" style={{ marginLeft: 8 }} onClick={() => setMine(null)}>{t('detail.applyReapply')}</button>
        )}
      </div>
    );
  }
  return (
    <>
      <textarea className="reason-input" placeholder={t('detail.applyPlaceholder')}
                value={reason} onChange={(e) => setReason(e.target.value)} />
      <div style={{ marginTop: 12 }}>
        <button className="btn btn-primary" disabled={busy} onClick={submit}>
          {busy ? t('detail.applySubmitting') : t('detail.applySubmit')}
        </button>
      </div>
    </>
  );
}
