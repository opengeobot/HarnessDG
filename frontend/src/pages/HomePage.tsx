// 首页（09 §3）— 热门模型（综合排序 6 条）+ 精选工作空间（featured=true，未就绪降级空板块）
// 时间：2026-08-21  作者：AxeXie
import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, type Page, type Repository } from '../api/client';
import { RepoCardRenderer } from '../components/cards';
import GlobalSearchOverlay from '../components/GlobalSearchOverlay';

const MARKET_LINKS = [
  { key: 'model', to: '/models', title: '模型市场', desc: '开源模型目录，按任务/框架/协议筛选' },
  { key: 'dataset', to: '/datasets', title: '数据市场', desc: '训练数据集，支持申请制访问' },
  { key: 'studio', to: '/studios', title: '工作空间', desc: '可交互的创意应用与演示' },
];

export default function HomePage() {
  const nav = useNavigate();
  const [searchOpen, setSearchOpen] = useState(false);
  const [hot, setHot] = useState<Record<string, Page<Repository>>>({});

  useEffect(() => {
    // 热门模型：默认综合排序 relevance-v1 取 6 条（09 §3）
    api.listRepositories({ type: 'model', sort: 'relevance-v1', pageSize: 6 })
      .then((d) => setHot((prev) => ({ ...prev, model: d })))
      .catch(() => {});
    // 精选工作空间：featured=true；投影未就绪时降级为空板块（09 §3）
    api.listRepositories({ type: 'studio', featured: true, pageSize: 6 })
      .then((d) => setHot((prev) => ({ ...prev, studio: d })))
      .catch(() => setHot((prev) => ({ ...prev, studio: { items: [], total: 0, page: 1, pageSize: 6 } })));
  }, []);

  return (
    <div>
      {/* Hero */}
      <div className="home-hero">
        <h1>ModelHub 模型社区</h1>
        <p>发现、分享与部署模型、数据集与创意工作空间</p>
        <button className="home-search-btn" onClick={() => setSearchOpen(true)}>
          🔍 搜索模型、数据集与工作空间…
        </button>
      </div>

      {/* 三市场入口 */}
      <div className="home-entries">
        {MARKET_LINKS.map((m) => (
          <div key={m.key} className="card card-pad home-entry" onClick={() => nav(m.to)}>
            <div className="home-entry-title">{m.title}</div>
            <div className="home-entry-desc">{m.desc}</div>
            <span className="home-entry-go">进入 →</span>
          </div>
        ))}
      </div>

      {/* 热门模型（09 §3） */}
      <section className="home-sec">
        <div className="home-sec-head">
          <h2>热门模型</h2>
          <a onClick={() => nav('/models')} style={{ cursor: 'pointer' }}>查看全部 →</a>
        </div>
        {!hot['model'] ? (
          <div className="loading">加载中…</div>
        ) : hot['model'].items.length === 0 ? (
          <div className="empty-state card">暂无内容</div>
        ) : (
          <div className="repo-grid">
            {hot['model'].items.map((r) => <RepoCardRenderer key={r.id} repo={r} />)}
          </div>
        )}
      </section>

      {/* 精选工作空间（featured=true；未就绪降级空板块，09 §3） */}
      <section className="home-sec">
        <div className="home-sec-head">
          <h2>精选工作空间</h2>
          <a onClick={() => nav('/studios')} style={{ cursor: 'pointer' }}>查看全部 →</a>
        </div>
        {!hot['studio'] ? (
          <div className="loading">加载中…</div>
        ) : hot['studio'].items.length === 0 ? (
          <div className="empty-state card">精选内容陆续上线中，先去看看全部工作空间吧</div>
        ) : (
          <div className="repo-grid">
            {hot['studio'].items.map((r) => <RepoCardRenderer key={r.id} repo={r} />)}
          </div>
        )}
      </section>

      {searchOpen && <GlobalSearchOverlay onClose={() => setSearchOpen(false)} />}
    </div>
  );
}
