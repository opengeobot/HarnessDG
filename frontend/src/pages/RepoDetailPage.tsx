// 仓库详情页 — 基本信息 + 点赞/收藏 + gated 申请 + 文件浏览/下载
// 时间：2026-08-21  作者：AxeXie
import React, { useState, useEffect, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { api, type Repository, type FileNode } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { errMsg } from '../App';

const VIS_LABEL: Record<string, string> = { public: '公开', organization: '组织', private: '私有' };

export default function RepoDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const [repo, setRepo] = useState<Repository | null>(null);
  const [err, setErr] = useState('');
  const [tab, setTab] = useState<'overview' | 'files'>('overview');
  const [toast, setToast] = useState('');

  // 点赞/收藏状态（从关系返回推断，先以 stats>0 粗显）
  const [liked, setLiked] = useState(false);
  const [favorited, setFavorited] = useState(false);

  // gated 申请
  const [reason, setReason] = useState('');
  const [reqBusy, setReqBusy] = useState(false);

  // 文件
  const [files, setFiles] = useState<FileNode[]>([]);
  const [filesErr, setFilesErr] = useState('');
  const [curPath, setCurPath] = useState('');
  const [parentPath, setParentPath] = useState('');

  function flash(msg: string) {
    setToast(msg);
    setTimeout(() => setToast(''), 2500);
  }

  const loadRepo = useCallback(async () => {
    if (!id) return;
    try {
      setRepo(await api.getRepository(id));
    } catch (e) {
      setErr(errMsg(e));
    }
  }, [id]);

  useEffect(() => { loadRepo(); }, [loadRepo]);

  const loadFiles = useCallback(async () => {
    if (!id) return;
    setFilesErr('');
    try {
      const list = await api.listFiles(id, 'main', curPath || undefined);
      setFiles(list);
    } catch (e) {
      setFilesErr(errMsg(e));
    }
  }, [id, curPath]);

  useEffect(() => { if (tab === 'files') loadFiles(); }, [tab, loadFiles]);

  async function toggleLike() {
    if (!id || !user) return;
    try {
      const s = liked ? await api.unlike(id) : await api.like(id);
      setLiked(!!s?.active);
      await loadRepo();
    } catch (e) { flash(errMsg(e)); }
  }

  async function toggleFav() {
    if (!id || !user) return;
    try {
      const s = favorited ? await api.unfavorite(id) : await api.favorite(id);
      setFavorited(!!s?.active);
      await loadRepo();
    } catch (e) { flash(errMsg(e)); }
  }

  async function submitAccess() {
    if (!id) return;
    setReqBusy(true);
    try {
      await api.requestAccess(id, reason.trim() || '申请访问该仓库');
      flash('申请已提交，等待维护者审批');
      setReason('');
    } catch (e) {
      flash(errMsg(e));
    } finally {
      setReqBusy(false);
    }
  }

  async function downloadFile(f: FileNode) {
    if (!id) return;
    try {
      const session = await api.createDownloadSession(id, f.id);
      window.open(session.url, '_blank');
    } catch (e) {
      flash('下载失败：' + errMsg(e));
    }
  }

  function openFolder(f: FileNode) {
    setParentPath(curPath);
    setCurPath(f.path);
  }

  if (err) return <div className="form-error">{err}</div>;
  if (!repo) return <div className="loading">加载中…</div>;

  const noAccess = repo.gated && repo.visibility !== 'public';

  return (
    <div>
      <div className="card card-pad">
        <div className="detail-head">
          <div>
            <h1 className="detail-title">{repo.displayName || repo.name}</h1>
            <div className="detail-ns">
              {repo.namespace}/{repo.name}
              <span style={{ marginLeft: 10 }}>
                <span className={`badge badge-${repo.visibility}`}>{VIS_LABEL[repo.visibility]}</span>
              </span>
              {repo.gated && <span className="badge badge-gated" style={{ marginLeft: 6 }}>需申请</span>}
            </div>
          </div>
          {user && (
            <div style={{ display: 'flex', gap: 10 }}>
              <button className={`btn btn-ghost ${liked ? 'active' : ''}`} onClick={toggleLike}
                      style={liked ? { borderColor: 'var(--primary)', color: 'var(--primary)' } : {}}>
                ♥ 点赞 {repo.stats.likes}
              </button>
              <button className="btn btn-ghost" onClick={toggleFav}
                      style={favorited ? { borderColor: 'var(--primary)', color: 'var(--primary)' } : {}}>
                ★ 收藏 {repo.stats.favorites}
              </button>
            </div>
          )}
        </div>
        {repo.description && <p className="detail-desc">{repo.description}</p>}
        <div className="stat-row">
          <span>类型 <b>{repo.type}</b></span>
          <span>下载 <b>{repo.stats.downloads}</b></span>
          <span>文件 <b>{repo.stats.fileCount}</b></span>
          <span>更新 <b>{new Date(repo.updatedAt).toLocaleDateString('zh-CN')}</b></span>
        </div>
      </div>

      {/* gated 申请（需申请且未登录/无权限时） */}
      {noAccess && user && (
        <div className="card card-pad">
          <h3 style={{ marginTop: 0 }}>申请访问</h3>
          <p className="detail-desc" style={{ margin: '0 0 10px' }}>
            该仓库为受限资源，提交申请后由维护者审批。
          </p>
          <textarea className="reason-input" placeholder="简述访问用途（可选）"
                    value={reason} onChange={(e) => setReason(e.target.value)} />
          <div style={{ marginTop: 12 }}>
            <button className="btn btn-primary" onClick={submitAccess} disabled={reqBusy}>
              {reqBusy ? '提交中…' : '提交申请'}
            </button>
          </div>
        </div>
      )}
      {noAccess && !user && (
        <div className="card card-pad">该仓库为受限资源，请登录后申请访问。</div>
      )}

      {/* 标签页 */}
      <div className="tabs">
        <div className={`tab ${tab === 'overview' ? 'active' : ''}`} onClick={() => setTab('overview')}>概览</div>
        <div className={`tab ${tab === 'files' ? 'active' : ''}`} onClick={() => setTab('files')}>文件</div>
      </div>

      {tab === 'overview' && (
        <div className="card card-pad">
          <h3 style={{ marginTop: 0 }}>元数据</h3>
          {repo.metadata && Object.keys(repo.metadata).length > 0 ? (
            <pre style={{ background: 'var(--bg)', padding: 14, borderRadius: 8, overflowX: 'auto' }}>
              {JSON.stringify(repo.metadata, null, 2)}
            </pre>
          ) : (
            <div className="empty-state" style={{ padding: 24 }}>暂无元数据</div>
          )}
        </div>
      )}

      {tab === 'files' && (
        <div className="card card-pad">
          <div style={{ marginBottom: 12 }}>
            {filesErr
              ? <span className="form-error" style={{ display: 'inline-block' }}>{filesErr}</span>
              : <div className="file-list" style={{ marginBottom: 12 }}>
                  <span className="file-item" style={{ cursor: parentPath ? 'pointer' : 'default' }}
                        onClick={() => { if (parentPath) { setCurPath(parentPath); } }}>
                    <span className="file-icon">📁</span>
                    <span className="file-name">{curPath ? curPath : '(根目录)'}</span>
                  </span>
                </div>}
          </div>
          <div className="file-list">
            {files.map((f) => (
              <div key={f.id} className="file-item"
                   onClick={() => (f.type === 'directory' ? openFolder(f) : downloadFile(f))}>
                <span className="file-icon">{f.type === 'directory' ? '📁' : '📄'}</span>
                <span className="file-name">{f.name}</span>
                {f.type === 'file' && f.size != null && (
                  <span className="file-size">{(f.size / 1024).toFixed(1)} KB</span>
                )}
              </div>
            ))}
            {files.length === 0 && !filesErr && (
              <div className="empty-state" style={{ padding: 24 }}>
                {noAccess ? '无权限查看文件（需先获批访问）' : '该分支下暂无文件'}
              </div>
            )}
          </div>
        </div>
      )}

      {toast && <div className="form-success" style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 300 }}>{toast}</div>}
    </div>
  );
}
