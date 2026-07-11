/**
 * 功能: 上传中心页面——Multipart 上传（Part 进度/暂停/刷新恢复/Complete/Worker 状态）。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
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

const PART_SIZE = 5 * 1024 * 1024; // 5MiB

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
  const [searchParams] = useSearchParams();
  useDocumentTitle(t('upload.title'));
  const [assetId, setAssetId] = useState('');
  const [versionId, setVersionId] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [session, setSession] = useState<UploadSession | null>(null);
  const [parts, setParts] = useState<PartProgress[]>([]);
  const [uploading, setUploading] = useState(false);
  const [paused, setPaused] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [restoring, setRestoring] = useState(false);
  const [retryPart, setRetryPart] = useState<number | null>(null);
  const uploadBlobRef = useRef<Blob | null>(null);

  const buildPartsFromSession = useCallback((sess: UploadSession): PartProgress[] => {
    const totalParts = Math.max(1, Math.ceil(sess.totalBytes / PART_SIZE));
    const serverParts = new Map(
      (sess.parts ?? []).map((p) => [p.partNumber, p]),
    );
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
      setAssetId(result.assetId);
      setVersionId(result.versionId);
      setParts(buildPartsFromSession(result));
      setMessage(t('upload.sessionRestored', { id: result.sessionId }));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('upload.restoreFailed'));
    } finally {
      setRestoring(false);
    }
  }, [buildPartsFromSession, t]);

  useEffect(() => {
    const sessionId = searchParams.get('sessionId');
    const asset = searchParams.get('assetId') ?? undefined;
    if (sessionId) {
      void restoreSession(sessionId, asset);
    }
  }, [searchParams, restoreSession]);

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      setFiles(Array.from(e.target.files));
      uploadBlobRef.current = null;
    }
  };

  const createSession = useCallback(async () => {
    if (!assetId || !versionId || files.length === 0) {
      setError(t('upload.fillRequired'));
      return;
    }
    setError(null);
    setMessage(null);

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
      setMessage(t('upload.sessionCreated', { id: result.sessionId }));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('upload.createSessionFailed'));
    }
  }, [assetId, versionId, files, t, buildPartsFromSession]);

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
          prev.map((p) =>
            p.partNumber === partNumber ? { ...p, progress: pct } : p,
          ),
        );
      });
    } catch (firstError) {
      const retryPresign = await apiClient.get<PresignResponse>(
        `/upload-sessions/${sessionId}/parts/${partNumber}/presign`,
      );
      try {
        return await uploadPartWithProgress(retryPresign.url, slice, (pct) => {
          setParts((prev) =>
            prev.map((p) =>
              p.partNumber === partNumber ? { ...p, progress: pct } : p,
            ),
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

    const indices = onlyPart != null
      ? [onlyPart - 1]
      : parts.map((_, i) => i);

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
            p.partNumber === i + 1
              ? { ...p, status: 'completed', progress: 100, etag }
              : p,
          ),
        );
      }

      setMessage(t('upload.allPartsComplete'));
    } catch (e: unknown) {
      const failedPart = parts.find((p) => p.status === 'uploading')?.partNumber ?? onlyPart;
      if (failedPart != null) {
        setParts((prev) =>
          prev.map((p) =>
            p.partNumber === failedPart ? { ...p, status: 'failed' } : p,
          ),
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
      await apiClient.post(
        `/upload-sessions/${session.sessionId}/complete`,
        {
          parts: parts
            .filter((p) => p.status === 'completed')
            .map((p) => ({ partNumber: p.partNumber, etag: p.etag })),
        },
      );
      setMessage(t('upload.uploadCompleted'));
      setSession((prev) => prev ? { ...prev, status: 'COMPLETED' } : null);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('upload.completeFailed'));
    }
  };

  const cancelUpload = async () => {
    if (!session) return;
    try {
      await apiClient.post(`/upload-sessions/${session.sessionId}/cancel`);
      setMessage(t('upload.uploadCancelled'));
      setSession(null);
      setParts([]);
      uploadBlobRef.current = null;
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('upload.cancelFailed'));
    }
  };

  const completedParts = parts.filter((p) => p.status === 'completed').length;
  const overallProgress =
    parts.length > 0 ? Math.round((completedParts / parts.length) * 100) : 0;

  if (restoring) {
    return (
      <div className="max-w-4xl mx-auto p-6">
        <h1 className="text-2xl font-bold mb-4">{t('upload.title')}</h1>
        <p className="text-gray-600">{t('upload.restoringSession')}</p>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-4">{t('upload.title')}</h1>

      {!session && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <input
              className="border rounded px-3 py-2"
              placeholder={t('upload.assetIdPlaceholder')}
              value={assetId}
              onChange={(e) => setAssetId(e.target.value)}
            />
            <input
              className="border rounded px-3 py-2"
              placeholder={t('upload.versionIdPlaceholder')}
              value={versionId}
              onChange={(e) => setVersionId(e.target.value)}
            />
          </div>

          <div>
            <label className="block text-sm text-gray-600 mb-1">{t('upload.selectFiles')}</label>
            <input
              type="file"
              multiple
              onChange={handleFileSelect}
              className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
            />
            {files.length > 0 && (
              <div className="mt-2 text-sm text-gray-600">
                {t('upload.fileCount', { count: files.length })},{' '}
                {t('upload.totalSize', { size: (files.reduce((s, f) => s + f.size, 0) / 1024 / 1024).toFixed(1) })}
              </div>
            )}
          </div>

          <button
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
            onClick={createSession}
            disabled={!assetId || !versionId || files.length === 0}
          >
            {t('upload.createSession')}
          </button>
        </div>
      )}

      {error && <p className="text-red-600 mt-4">{error}</p>}
      {message && <p className="text-green-600 mt-4">{message}</p>}

      {session && (
        <div className="mt-6 space-y-4">
          <div className="border rounded p-4">
            <h2 className="font-semibold mb-2">{t('upload.sessionInfo')}</h2>
            <div className="grid grid-cols-2 gap-2 text-sm">
              <div>
                <span className="text-gray-500">{t('upload.sessionId')}:</span>{' '}
                <span className="font-mono">{session.sessionId}</span>
              </div>
              <div>
                <span className="text-gray-500">{t('common.status')}:</span>{' '}
                <span>{session.status}</span>
              </div>
              <div>
                <span className="text-gray-500">{t('upload.fileCountLabel')}:</span>{' '}
                <span>{session.fileCount}</span>
              </div>
              <div>
                <span className="text-gray-500">{t('upload.totalSizeLabel')}:</span>{' '}
                <span>{(session.totalBytes / 1024 / 1024).toFixed(1)} MB</span>
              </div>
            </div>
          </div>

          {(session.files?.length ?? 0) > 0 && (
            <div>
              <h3 className="font-semibold mb-2">{t('upload.fileList')}</h3>
              <div className="max-h-40 overflow-y-auto border rounded text-sm">
                {session.files!.map((f) => (
                  <div key={f.fileId} className="flex items-center gap-2 p-2 border-b last:border-b-0">
                    <span className="flex-1 font-mono truncate">{f.path}</span>
                    <span className="text-gray-500">{(f.size / 1024).toFixed(1)} KB</span>
                    <span className={`text-xs ${
                      f.status === 'COMPLETED'
                        ? 'text-green-600'
                        : f.status === 'FAILED'
                          ? 'text-red-600'
                          : 'text-gray-500'
                    }`}
                    >
                      {f.status}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {session.status === 'OPEN' && (
            <div>
              <label className="block text-sm text-gray-600 mb-1">{t('upload.reselectFiles')}</label>
              <input
                type="file"
                multiple
                onChange={handleFileSelect}
                className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
              />
            </div>
          )}

          <div>
            <div className="flex justify-between text-sm mb-1">
              <span>{t('upload.overallProgress')}</span>
              <span>{overallProgress}%</span>
            </div>
            <div className="w-full bg-gray-200 rounded h-4">
              <div
                className="bg-blue-600 rounded h-4 transition-all"
                style={{ width: `${overallProgress}%` }}
              />
            </div>
          </div>

          <div>
            <h3 className="font-semibold mb-2">
              {t('upload.partDetails')} ({completedParts}/{parts.length})
            </h3>
            <div className="max-h-48 overflow-y-auto border rounded">
              {parts.map((p) => (
                <div
                  key={p.partNumber}
                  className="flex items-center gap-2 p-2 border-b last:border-b-0 text-sm"
                >
                  <span className="w-16">Part {p.partNumber}</span>
                  <div className="flex-1 bg-gray-200 rounded h-2">
                    <div
                      className={`rounded h-2 transition-all ${
                        p.status === 'completed'
                          ? 'bg-green-500'
                          : p.status === 'uploading'
                            ? 'bg-blue-500'
                            : p.status === 'failed'
                              ? 'bg-red-500'
                              : 'bg-gray-300'
                      }`}
                      style={{ width: `${p.progress}%` }}
                    />
                  </div>
                  <span className="w-12 text-right">{p.progress}%</span>
                  <span
                    className={`w-16 text-xs ${
                      p.status === 'completed'
                        ? 'text-green-600'
                        : p.status === 'failed'
                          ? 'text-red-600'
                          : 'text-gray-500'
                    }`}
                  >
                    {p.status}
                  </span>
                  {p.status === 'failed' && !uploading && (
                    <button
                      type="button"
                      className="text-xs text-blue-600 hover:underline"
                      onClick={() => void startUpload(p.partNumber)}
                    >
                      {t('upload.retryPart')}
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>

          {retryPart != null && !uploading && (
            <button
              type="button"
              className="px-3 py-1 text-sm bg-orange-100 text-orange-800 rounded hover:bg-orange-200"
              onClick={() => void startUpload(retryPart)}
            >
              {t('upload.retryFailedPart', { part: retryPart })}
            </button>
          )}

          <div className="flex gap-2">
            {session.status === 'OPEN' && !uploading && (
              <button
                className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
                onClick={() => void startUpload()}
              >
                {t('upload.startUpload')}
              </button>
            )}
            {uploading && !paused && (
              <button
                className="px-4 py-2 bg-yellow-500 text-white rounded hover:bg-yellow-600"
                onClick={() => setPaused(true)}
              >
                {t('upload.pause')}
              </button>
            )}
            {uploading && paused && (
              <button
                className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
                onClick={() => void startUpload()}
              >
                {t('upload.resume')}
              </button>
            )}
            {session.status === 'OPEN' &&
              completedParts === parts.length &&
              parts.length > 0 && (
                <button
                  className="px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700"
                  onClick={() => void completeUpload()}
                >
                  {t('upload.completeUpload')}
                </button>
              )}
            <button
              className="px-4 py-2 bg-red-100 text-red-700 rounded hover:bg-red-200"
              onClick={() => void cancelUpload()}
            >
              {t('common.cancel')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
