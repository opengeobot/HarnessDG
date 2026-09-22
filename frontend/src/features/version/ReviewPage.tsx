/**
 * 功能: 发布审批页面——真实版本查询、提交发布请求、审批决策。
 *       后端契约（ReviewController）只有提交与决策两个 POST 端点，没有发布请求
 *       列表/读取端点，因此审批队列仅包含本会话内以真实 requestId 提交的请求，
 *       不渲染任何派生或虚构的行（TASK-P0BR-065 / AC-P0B-UI-003）。
 * 时间: 2026-09-22
 * 作者: AxeXie
 */
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useDocumentTitle } from '@/shared/hooks';
import { apiClient } from '@/shared/api';
import { listVersions } from './api';
import type { VersionView } from './types';

/** 本会话内提交成功的发布请求（requestId 全部来自后端真实响应）。 */
interface SessionRequest {
  requestId: string;
  versionId: string;
  versionLabel: string;
  /** SUBMITTED=待决策；其余值为已提交的本会话决策（如 DECIDED:APPROVE）。 */
  status: string;
}

export function ReviewPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('review.title'));
  const [assetId, setAssetId] = useState('');
  const [versions, setVersions] = useState<VersionView[]>([]);
  const [loading, setLoading] = useState(false);
  const [queried, setQueried] = useState(false);
  const [requests, setRequests] = useState<SessionRequest[]>([]);
  const [selectedRequest, setSelectedRequest] = useState<SessionRequest | null>(null);
  const [decision, setDecision] = useState<'APPROVE' | 'REJECT'>('APPROVE');
  const [comments, setComments] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const loadVersions = useCallback(async () => {
    if (!assetId) return;
    setLoading(true);
    setError(null);
    try {
      const page = await listVersions(assetId);
      setVersions(page.items ?? []);
      setQueried(true);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('review.loadFailedMsg'));
    } finally {
      setLoading(false);
    }
  }, [assetId, t]);

  const submitPublishRequest = async (v: VersionView) => {
    setError(null);
    setMessage(null);
    try {
      const result = await apiClient.post<{ requestId: string }>(
        `/versions/${v.versionId}/publish-requests`,
      );
      const created: SessionRequest = {
        requestId: result.requestId,
        versionId: v.versionId,
        versionLabel: v.version,
        status: 'SUBMITTED',
      };
      setRequests((prev) => [created, ...prev.filter((r) => r.requestId !== created.requestId)]);
      setMessage(t('review.requestSubmitted', { id: result.requestId }));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('review.submitFailedMsg'));
    }
  };

  const submitDecision = async (requestId: string) => {
    setError(null);
    try {
      await apiClient.post(`/publish-requests/${requestId}/decisions`, {
        decision,
        comments,
      });
      setRequests((prev) =>
        prev.map((r) => (r.requestId === requestId ? { ...r, status: `DECIDED:${decision}` } : r)),
      );
      setMessage(t('review.decisionSubmitted', { decision: t(decision === 'APPROVE' ? 'review.approve' : 'review.reject') }));
      setComments('');
      setSelectedRequest(null);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('review.decisionFailed'));
    }
  };

  const pendingVersions = versions.filter((v) => v.status === 'PENDING_REVIEW');
  const selectedIsOpen = selectedRequest?.status === 'SUBMITTED';

  return (
    <div className="max-w-6xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-4">{t('review.title')}</h1>

      <div className="mb-4 flex gap-2">
        <input
          className="border rounded px-3 py-2 flex-1"
          placeholder={t('version.assetIdPlaceholder')}
          value={assetId}
          onChange={(e) => setAssetId(e.target.value)}
        />
        <button
          className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
          onClick={loadVersions}
        >
          {t('common.query')}
        </button>
      </div>

      {error && (
        <div className="mb-4 flex items-center gap-3">
          <p className="text-red-600" role="alert">{error}</p>
          <button
            className="text-sm px-3 py-1 border rounded hover:bg-gray-100"
            onClick={loadVersions}
          >
            {t('common.retry')}
          </button>
        </div>
      )}
      {message && <p className="text-green-600 mb-4">{message}</p>}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div>
          <h2 className="text-lg font-semibold mb-2">{t('review.pendingVersions')}</h2>
          {loading && <p className="text-gray-500">{t('common.loading')}</p>}
          {!loading && !queried && <p className="text-gray-400 text-sm">{t('review.queryHint')}</p>}
          {!loading && queried && pendingVersions.length === 0 && (
            <p className="text-gray-400 text-sm">{t('review.emptyPending')}</p>
          )}
          {!loading && (
            <div className="space-y-2">
              {pendingVersions.map((v) => (
                <div key={v.versionId} className="p-3 border rounded">
                  <div className="flex items-center justify-between">
                    <span className="font-mono font-semibold">{v.version}</span>
                    <span className="text-xs px-2 py-0.5 rounded bg-blue-100 text-blue-800">
                      {v.status}
                    </span>
                  </div>
                  <div className="text-sm text-gray-500 mt-1">
                    {t('version.versionId')}: <span className="font-mono">{v.versionId}</span>
                  </div>
                  <button
                    className="mt-2 text-xs px-2 py-1 bg-blue-600 text-white rounded hover:bg-blue-700"
                    onClick={() => submitPublishRequest(v)}
                  >
                    {t('review.submitPublish')}
                  </button>
                </div>
              ))}
            </div>
          )}

          <h2 className="text-lg font-semibold mt-6 mb-2">{t('review.sessionRequests')}</h2>
          <div className="space-y-2">
            {requests.map((r) => (
              <div
                key={r.requestId}
                className={`p-3 border rounded cursor-pointer transition ${
                  selectedRequest?.requestId === r.requestId
                    ? 'border-blue-500 bg-blue-50'
                    : 'hover:bg-gray-50'
                }`}
                onClick={() => setSelectedRequest(r)}
              >
                <div className="flex items-center justify-between">
                  <span className="font-mono text-sm">{r.requestId}</span>
                  <span
                    className={`text-xs px-2 py-0.5 rounded ${
                      r.status === 'SUBMITTED'
                        ? 'bg-blue-100 text-blue-800'
                        : 'bg-green-100 text-green-800'
                    }`}
                  >
                    {r.status}
                  </span>
                </div>
                <div className="text-sm text-gray-500 mt-1">
                  {t('version.version')}: {r.versionLabel}
                </div>
              </div>
            ))}
            {requests.length === 0 && (
              <p className="text-gray-400 text-sm">{t('review.noSessionRequests')}</p>
            )}
          </div>
        </div>

        <div>
          <h2 className="text-lg font-semibold mb-2">{t('review.decisionPanel')}</h2>
          {selectedRequest ? (
            <div className="space-y-4">
              <div className="border rounded p-4 space-y-2">
                <div className="flex justify-between">
                  <span className="text-gray-500">{t('review.requestId')}</span>
                  <span className="font-mono text-sm">{selectedRequest.requestId}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">{t('version.versionId')}</span>
                  <span className="font-mono text-sm">{selectedRequest.versionId}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">{t('common.status')}</span>
                  <span>{selectedRequest.status}</span>
                </div>
              </div>

              {selectedIsOpen && (
                <div className="space-y-3">
                  <div>
                    <label className="block text-sm text-gray-600 mb-1">{t('review.decision')}</label>
                    <select
                      className="border rounded px-3 py-2 w-full"
                      value={decision}
                      onChange={(e) => setDecision(e.target.value as 'APPROVE' | 'REJECT')}
                    >
                      <option value="APPROVE">{t('review.approve')}</option>
                      <option value="REJECT">{t('review.reject')}</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm text-gray-600 mb-1">{t('review.comments')}</label>
                    <textarea
                      className="border rounded px-3 py-2 w-full"
                      rows={3}
                      value={comments}
                      onChange={(e) => setComments(e.target.value)}
                      placeholder={t('review.commentsPlaceholder')}
                    />
                  </div>
                  <button
                    className={`px-4 py-2 text-white rounded ${
                      decision === 'APPROVE'
                        ? 'bg-green-600 hover:bg-green-700'
                        : 'bg-red-600 hover:bg-red-700'
                    }`}
                    onClick={() => submitDecision(selectedRequest.requestId)}
                  >
                    {t('review.submitDecision')}
                  </button>
                </div>
              )}

              {!selectedIsOpen && (
                <p className="text-gray-500 text-sm">{t('review.alreadyProcessed')}</p>
              )}
            </div>
          ) : (
            <p className="text-gray-400">{t('review.selectRequest')}</p>
          )}
        </div>
      </div>
    </div>
  );
}
