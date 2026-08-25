// 应用外壳 — 路由 + 顶栏（5 项导航 + 全局搜索 + 创建下拉 + 头像菜单）
// 时间：2026-08-21  作者：AxeXie
import React, { useEffect, useState } from 'react';
import { Routes, Route, Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import { ApiRequestError } from './api/client';
import HomePage from './pages/HomePage';
import MarketPage from './pages/MarketPage';
import DetailPage from './pages/DetailPage';
import MyPage from './pages/MyPage';
import DocsPage from './pages/DocsPage';
import AuthModal from './components/AuthModal';
import GlobalSearchOverlay from './components/GlobalSearchOverlay';
import { ToastProvider } from './components/ui';

const NAV = [
  { to: '/', label: '首页', end: true },
  { to: '/models', label: '模型市场' },
  { to: '/datasets', label: '数据市场' },
  { to: '/studios', label: '工作空间' },
  { to: '/docs', label: '文档' },
];

const CREATE_ITEMS = [
  { type: 'model', to: '/models?create=1', label: '创建模型' },
  { type: 'dataset', to: '/datasets?create=1', label: '创建数据集' },
  { type: 'studio', to: '/studios?create=1', label: '创建工作空间' },
];

/** 顶栏：logo + 5 项导航 + 搜索按钮 + 创建下拉 + 头像菜单。 */
function TopBar() {
  const { user, logout, openLogin } = useAuth();
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
            {n.label}
          </NavLink>
        ))}
      </nav>
      <div className="user-area">
        <button className="btn btn-ghost btn-sm topbar-search" onClick={() => setSearchOpen(true)}>
          🔍 搜索
        </button>
        {user ? (
          <>
            <div className="dropdown">
              <button className="btn btn-primary btn-sm" onClick={() => setCreateOpen(!createOpen)}>
                + 创建 ▾
              </button>
              {createOpen && (
                <div className="dropdown-menu" onMouseLeave={() => setCreateOpen(false)}>
                  {CREATE_ITEMS.map((c) => (
                    <div key={c.type} className="dropdown-item"
                         onClick={() => { setCreateOpen(false); nav(c.to); }}>
                      {c.label}
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
                    个人中心
                  </div>
                  <div className="dropdown-item" onClick={async () => { setUserMenuOpen(false); await logout(); nav('/'); }}>
                    退出登录
                  </div>
                </div>
              )}
            </div>
          </>
        ) : (
          <>
            <button className="btn btn-ghost" onClick={() => openLogin()}>登录</button>
            <button className="btn btn-primary" onClick={() => openLogin()}>注册</button>
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

/** 统一错误提示（业务错误码 → 中文）。 */
export function errMsg(e: unknown): string {
  if (e instanceof ApiRequestError) {
    const map: Record<string, string> = {
      UNAUTHENTICATED: '登录已过期，请重新登录',
      FORBIDDEN: '没有权限执行该操作',
      RESOURCE_NOT_FOUND: '资源不存在',
      CONFLICT: '状态冲突，请刷新后重试',
      PRECONDITION_FAILED: '版本已变化，请刷新后重试',
      VALIDATION_FAILED: '输入不合法',
      METADATA_SCHEMA_INVALID: '元数据不符合该资源类型的 Schema',
      RATE_LIMITED: '请求过于频繁，请稍后再试',
    };
    return map[e.apiCode] || e.message || '请求失败';
  }
  return e instanceof Error ? e.message : '未知错误';
}

/** requireLogin 包装的创建入口已由 TopBar 登录后导航；MarketPage 自行处理 ?create=1。 */
export default function App() {
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
            <Route path="/login" element={<LegacyAuthRedirect />} />
            <Route path="/register" element={<LegacyAuthRedirect />} />
            <Route path="*" element={<div className="empty">页面不存在</div>} />
          </Routes>
        </main>
        <AuthModal />
      </div>
    </ToastProvider>
  );
}
