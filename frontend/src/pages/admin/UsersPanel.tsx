// 用户管理面板：搜索/状态过滤 + cursor 分页 + 禁用/启用/解锁 + 角色指派/吊销
// 时间：2026-08-25  作者：AxeXie
import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, type AdminUser, type AdminRole } from '../../api/client';
import { errMsg } from '../../App';
import { DataState, useToast } from '../../components/ui';

const STATUS_KEY: Record<string, string> = {
  active: 'admin.statusActive', locked: 'admin.statusLocked', disabled: 'admin.statusDisabled',
};

export default function UsersPanel() {
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('');
  const [items, setItems] = useState<AdminUser[]>([]);
  const [cursor, setCursor] = useState('');
  const [state, setState] = useState<'loading' | 'ready' | 'empty' | 'error'>('loading');
  const [allRoles, setAllRoles] = useState<AdminRole[]>([]);

  useEffect(() => {
    api.adminRoles().then((d) => setAllRoles(d.items)).catch(() => {});
  }, []);

  const load = useCallback((nextCursor?: string) => {
    if (!nextCursor) setState('loading');
    api.adminUsers({ q: q || undefined, status: status || undefined, cursor: nextCursor || undefined, limit: 20 })
      .then((d) => {
        setItems((prev) => nextCursor ? [...prev, ...d.items] : d.items);
        setCursor(d.nextCursor ?? '');
        setState(!nextCursor && d.items.length === 0 ? 'empty' : 'ready');
      })
      .catch(() => setState('error'));
  }, [q, status]);

  useEffect(() => { load(); }, [load]);

  async function act(user: AdminUser, action: 'disable' | 'enable' | 'unlock') {
    try {
      if (action === 'disable') await api.adminDisableUser(user.id);
      else if (action === 'enable') await api.adminEnableUser(user.id);
      else await api.adminUnlockUser(user.id);
      toast.show(t('admin.opOk'));
      load();
    } catch (e) {
      toast.show(errMsg(e), 'err');
    }
  }

  async function assign(user: AdminUser, roleCode: string) {
    if (!roleCode) return;
    try {
      await api.adminAssignRole(user.id, roleCode);
      toast.show(t('admin.opOk'));
      load();
    } catch (e) {
      toast.show(errMsg(e), 'err');
    }
  }

  async function revoke(user: AdminUser, roleCode: string) {
    try {
      await api.adminRevokeRole(user.id, roleCode);
      toast.show(t('admin.opOk'));
      load();
    } catch (e) {
      toast.show(errMsg(e), 'err');
    }
  }

  const locale = i18n.language === 'zh' ? 'zh-CN' : 'en-US';

  return (
    <div className="card card-pad">
      <div className="dir-head" style={{ marginBottom: 12 }}>
        <input type="search" placeholder={t('admin.usersSearchPh')} defaultValue={q}
               onKeyDown={(e) => { if (e.key === 'Enter') setQ((e.target as HTMLInputElement).value); }}
               style={{ width: 220 }} />
        <select value={status} onChange={(e) => setStatus(e.target.value)} style={{ width: 130 }}>
          <option value="">{t('admin.statusAll')}</option>
          <option value="active">{t('admin.statusActive')}</option>
          <option value="locked">{t('admin.statusLocked')}</option>
          <option value="disabled">{t('admin.statusDisabled')}</option>
        </select>
      </div>
      <DataState state={state} onRetry={() => load()}>
        <table className="repo-table">
          <thead>
            <tr>
              <th>{t('admin.thUser')}</th><th>{t('admin.thStatus')}</th>
              <th>{t('admin.thRoles')}</th><th>{t('admin.thCreated')}</th>
              <th style={{ width: 260 }}>{t('my.thActions')}</th>
            </tr>
          </thead>
          <tbody>
            {items.map((u) => (
              <tr key={u.id}>
                <td>
                  <b>{u.nickname || u.username}</b>
                  <span className="repo-ns" style={{ marginLeft: 8 }}>@{u.username}</span>
                </td>
                <td>
                  <span className={`badge badge-${u.status === 'active' ? 'public' : u.status === 'locked' ? 'pending' : 'gated'}`}>
                    {STATUS_KEY[u.status] ? t(STATUS_KEY[u.status]) : u.status}
                  </span>
                </td>
                <td>
                  {u.roles.length === 0
                    ? <span className="req-meta">{t('admin.noRoles')}</span>
                    : u.roles.map((r) => (
                      <span key={r} className="tag-chip" style={{ marginRight: 4 }}>
                        {r} <b style={{ cursor: 'pointer' }} onClick={() => revoke(u, r)}>✕</b>
                      </span>
                    ))}
                  <select value="" onChange={(e) => assign(u, e.target.value)} style={{ marginLeft: 6, width: 110 }}>
                    <option value="">{t('admin.assignRole')}</option>
                    {allRoles.filter((r) => !u.roles.includes(r.code)).map((r) => (
                      <option key={r.id} value={r.code}>{r.code}</option>
                    ))}
                  </select>
                </td>
                <td className="req-meta">{new Date(u.createdAt).toLocaleDateString(locale)}</td>
                <td>
                  {u.status === 'active' && (
                    <button className="btn btn-ghost btn-sm" onClick={() => act(u, 'disable')}>{t('admin.actDisable')}</button>
                  )}
                  {u.status === 'disabled' && (
                    <button className="btn btn-ghost btn-sm" onClick={() => act(u, 'enable')}>{t('admin.actEnable')}</button>
                  )}
                  {u.status === 'locked' && (
                    <button className="btn btn-ghost btn-sm" onClick={() => act(u, 'unlock')}>{t('admin.actUnlock')}</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {cursor && (
          <div style={{ textAlign: 'center', marginTop: 12 }}>
            <button className="btn btn-ghost btn-sm" onClick={() => load(cursor)}>{t('admin.loadMore')}</button>
          </div>
        )}
      </DataState>
    </div>
  );
}
