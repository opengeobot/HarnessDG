// 仓库运维面板：关键词检索 + 生命周期状态 + :retry（provisioning 失败重试）/ :delete（强制删除）
// 时间：2026-08-25  作者：AxeXie
import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, type Repository } from '../../api/client';
import { errMsg } from '../../App';
import { ConfirmDialog, DataState, useToast } from '../../components/ui';

/** retry 适用状态（04 §8：provisioning 失败可重试）。 */
const RETRY_STATES = ['failed', 'provisioning'];
/** 强制删除适用源状态（05 §8：failed / draft / provisioning）。 */
const DELETE_STATES = ['failed', 'draft', 'provisioning'];

export default function ReposPanel() {
  const { t } = useTranslation();
  const toast = useToast();
  const [keyword, setKeyword] = useState('');
  const [items, setItems] = useState<Repository[]>([]);
  const [state, setState] = useState<'loading' | 'ready' | 'empty' | 'error'>('ready');
  const [deleting, setDeleting] = useState<Repository | null>(null);
  const [busy, setBusy] = useState(false);

  async function search(kw: string) {
    setState('loading');
    try {
      const d = await api.listRepositories({ keyword: kw || undefined, page: 1, pageSize: 20 });
      setItems(d.items);
      setState(d.items.length === 0 ? 'empty' : 'ready');
    } catch (e) {
      toast.show(errMsg(e), 'err');
      setState('error');
    }
  }

  async function retry(repo: Repository) {
    try {
      await api.adminRepoRetry(repo.id);
      toast.show(t('admin.retryAccepted'));
      search(keyword);
    } catch (e) {
      toast.show(errMsg(e), 'err');
    }
  }

  async function confirmDelete() {
    if (!deleting) return;
    setBusy(true);
    try {
      await api.adminRepoDelete(deleting.id);
      toast.show(t('admin.deleteAccepted'));
      setDeleting(null);
      search(keyword);
    } catch (e) {
      toast.show(errMsg(e), 'err');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card card-pad">
      <div className="dir-head" style={{ marginBottom: 12 }}>
        <input type="search" placeholder={t('admin.reposSearchPh')} defaultValue={keyword}
               onKeyDown={(e) => {
                 if (e.key === 'Enter') {
                   const v = (e.target as HTMLInputElement).value;
                   setKeyword(v);
                   search(v);
                 }
               }}
               style={{ width: 260 }} />
        <button className="btn btn-primary btn-sm" onClick={() => search(keyword)}>
          {t('admin.auditSearch')}
        </button>
      </div>
      <DataState state={state} onRetry={() => search(keyword)}>
        <table className="repo-table">
          <thead>
            <tr>
              <th>{t('admin.thRepo')}</th><th>{t('admin.thType')}</th>
              <th>{t('admin.thLifecycle')}</th>
              <th style={{ width: 200 }}>{t('my.thActions')}</th>
            </tr>
          </thead>
          <tbody>
            {items.map((r) => (
              <tr key={r.id}>
                <td>
                  <Link to={`/resources/${r.type}/${r.namespace}/${r.name}`}>
                    <b>{r.displayName || r.name}</b>
                  </Link>
                  <span className="repo-ns" style={{ marginLeft: 8 }}>@{r.namespace}/{r.name}</span>
                </td>
                <td>{r.type}</td>
                <td>
                  <span className={`badge ${r.lifecycleStatus === 'active' ? 'badge-public' : 'badge-pending'}`}>
                    {r.lifecycleStatus}
                  </span>
                </td>
                <td>
                  {RETRY_STATES.includes(r.lifecycleStatus) && (
                    <button className="btn btn-ghost btn-sm" onClick={() => retry(r)}>
                      {t('admin.actRetry')}
                    </button>
                  )}
                  {DELETE_STATES.includes(r.lifecycleStatus) && (
                    <button className="btn btn-ghost btn-sm" onClick={() => setDeleting(r)}>
                      {t('admin.actDeleteRepo')}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </DataState>

      {deleting && (
        <ConfirmDialog title={t('admin.repoConfirmTitle')}
                       message={t('admin.repoDeleteMessage', { name: `${deleting.namespace}/${deleting.name}` })}
                       confirmText={t('admin.actDeleteRepo')} busy={busy}
                       onConfirm={confirmDelete} onCancel={() => setDeleting(null)} />
      )}
    </div>
  );
}
