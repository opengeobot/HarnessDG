/**
 * 功能: 发布审批页面——版本列表、校验报告、工件差异、提交发布与审批决策。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useDocumentTitle } from '@/shared/hooks';
import { apiClient } from '@/shared/api';
import {
  getValidationReport,
  listArtifacts,
  listDecisions,
  listPublishRequests,
  listVersions,
} from './api';
import type { ArtifactView } from './types';

interface PublishRequest {
  requestId: string;
  versionId: string;
  frozenDigest: string;
  frozenSourceCommit: string;
  status: string;
  submittedBy: string;
  submittedAt: string;
}

type ReviewDecision = 'APPROVE' | 'REJECT' | 'REQUEST_CHANGES';

interface ValidationFinding {
  code?: string;
  severity?: string;
  message?: string;
  path?: string;
}

interface ArtifactDiffEntry {
  path: string;
  change: 'added' | 'removed' | 'changed';
  currentSha?: string;
  previousSha?: string;
}

const STATUS_COLORS: Record<string, string> = {
  SUBMITTED: 'bg-blue-100 text-blue-800',
  APPROVED: 'bg-green-100 text-green-800',
  REJECTED: 'bg-red-100 text-red-800',
  CHANGES_REQUESTED: 'bg-orange-100 text-orange-800',
  PUBLISHING: 'bg-yellow-100 text-yellow-800',
  PUBLISHED: 'bg-green-200 text-green-900',
  FAILED: 'bg-red-200 text-red-900',
};

function computeArtifactDiff(
  current: ArtifactView[],
  previous: ArtifactView[],
): ArtifactDiffEntry[] {
  const prevByPath = new Map(previous.map((a) => [a.path, a]));
  const currByPath = new Map(current.map((a) => [a.path, a]));
  const paths = new Set([...prevByPath.keys(), ...currByPath.keys()]);
  const diff: ArtifactDiffEntry[] = [];

  for (const path of [...paths].sort()) {
    const prev = prevByPath.get(path);
    const curr = currByPath.get(path);
    if (prev && !curr) {
      diff.push({ path, change: 'removed', previousSha: prev.sha256 });
    } else if (!prev && curr) {
      diff.push({ path, change: 'added', currentSha: curr.sha256 });
    } else if (prev && curr && prev.sha256 !== curr.sha256) {
      diff.push({
        path,
        change: 'changed',
        currentSha: curr.sha256,
        previousSha: prev.sha256,
      });
    }
  }
  return diff;
}

export function ReviewPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('review.title'));
  const [assetId, setAssetId] = useState('');
  const [requests, setRequests] = useState<PublishRequest[]>([]);
  const [selectedRequest, setSelectedRequest] = useState<PublishRequest | null>(null);
  const [decision, setDecision] = useState<ReviewDecision>('APPROVE');
  const [comments, setComments] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [validationReport, setValidationReport] = useState<Record<string, unknown> | null>(null);
  const [findings, setFindings] = useState<ValidationFinding[]>([]);
  const [artifactDiff, setArtifactDiff] = useState<ArtifactDiffEntry[]>([]);
  const [decisionHistory, setDecisionHistory] = useState<Record<string, unknown>[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);

  const loadRequests = useCallback(async () => {
    if (!assetId) return;
    setError(null);
    try {
      const rows = await listPublishRequests(assetId);
      const mapped: PublishRequest[] = rows.map((r) => ({
        requestId: String(r.request_id),
        versionId: String(r.version_id),
        frozenDigest: String(r.frozen_digest ?? ''),
        frozenSourceCommit: String(r.frozen_source_commit ?? ''),
        status: String(r.status),
        submittedBy: String(r.submitted_by),
        submittedAt: String(r.created_at),
      }));
      setRequests(mapped);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('review.loadFailedMsg'));
    }
  }, [assetId, t]);

  useEffect(() => {
    if (!selectedRequest || !assetId) {
      setValidationReport(null);
      setFindings([]);
      setArtifactDiff([]);
      setDecisionHistory([]);
      return;
    }

    let cancelled = false;
    const loadDetails = async () => {
      setDetailLoading(true);
      try {
        const [report, artifacts, versions, decisions] = await Promise.all([
          getValidationReport(assetId, selectedRequest.versionId),
          listArtifacts(assetId, selectedRequest.versionId),
          listVersions(assetId, undefined, 50),
          listDecisions(assetId, selectedRequest.requestId),
        ]);
        if (cancelled) return;

        setValidationReport(report.status === 'NOT_FOUND' ? null : report);
        const rawFindings = Array.isArray(report.findings) ? report.findings : [];
        setFindings(
          rawFindings.map((f) => {
            const row = f as Record<string, unknown>;
            return {
              code: String(row.code ?? ''),
              severity: String(row.severity ?? ''),
              message: String(row.message ?? row.summary ?? ''),
              path: String(row.path ?? ''),
            };
          }),
        );
        setDecisionHistory(decisions);

        const published = versions.items.filter(
          (v) => v.status === 'PUBLISHED' && v.versionId !== selectedRequest.versionId,
        );
        const previous = published[0];
        if (previous) {
          const prevArtifacts = await listArtifacts(assetId, previous.versionId);
          if (!cancelled) {
            setArtifactDiff(computeArtifactDiff(artifacts, prevArtifacts));
          }
        } else {
          setArtifactDiff(
            artifacts.map((a) => ({ path: a.path, change: 'added' as const, currentSha: a.sha256 })),
          );
        }
      } catch {
        if (!cancelled) {
          setValidationReport(null);
          setFindings([]);
          setArtifactDiff([]);
        }
      } finally {
        if (!cancelled) setDetailLoading(false);
      }
    };

    loadDetails();
    return () => {
      cancelled = true;
    };
  }, [selectedRequest, assetId]);

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
                  <span className="font-mono text-xs break-all">{selectedRequest.frozenDigest}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">{t('review.frozenCommit')}</span>
                  <span className="font-mono text-xs break-all">
                    {selectedRequest.frozenSourceCommit || '-'}
                  </span>
                </div>
                {selectedRequest.frozenDigest && (
                  <p className="text-xs text-amber-700 bg-amber-50 rounded p-2">
                    {t('review.driftWarning')}
                  </p>
                )}
                <div className="flex justify-between">
                  <span className="text-gray-500">{t('common.status')}</span>
                  <span className={STATUS_COLORS[selectedRequest.status] ?? ''}>
                    {selectedRequest.status}
                  </span>
                </div>
              </div>

              {detailLoading && (
                <p className="text-sm text-gray-500">{t('review.loadingDetails')}</p>
              )}

              {!detailLoading && validationReport && (
                <div className="border rounded p-4">
                  <h3 className="text-sm font-semibold mb-2">{t('review.validationReport')}</h3>
                  <p className="text-xs text-gray-500 mb-2">
                    {t('version.policyVersion')}: {String(validationReport.policy_version ?? '-')}
                    {' | '}
                    {t('common.status')}: {String(validationReport.status ?? '-')}
                  </p>
                  {findings.length > 0 ? (
                    <ul className="text-xs space-y-1 max-h-40 overflow-y-auto">
                      {findings.map((f, idx) => (
                        <li key={`${f.code}-${idx}`} className="font-mono">
                          <span
                            className={
                              f.severity === 'FAILED' || f.severity === 'ERROR'
                                ? 'text-red-700'
                                : 'text-gray-700'
                            }
                          >
                            [{f.severity || 'INFO'}] {f.code}
                          </span>
                          {f.path ? ` @ ${f.path}` : ''}
                          {f.message ? ` — ${f.message}` : ''}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-xs text-gray-400">{t('review.noFindings')}</p>
                  )}
                </div>
              )}

              {!detailLoading && artifactDiff.length > 0 && (
                <div className="border rounded p-4">
                  <h3 className="text-sm font-semibold mb-2">{t('review.artifactDiff')}</h3>
                  <ul className="text-xs space-y-1 max-h-32 overflow-y-auto font-mono">
                    {artifactDiff.map((d) => (
                      <li key={d.path}>
                        <span
                          className={
                            d.change === 'added'
                              ? 'text-green-700'
                              : d.change === 'removed'
                                ? 'text-red-700'
                                : 'text-amber-700'
                          }
                        >
                          {t(`review.diff.${d.change}`)}
                        </span>{' '}
                        {d.path}
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {decisionHistory.length > 0 && (
                <div className="border rounded p-4">
                  <h3 className="text-sm font-semibold mb-2">{t('review.decisionHistory')}</h3>
                  <ul className="text-xs space-y-1">
                    {decisionHistory.map((d) => (
                      <li key={String(d.review_id)}>
                        {String(d.reviewer_id)}: {String(d.decision)}
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {selectedRequest.status === 'SUBMITTED' && (
                <div className="space-y-3">
                  <div>
                    <label className="block text-sm text-gray-600 mb-1">{t('review.decision')}</label>
                    <select
                      className="border rounded px-3 py-2 w-full"
                      value={decision}
                      onChange={(e) => setDecision(e.target.value as ReviewDecision)}
                    >
                      <option value="APPROVE">{t('review.approve')}</option>
                      <option value="REJECT">{t('review.reject')}</option>
                      <option value="REQUEST_CHANGES">{t('review.requestChanges')}</option>
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
                        : decision === 'REQUEST_CHANGES'
                          ? 'bg-orange-600 hover:bg-orange-700'
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
