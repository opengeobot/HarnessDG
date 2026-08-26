// 菜单管理面板（字典统一+菜单权限重构计划 §七.3）：菜单树表 + 创建/编辑弹窗 + 启停用
// 菜单是权限的呈现载体：permissionCode 下拉取自 /admin/permissions，父级仅 directory。
// 时间：2026-08-26  作者：AxeXie
import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, type AdminPermission, type SysMenu } from '../../api/client';
import { errMsg } from '../../App';
import { ConfirmDialog, DataState, useToast } from '../../components/ui';

/** 客户端重算 ETag（与 shared ETags.ofVersion 同规则，04 §10）。 */
const etagOfVersion = (v: number) => '"' + (v + 0x5f000000).toString(16) + '"';

const MENU_TYPES = ['directory', 'menu', 'button'] as const;

/** 菜单表单弹窗（创建/编辑共用；编辑时 code 只读）。 */
function MenuFormModal({ initial, menus, permissions, onClose, onSaved }: {
  initial: SysMenu | null; menus: SysMenu[]; permissions: AdminPermission[];
  onClose: () => void; onSaved: () => void;
}) {
  const { t, i18n } = useTranslation();
  const editing = !!initial;
  const [code, setCode] = useState(initial?.code ?? '');
  const [nameZh, setNameZh] = useState(initial?.nameZh ?? '');
  const [nameEn, setNameEn] = useState(initial?.nameEn ?? '');
  const [parentCode, setParentCode] = useState(initial?.parentCode ?? '');
  const [menuType, setMenuType] = useState<SysMenu['menuType']>(initial?.menuType ?? 'menu');
  const [path, setPath] = useState(initial?.path ?? '');
  const [permissionCode, setPermissionCode] = useState(initial?.permissionCode ?? '');
  const [sortOrder, setSortOrder] = useState(initial?.sortOrder ?? 0);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  // 父级候选：仅 directory（服务端守卫），编辑时排除自身
  const parentOptions = menus.filter((m) => m.menuType === 'directory' && m.code !== initial?.code);
  const zh = i18n.language.startsWith('zh');

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErr(''); setBusy(true);
    try {
      if (editing && initial) {
        await api.adminUpdateMenu(initial.code, {
          nameZh, nameEn,
          parentCode: parentCode || '',
          path: path || '',
          permissionCode: permissionCode || '',
          sortOrder,
        }, initial.etag ?? etagOfVersion(initial.version));
      } else {
        await api.adminCreateMenu({
          code: code.trim(), nameZh, nameEn,
          parentCode: parentCode || undefined,
          menuType,
          path: path || undefined,
          permissionCode: permissionCode || undefined,
          sortOrder,
        });
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
          <span className="modal-title">{editing ? t('adminMenu.editTitle') : t('adminMenu.createTitle')}</span>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>✕</button>
        </div>
        <form onSubmit={submit}>
          <div className="modal-body form">
            {err && <div className="form-error">{err}</div>}
            <div className="field">
              <label>{t('adminMenu.codeLabel')} *</label>
              <input value={code} disabled={editing} required
                     placeholder="^[a-z][a-z0-9_]{2,63}$"
                     onChange={(e) => setCode(e.target.value)} />
            </div>
            <div style={{ display: 'flex', gap: 12 }}>
              <div className="field" style={{ flex: 1 }}>
                <label>{t('admin.labelZh')} *</label>
                <input value={nameZh} required onChange={(e) => setNameZh(e.target.value)} />
              </div>
              <div className="field" style={{ flex: 1 }}>
                <label>{t('admin.labelEn')} *</label>
                <input value={nameEn} required onChange={(e) => setNameEn(e.target.value)} />
              </div>
            </div>
            <div style={{ display: 'flex', gap: 12 }}>
              <div className="field" style={{ flex: 1 }}>
                <label>{t('adminMenu.typeLabel')} *</label>
                <select value={menuType} disabled={editing}
                        onChange={(e) => setMenuType(e.target.value as SysMenu['menuType'])}>
                  {MENU_TYPES.map((mt) => (
                    <option key={mt} value={mt}>{t(`adminMenu.type_${mt}`)}</option>
                  ))}
                </select>
              </div>
              <div className="field" style={{ flex: 1 }}>
                <label>{t('adminMenu.parentLabel')}</label>
                <select value={parentCode} onChange={(e) => setParentCode(e.target.value)}>
                  <option value="">{t('adminMenu.noParent')}</option>
                  {parentOptions.map((p) => (
                    <option key={p.code} value={p.code}>
                      {p.code}（{zh ? p.nameZh : p.nameEn}）
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="field">
              <label>{t('adminMenu.pathLabel')}</label>
              <input value={path} placeholder="/admin/xxx" onChange={(e) => setPath(e.target.value)} />
            </div>
            <div style={{ display: 'flex', gap: 12 }}>
              <div className="field" style={{ flex: 1 }}>
                <label>{t('adminMenu.permissionLabel')}</label>
                <select value={permissionCode} onChange={(e) => setPermissionCode(e.target.value)}>
                  <option value="">{t('adminMenu.noPermission')}</option>
                  {permissions.map((p) => (
                    <option key={p.code} value={p.code}>{p.code}</option>
                  ))}
                </select>
              </div>
              <div className="field" style={{ flex: 1 }}>
                <label>{t('admin.sortOrder')}</label>
                <input type="number" value={sortOrder}
                       onChange={(e) => setSortOrder(Number(e.target.value) || 0)} />
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

export default function MenusPanel() {
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const zh = i18n.language.startsWith('zh');
  const [menus, setMenus] = useState<SysMenu[]>([]);
  const [permissions, setPermissions] = useState<AdminPermission[]>([]);
  const [state, setState] = useState<'loading' | 'ready' | 'empty' | 'error'>('loading');
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<SysMenu | null>(null);
  const [deleting, setDeleting] = useState<SysMenu | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    setState('loading');
    Promise.all([api.adminMenus(), api.adminPermissions()])
      .then(([m, p]) => {
        setMenus(m.items);
        setPermissions(p.items);
        setState(m.items.length === 0 ? 'empty' : 'ready');
      })
      .catch(() => setState('error'));
  }, []);
  useEffect(() => { load(); }, [load]);

  function refreshAfterSave() {
    setCreating(false);
    setEditing(null);
    toast.show(t('admin.opOk'));
    load();
  }

  async function toggle(menu: SysMenu) {
    try {
      await api.adminSetMenuStatus(menu.code, menu.status !== 'active');
      toast.show(t('admin.opOk'));
      load();
    } catch (e) {
      toast.show(errMsg(e), 'err');
    }
  }

  async function confirmDelete() {
    if (!deleting) return;
    setBusy(true);
    try {
      await api.adminDeleteMenu(deleting.code);
      toast.show(t('admin.opOk'));
      setDeleting(null);
      load();
    } catch (e) {
      toast.show(errMsg(e), 'err');
    } finally {
      setBusy(false);
    }
  }

  // 树形展示：目录在前，其子项紧随其后缩进（服务端已按 sortOrder 排序）
  const ordered: Array<{ menu: SysMenu; depth: number }> = [];
  for (const dir of menus.filter((m) => !m.parentCode)) {
    ordered.push({ menu: dir, depth: 0 });
    for (const child of menus.filter((m) => m.parentCode === dir.code)) {
      ordered.push({ menu: child, depth: 1 });
    }
  }
  for (const m of menus.filter((m) => m.parentCode && !menus.some((p) => p.code === m.parentCode))) {
    ordered.push({ menu: m, depth: 1 });
  }

  const typeBadge = (mt: SysMenu['menuType']) =>
    mt === 'directory' ? 'badge-public' : mt === 'menu' ? 'badge-pending' : 'badge-gated';

  return (
    <>
      <div className="card card-pad">
        <div className="dir-head" style={{ marginBottom: 12 }}>
          <span className="dir-title">{t('admin.menuMenus')}</span>
          <button className="btn btn-primary btn-sm" onClick={() => setCreating(true)}>
            {t('adminMenu.createBtn')}
          </button>
        </div>
        <DataState state={state} onRetry={load}>
          <table className="repo-table">
            <thead>
              <tr>
                <th>{t('adminMenu.codeLabel')}</th>
                <th>{t('adminMenu.nameLabel')}</th>
                <th>{t('adminMenu.typeLabel')}</th>
                <th>{t('adminMenu.pathLabel')}</th>
                <th>{t('adminMenu.permissionLabel')}</th>
                <th>{t('admin.sortOrder')}</th>
                <th>{t('admin.thStatus')}</th>
                <th style={{ width: 200 }}>{t('my.thActions')}</th>
              </tr>
            </thead>
            <tbody>
              {ordered.map(({ menu, depth }) => (
                <tr key={menu.code}>
                  <td>
                    <b style={depth > 0 ? { paddingLeft: 22, fontWeight: 500 } : undefined}>
                      {depth > 0 ? '└─ ' : ''}{menu.code}
                    </b>
                  </td>
                  <td>{zh ? menu.nameZh : menu.nameEn}</td>
                  <td>
                    <span className={`badge ${typeBadge(menu.menuType)}`}>
                      {t(`adminMenu.type_${menu.menuType}`)}
                    </span>
                  </td>
                  <td>{menu.path ?? '—'}</td>
                  <td>{menu.permissionCode ?? '—'}</td>
                  <td>{menu.sortOrder}</td>
                  <td>
                    <span className={`badge ${menu.status === 'active' ? 'badge-public' : 'badge-pending'}`}>
                      {menu.status}
                    </span>
                  </td>
                  <td>
                    <button className="btn btn-ghost btn-sm" onClick={() => setEditing(menu)}>{t('admin.editBtn')}</button>
                    <button className="btn btn-ghost btn-sm" onClick={() => toggle(menu)}>
                      {menu.status === 'active' ? t('admin.actDisable') : t('admin.actEnable')}
                    </button>
                    <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => setDeleting(menu)}>
                      {t('admin.deleteBtn')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </DataState>
      </div>

      {(creating || editing) && (
        <MenuFormModal initial={editing} menus={menus} permissions={permissions}
                       onClose={() => { setCreating(false); setEditing(null); }}
                       onSaved={refreshAfterSave} />
      )}
      {deleting && (
        <ConfirmDialog title={t('adminMenu.deleteTitle')}
                       message={t('adminMenu.deleteMessage', { code: deleting.code })}
                       confirmText={t('admin.deleteBtn')} busy={busy}
                       onConfirm={confirmDelete} onCancel={() => setDeleting(null)} />
      )}
    </>
  );
}
