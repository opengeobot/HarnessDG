// 字典管理面板：字典列表 + 选中字典的字典项 CRUD（有项字典禁删，服务端 409）
// 时间：2026-08-25  作者：AxeXie
import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, type AdminDict, type AdminDictItem } from '../../api/client';
import { errMsg } from '../../App';
import { ConfirmDialog, DataState, useToast } from '../../components/ui';

/** 客户端重算 ETag（与 shared ETags.ofVersion 同规则，04 §10）。 */
const etagOfVersion = (v: number) => '"' + (v + 0x5f000000).toString(16) + '"';

/** 字典表单弹窗（创建/编辑共用；编辑时 dictCode 禁用）。 */
function DictFormModal({ initial, onClose, onSaved }: {
  initial: AdminDict | null; onClose: () => void; onSaved: () => void;
}) {
  const { t } = useTranslation();
  const editing = !!initial;
  const [dictCode, setDictCode] = useState(initial?.dictCode ?? '');
  const [name, setName] = useState(initial?.name ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErr(''); setBusy(true);
    try {
      if (editing && initial) {
        await api.adminUpdateDict(initial.id, { name, description },
          initial.etag ?? etagOfVersion(initial.version));
      } else {
        await api.adminCreateDict({ dictCode: dictCode.trim(), name: name.trim(), description });
      }
      onSaved();
    } catch (ex) {
      setErr(errMsg(ex));
      setBusy(false);
    }
  }

  return (
    <div className="modal-mask" onClick={onClose}>
      <div className="modal" style={{ maxWidth: 520 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <span className="modal-title">{editing ? t('admin.dictEditTitle') : t('admin.dictCreateTitle')}</span>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>✕</button>
        </div>
        <form onSubmit={submit}>
          <div className="modal-body form">
            {err && <div className="form-error">{err}</div>}
            <div className="field">
              <label>{t('admin.dictCodeLabel')} *</label>
              <input value={dictCode} disabled={editing} required
                     onChange={(e) => setDictCode(e.target.value)} />
            </div>
            <div className="field">
              <label>{t('admin.thDictName')} *</label>
              <input value={name} required onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="field">
              <label>{t('admin.descLabel')}</label>
              <input value={description} onChange={(e) => setDescription(e.target.value)} />
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

/** 字典项表单弹窗（新增/编辑共用；编辑时 itemValue 只读）。 */
function ItemFormModal({ dictId, initial, onClose, onSaved }: {
  dictId: string; initial: AdminDictItem | null; onClose: () => void; onSaved: () => void;
}) {
  const { t } = useTranslation();
  const editing = !!initial;
  const [itemValue, setItemValue] = useState(initial?.itemValue ?? '');
  const [labelZh, setLabelZh] = useState(initial?.labelZh ?? '');
  const [labelEn, setLabelEn] = useState(initial?.labelEn ?? '');
  const [sortOrder, setSortOrder] = useState(initial?.sortOrder ?? 0);
  const [remark, setRemark] = useState(initial?.remark ?? '');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErr(''); setBusy(true);
    try {
      if (editing && initial) {
        await api.adminUpdateDictItem(dictId, initial.id, { labelZh, labelEn, sortOrder, remark });
      } else {
        await api.adminCreateDictItem(dictId, { itemValue: itemValue.trim(), labelZh, labelEn, sortOrder, remark });
      }
      onSaved();
    } catch (ex) {
      setErr(errMsg(ex));
      setBusy(false);
    }
  }

  return (
    <div className="modal-mask" onClick={onClose}>
      <div className="modal" style={{ maxWidth: 520 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <span className="modal-title">{editing ? t('admin.editItem') : t('admin.addItem')}</span>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>✕</button>
        </div>
        <form onSubmit={submit}>
          <div className="modal-body form">
            {err && <div className="form-error">{err}</div>}
            <div className="field">
              <label>{t('admin.itemValue')} *</label>
              <input value={itemValue} disabled={editing} required
                     onChange={(e) => setItemValue(e.target.value)} />
            </div>
            <div style={{ display: 'flex', gap: 12 }}>
              <div className="field" style={{ flex: 1 }}>
                <label>{t('admin.labelZh')}</label>
                <input value={labelZh} onChange={(e) => setLabelZh(e.target.value)} />
              </div>
              <div className="field" style={{ flex: 1 }}>
                <label>{t('admin.labelEn')}</label>
                <input value={labelEn} onChange={(e) => setLabelEn(e.target.value)} />
              </div>
            </div>
            <div className="field">
              <label>{t('admin.sortOrder')}</label>
              <input type="number" value={sortOrder}
                     onChange={(e) => setSortOrder(Number(e.target.value) || 0)} />
            </div>
            <div className="field">
              <label>{t('admin.remark')}</label>
              <input value={remark} onChange={(e) => setRemark(e.target.value)} />
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

export default function DictsPanel() {
  const { t } = useTranslation();
  const toast = useToast();
  const [dicts, setDicts] = useState<AdminDict[]>([]);
  const [state, setState] = useState<'loading' | 'ready' | 'empty' | 'error'>('loading');
  const [selected, setSelected] = useState<AdminDict | null>(null);
  const [items, setItems] = useState<AdminDictItem[]>([]);
  const [itemsState, setItemsState] = useState<'loading' | 'ready' | 'empty' | 'error'>('loading');

  const [dictForm, setDictForm] = useState(false);
  const [editingDict, setEditingDict] = useState<AdminDict | null>(null);
  const [deletingDict, setDeletingDict] = useState<AdminDict | null>(null);
  const [itemForm, setItemForm] = useState(false);
  const [editingItem, setEditingItem] = useState<AdminDictItem | null>(null);
  const [busy, setBusy] = useState(false);

  const loadDicts = useCallback(() => {
    setState('loading');
    api.adminDicts()
      .then((d) => { setDicts(d.items); setState(d.items.length === 0 ? 'empty' : 'ready'); })
      .catch(() => setState('error'));
  }, []);
  useEffect(() => { loadDicts(); }, [loadDicts]);

  const loadItems = useCallback((dict: AdminDict) => {
    setItemsState('loading');
    api.adminDictItems(dict.id)
      .then((d) => { setItems(d.items); setItemsState(d.items.length === 0 ? 'empty' : 'ready'); })
      .catch(() => setItemsState('error'));
  }, []);

  function pick(dict: AdminDict) {
    setSelected(dict);
    loadItems(dict);
  }

  function refreshAfterItemChange() {
    setItemForm(false);
    setEditingItem(null);
    toast.show(t('admin.opOk'));
    if (selected) loadItems(selected);
    loadDicts();
  }

  async function deleteItem(item: AdminDictItem) {
    if (!selected) return;
    setBusy(true);
    try {
      await api.adminDeleteDictItem(selected.id, item.id);
      toast.show(t('admin.opOk'));
      loadItems(selected);
      loadDicts();
    } catch (e) {
      toast.show(errMsg(e), 'err');
    } finally {
      setBusy(false);
    }
  }

  async function toggleItem(item: AdminDictItem) {
    if (!selected) return;
    try {
      await api.adminSetDictItemStatus(selected.id, item.id, item.status !== 'active');
      toast.show(t('admin.opOk'));
      loadItems(selected);
    } catch (e) {
      toast.show(errMsg(e), 'err');
    }
  }

  async function confirmDeleteDict() {
    if (!deletingDict) return;
    setBusy(true);
    try {
      await api.adminDeleteDict(deletingDict.id);
      toast.show(t('admin.opOk'));
      if (selected?.id === deletingDict.id) { setSelected(null); setItems([]); }
      setDeletingDict(null);
      loadDicts();
    } catch (e) {
      toast.show(errMsg(e), 'err');
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <div className="card card-pad">
        <div className="dir-head" style={{ marginBottom: 12 }}>
          <span className="dir-title">{t('admin.menuDicts')}</span>
          <button className="btn btn-primary btn-sm" onClick={() => setDictForm(true)}>
            {t('admin.dictsCreateBtn')}
          </button>
        </div>
        <DataState state={state} onRetry={loadDicts}>
          <table className="repo-table">
            <thead>
              <tr>
                <th>{t('admin.thDictCode')}</th><th>{t('admin.thDictName')}</th>
                <th>{t('admin.thDesc')}</th><th>{t('admin.thItems')}</th>
                <th style={{ width: 180 }}>{t('my.thActions')}</th>
              </tr>
            </thead>
            <tbody>
              {dicts.map((d) => (
                <tr key={d.id} className="repo-row" onClick={() => pick(d)}
                    style={selected?.id === d.id ? { background: 'rgba(39,84,240,.06)' } : undefined}>
                  <td><b>{d.dictCode}</b></td>
                  <td>{d.name}</td>
                  <td>{d.description}</td>
                  <td>{d.itemCount}</td>
                  <td onClick={(e) => e.stopPropagation()}>
                    <button className="btn btn-ghost btn-sm" onClick={() => setEditingDict(d)}>{t('admin.editBtn')}</button>
                    <button className="btn btn-ghost btn-sm" onClick={() => setDeletingDict(d)}>{t('admin.deleteBtn')}</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </DataState>
      </div>

      {selected && (
        <div className="card card-pad" style={{ marginTop: 16 }}>
          <div className="dir-head" style={{ marginBottom: 12 }}>
            <span className="dir-title">{t('admin.itemsTitle', { name: selected.name })}</span>
            <button className="btn btn-primary btn-sm" onClick={() => setItemForm(true)}>
              {t('admin.addItem')}
            </button>
          </div>
          <DataState state={itemsState} onRetry={() => loadItems(selected)}>
            <table className="repo-table">
              <thead>
                <tr>
                  <th>{t('admin.itemValue')}</th><th>{t('admin.labelZh')}</th><th>{t('admin.labelEn')}</th>
                  <th>{t('admin.sortOrder')}</th><th>{t('admin.thStatus')}</th><th>{t('admin.remark')}</th>
                  <th style={{ width: 200 }}>{t('my.thActions')}</th>
                </tr>
              </thead>
              <tbody>
                {items.map((it) => (
                  <tr key={it.id}>
                    <td><b>{it.itemValue}</b></td>
                    <td>{it.labelZh}</td>
                    <td>{it.labelEn}</td>
                    <td>{it.sortOrder}</td>
                    <td>
                      <span className={`badge ${it.status === 'active' ? 'badge-public' : 'badge-pending'}`}>
                        {it.status}
                      </span>
                    </td>
                    <td>{it.remark}</td>
                    <td>
                      <button className="btn btn-ghost btn-sm" onClick={() => setEditingItem(it)}>{t('admin.editBtn')}</button>
                      <button className="btn btn-ghost btn-sm" onClick={() => toggleItem(it)}>
                        {it.status === 'active' ? t('admin.actDisable') : t('admin.actEnable')}
                      </button>
                      <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => deleteItem(it)}>
                        {t('admin.deleteBtn')}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </DataState>
        </div>
      )}

      {(dictForm || editingDict) && (
        <DictFormModal initial={editingDict}
                       onClose={() => { setDictForm(false); setEditingDict(null); }}
                       onSaved={() => {
                         setDictForm(false); setEditingDict(null);
                         toast.show(t('admin.opOk')); loadDicts();
                       }} />
      )}
      {selected && (itemForm || editingItem) && (
        <ItemFormModal dictId={selected.id} initial={editingItem}
                       onClose={() => { setItemForm(false); setEditingItem(null); }}
                       onSaved={refreshAfterItemChange} />
      )}
      {deletingDict && (
        <ConfirmDialog title={t('admin.dictDeleteTitle')}
                       message={t('admin.dictDeleteMessage', { code: deletingDict.dictCode })}
                       confirmText={t('admin.deleteBtn')} busy={busy}
                       onConfirm={confirmDeleteDict} onCancel={() => setDeletingDict(null)} />
      )}
    </>
  );
}
