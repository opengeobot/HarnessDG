// 审计日志面板：actor/action/时间范围过滤 + cursor 分页 + NDJSON 导出（04 §4.1）
// 时间：2026-08-25  作者：AxeXie
import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, currentAccessToken, type AdminAuditLog } from '../../api/client';
import { errMsg } from '../../App';
import { DataState, useToast } from '../../components/ui';

/** datetime-local 值 → RFC-3339（UTC，服务端要求如 2026-08-25T00:00:00Z）。 */
const toRfc3339 = (v: string) => (v ? (v.length === 16 ? `${v}:00Z` : `${v}Z`) : undefined);

export default function AuditPanel() {
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const [actor, setActor] = useState('');
  const [action, setAction] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [items, setItems] = useState<AdminAuditLog[]>([]);
  const [cursor, setCursor] = useState('');
  const [state, setState] = useState<'loading' | 'ready' | 'empty' | 'error'>('loading');
  const [exporting, setExporting] = useState(false);

  const params = useCallback(() => ({
    actor: actor || undefined, action: action || undefined,
    from: toRfc3339(from), to: toRfc3339(to),
  }), [actor, action, from, to]);

  const load = useCallback((nextCursor?: string) => {
    if (!nextCursor) setState('loading');
    api.adminAuditLogs({ ...params(), cursor: nextCursor || undefined, limit: 30 })
      .then((d) => {
        setItems((prev) => nextCursor ? [...prev, ...d.items] : d.items);
        setCursor(d.nextCursor ?? '');
        setState(!nextCursor && d.items.length === 0 ? 'empty' : 'ready');
      })
      .catch(() => setState('error'));
  }, [params]);

  useEffect(() => { load(); }, [load]);

  async function exportNdjson() {
    setExporting(true);
    try {
      const qs = new URLSearchParams({ format: 'ndjson' });
      const p = params();
      if (p.actor) qs.set('actor', p.actor);
      if (p.action) qs.set('action', p.action);
      if (p.from) qs.set('from', p.from);
      if (p.to) qs.set('to', p.to);
      const resp = await fetch(`/api/v1/admin/audit-logs?${qs}`, {
        headers: { Authorization: `Bearer ${currentAccessToken()}` },
        credentials: 'include',
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const blob = await resp.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'audit-logs.ndjson';
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      toast.show(errMsg(e), 'err');
    } finally {
      setExporting(false);
    }
  }

  const locale = i18n.language === 'zh' ? 'zh-CN' : 'en-US';

  return (
    <div className="card card-pad">
      <div className="dir-head" style={{ marginBottom: 12, flexWrap: 'wrap' }}>
        <input type="search" placeholder={t('admin.auditActor')} defaultValue={actor}
               onKeyDown={(e) => { if (e.key === 'Enter') setActor((e.target as HTMLInputElement).value); }}
               style={{ width: 150 }} />
        <input type="search" placeholder={t('admin.auditAction')} defaultValue={action}
               onKeyDown={(e) => { if (e.key === 'Enter') setAction((e.target as HTMLInputElement).value); }}
               style={{ width: 180 }} />
        <input type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)}
               title={t('admin.auditFrom')} />
        <input type="datetime-local" value={to} onChange={(e) => setTo(e.target.value)}
               title={t('admin.auditTo')} />
        <button className="btn btn-primary btn-sm" onClick={() => load()}>{t('admin.auditSearch')}</button>
        <button className="btn btn-ghost btn-sm" disabled={exporting} onClick={exportNdjson}>
          {t('admin.auditExport')}
        </button>
      </div>
      <DataState state={state} onRetry={() => load()}>
        {state === 'ready' && items.length === 0 && (
          <div className="empty-state" style={{ padding: 24 }}>{t('admin.noAudit')}</div>
        )}
        <table className="repo-table">
          <thead>
            <tr>
              <th>{t('admin.thTime')}</th><th>{t('admin.thActor')}</th><th>{t('admin.thAction')}</th>
              <th>{t('admin.thResource')}</th><th>{t('admin.thResult')}</th><th>{t('admin.thIp')}</th>
            </tr>
          </thead>
          <tbody>
            {items.map((a) => (
              <tr key={a.id}>
                <td className="req-meta">{new Date(a.createdAt).toLocaleString(locale)}</td>
                <td>{a.actor}</td>
                <td><span className="tag-chip">{a.action}</span></td>
                <td className="req-meta" style={{ maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {a.resource}
                </td>
                <td>
                  <span className={`badge ${a.result === 'success' ? 'badge-public' : 'badge-gated'}`}>
                    {a.result}
                  </span>
                </td>
                <td className="req-meta">{a.sourceIp}</td>
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
