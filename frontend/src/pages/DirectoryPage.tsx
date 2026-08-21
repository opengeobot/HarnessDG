// 目录页 — 仓库搜索/筛选/分页
// 时间：2026-08-21  作者：AxeXie
import React, { useState, useEffect, useCallback } from 'react';
import { api, type Repository, type RepoListQuery } from '../api/client';
import RepoCard from '../components/RepoCard';

export default function DirectoryPage() {
  const [items, setItems] = useState<Repository[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');

  // 筛选状态
  const [keyword, setKeyword] = useState('');
  const [type, setType] = useState('');
  const [sort, setSort] = useState<NonNullable<RepoListQuery['sort']>>('relevance-v1');
  const [page, setPage] = useState(1);
  const pageSize = 12;

  // 资源类型（下拉筛选）
  const [typeKeys, setTypeKeys] = useState<string[]>([]);
  useEffect(() => {
    api.resourceTypes().then((ts) => setTypeKeys(ts.map((t) => t.typeKey))).catch(() => {});
  }, []);

  const load = useCallback(async () => {
    setLoading(true); setErr('');
    try {
      const q: RepoListQuery = { page, pageSize, sort };
      if (keyword.trim()) q.keyword = keyword.trim();
      if (type) q.type = type;
      const data = await api.listRepositories(q);
      setItems(data.items);
      setTotal(data.total);
    } catch (e) {
      setErr(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, [keyword, type, sort, page]);

  useEffect(() => { load(); }, [load]);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  return (
    <div>
      <div className="dir-head">
        <h1 className="dir-title">模型目录</h1>
        <div className="toolbar">
          <input type="search" placeholder="搜索名称/描述…"
                 value={keyword}
                 onChange={(e) => { setKeyword(e.target.value); setPage(1); }} />
          <select value={type} onChange={(e) => { setType(e.target.value); setPage(1); }}>
            <option value="">全部类型</option>
            {typeKeys.map((k) => <option key={k} value={k}>{k}</option>)}
          </select>
          <select value={sort} onChange={(e) => { setSort((e.target.value || 'relevance-v1') as NonNullable<RepoListQuery['sort']>); setPage(1); }}>
            <option value="relevance-v1">相关度</option>
            <option value="updatedAt-desc">最近更新</option>
            <option value="downloads-desc">最多下载</option>
            <option value="likes-desc">最多点赞</option>
            <option value="hot-desc">最热</option>
          </select>
        </div>
      </div>

      {err && <div className="form-error">{err}</div>}
      {loading ? (
        <div className="loading">加载中…</div>
      ) : items.length === 0 ? (
        <div className="empty-state">没有匹配的仓库</div>
      ) : (
        <div className="repo-grid">
          {items.map((r) => <RepoCard key={r.id} repo={r} />)}
        </div>
      )}

      {!loading && totalPages > 1 && (
        <div className="pager">
          <button className="btn btn-ghost btn-sm" disabled={page <= 1}
                  onClick={() => setPage((p) => p - 1)}>上一页</button>
          <span className="pager-info">第 {page} / {totalPages} 页 · 共 {total} 个</span>
          <button className="btn btn-ghost btn-sm" disabled={page >= totalPages}
                  onClick={() => setPage((p) => p + 1)}>下一页</button>
        </div>
      )}
    </div>
  );
}
