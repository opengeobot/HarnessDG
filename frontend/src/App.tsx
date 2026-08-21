// 应用外壳 — 路由 + 顶栏布局
// 时间：2026-08-21  作者：AxeXie
import React from 'react';
import { Routes, Route, Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DirectoryPage from './pages/DirectoryPage';
import RepoDetailPage from './pages/RepoDetailPage';
import MePage from './pages/MePage';
import { ApiRequestError } from './api/client';

/** 顶栏：logo + 主导航 + 用户区。 */
function TopBar() {
  const { user, logout } = useAuth();
  const nav = useNavigate();
  return (
    <header className="topbar">
      <Link to="/" className="brand">
        <span className="brand-mark">M</span>
        <span className="brand-name">ModelHub</span>
      </Link>
      <nav className="nav">
        <NavLink to="/" end className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
          目录
        </NavLink>
        {user && (
          <NavLink to="/me" className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
            我的
          </NavLink>
        )}
      </nav>
      <div className="user-area">
        {user ? (
          <>
            <span className="user-chip" title={user.id}>
              <span className="avatar">{(user.nickname || user.username).slice(0, 1).toUpperCase()}</span>
              <span className="user-name">{user.nickname || user.username}</span>
            </span>
            <button className="btn btn-ghost" onClick={async () => { await logout(); nav('/'); }}>
              退出
            </button>
          </>
        ) : (
          <>
            <Link to="/login" className="btn btn-ghost">登录</Link>
            <Link to="/register" className="btn btn-primary">注册</Link>
          </>
        )}
      </div>
    </header>
  );
}

/** 未登录访问受保护路由 → 重定向登录。 */
function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <div className="page-loading">加载中…</div>;
  if (!user) return <NavigateToLogin />;
  return <>{children}</>;
}

function NavigateToLogin() {
  const nav = useNavigate();
  React.useEffect(() => { nav('/login'); }, [nav]);
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
      RATE_LIMITED: '请求过于频繁，请稍后再试',
    };
    return map[e.apiCode] || e.message || '请求失败';
  }
  return e instanceof Error ? e.message : '未知错误';
}

export default function App() {
  return (
    <div className="app">
      <TopBar />
      <main className="main">
        <Routes>
          <Route path="/" element={<DirectoryPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/repo/:id" element={<RepoDetailPage />} />
          <Route path="/me" element={<RequireAuth><MePage /></RequireAuth>} />
          <Route path="*" element={<div className="empty">页面不存在</div>} />
        </Routes>
      </main>
    </div>
  );
}
