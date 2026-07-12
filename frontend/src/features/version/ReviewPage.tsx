/**
 * 功能: 发布审批页面——版本列表、校验报告、工件差异、提交发布与审批决策。
 * 时间: 2026-07-10，2026-07-12 Wave Z Ant Design 迁移
 * 作者: AxeXie
 */
import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Flex,
  Input,
  Row,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
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

const STATUS_COLOR: Record<string, string> = {
  SUBMITTED: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
  CHANGES_REQUESTED: 'warning',
  PUBLISHING: 'processing',
  PUBLISHED: 'success',
  FAILED: 'error',
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
  const { message } = App.useApp();
  useDocumentTitle(t('review.title'));
  const [searchParams] = useSearchParams();
  const assetId = searchParams.get('assetId') ?? '';
  const [requests, setRequests] = useState<PublishRequest[]>([]);
  const [selectedRequest, setSelectedRequest] = useState<PublishRequest | null>(null);
  const [decision, setDecision] = useState<ReviewDecision>('APPROVE');
  const [comments, setComments] = useState('');
  const [error, setError] = useState<string | null>(null);
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
    void loadRequests();
  }, [loadRequests]);

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
          if (!cancelled) setArtifactDiff(computeArtifactDiff(artifacts, prevArtifacts));
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

    void loadDetails();
    return () => {
      cancelled = true;
    };
  }, [selectedRequest, assetId]);

  const submitPublishRequest = async (versionId: string) => {
    try {
      const result = await apiClient.post<{ requestId: string }>(
        `/versions/${versionId}/publish-requests`,
      );
      message.success(t('review.requestSubmitted', { id: result.requestId }));
      void loadRequests();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('review.submitFailedMsg'));
    }
  };

  const submitDecision = async (requestId: string) => {
    try {
      await apiClient.post(`/publish-requests/${requestId}/decisions`, { decision, comments });
      message.success(t('review.decisionSubmitted', { decision }));
      setComments('');
      setSelectedRequest(null);
      void loadRequests();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('review.decisionFailed'));
    }
  };

  if (!assetId) {
    return (
      <Card title={t('review.title')}>
        <Empty description={t('version.deepLinkHint')}>
          <Link to="/assets">
            <Button type="primary">{t('nav.assetCatalog')}</Button>
          </Link>
        </Empty>
      </Card>
    );
  }

  return (
    <Flex vertical gap={16}>
      <Card
        title={t('review.title')}
        extra={
          <Link to={`/assets/${assetId}`}>
            <Button type="link">{t('assets.detailTitle')}</Button>
          </Link>
        }
      >
        <Descriptions size="small" column={1}>
          <Descriptions.Item label={t('version.assetIdLabel')}>
            <Typography.Text code>{assetId}</Typography.Text>
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {error && <Alert type="error" showIcon message={error} closable onClose={() => setError(null)} />}

      <Row gutter={16}>
        <Col xs={24} lg={12}>
          <Card title={t('review.publishRequests')} size="small">
            {requests.length === 0 ? (
              <Empty description={t('review.noRequests')} />
            ) : (
              <Space direction="vertical" style={{ width: '100%' }} size={8}>
                {requests.map((r) => (
                  <Card
                    key={r.requestId}
                    size="small"
                    hoverable
                    onClick={() => setSelectedRequest(r)}
                    style={{
                      borderColor: selectedRequest?.requestId === r.requestId ? '#1677ff' : undefined,
                      background: selectedRequest?.requestId === r.requestId ? '#e6f4ff' : undefined,
                    }}
                  >
                    <Flex justify="space-between" align="center">
                      <Typography.Text code>{r.versionId}</Typography.Text>
                      <Tag color={STATUS_COLOR[r.status]}>{r.status}</Tag>
                    </Flex>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {t('review.submitter')}: {r.submittedBy} | {new Date(r.submittedAt).toLocaleString()}
                    </Typography.Text>
                    {r.status === 'SUBMITTED' && (
                      <Button
                        size="small"
                        style={{ marginTop: 8 }}
                        onClick={(e) => {
                          e.stopPropagation();
                          void submitPublishRequest(r.versionId);
                        }}
                      >
                        {t('review.resubmit')}
                      </Button>
                    )}
                  </Card>
                ))}
              </Space>
            )}
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title={t('review.decisionPanel')} size="small">
            {selectedRequest ? (
              <Flex vertical gap={16}>
                <Descriptions column={1} size="small" bordered>
                  <Descriptions.Item label={t('review.requestId')}>
                    <Typography.Text code>{selectedRequest.requestId}</Typography.Text>
                  </Descriptions.Item>
                  <Descriptions.Item label={t('version.versionId')}>
                    <Typography.Text code>{selectedRequest.versionId}</Typography.Text>
                  </Descriptions.Item>
                  <Descriptions.Item label={t('review.frozenDigest')}>
                    <Typography.Text code style={{ fontSize: 11 }}>{selectedRequest.frozenDigest}</Typography.Text>
                  </Descriptions.Item>
                  <Descriptions.Item label={t('review.frozenCommit')}>
                    <Typography.Text code style={{ fontSize: 11 }}>
                      {selectedRequest.frozenSourceCommit || '-'}
                    </Typography.Text>
                  </Descriptions.Item>
                  <Descriptions.Item label={t('common.status')}>
                    <Tag color={STATUS_COLOR[selectedRequest.status]}>{selectedRequest.status}</Tag>
                  </Descriptions.Item>
                </Descriptions>

                {selectedRequest.frozenDigest && (
                  <Alert type="warning" showIcon message={t('review.driftWarning')} />
                )}

                {detailLoading && <Spin />}

                {!detailLoading && validationReport && (
                  <Card title={t('review.validationReport')} size="small" type="inner">
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {t('version.policyVersion')}: {String(validationReport.policy_version ?? '-')}
                      {' | '}
                      {t('common.status')}: {String(validationReport.status ?? '-')}
                    </Typography.Text>
                    {findings.length > 0 ? (
                      <ul style={{ fontSize: 12, maxHeight: 160, overflow: 'auto', marginTop: 8 }}>
                        {findings.map((f, idx) => (
                          <li key={`${f.code}-${idx}`}>
                            <Typography.Text
                              type={f.severity === 'FAILED' || f.severity === 'ERROR' ? 'danger' : undefined}
                              code
                            >
                              [{f.severity || 'INFO'}] {f.code}
                            </Typography.Text>
                            {f.path ? ` @ ${f.path}` : ''}
                            {f.message ? ` — ${f.message}` : ''}
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <Empty description={t('review.noFindings')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    )}
                  </Card>
                )}

                {!detailLoading && artifactDiff.length > 0 && (
                  <Card title={t('review.artifactDiff')} size="small" type="inner">
                    <ul style={{ fontSize: 12, maxHeight: 128, overflow: 'auto' }}>
                      {artifactDiff.map((d) => (
                        <li key={d.path}>
                          <Tag
                            color={
                              d.change === 'added' ? 'success' : d.change === 'removed' ? 'error' : 'warning'
                            }
                          >
                            {t(`review.diff.${d.change}`)}
                          </Tag>{' '}
                          <Typography.Text code>{d.path}</Typography.Text>
                        </li>
                      ))}
                    </ul>
                  </Card>
                )}

                {decisionHistory.length > 0 && (
                  <Card title={t('review.decisionHistory')} size="small" type="inner">
                    <ul style={{ fontSize: 12 }}>
                      {decisionHistory.map((d) => (
                        <li key={String(d.review_id)}>
                          {String(d.reviewer_id)}: {String(d.decision)}
                        </li>
                      ))}
                    </ul>
                  </Card>
                )}

                {selectedRequest.status === 'SUBMITTED' ? (
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <div>
                      <Typography.Text>{t('review.decision')}</Typography.Text>
                      <Select
                        style={{ width: '100%', marginTop: 4 }}
                        value={decision}
                        onChange={(v) => setDecision(v)}
                        options={[
                          { value: 'APPROVE', label: t('review.approve') },
                          { value: 'REJECT', label: t('review.reject') },
                          { value: 'REQUEST_CHANGES', label: t('review.requestChanges') },
                        ]}
                      />
                    </div>
                    <div>
                      <Typography.Text>{t('review.comments')}</Typography.Text>
                      <Input.TextArea
                        rows={3}
                        value={comments}
                        onChange={(e) => setComments(e.target.value)}
                        placeholder={t('review.commentsPlaceholder')}
                        style={{ marginTop: 4 }}
                      />
                    </div>
                    <Button
                      type="primary"
                      danger={decision === 'REJECT'}
                      onClick={() => void submitDecision(selectedRequest.requestId)}
                    >
                      {t('review.submitDecision')}
                    </Button>
                  </Space>
                ) : (
                  <Typography.Text type="secondary">{t('review.alreadyProcessed')}</Typography.Text>
                )}
              </Flex>
            ) : (
              <Empty description={t('review.selectRequest')} />
            )}
          </Card>
        </Col>
      </Row>
    </Flex>
  );
}
