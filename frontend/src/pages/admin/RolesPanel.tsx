// 角色权限面板：角色 CRUD + 权限点勾选 + 指派计数（内置角色禁改权限/禁删，服务端 409）
// 时间：2026-08-25  作者：AxeXie
import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, type AdminRole, type AdminPermission } from '../../api/client';
import { errMsg } from '../../App';
import { ConfirmDialog, DataState, useToast } from '../../components/ui';

/** 客户端重算 ETag（与 shared ETags.ofVersion 同规则，04 §10）。 */
const etagOfVersion = (v: number) => '"' + (v + 0x5f000000).toString(16) + '"';

/** 角色表单弹窗（创建/编辑共用；编辑时 code 禁用）。 */
function RoleFormModal({ initial, permissions, onClose, onSaved }: {
  initial: AdminRole | null;
  permissions: AdminPermission[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const { t } = useTranslation();
  const editing = !!initial;
  const [code, setCode] = useState(initial?.code ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [perms, setPerms] = useState<string[]>(initial?.permissions ?? []);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErr(''); setBusy(true);
    try {
      if (editing && initial) {
        await api.adminUpdateRole(initial.id, { description, permissions: perms },
          initial.etag ?? etagOfVersion(initial.version));
      } else {
        await api.adminCreateRole({ code: code.trim(), description, permissions: perms });
      }
      onSaved();
    } catch (ex) {
      setErr(errMsg(ex));
      setBusy(false);
    }
  }

  return (
    <div className="modal-mask" onClick={onClose}>
      <div className="modal" style={{ maxWidth: 560 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <span className="modal-title">{editing ? t('admin.roleEditTitle') : t('admin.roleCreateTitle')}</span>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>✕</button>
        </div>
        <form onSubmit={submit}>
          <div className="modal-body form">
            {err && <div className="form-error">{err}</div>}
            <div className="field">
              <label>{t('admin.codeLabel')} *</label>
              <input value={code} disabled={editing} required pattern="[a-z][a-z0-9_]{1,63}"
                     onChange={(e) => setCode(e.target.value)} />
            </div>
            <div className="field">
              <label>{t('admin.descLabel')}</label>
              <input value={description} onChange={(e) => setDescription(e.target.value)} />
            </div>
            <div className="field">
              <label>{t('admin.permsLabel')}</label>
              <div className="chip-wrap">
                {permissions.map((p) => (
                  <label key={p.code} className="filter-check" title={p.description}
                         style={{ display: editing && initial?.builtIn ? 'none' : undefined }}>
                    <input type="checkbox" checked={perms.includes(p.code)}
                           onChange={() => setPerms((prev) =>
                             prev.includes(p.code) ? prev.filter((c) => c !== p.code) : [...prev, p.code])} />
                    {p.code}
                  </label>
                ))}
                {editing && initial?.builtIn && (
                  <div className="filter-opt-list">
                    {(initial.permissions ?? []).map((c) => <div key={c} className="filter-opt active">{c}</div>)}
                  </div>
                )}
              </div>
            </div>
          </div>
          <div className="modal-foot">
            <button type="button" className="btn btn-ghost" onClick={onClose}>{t('repoForm.cancel')}</button>
            <button type="submit" className="btn btn-primary" disabled={busy}>
              {busy ? t('repoForm.submitting') : t('common.ok')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function RolesPanel() {
  const { t } = useTranslation();
  const toast = useToast();
  const [roles, setRoles] = useState<AdminRole[]>([]);
  const [permissions, setPermissions] = useState<AdminPermission[]>([]);
  const [state, setState] = useState<'loading' | 'ready' | 'empty' | 'error'>('loading');
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<AdminRole | null>(null);
  const [deleting, setDeleting] = useState<AdminRole | null>(null);
  const [delBusy, setDelBusy] = useState(false);

  function load() {
    setState('loading');
    Promise.all([api.adminRoles(), api.adminPermissions()])
      .then(([r, p]) => {
        setRoles(r.items);
        setPermissions(p.items);
        setState(r.items.length === 0 ? 'empty' : 'ready');
      })
      .catch(() => setState('error'));
  }

  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function confirmDelete() {
    if (!deleting) return;
    setDelBusy(true);
    try {
      await api.adminDeleteRole(deleting.id);
      toast.show(t('admin.opOk'));
      setDeleting(null);
      load();
    } catch (e) {
      toast.show(errMsg(e), 'err');
    } finally {
      setDelBusy(false);
    }
  }

  return (
    <div className="card card-pad">
      <div className="dir-head" style={{ marginBottom: 12 }}>
        <span className="dir-title">{t('admin.menuRoles')}</span>
        <button className="btn btn-primary btn-sm" onClick={() => setFormOpen(true)}>
          {t('admin.rolesCreateBtn')}
        </button>
      </div>
      <DataState state={state} onRetry={load}>
        <table className="repo-table">
          <thead>
            <tr>
              <th>{t('admin.thCode')}</th><th>{t('admin.thDesc')}</th>
              <th>{t('admin.thPerms')}</th><th>{t('admin.thAssignees')}</th>
              <th>{t('admin.thBuiltIn')}</th>
              <th style={{ width: 140 }}>{t('my.thActions')}</th>
            </tr>
          </thead>
          <tbody>
            {roles.map((r) => (
              <tr key={r.id}>
                <td><b>{r.code}</b></td>
                <td>{r.description}</td>
                <td>
                  {(r.permissions ?? []).slice(0, 4).map((p) => (
                    <span key={p} className="tag-chip" style={{ marginRight: 4 }}>{p}</span>
                  ))}
                  {(r.permissions ?? []).length > 4 && (
                    <span className="req-meta">+{r.permissions.length - 4}</span>
                  )}
                </td>
                <td>{r.assigneeCount}</td>
                <td>{r.builtIn ? t('admin.builtInYes') : t('admin.builtInNo')}</td>
                <td>
                  <button className="btn btn-ghost btn-sm" onClick={() => setEditing(r)}>{t('admin.editBtn')}</button>
                  {!r.builtIn && (
                    <button className="btn btn-ghost btn-sm" onClick={() => setDeleting(r)}>{t('admin.deleteBtn')}</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </DataState>

      {(formOpen || editing) && (
        <RoleFormModal initial={editing} permissions={permissions}
                       onClose={() => { setFormOpen(false); setEditing(null); }}
                       onSaved={() => {
                         setFormOpen(false); setEditing(null);
                         toast.show(t('admin.opOk')); load();
                       }} />
      )}
      {deleting && (
        <ConfirmDialog title={t('admin.deleteTitle')}
                       message={t('admin.deleteMessage', { code: deleting.code })}
                       confirmText={t('admin.deleteBtn')} busy={delBusy}
                       onConfirm={confirmDelete} onCancel={() => setDeleting(null)} />
      )}
    </div>
  );
}
