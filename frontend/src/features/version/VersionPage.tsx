/**
 * 功能: 版本中心页面——版本列表 + 详情（Commit/Tag/Manifest/Artifact 展示）。
 * 时间: 2026-07-05，2026-07-12 Wave Z Ant Design 迁移
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
  Row,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useDocumentTitle } from '@/shared/hooks';
import {
  getValidationReport,
  issueDownloadTicket,
  listArtifacts,
  listVersions,
  transitionVersion,
} from './api';
import type { ArtifactView, VersionView } from './types';

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  VALIDATING: 'processing',
  PENDING_REVIEW: 'blue',
  PUBLISHED: 'success',
  DEPRECATED: 'warning',
  ARCHIVED: 'default',
};

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
}

export function VersionPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  useDocumentTitle(t('version.title'));
  const [searchParams] = useSearchParams();
  const assetId = searchParams.get('assetId') ?? '';
  const [versions, setVersions] = useState<VersionView[]>([]);
  const [selectedVersion, setSelectedVersion] = useState<VersionView | null>(null);
  const [artifacts, setArtifacts] = useState<ArtifactView[]>([]);
  const [validationReport, setValidationReport] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadVersions = useCallback(async () => {
    if (!assetId) return;
    setLoading(true);
    setError(null);
    try {
      const page = await listVersions(assetId);
      setVersions(page.items ?? []);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('version.loadFailedMsg'));
    } finally {
      setLoading(false);
    }
  }, [assetId, t]);

  useEffect(() => {
    void loadVersions();
  }, [loadVersions]);

  const handleSelectVersion = async (v: VersionView) => {
    setSelectedVersion(v);
    setValidationReport(null);
    try {
      const arts = await listArtifacts(assetId, v.versionId);
      setArtifacts(arts);
    } catch {
      setArtifacts([]);
    }
    if (v.status !== 'DRAFT') {
      try {
        const report = await getValidationReport(assetId, v.versionId);
        if (report.status !== 'NOT_FOUND') setValidationReport(report);
      } catch {
        /* ignore */
      }
    }
  };

  const handleTransition = async (v: VersionView, target: string) => {
    try {
      const updated = await transitionVersion(assetId, v.versionId, target);
      setVersions((prev) => prev.map((x) => (x.versionId === v.versionId ? updated : x)));
      if (selectedVersion?.versionId === v.versionId) setSelectedVersion(updated);
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : t('version.transitionFailed'));
    }
  };

  const handleDownload = async (v: VersionView, artifactId?: string) => {
    try {
      const ticket = await issueDownloadTicket(v.versionId, artifactId);
      if (ticket.method === 'PRESIGNED_URL' && ticket.presignedUrl) {
        window.open(ticket.presignedUrl, '_blank');
      } else {
        message.info(t('version.downloadMethod', { method: ticket.method }));
      }
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : t('version.downloadTicketFailed'));
    }
  };

  const artifactColumns: ColumnsType<ArtifactView> = [
    { title: t('version.path'), dataIndex: 'path', key: 'path', render: (v: string) => <Typography.Text code>{v}</Typography.Text> },
    { title: t('version.size'), dataIndex: 'size', key: 'size', align: 'right', render: (v: number) => formatBytes(v) },
    {
      title: 'SHA-256',
      dataIndex: 'sha256',
      key: 'sha256',
      align: 'right',
      render: (v: string) => <Typography.Text code style={{ fontSize: 11 }}>{v.slice(0, 12)}…</Typography.Text>,
    },
    {
      title: t('common.action'),
      key: 'action',
      align: 'center',
      render: (_, record) =>
        selectedVersion?.status === 'PUBLISHED' ? (
          <Button type="link" size="small" onClick={() => handleDownload(selectedVersion, record.artifactId)}>
            {t('version.download')}
          </Button>
        ) : null,
    },
  ];

  if (!assetId) {
    return (
      <Card title={t('version.title')}>
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
        title={t('version.title')}
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

      {error && <Alert type="error" showIcon message={error} />}
      {loading && <Flex justify="center"><Spin /></Flex>}

      <Row gutter={16}>
        <Col xs={24} lg={12}>
          <Card title={t('version.versionList')} size="small">
            {versions.length === 0 && !loading ? (
              <Empty description={t('version.noVersions')} />
            ) : (
              <Space direction="vertical" style={{ width: '100%' }} size={8}>
                {versions.map((v) => (
                  <Card
                    key={v.versionId}
                    size="small"
                    hoverable
                    onClick={() => void handleSelectVersion(v)}
                    style={{
                      borderColor: selectedVersion?.versionId === v.versionId ? '#1677ff' : undefined,
                      background: selectedVersion?.versionId === v.versionId ? '#e6f4ff' : undefined,
                    }}
                  >
                    <Flex justify="space-between" align="center">
                      <Typography.Text strong code>{v.version}</Typography.Text>
                      <Tag color={STATUS_COLOR[v.status]}>{v.status}</Tag>
                    </Flex>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {v.createdAt && new Date(v.createdAt).toLocaleString()}
                      {v.sourceCommit && ` | Commit: ${v.sourceCommit.slice(0, 8)}`}
                    </Typography.Text>
                    <Space size={4} style={{ marginTop: 8 }} wrap>
                      {v.status === 'DRAFT' && (
                        <Button
                          size="small"
                          onClick={(e) => {
                            e.stopPropagation();
                            void handleTransition(v, 'VALIDATING');
                          }}
                        >
                          {t('version.submitValidation')}
                        </Button>
                      )}
                      {v.status === 'PUBLISHED' && (
                        <>
                          <Button
                            size="small"
                            onClick={(e) => {
                              e.stopPropagation();
                              void handleDownload(v);
                            }}
                          >
                            {t('version.downloadDvc')}
                          </Button>
                          <Button
                            size="small"
                            danger
                            onClick={(e) => {
                              e.stopPropagation();
                              void handleTransition(v, 'DEPRECATED');
                            }}
                          >
                            {t('version.deprecate')}
                          </Button>
                        </>
                      )}
                      {v.status === 'DEPRECATED' && (
                        <Button
                          size="small"
                          onClick={(e) => {
                            e.stopPropagation();
                            void handleTransition(v, 'ARCHIVED');
                          }}
                        >
                          {t('version.archive')}
                        </Button>
                      )}
                    </Space>
                  </Card>
                ))}
              </Space>
            )}
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title={t('version.versionDetail')} size="small">
            {selectedVersion ? (
              <Flex vertical gap={16}>
                <Descriptions column={1} size="small" bordered>
                  <Descriptions.Item label={t('version.versionId')}>
                    <Typography.Text code>{selectedVersion.versionId}</Typography.Text>
                  </Descriptions.Item>
                  <Descriptions.Item label={t('common.status')}>
                    <Tag color={STATUS_COLOR[selectedVersion.status]}>{selectedVersion.status}</Tag>
                  </Descriptions.Item>
                  {selectedVersion.sourceCommit && (
                    <Descriptions.Item label={t('version.commit')}>
                      <Typography.Text code>{selectedVersion.sourceCommit}</Typography.Text>
                    </Descriptions.Item>
                  )}
                  {selectedVersion.gitTag && (
                    <Descriptions.Item label="Git Tag">
                      <Typography.Text code>{selectedVersion.gitTag}</Typography.Text>
                    </Descriptions.Item>
                  )}
                  {selectedVersion.manifestDigest && (
                    <Descriptions.Item label="Manifest Digest">
                      <Typography.Text code style={{ fontSize: 11 }}>{selectedVersion.manifestDigest}</Typography.Text>
                    </Descriptions.Item>
                  )}
                  {selectedVersion.notes && (
                    <Descriptions.Item label={t('version.notes')}>{selectedVersion.notes}</Descriptions.Item>
                  )}
                  <Descriptions.Item label={t('version.createdBy')}>{selectedVersion.createdBy}</Descriptions.Item>
                </Descriptions>

                {validationReport && (
                  <Card title={t('version.validationReport')} size="small" type="inner">
                    <Descriptions column={1} size="small">
                      <Descriptions.Item label="Report ID">
                        <Typography.Text code>{String(validationReport.report_id)}</Typography.Text>
                      </Descriptions.Item>
                      <Descriptions.Item label={t('version.policyVersion')}>
                        {String(validationReport.policy_version)}
                      </Descriptions.Item>
                      <Descriptions.Item label={t('common.status')}>
                        <Tag color={validationReport.status === 'PASSED' ? 'success' : 'error'}>
                          {String(validationReport.status)}
                        </Tag>
                      </Descriptions.Item>
                    </Descriptions>
                    {validationReport.findings ? (
                      <Typography.Paragraph>
                        <pre style={{ fontSize: 11, maxHeight: 192, overflow: 'auto' }}>
                          {typeof validationReport.findings === 'string'
                            ? validationReport.findings
                            : JSON.stringify(validationReport.findings, null, 2)}
                        </pre>
                      </Typography.Paragraph>
                    ) : null}
                  </Card>
                )}

                <Card
                  title={`${t('version.artifactList')} (${artifacts.length})`}
                  size="small"
                  type="inner"
                >
                  {artifacts.length > 0 ? (
                    <Table
                      size="small"
                      rowKey="artifactId"
                      columns={artifactColumns}
                      dataSource={artifacts}
                      pagination={false}
                      scroll={{ y: 240 }}
                    />
                  ) : (
                    <Empty description={t('version.noArtifacts')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  )}
                </Card>
              </Flex>
            ) : (
              <Empty description={t('version.selectVersion')} />
            )}
          </Card>
        </Col>
      </Row>
    </Flex>
  );
}
