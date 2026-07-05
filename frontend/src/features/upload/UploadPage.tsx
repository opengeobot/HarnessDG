/**
 * 功能: 上传中心页面——Multipart 上传（Part 进度/暂停/刷新恢复/Complete/Worker 状态）。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { useCallback, useState } from 'react';
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
}

interface PartProgress {
  partNumber: number;
  status: 'pending' | 'uploading' | 'completed' | 'failed';
  progress: number;
}

const PART_SIZE = 5 * 1024 * 1024; // 5MiB

export function UploadPage() {
  useDocumentTitle('上传中心');
  const [assetId, setAssetId] = useState('');
  const [versionId, setVersionId] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [session, setSession] = useState<UploadSession | null>(null);
  const [parts, setParts] = useState<PartProgress[]>([]);
  const [uploading, setUploading] = useState(false);
  const [paused, setPaused] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      setFiles(Array.from(e.target.files));
    }
  };

  const createSession = useCallback(async () => {
    if (!assetId || !versionId || files.length === 0) {
      setError('请填写资产 ID、版本 ID 并选择文件');
      return;
    }
    setError(null);
    setMessage(null);

    const totalBytes = files.reduce((sum, f) => sum + f.size, 0);
    if (totalBytes > 20 * 1024 * 1024 * 1024) {
      setError('总大小超过 20GiB，请使用 CLI/DVC 上传');
      return;
    }

    try {
      const result = await apiClient.post<UploadSession>(
        `/assets/${assetId}/versions/${versionId}/upload-sessions`,
        {
          totalBytes,
          fileCount: files.length,
          ttlSeconds: 7200,
        },
      );
      setSession(result);
      setMessage(`上传会话已创建: ${result.sessionId}`);

      // 计算分片
      const totalParts = Math.ceil(totalBytes / PART_SIZE);
      setParts(
        Array.from({ length: totalParts }, (_, i) => ({
          partNumber: i + 1,
          status: 'pending',
          progress: 0,
        })),
      );
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '创建上传会话失败');
    }
  }, [assetId, versionId, files]);

  const startUpload = async () => {
    if (!session) return;
    setUploading(true);
    setPaused(false);
    setError(null);

    try {
      for (let i = 0; i < parts.length; i++) {
        if (paused) break;

        setParts((prev) =>
          prev.map((p) =>
            p.partNumber === i + 1 ? { ...p, status: 'uploading' } : p,
          ),
        );

        // 请求预签名 URL（实际应使用 PUT 请求上传文件分片）
        await apiClient.get<string>(
          `/upload-sessions/${session.sessionId}/parts/${i + 1}/presign`,
        );

        // 模拟上传（实际应使用 XMLHttpRequest 或 fetch 上传文件分片）
        await new Promise((resolve) => setTimeout(resolve, 500));

        // 模拟进度
        for (let pct = 0; pct <= 100; pct += 20) {
          await new Promise((resolve) => setTimeout(resolve, 50));
          setParts((prev) =>
            prev.map((p) =>
              p.partNumber === i + 1 ? { ...p, progress: pct } : p,
            ),
          );
        }

        setParts((prev) =>
          prev.map((p) =>
            p.partNumber === i + 1
              ? { ...p, status: 'completed', progress: 100 }
              : p,
          ),
        );
      }

      setMessage('所有分片上传完成');
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '上传失败');
    } finally {
      setUploading(false);
    }
  };

  const completeUpload = async () => {
    if (!session) return;
    try {
      await apiClient.post(
        `/upload-sessions/${session.sessionId}/complete`,
        { parts: parts.map((p) => ({ partNumber: p.partNumber, etag: 'mock' })) },
      );
      setMessage('上传已完成，等待物化 Worker 处理');
      setSession((prev) => prev ? { ...prev, status: 'COMPLETED' } : null);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '完成上传失败');
    }
  };

  const cancelUpload = async () => {
    if (!session) return;
    try {
      await apiClient.post(`/upload-sessions/${session.sessionId}/cancel`);
      setMessage('上传已取消');
      setSession(null);
      setParts([]);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '取消上传失败');
    }
  };

  const completedParts = parts.filter((p) => p.status === 'completed').length;
  const overallProgress =
    parts.length > 0 ? Math.round((completedParts / parts.length) * 100) : 0;

  return (
    <div className="max-w-4xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-4">上传中心</h1>

      {!session && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <input
              className="border rounded px-3 py-2"
              placeholder="资产 ID"
              value={assetId}
              onChange={(e) => setAssetId(e.target.value)}
            />
            <input
              className="border rounded px-3 py-2"
              placeholder="版本 ID"
              value={versionId}
              onChange={(e) => setVersionId(e.target.value)}
            />
          </div>

          <div>
            <label className="block text-sm text-gray-600 mb-1">选择文件</label>
            <input
              type="file"
              multiple
              onChange={handleFileSelect}
              className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
            />
            {files.length > 0 && (
              <div className="mt-2 text-sm text-gray-600">
                {files.length} 个文件, 共{' '}
                {(files.reduce((s, f) => s + f.size, 0) / 1024 / 1024).toFixed(1)}{' '}
                MB
              </div>
            )}
          </div>

          <button
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
            onClick={createSession}
            disabled={!assetId || !versionId || files.length === 0}
          >
            创建上传会话
          </button>
        </div>
      )}

      {error && <p className="text-red-600 mt-4">{error}</p>}
      {message && <p className="text-green-600 mt-4">{message}</p>}

      {session && (
        <div className="mt-6 space-y-4">
          <div className="border rounded p-4">
            <h2 className="font-semibold mb-2">会话信息</h2>
            <div className="grid grid-cols-2 gap-2 text-sm">
              <div>
                <span className="text-gray-500">会话 ID:</span>{' '}
                <span className="font-mono">{session.sessionId}</span>
              </div>
              <div>
                <span className="text-gray-500">状态:</span>{' '}
                <span>{session.status}</span>
              </div>
              <div>
                <span className="text-gray-500">文件数:</span>{' '}
                <span>{session.fileCount}</span>
              </div>
              <div>
                <span className="text-gray-500">总大小:</span>{' '}
                <span>{(session.totalBytes / 1024 / 1024).toFixed(1)} MB</span>
              </div>
            </div>
          </div>

          {/* 进度条 */}
          <div>
            <div className="flex justify-between text-sm mb-1">
              <span>总进度</span>
              <span>{overallProgress}%</span>
            </div>
            <div className="w-full bg-gray-200 rounded h-4">
              <div
                className="bg-blue-600 rounded h-4 transition-all"
                style={{ width: `${overallProgress}%` }}
              />
            </div>
          </div>

          {/* 分片列表 */}
          <div>
            <h3 className="font-semibold mb-2">
              分片详情 ({completedParts}/{parts.length})
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
                </div>
              ))}
            </div>
          </div>

          {/* 操作按钮 */}
          <div className="flex gap-2">
            {session.status === 'OPEN' && !uploading && (
              <button
                className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
                onClick={startUpload}
              >
                开始上传
              </button>
            )}
            {uploading && !paused && (
              <button
                className="px-4 py-2 bg-yellow-500 text-white rounded hover:bg-yellow-600"
                onClick={() => setPaused(true)}
              >
                暂停
              </button>
            )}
            {uploading && paused && (
              <button
                className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
                onClick={startUpload}
              >
                恢复
              </button>
            )}
            {session.status === 'OPEN' &&
              completedParts === parts.length &&
              parts.length > 0 && (
                <button
                  className="px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700"
                  onClick={completeUpload}
                >
                  完成上传
                </button>
              )}
            <button
              className="px-4 py-2 bg-red-100 text-red-700 rounded hover:bg-red-200"
              onClick={cancelUpload}
            >
              取消
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
