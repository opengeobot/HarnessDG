// 个人中心 — 资料 + 我的仓库(created/likes/favorites) + 我的访问申请
// 时间：2026-08-21  作者：AxeXie
import React, { useState, useEffect, useCallback } from 'react';
import { api, type Repository, type AccessRequest } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { errMsg } from '../App';
import RepoCard from '../components/RepoCard';

type RepoTab = 'created' | 'likes' | 'favorites';
const REQ_BADGE: Record<string, string> = {
  pending: 'badge-pending', approved: 'badge-approved', rejected: 'badge-rejected',
  revoked: 'badge-revoked', expired: 'badge-expired', withdrawn: 'badge-withdrawn',
};

export default function MePage() {
  const { user } = useAuth();
  const [tab, setTab] = useState<RepoTab>('created');
  const [items, setItems] = useState<Repository[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [reqs, setReqs] = useState<AccessRequest[]>([]);
  const pageSize = 12;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await api.meRepositories(tab, page, pageSize);
      setItems(data.items);
      setTotal(data.total);
    } catch (e) {
      setItems([]); setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [tab, page]);

  const loadReqs = useCallback(async () => {
    try {
      const data = await api.myAccessRequests();
      setReqs(data.items);
    } catch { /* 忽略：无权/无申请 */ }
  }, []);

  useEffect(() => { load(); }, [load]);
  useEffect(() => { loadReqs(); }, [loadReqs]);

  if (!user) return null;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  return (
    <div>
      <div className="card card-pad me-head">
        <span className="avatar me-avatar">{(user.nickname || user.username).slice(0, 1).toUpperCase()}</span>
        <div>
          <div className="me-name">{user.nickname || user.username}</div>
          <div className="me-id">@{user.username} · 创建于命名空间 {user.namespaceId}</div>
        </div>
      </div>

      <div className="tabs">
        {(['created', 'likes', 'favorites'] as RepoTab[]).map((t) => (
          <div key={t} className={`tab ${tab === t ? 'active' : ''}`} onClick={() => { setTab(t); setPage(1); }}>
            {{ created: '我创建的', likes: '我点赞的', favorites: '我收藏的' }[t]}
          </div>
        ))}
      </div>

      {loading ? (
        <div className="loading">加载中…</div>
      ) : items.length === 0 ? (
        <div className="empty-state">这里还没有仓库</div>
      ) : (
        <div className="repo-grid">
          {items.map((r) => <RepoCard key={r.id} repo={r} />)}
        </div>
      )}

      {!loading && totalPages > 1 && (
        <div className="pager">
          <button className="btn btn-ghost btn-sm" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>上一页</button>
          <span className="pager-info">第 {page} / {totalPages} 页</span>
          <button className="btn btn-ghost btn-sm" disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)}>下一页</button>
        </div>
      )}

      <div className="card card-pad" style={{ marginTop: 24 }}>
        <h3 style={{ marginTop: 0 }}>我的访问申请</h3>
        {reqs.length === 0 ? (
          <div className="empty-state" style={{ padding: 16 }}>暂无访问申请</div>
        ) : (
          <div className="req-list">
            {reqs.map((r) => (
              <div key={r.id} className="req-item">
                <div>
                  <div>仓库 <b>{r.repositoryId}</b></div>
                  <div className="req-meta">
                    {r.reason && <span>{r.reason} · </span>}
                    <span>{new Date(r.createdAt).toLocaleDateString('zh-CN')}</span>
                  </div>
                </div>
                <span className={`badge ${REQ_BADGE[r.status] || 'badge-pending'}`}>{r.status}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
