// 全局搜索覆盖层（09 §2.2）：空输入热门榜 + 400ms 防抖跨库即时搜索（AbortController 取消旧请求）
// 时间：2026-08-21  作者：AxeXie
import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, type HotSearches, type Page, type Repository } from '../api/client';

const SEARCH_TYPES = ['model', 'dataset', 'studio'] as const;
const TYPE_LABEL: Record<string, string> = { model: '模型市场', dataset: '数据市场', studio: '工作空间' };

type GroupResults = Record<string, Page<Repository> | null>;

export default function GlobalSearchOverlay({ onClose }: { onClose: () => void }) {
  const nav = useNavigate();
  const [keyword, setKeyword] = useState('');
  const [hot, setHot] = useState<HotSearches | null>(null);
  const [groups, setGroups] = useState<GroupResults>({});
  const [searched, setSearched] = useState(false);
  const [busy, setBusy] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const timerRef = useRef<number | null>(null);

  // 空输入时加载热门榜（匿名可读端点）
  useEffect(() => {
    api.hotSearches().then(setHot).catch(() => {});
    return () => { abortRef.current?.abort(); if (timerRef.current) window.clearTimeout(timerRef.current); };
  }, []);

  function runSearch(kw: string) {
    abortRef.current?.abort();
    if (!kw.trim()) {
      setGroups({});
      setSearched(false);
      return;
    }
    const controller = new AbortController();
    abortRef.current = controller;
    setBusy(true);
    setSearched(true);
    Promise.all(SEARCH_TYPES.map((t) =>
      api.listRepositories({ type: t, keyword: kw.trim(), pageSize: 5 }, controller.signal)
        .then((data) => [t, data] as const)
        .catch(() => [t, null] as const),
    )).then((entries) => {
      if (controller.signal.aborted) return;
      setGroups(Object.fromEntries(entries));
      setBusy(false);
    });
  }

  // 400ms 防抖；回车立即搜索
  function onInput(v: string) {
    setKeyword(v);
    if (timerRef.current) window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => runSearch(v), 400);
  }

  function goto(r: Repository) {
    onClose();
    nav(`/resources/${r.type}/${r.namespace}/${r.name}`);
  }

  return (
    <div className="modal-mask search-mask" onClick={onClose}>
      <div className="search-panel" onClick={(e) => e.stopPropagation()}>
        <div className="search-bar">
          <input autoFocus value={keyword} placeholder="搜索模型、数据集与工作空间…"
                 onChange={(e) => onInput(e.target.value)}
                 onKeyDown={(e) => {
                   if (e.key === 'Enter') {
                     if (timerRef.current) window.clearTimeout(timerRef.current);
                     runSearch(keyword);
                   }
                   if (e.key === 'Escape') onClose();
                 }} />
          <button className="btn btn-ghost btn-sm" onClick={onClose}>✕</button>
        </div>

        <div className="search-body">
          {!searched && (
            <>
              <div className="search-sec-title">热门搜索</div>
              {hot && hot.words.length > 0 ? (
                <div className="hot-words">
                  {hot.words.map((w, i) => (
                    <span key={w + i} className="hot-word" onClick={() => { setKeyword(w); runSearch(w); }}>
                      <b className={i < 3 ? 'hot-rank top' : 'hot-rank'}>{i + 1}</b>{w}
                    </span>
                  ))}
                </div>
              ) : (
                <div className="empty-state" style={{ padding: 24 }}>暂无热门搜索</div>
              )}
            </>
          )}

          {searched && (busy ? (
            <div className="loading">搜索中…</div>
          ) : SEARCH_TYPES.every((t) => !groups[t] || groups[t]!.items.length === 0) ? (
            <div className="empty-state" style={{ padding: 24 }}>未找到相关结果</div>
          ) : (
            SEARCH_TYPES.map((t) => {
              const g = groups[t];
              if (!g || g.items.length === 0) return null;
              return (
                <div key={t}>
                  <div className="search-sec-title">{TYPE_LABEL[t]}（{g.total}）</div>
                  {g.items.map((r) => (
                    <div key={r.id} className="search-item" onClick={() => goto(r)}>
                      <span className="search-item-path">{r.namespace}/{r.name}</span>
                      <span className="search-item-name">{r.displayName || r.description || ''}</span>
                    </div>
                  ))}
                </div>
              );
            })
          ))}
        </div>
      </div>
    </div>
  );
}
