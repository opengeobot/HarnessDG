/**
 * 功能: 发布审批页面——版本列表、提交发布、审批决策。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useDocumentTitle } from '@/shared/hooks';
import { apiClient } from '@/shared/api';

interface PublishRequest {
  requestId: string;
  versionId: string;
  frozenDigest: string;
  status: string;
  submittedBy: string;
  submittedAt: string;
}

const STATUS_COLORS: Record<string, string> = {
  SUBMITTED: 'bg-blue-100 text-blue-800',
  APPROVED: 'bg-green-100 text-green-800',
  REJECTED: 'bg-red-100 text-red-800',
  PUBLISHING: 'bg-yellow-100 text-yellow-800',
  PUBLISHED: 'bg-green-200 text-green-900',
  FAILED: 'bg-red-200 text-red-900',
};

export function ReviewPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('review.title'));
  const [assetId, setAssetId] = useState('');
  const [requests, setRequests] = useState<PublishRequest[]>([]);
  const [selectedRequest, setSelectedRequest] = useState<PublishRequest | null>(null);
  const [decision, setDecision] = useState<'APPROVE' | 'REJECT'>('APPROVE');
  const [comments, setComments] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const loadRequests = useCallback(async () => {
    if (!assetId) return;
    setError(null);
    try {
      const versions = await apiClient.get<Array<{ versionId: string; version: string; status: string }>>(
        `/assets/${assetId}/versions`,
      );
      const publishedRequests = versions
        .filter((v) => ['PENDING_REVIEW', 'PUBLISHED'].includes(v.status))
        .map((v) => ({
          requestId: `pub_${v.versionId}`,
          versionId: v.versionId,
          frozenDigest: 'pending',
          status: v.status === 'PUBLISHED' ? 'PUBLISHED' : 'SUBMITTED',
          submittedBy: 'system',
          submittedAt: new Date().toISOString(),
        }));
      setRequests(publishedRequests);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('review.loadFailedMsg'));
    }
  }, [assetId, t]);

  const submitPublishRequest = async (versionId: string) => {
    try {
      const result = await apiClient.post<{ requestId: string }>(
        `/versions/${versionId}/publish-requests`,
      );
      setMessage(t('review.requestSubmitted', { id: result.requestId }));
      loadRequests();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('review.submitFailedMsg'));
    }
  };

  const submitDecision = async (requestId: string) => {
    try {
      await apiClient.post(`/publish-requests/${requestId}/decisions`, {
        decision,
        comments,
      });
      setMessage(t('review.decisionSubmitted', { decision }));
      setComments('');
      setSelectedRequest(null);
      loadRequests();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('review.decisionFailed'));
    }
  };

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
          onClick={loadRequests}
        >
          {t('common.query')}
        </button>
      </div>

      {error && <p className="text-red-600 mb-4">{error}</p>}
      {message && <p className="text-green-600 mb-4">{message}</p>}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div>
          <h2 className="text-lg font-semibold mb-2">{t('review.publishRequests')}</h2>
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
                  <span className="font-mono text-sm">{r.versionId}</span>
                  <span
                    className={`text-xs px-2 py-0.5 rounded ${STATUS_COLORS[r.status] ?? 'bg-gray-100'}`}
                  >
                    {r.status}
                  </span>
                </div>
                <div className="text-sm text-gray-500 mt-1">
                  {t('review.submitter')}: {r.submittedBy} | {new Date(r.submittedAt).toLocaleString()}
                </div>
                {r.status === 'SUBMITTED' && (
                  <button
                    className="mt-2 text-xs px-2 py-1 bg-blue-100 rounded hover:bg-blue-200"
                    onClick={(e) => {
                      e.stopPropagation();
                      submitPublishRequest(r.versionId);
                    }}
                  >
                    {t('review.resubmit')}
                  </button>
                )}
              </div>
            ))}
            {requests.length === 0 && (
              <p className="text-gray-400 text-sm">{t('review.noRequests')}</p>
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
                  <span className="text-gray-500">{t('review.frozenDigest')}</span>
                  <span className="font-mono text-xs">{selectedRequest.frozenDigest}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">{t('common.status')}</span>
                  <span className={STATUS_COLORS[selectedRequest.status] ?? ''}>
                    {selectedRequest.status}
                  </span>
                </div>
              </div>

              {selectedRequest.status === 'SUBMITTED' && (
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

              {selectedRequest.status !== 'SUBMITTED' && (
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
