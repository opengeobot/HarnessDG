// 管理后台外壳（管理后台计划 §五）：左侧菜单 + 6 面板，/admin/:tab? 路由
// 守卫：未登录 → 首页 + 登录弹窗；无平台角色 → 403 文案；
// platform_auditor 仅可见概览与审计（写面服务端二次校验）。
// 时间：2026-08-25  作者：AxeXie
import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, type SystemOverview } from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { DataState } from '../../components/ui';
import UsersPanel from './UsersPanel';
import RolesPanel from './RolesPanel';
import DictsPanel from './DictsPanel';
import AuditPanel from './AuditPanel';
import ReposPanel from './ReposPanel';

const MENU: Array<{ key: string; labelKey: string; icon: string; adminOnly: boolean }> = [
  { key: 'overview', labelKey: 'admin.menuOverview', icon: '📊', adminOnly: false },
  { key: 'users', labelKey: 'admin.menuUsers', icon: '👥', adminOnly: true },
  { key: 'roles', labelKey: 'admin.menuRoles', icon: '🔑', adminOnly: true },
  { key: 'dicts', labelKey: 'admin.menuDicts', icon: '📖', adminOnly: true },
  { key: 'audit', labelKey: 'admin.menuAudit', icon: '📜', adminOnly: false },
  { key: 'repos', labelKey: 'admin.menuRepos', icon: '🛠️', adminOnly: true },
];

/** 概览面板：平台计数 + 健康检查（/admin/system/overview）。 */
function OverviewPanel() {
  const { t } = useTranslation();
  const [data, setData] = useState<SystemOverview | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading');

  useEffect(() => {
    api.adminOverview()
      .then((d) => { setData(d); setState('ready'); })
      .catch(() => setState('error'));
  }, []);

  return (
    <DataState state={state}>
      <div className="stat-cards">
        {[
          { labelKey: 'admin.countUsers', value: data?.counts.users ?? 0 },
          { labelKey: 'admin.countOrgs', value: data?.counts.organizations ?? 0 },
          { labelKey: 'admin.countRepos', value: data?.counts.repositories ?? 0 },
          { labelKey: 'admin.countAudit', value: data?.counts.auditLogs ?? 0 },
        ].map((c) => (
          <div key={c.labelKey} className="card card-pad stat-card" style={{ cursor: 'default' }}>
            <div className="stat-card-num">{c.value}</div>
            <div className="stat-card-label">{t(c.labelKey)}</div>
          </div>
        ))}
      </div>
      <div className="card card-pad" style={{ marginTop: 16 }}>
        <h3 style={{ marginTop: 0 }}>
          {t('admin.healthTitle')}
          <span className={`badge ${data?.healthStatus === 'UP' ? 'badge-public' : 'badge-gated'}`}
                style={{ marginLeft: 10 }}>{data?.healthStatus}</span>
        </h3>
        <table className="repo-table">
          <thead>
            <tr><th>{t('admin.checkName')}</th><th>{t('admin.checkStatus')}</th></tr>
          </thead>
          <tbody>
            {Object.entries(data?.healthChecks ?? {}).map(([name, status]) => (
              <tr key={name}>
                <td>{name}</td>
                <td>
                  <span className={`badge ${status === 'UP' ? 'badge-public' : 'badge-pending'}`}>{status}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </DataState>
  );
}

export default function AdminPage() {
  const { tab = 'overview' } = useParams();
  const nav = useNavigate();
  const { t } = useTranslation();
  const { user, loading, openLogin } = useAuth();

  // 未登录守卫（同 /my）
  useEffect(() => {
    if (!loading && !user) {
      openLogin(() => nav('/admin'));
      nav('/');
    }
  }, [loading, user, nav, openLogin]);

  if (loading) return <div className="page-loading">{t('common.loading')}</div>;
  if (!user) return null;
  if (user.platformRoles.length === 0) {
    return <div className="card card-pad empty-state" style={{ margin: 24 }}>{t('admin.guardForbidden')}</div>;
  }

  const isAdmin = user.platformRoles.includes('platform_admin');
  const items = MENU.filter((m) => !m.adminOnly || isAdmin);
  const active = items.some((m) => m.key === tab) ? tab : 'overview';

  return (
    <div className="my-layout admin-layout">
      <aside className="my-side card">
        <div className="my-profile">
          <span className="avatar me-avatar">⚙</span>
          <div>
            <div className="me-name">{t('nav.admin')}</div>
            <div className="me-id">@{user.username}</div>
          </div>
        </div>
        <nav className="my-menu">
          {items.map((m) => (
            <div key={m.key} className={`my-menu-item ${active === m.key ? 'active' : ''}`}
                 onClick={() => nav(`/admin/${m.key}`)}>
              <span>{m.icon}</span>{t(m.labelKey)}
            </div>
          ))}
        </nav>
      </aside>
      <div className="my-main">
        {active === 'overview' && <OverviewPanel />}
        {active === 'users' && <UsersPanel />}
        {active === 'roles' && <RolesPanel />}
        {active === 'dicts' && <DictsPanel />}
        {active === 'audit' && <AuditPanel />}
        {active === 'repos' && <ReposPanel />}
      </div>
    </div>
  );
}
