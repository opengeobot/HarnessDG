// 通用展示组件 — 页码分页（09 §2.4）/ 确认对话框 / Toast（09 §2.5）
// 时间：2026-08-21  作者：AxeXie
import React, { createContext, useCallback, useContext, useState } from 'react';
import { useTranslation } from 'react-i18next';

/** 页码分页（09 §2.4）：≤7 页全展；否则首页 + 当前邻域 + 省略号 + 末页 + 跳页输入。 */
export function Pagination({ page, pageSize, total, onChange }: {
  page: number; pageSize: number; total: number; onChange: (p: number) => void;
}) {
  const { t } = useTranslation();
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const [jump, setJump] = useState('');
  if (totalPages <= 1) return null;

  // 页码序列：≤7 全展；否则 1 … 当前±2 … 末页（边界收敛，不产生相邻省略号）
  const pages: Array<number | 'gap-l' | 'gap-r'> = [];
  if (totalPages <= 7) {
    for (let i = 1; i <= totalPages; i++) pages.push(i);
  } else {
    const lo = Math.max(2, page - 2);
    const hi = Math.min(totalPages - 1, page + 2);
    pages.push(1);
    if (lo > 2) pages.push('gap-l');
    for (let i = lo; i <= hi; i++) pages.push(i);
    if (hi < totalPages - 1) pages.push('gap-r');
    pages.push(totalPages);
  }

  function doJump() {
    const n = parseInt(jump, 10);
    setJump('');
    // 越界值忽略（09 §2.4）
    if (!Number.isNaN(n) && n >= 1 && n <= totalPages && n !== page) onChange(n);
  }

  return (
    <div className="pager pager-nums">
      <button className="btn btn-ghost btn-sm" disabled={page <= 1} onClick={() => onChange(page - 1)}>
        {t('common.prevPage')}
      </button>
      {pages.map((p) => p === 'gap-l' || p === 'gap-r' ? (
        <span key={p} className="pager-gap">…</span>
      ) : (
        <button key={p} className={`pager-num ${p === page ? 'active' : ''}`}
                onClick={() => p !== page && onChange(p)}>{p}</button>
      ))}
      <button className="btn btn-ghost btn-sm" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>
        {t('common.nextPage')}
      </button>
      <span className="pager-jump">
        {t('common.jumpTo')}
        <input value={jump} onChange={(e) => setJump(e.target.value)}
               onKeyDown={(e) => { if (e.key === 'Enter') doJump(); }} />
        {t('common.pageUnit')}
        <button className="btn btn-ghost btn-sm" onClick={doJump}>{t('common.ok')}</button>
      </span>
    </div>
  );
}

/** 危险操作确认对话框（09 §2.5）。 */
export function ConfirmDialog({ title, message, confirmText, busy, onConfirm, onCancel }: {
  title: string; message: string; confirmText?: string; busy?: boolean;
  onConfirm: () => void; onCancel: () => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="modal-mask" onClick={onCancel}>
      <div className="modal" style={{ maxWidth: 400 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <span className="modal-title">{title}</span>
        </div>
        <div className="modal-body">{message}</div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onCancel}>{t('common.cancel')}</button>
          <button className="btn btn-danger" disabled={busy} onClick={onConfirm}>
            {busy ? t('common.processing') : (confirmText ?? t('common.confirm'))}
          </button>
        </div>
      </div>
    </div>
  );
}

// ---------- Toast（约 2.6s 自动消失，09 §2.5） ----------

interface ToastCtx { show: (msg: string, kind?: 'ok' | 'err') => void; }
const ToastContext = createContext<ToastCtx>({ show: () => {} });
export const useToast = () => useContext(ToastContext);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toast, setToast] = useState<{ msg: string; kind: 'ok' | 'err' } | null>(null);
  const show = useCallback((msg: string, kind: 'ok' | 'err' = 'ok') => {
    setToast({ msg, kind });
    setTimeout(() => setToast(null), 2600);
  }, []);
  return (
    <ToastContext.Provider value={{ show }}>
      {children}
      {toast && (
        <div className={toast.kind === 'ok' ? 'form-success toast-float' : 'form-error toast-float'}>
          {toast.msg}
        </div>
      )}
    </ToastContext.Provider>
  );
}

/** 数据区六态渲染（09 §2.6）：loading/empty/error/forbidden/degraded/retry。 */
export function DataState({ state, errMsg, onRetry, children }: {
  state: 'loading' | 'ready' | 'empty' | 'error' | 'forbidden';
  errMsg?: string; onRetry?: () => void; children?: React.ReactNode;
}) {
  const { t } = useTranslation();
  if (state === 'loading') return <div className="loading">{t('common.loading')}</div>;
  if (state === 'empty') return <div className="empty-state">{t('common.noData')}</div>;
  if (state === 'forbidden') {
    return <div className="empty-state">{t('common.forbidden')}</div>;
  }
  if (state === 'error') {
    return (
      <div className="empty-state">
        <div className="form-error" style={{ display: 'inline-block' }}>{errMsg || t('common.loadFailed')}</div>
        {onRetry && (
          <div style={{ marginTop: 12 }}>
            <button className="btn btn-ghost btn-sm" onClick={onRetry}>{t('common.retry')}</button>
          </div>
        )}
      </div>
    );
  }
  return <>{children}</>;
}
