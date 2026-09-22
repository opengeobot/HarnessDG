/**
 * 功能: 上传中心页面——Multipart 上传（Part 进度/暂停/刷新恢复/Complete/Worker 状态）。
 * 时间: 2026-07-05，2026-07-12 Wave Z Ant Design 迁移
 * 作者: AxeXie
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Empty,
  Flex,
  Progress,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  Upload,
} from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { useDocumentTitle } from '@/shared/hooks';
import { apiClient } from '@/shared/api';

interface UploadSession {
  sessionId: string;
  assetId: string;
  versionId: string;
  status: string;
  totalBytes: number;
  fileCount: number;
  expiresAt: string;
  parts?: UploadPartStatus[];
  files?: UploadFileStatus[];
}

interface UploadPartStatus {
  partNumber: number;
  size: number;
  etag?: string;
  status: 'PENDING' | 'COMPLETED';
  uploadedAt?: string;
}

interface UploadFileStatus {
  fileId: string;
  path: string;
  size: number;
  sha256?: string;
  mediaType?: string;
  status: 'PENDING' | 'UPLOADING' | 'COMPLETED' | 'FAILED';
}

interface PartProgress {
  partNumber: number;
  status: 'pending' | 'uploading' | 'completed' | 'failed';
  progress: number;
  etag?: string;
}

const PART_SIZE = 5 * 1024 * 1024;

interface PresignResponse {
  url: string;
}

function uploadPartWithProgress(
  url: string,
  body: Blob,
  onProgress: (pct: number) => void,
): Promise<string> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        const raw = xhr.getResponseHeader('ETag');
        resolve(raw?.replace(/"/g, '') ?? '');
        return;
      }
      reject(new Error(`Upload failed with status ${xhr.status}`));
    };
    xhr.onerror = () => reject(new Error('Network error during upload'));
    xhr.send(body);
  });
}

export function UploadPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const [searchParams] = useSearchParams();
  useDocumentTitle(t('upload.title'));
  const assetId = searchParams.get('assetId') ?? '';
  const versionId = searchParams.get('versionId') ?? '';
  const [files, setFiles] = useState<File[]>([]);
  const [session, setSession] = useState<UploadSession | null>(null);
  const [parts, setParts] = useState<PartProgress[]>([]);
  const [uploading, setUploading] = useState(false);
  const [paused, setPaused] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [restoring, setRestoring] = useState(false);
  const [retryPart, setRetryPart] = useState<number | null>(null);
  const uploadBlobRef = useRef<Blob | null>(null);

  const buildPartsFromSession = useCallback((sess: UploadSession): PartProgress[] => {
    const totalParts = Math.max(1, Math.ceil(sess.totalBytes / PART_SIZE));
    const serverParts = new Map((sess.parts ?? []).map((p) => [p.partNumber, p]));
    return Array.from({ length: totalParts }, (_, i) => {
      const partNumber = i + 1;
      const server = serverParts.get(partNumber);
      const completed = server?.status === 'COMPLETED';
      return {
        partNumber,
        status: completed ? 'completed' as const : 'pending' as const,
        progress: completed ? 100 : 0,
        etag: server?.etag,
      };
    });
  }, []);

  const restoreSession = useCallback(async (sessionId: string, asset?: string) => {
    setRestoring(true);
    setError(null);
    try {
      const path = asset
        ? `/assets/${asset}/upload-sessions/${sessionId}`
        : `/upload-sessions/${sessionId}`;
      const result = await apiClient.get<UploadSession>(path);
      setSession(result);
      setParts(buildPartsFromSession(result));
      message.success(t('upload.sessionRestored', { id: result.sessionId }));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('upload.restoreFailed'));
    } finally {
      setRestoring(false);
    }
  }, [buildPartsFromSession, message, t]);

  useEffect(() => {
    const sessionId = searchParams.get('sessionId');
    const asset = searchParams.get('assetId') ?? undefined;
    if (sessionId) {
      void restoreSession(sessionId, asset);
    }
  }, [searchParams, restoreSession]);

  const createSession = useCallback(async () => {
    if (!assetId || !versionId || files.length === 0) {
      setError(t('upload.fillRequired'));
      return;
    }
    setError(null);

    const totalBytes = files.reduce((sum, f) => sum + f.size, 0);
    if (totalBytes > 20 * 1024 * 1024 * 1024) {
      setError(t('upload.sizeExceeded'));
      return;
    }

    try {
      const result = await apiClient.post<UploadSession>(
        `/assets/${assetId}/versions/${versionId}/upload-sessions`,
        { totalBytes, fileCount: files.length, ttlSeconds: 7200 },
      );
      setSession(result);
      setParts(buildPartsFromSession(result));
      uploadBlobRef.current = new Blob(files);
      message.success(t('upload.sessionCreated', { id: result.sessionId }));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('upload.createSessionFailed'));
    }
  }, [assetId, versionId, files, t, buildPartsFromSession, message]);

  const presignAndUploadPart = async (
    sessionId: string,
    partNumber: number,
    blob: Blob,
  ): Promise<string> => {
    const start = (partNumber - 1) * PART_SIZE;
    const slice = blob.slice(start, Math.min(start + PART_SIZE, blob.size));

    const presign = await apiClient.get<PresignResponse>(
      `/upload-sessions/${sessionId}/parts/${partNumber}/presign`,
    );

    try {
      return await uploadPartWithProgress(presign.url, slice, (pct) => {
        setParts((prev) =>
          prev.map((p) => (p.partNumber === partNumber ? { ...p, progress: pct } : p)),
        );
      });
    } catch (firstError) {
      const retryPresign = await apiClient.get<PresignResponse>(
        `/upload-sessions/${sessionId}/parts/${partNumber}/presign`,
      );
      try {
        return await uploadPartWithProgress(retryPresign.url, slice, (pct) => {
          setParts((prev) =>
            prev.map((p) => (p.partNumber === partNumber ? { ...p, progress: pct } : p)),
          );
        });
      } catch {
        throw firstError;
      }
    }
  };

  const startUpload = async (onlyPart?: number) => {
    if (!session) return;
    if (files.length === 0 && !uploadBlobRef.current) {
      setError(t('upload.selectFilesToResume'));
      return;
    }

    const blob = uploadBlobRef.current ?? new Blob(files);
    uploadBlobRef.current = blob;

    setUploading(true);
    setPaused(false);
    setError(null);
    setRetryPart(null);

    const indices = onlyPart != null ? [onlyPart - 1] : parts.map((_, i) => i);

    try {
      for (const i of indices) {
        if (paused) break;
        if (parts[i].status === 'completed') continue;

        setParts((prev) =>
          prev.map((p) =>
            p.partNumber === i + 1 ? { ...p, status: 'uploading', progress: 0 } : p,
          ),
        );

        const etag = await presignAndUploadPart(session.sessionId, i + 1, blob);

        setParts((prev) =>
          prev.map((p) =>
            p.partNumber === i + 1 ? { ...p, status: 'completed', progress: 100, etag } : p,
          ),
        );
      }

      message.success(t('upload.allPartsComplete'));
    } catch (e: unknown) {
      const failedPart = parts.find((p) => p.status === 'uploading')?.partNumber ?? onlyPart;
      if (failedPart != null) {
        setParts((prev) =>
          prev.map((p) => (p.partNumber === failedPart ? { ...p, status: 'failed' } : p)),
        );
        setRetryPart(failedPart);
      }
      setError(e instanceof Error ? e.message : t('upload.uploadFailed'));
    } finally {
      setUploading(false);
    }
  };

  const completeUpload = async () => {
    if (!session) return;
    const missingEtag = parts.some((p) => p.status === 'completed' && !p.etag);
    if (missingEtag) {
      setError(t('upload.missingEtags'));
      return;
    }
    try {
      await apiClient.post(`/upload-sessions/${session.sessionId}/complete`, {
        parts: parts
          .filter((p) => p.status === 'completed')
          .map((p) => ({ partNumber: p.partNumber, etag: p.etag })),
      });
      message.success(t('upload.uploadCompleted'));
      setSession((prev) => (prev ? { ...prev, status: 'COMPLETED' } : null));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('upload.completeFailed'));
    }
  };

  const cancelUpload = async () => {
    if (!session) return;
    try {
      await apiClient.post(`/upload-sessions/${session.sessionId}/cancel`);
      message.info(t('upload.uploadCancelled'));
      setSession(null);
      setParts([]);
      uploadBlobRef.current = null;
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('upload.cancelFailed'));
    }
  };

  const completedParts = parts.filter((p) => p.status === 'completed').length;
  const overallProgress = parts.length > 0 ? Math.round((completedParts / parts.length) * 100) : 0;

  if (restoring) {
    return (
      <Card title={t('upload.title')}>
        <Flex justify="center" align="center" style={{ minHeight: 120 }}>
          <Spin tip={t('upload.restoringSession')} />
        </Flex>
      </Card>
    );
  }

  if (!assetId || !versionId) {
    return (
      <Card title={t('upload.title')}>
        <Empty description={t('upload.deepLinkHint')}>
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
        title={t('upload.title')}
        extra={
          <Link to={`/assets/${assetId}?version=${versionId}`}>
            <Button type="link">{t('assets.detailTitle')}</Button>
          </Link>
        }
      >
        <Descriptions size="small" column={2}>
          <Descriptions.Item label={t('upload.assetIdLabel')}>
            <Typography.Text code>{assetId}</Typography.Text>
          </Descriptions.Item>
          <Descriptions.Item label={t('upload.versionIdLabel')}>
            <Typography.Text code>{versionId}</Typography.Text>
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {error && <Alert type="error" showIcon message={error} closable onClose={() => setError(null)} />}

      {!session && (
        <Card>
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Upload.Dragger
              multiple
              beforeUpload={() => false}
              onChange={(info) => {
                const selected = info.fileList
                  .map((f) => f.originFileObj)
                  .filter((f): f is NonNullable<typeof f> => !!f);
                setFiles(selected);
                uploadBlobRef.current = null;
              }}
              fileList={files.map((f, i) => ({
                uid: `${i}`,
                name: f.name,
                size: f.size,
                status: 'done' as const,
              }))}
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">{t('upload.selectFiles')}</p>
            </Upload.Dragger>
            {files.length > 0 && (
              <Typography.Text type="secondary">
                {t('upload.fileCount', { count: files.length })},{' '}
                {t('upload.totalSize', {
                  size: (files.reduce((s, f) => s + f.size, 0) / 1024 / 1024).toFixed(1),
                })}
              </Typography.Text>
            )}
            <Button
              type="primary"
              onClick={() => void createSession()}
              disabled={files.length === 0}
            >
              {t('upload.createSession')}
            </Button>
          </Space>
        </Card>
      )}

      {session && (
        <>
          <Card title={t('upload.sessionInfo')} size="small">
            <Descriptions column={2} size="small">
              <Descriptions.Item label={t('upload.sessionId')}>
                <Typography.Text code>{session.sessionId}</Typography.Text>
              </Descriptions.Item>
              <Descriptions.Item label={t('common.status')}>
                <Tag>{session.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('upload.fileCountLabel')}>{session.fileCount}</Descriptions.Item>
              <Descriptions.Item label={t('upload.totalSizeLabel')}>
                {(session.totalBytes / 1024 / 1024).toFixed(1)} MB
              </Descriptions.Item>
            </Descriptions>
          </Card>

          {(session.files?.length ?? 0) > 0 && (
            <Card title={t('upload.fileList')} size="small">
              <Table
                size="small"
                rowKey="fileId"
                pagination={false}
                dataSource={session.files}
                columns={[
                  { title: t('version.path'), dataIndex: 'path', key: 'path', render: (v: string) => <Typography.Text code>{v}</Typography.Text> },
                  {
                    title: t('version.size'),
                    dataIndex: 'size',
                    key: 'size',
                    render: (v: number) => `${(v / 1024).toFixed(1)} KB`,
                  },
                  {
                    title: t('common.status'),
                    dataIndex: 'status',
                    key: 'status',
                    render: (v: string) => (
                      <Tag color={v === 'COMPLETED' ? 'success' : v === 'FAILED' ? 'error' : 'default'}>
                        {v}
                      </Tag>
                    ),
                  },
                ]}
              />
            </Card>
          )}

          {session.status === 'OPEN' && (
            <Card size="small">
              <Upload.Dragger
                multiple
                beforeUpload={() => false}
                onChange={(info) => {
                  const selected = info.fileList
                    .map((f) => f.originFileObj)
                    .filter((f): f is NonNullable<typeof f> => !!f);
                  setFiles(selected);
                  uploadBlobRef.current = null;
                }}
              >
                <p className="ant-upload-text">{t('upload.reselectFiles')}</p>
              </Upload.Dragger>
            </Card>
          )}

          <Card title={t('upload.overallProgress')} size="small">
            <Progress percent={overallProgress} status={uploading ? 'active' : undefined} />
          </Card>

          <Card title={`${t('upload.partDetails')} (${completedParts}/${parts.length})`} size="small">
            <Space direction="vertical" style={{ width: '100%' }}>
              {parts.map((p) => (
                <Flex key={p.partNumber} align="center" gap={8}>
                  <Typography.Text style={{ width: 64 }}>Part {p.partNumber}</Typography.Text>
                  <Progress
                    percent={p.progress}
                    size="small"
                    style={{ flex: 1 }}
                    status={
                      p.status === 'failed' ? 'exception' : p.status === 'completed' ? 'success' : 'active'
                    }
                  />
                  <Tag
                    color={
                      p.status === 'completed' ? 'success' : p.status === 'failed' ? 'error' : 'default'
                    }
                  >
                    {p.status}
                  </Tag>
                  {p.status === 'failed' && !uploading && (
                    <Button type="link" size="small" onClick={() => void startUpload(p.partNumber)}>
                      {t('upload.retryPart')}
                    </Button>
                  )}
                </Flex>
              ))}
            </Space>
          </Card>

          {retryPart != null && !uploading && (
            <Button onClick={() => void startUpload(retryPart)}>
              {t('upload.retryFailedPart', { part: retryPart })}
            </Button>
          )}

          <Space wrap>
            {session.status === 'OPEN' && !uploading && (
              <Button type="primary" onClick={() => void startUpload()}>
                {t('upload.startUpload')}
              </Button>
            )}
            {uploading && !paused && (
              <Button onClick={() => setPaused(true)}>{t('upload.pause')}</Button>
            )}
            {uploading && paused && (
              <Button type="primary" onClick={() => void startUpload()}>
                {t('upload.resume')}
              </Button>
            )}
            {session.status === 'OPEN' && completedParts === parts.length && parts.length > 0 && (
              <Button type="primary" onClick={() => void completeUpload()}>
                {t('upload.completeUpload')}
              </Button>
            )}
            <Button danger onClick={() => void cancelUpload()}>
              {t('common.cancel')}
            </Button>
          </Space>
        </>
      )}
    </Flex>
  );
}
