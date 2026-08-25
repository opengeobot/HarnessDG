// 首页（09 §3）— 热门模型（综合排序 6 条）+ 精选工作空间（featured=true，未就绪降级空板块）
// 时间：2026-08-21  作者：AxeXie
import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, type Page, type Repository } from '../api/client';
import { RepoCardRenderer } from '../components/cards';
import GlobalSearchOverlay from '../components/GlobalSearchOverlay';

const MARKET_LINKS = [
  { key: 'model', to: '/models', titleKey: 'home.entryModel', descKey: 'home.entryModelDesc' },
  { key: 'dataset', to: '/datasets', titleKey: 'home.entryDataset', descKey: 'home.entryDatasetDesc' },
  { key: 'studio', to: '/studios', titleKey: 'home.entryStudio', descKey: 'home.entryStudioDesc' },
];

export default function HomePage() {
  const nav = useNavigate();
  const { t } = useTranslation();
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
        <h1>{t('home.heroTitle')}</h1>
        <p>{t('home.heroSubtitle')}</p>
        <button className="home-search-btn" onClick={() => setSearchOpen(true)}>
          {t('home.searchBtn')}
        </button>
      </div>

      {/* 三市场入口 */}
      <div className="home-entries">
        {MARKET_LINKS.map((m) => (
          <div key={m.key} className="card card-pad home-entry" onClick={() => nav(m.to)}>
            <div className="home-entry-title">{t(m.titleKey)}</div>
            <div className="home-entry-desc">{t(m.descKey)}</div>
            <span className="home-entry-go">{t('home.entryGo')}</span>
          </div>
        ))}
      </div>

      {/* 热门模型（09 §3） */}
      <section className="home-sec">
        <div className="home-sec-head">
          <h2>{t('home.hotModels')}</h2>
          <a onClick={() => nav('/models')} style={{ cursor: 'pointer' }}>{t('home.viewAll')}</a>
        </div>
        {!hot['model'] ? (
          <div className="loading">{t('common.loading')}</div>
        ) : hot['model'].items.length === 0 ? (
          <div className="empty-state card">{t('home.empty')}</div>
        ) : (
          <div className="repo-grid">
            {hot['model'].items.map((r) => <RepoCardRenderer key={r.id} repo={r} />)}
          </div>
        )}
      </section>

      {/* 精选工作空间（featured=true；未就绪降级空板块，09 §3） */}
      <section className="home-sec">
        <div className="home-sec-head">
          <h2>{t('home.featuredStudios')}</h2>
          <a onClick={() => nav('/studios')} style={{ cursor: 'pointer' }}>{t('home.viewAll')}</a>
        </div>
        {!hot['studio'] ? (
          <div className="loading">{t('common.loading')}</div>
        ) : hot['studio'].items.length === 0 ? (
          <div className="empty-state card">{t('home.studioEmpty')}</div>
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
