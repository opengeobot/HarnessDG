// 管理后台外壳（字典统一+菜单权限重构计划 §七.2）：左侧菜单由 /me/menus 接口驱动
// （标准 RBAC：可见菜单 = 用户权限派生结果），path→panel 映射保留在前端。
// 守卫：未登录 → 首页 + 登录弹窗；无可见菜单（无权限/接口失败）→ 403 文案；
// 写面服务端二次校验（权限点）。
// 时间：2026-08-26  作者：AxeXie
import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, type SystemOverview, type VisibleMenu } from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { DataState } from '../../components/ui';
import UsersPanel from './UsersPanel';
import RolesPanel from './RolesPanel';
import DictsPanel from './DictsPanel';
import AuditPanel from './AuditPanel';
import ReposPanel from './ReposPanel';
import MenusPanel from './MenusPanel';

/** 路径尾段 → 面板渲染（接口只提供 code/path/名称/排序，组件映射保留在前端）。 */
function PanelOf({ tab }: { tab: string }) {
  const { t } = useTranslation();
  switch (tab) {
    case 'overview': return <OverviewPanel />;
    case 'users': return <UsersPanel />;
    case 'roles': return <RolesPanel />;
    case 'dicts': return <DictsPanel />;
    case 'audit': return <AuditPanel />;
    case 'repos': return <ReposPanel />;
    case 'menus': return <MenusPanel />;
    default:
      return <div className="card card-pad empty-state">{t('adminMenu.noPanel')}</div>;
  }
}

/** 面板图标（仅前端展示属性）。 */
const TAB_ICON: Record<string, string> = {
  overview: '📊', users: '👥', roles: '🔑', dicts: '📖',
  audit: '📜', repos: '🛠️', menus: '🧭', orgs: '🏢',
};

/** 菜单路径 → 面板 tab 键（取路径尾段）。 */
function tabOf(path: string | null): string {
  if (!path) return '';
  const seg = path.split('/').filter(Boolean);
  return seg[seg.length - 1] ?? '';
}

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
  const { tab = '' } = useParams();
  const nav = useNavigate();
  const { t, i18n } = useTranslation();
  const { user, loading, openLogin } = useAuth();
  const zh = i18n.language.startsWith('zh');

  // 可见菜单（按权限派生）；接口失败降级为空 → 守卫文案
  const [menus, setMenus] = useState<VisibleMenu[] | null>(null);

  // 未登录守卫（同 /my）
  useEffect(() => {
    if (!loading && !user) {
      openLogin(() => nav('/admin'));
      nav('/');
    }
  }, [loading, user, nav, openLogin]);

  useEffect(() => {
    if (!user) return;
    api.meMenus()
      .then((d) => setMenus(d.items))
      .catch(() => setMenus([]));
  }, [user]);

  if (loading) return <div className="page-loading">{t('common.loading')}</div>;
  if (!user) return null;
  if (menus === null) return <div className="page-loading">{t('common.loading')}</div>;
  if (menus.length === 0) {
    return <div className="card card-pad empty-state" style={{ margin: 24 }}>{t('admin.guardForbidden')}</div>;
  }

  const items = [...menus].sort((a, b) => a.sortOrder - b.sortOrder);
  const tabs = items.map((m) => tabOf(m.path));
  const active = tabs.includes(tab) ? tab : tabs[0];

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
          {items.map((m) => {
            const key = tabOf(m.path);
            return (
              <div key={m.code} className={`my-menu-item ${active === key ? 'active' : ''}`}
                   onClick={() => nav(`/admin/${key}`)}>
                <span>{TAB_ICON[key] ?? '📄'}</span>{zh ? m.nameZh : m.nameEn}
              </div>
            );
          })}
        </nav>
      </aside>
      <div className="my-main">
        <PanelOf tab={active} />
      </div>
    </div>
  );
}
