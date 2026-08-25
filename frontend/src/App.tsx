// 应用外壳 — 路由 + 顶栏（5 项导航 + 全局搜索 + 创建下拉 + 头像菜单 + 语言切换）
// 时间：2026-08-21  作者：AxeXie
import React, { useEffect, useState } from 'react';
import { Routes, Route, Link, NavLink, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from './context/AuthContext';
import { ApiRequestError } from './api/client';
import i18n, { LANGUAGES, changeLanguage, type LangCode } from './i18n';
import HomePage from './pages/HomePage';
import MarketPage from './pages/MarketPage';
import DetailPage from './pages/DetailPage';
import MyPage from './pages/MyPage';
import DocsPage from './pages/DocsPage';
import AdminPage from './pages/admin/AdminPage';
import AuthModal from './components/AuthModal';
import GlobalSearchOverlay from './components/GlobalSearchOverlay';
import { ToastProvider } from './components/ui';

const NAV = [
  { to: '/', labelKey: 'nav.home', end: true },
  { to: '/models', labelKey: 'nav.models' },
  { to: '/datasets', labelKey: 'nav.datasets' },
  { to: '/studios', labelKey: 'nav.studios' },
  { to: '/docs', labelKey: 'nav.docs' },
];

const CREATE_ITEMS = [
  { type: 'model', to: '/models?create=1', labelKey: 'nav.createModel' },
  { type: 'dataset', to: '/datasets?create=1', labelKey: 'nav.createDataset' },
  { type: 'studio', to: '/studios?create=1', labelKey: 'nav.createStudio' },
];

/** 顶栏：logo + 5 项导航 + 搜索按钮 + 创建下拉 + 头像菜单 + 语言切换器。 */
function TopBar() {
  const { user, logout, openLogin } = useAuth();
  const { t } = useTranslation();
  const nav = useNavigate();
  const [searchOpen, setSearchOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);

  return (
    <header className="topbar">
      <Link to="/" className="brand">
        <span className="brand-mark">M</span>
        <span className="brand-name">ModelHub</span>
      </Link>
      <nav className="nav">
        {NAV.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.end}
                   className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
            {t(n.labelKey)}
          </NavLink>
        ))}
      </nav>
      <div className="user-area">
        <button className="btn btn-ghost btn-sm topbar-search" onClick={() => setSearchOpen(true)}>
          {t('nav.search')}
        </button>
        <select className="lang-switch" aria-label={t('nav.language')}
                value={i18n.language}
                onChange={(e) => changeLanguage(e.target.value as LangCode)}>
          {LANGUAGES.map((l) => (
            <option key={l.code} value={l.code}>{l.label}</option>
          ))}
        </select>
        {user ? (
          <>
            <div className="dropdown">
              <button className="btn btn-primary btn-sm" onClick={() => setCreateOpen(!createOpen)}>
                {t('nav.create')}
              </button>
              {createOpen && (
                <div className="dropdown-menu" onMouseLeave={() => setCreateOpen(false)}>
                  {CREATE_ITEMS.map((c) => (
                    <div key={c.type} className="dropdown-item"
                         onClick={() => { setCreateOpen(false); nav(c.to); }}>
                      {t(c.labelKey)}
                    </div>
                  ))}
                </div>
              )}
            </div>
            <div className="dropdown">
              <span className="user-chip" onClick={() => setUserMenuOpen(!userMenuOpen)} style={{ cursor: 'pointer' }}>
                <span className="avatar">{(user.nickname || user.username).slice(0, 1).toUpperCase()}</span>
                <span className="user-name">{user.nickname || user.username}</span>
              </span>
              {userMenuOpen && (
                <div className="dropdown-menu" onMouseLeave={() => setUserMenuOpen(false)}>
                  <div className="dropdown-item" onClick={() => { setUserMenuOpen(false); nav('/my'); }}>
                    {t('nav.myPage')}
                  </div>
                  {user.platformRoles.length > 0 && (
                    <div className="dropdown-item" onClick={() => { setUserMenuOpen(false); nav('/admin'); }}>
                      {t('nav.admin')}
                    </div>
                  )}
                  <div className="dropdown-item" onClick={async () => { setUserMenuOpen(false); await logout(); nav('/'); }}>
                    {t('nav.logout')}
                  </div>
                </div>
              )}
            </div>
          </>
        ) : (
          <>
            <button className="btn btn-ghost" onClick={() => openLogin()}>{t('nav.login')}</button>
            <button className="btn btn-primary" onClick={() => openLogin()}>{t('nav.register')}</button>
          </>
        )}
      </div>
      {searchOpen && <GlobalSearchOverlay onClose={() => setSearchOpen(false)} />}
    </header>
  );
}

/** /login、/register 旧路由：跳首页并打开 AuthModal（09 §2.3）。 */
function LegacyAuthRedirect() {
  const nav = useNavigate();
  const { openLogin } = useAuth();
  useEffect(() => {
    openLogin();
    nav('/', { replace: true });
  }, [nav, openLogin]);
  return null;
}

/** 统一错误提示（业务错误码 → 当前语言文案，经 i18n 实例可在组件外使用）。 */
export function errMsg(e: unknown): string {
  if (e instanceof ApiRequestError) {
    const known = ['UNAUTHENTICATED', 'FORBIDDEN', 'RESOURCE_NOT_FOUND', 'CONFLICT',
      'PRECONDITION_FAILED', 'VALIDATION_FAILED', 'METADATA_SCHEMA_INVALID', 'RATE_LIMITED', 'NETWORK_ERROR'];
    if (known.includes(e.apiCode)) {
      return i18n.t('errMsg.' + e.apiCode);
    }
    return e.message || i18n.t('common.requestFailed');
  }
  return e instanceof Error ? e.message : i18n.t('common.unknownError');
}

/** requireLogin 包装的创建入口已由 TopBar 登录后导航；MarketPage 自行处理 ?create=1。 */
export default function App() {
  const { t } = useTranslation();
  return (
    <ToastProvider>
      <div className="app">
        <TopBar />
        <main className="main">
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/models" element={<MarketPage typeKey="model" />} />
            <Route path="/datasets" element={<MarketPage typeKey="dataset" />} />
            <Route path="/studios" element={<MarketPage typeKey="studio" />} />
            <Route path="/resources/:typeKey/:namespace/:name" element={<DetailPage />} />
            <Route path="/repo/:id" element={<DetailPage />} />
            <Route path="/my" element={<MyPage />} />
            <Route path="/docs" element={<DocsPage />} />
            <Route path="/admin/:tab?" element={<AdminPage />} />
            <Route path="/login" element={<LegacyAuthRedirect />} />
            <Route path="/register" element={<LegacyAuthRedirect />} />
            <Route path="*" element={<div className="empty">{t('common.pageNotFound')}</div>} />
          </Routes>
        </main>
        <AuthModal />
      </div>
    </ToastProvider>
  );
}
