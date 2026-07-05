/**
 * 功能: 版本中心页面——版本列表 + 详情（Commit/Tag/Manifest/Artifact 展示）。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { useCallback, useEffect, useState } from 'react';
import { useDocumentTitle } from '@/shared/hooks';
import {
  issueDownloadTicket,
  listArtifacts,
  listVersions,
  transitionVersion,
} from './api';
import type { ArtifactView, VersionView } from './types';

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'bg-gray-100 text-gray-800',
  VALIDATING: 'bg-yellow-100 text-yellow-800',
  PENDING_REVIEW: 'bg-blue-100 text-blue-800',
  PUBLISHED: 'bg-green-100 text-green-800',
  DEPRECATED: 'bg-orange-100 text-orange-800',
  ARCHIVED: 'bg-gray-200 text-gray-600',
};

export function VersionPage() {
  useDocumentTitle('版本中心');
  const [assetId, setAssetId] = useState('');
  const [versions, setVersions] = useState<VersionView[]>([]);
  const [selectedVersion, setSelectedVersion] = useState<VersionView | null>(null);
  const [artifacts, setArtifacts] = useState<ArtifactView[]>([]);
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
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, [assetId]);

  useEffect(() => {
    loadVersions();
  }, [loadVersions]);

  const handleSelectVersion = async (v: VersionView) => {
    setSelectedVersion(v);
    try {
      const arts = await listArtifacts(v.versionId);
      setArtifacts(arts);
    } catch {
      setArtifacts([]);
    }
  };

  const handleTransition = async (v: VersionView, target: string) => {
    try {
      const updated = await transitionVersion(v.versionId, target);
      setVersions((prev) =>
        prev.map((x) => (x.versionId === v.versionId ? updated : x)),
      );
      if (selectedVersion?.versionId === v.versionId) {
        setSelectedVersion(updated);
      }
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : '状态推进失败');
    }
  };

  const handleDownload = async (v: VersionView, artifactId?: string) => {
    try {
      const ticket = await issueDownloadTicket(v.versionId, artifactId);
      if (ticket.method === 'PRESIGNED_URL' && ticket.presignedUrl) {
        window.open(ticket.presignedUrl, '_blank');
      } else {
        alert(`下载方式: ${ticket.method}\n请使用 DVC CLI 拉取。`);
      }
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : '签发下载票据失败');
    }
  };

  function formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
  }

  return (
    <div className="max-w-6xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-4">版本中心</h1>

      <div className="mb-4 flex gap-2">
        <input
          className="border rounded px-3 py-2 flex-1"
          placeholder="输入资产 ID"
          value={assetId}
          onChange={(e) => setAssetId(e.target.value)}
        />
        <button
          className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
          onClick={loadVersions}
        >
          查询
        </button>
      </div>

      {error && <p className="text-red-600 mb-4">{error}</p>}
      {loading && <p className="text-gray-500">加载中...</p>}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 版本列表 */}
        <div>
          <h2 className="text-lg font-semibold mb-2">版本列表</h2>
          <div className="space-y-2">
            {versions.map((v) => (
              <div
                key={v.versionId}
                className={`p-3 border rounded cursor-pointer transition ${
                  selectedVersion?.versionId === v.versionId
                    ? 'border-blue-500 bg-blue-50'
                    : 'hover:bg-gray-50'
                }`}
                onClick={() => handleSelectVersion(v)}
              >
                <div className="flex items-center justify-between">
                  <span className="font-mono font-semibold">{v.version}</span>
                  <span
                    className={`text-xs px-2 py-0.5 rounded ${STATUS_COLORS[v.status] ?? 'bg-gray-100'}`}
                  >
                    {v.status}
                  </span>
                </div>
                <div className="text-sm text-gray-500 mt-1">
                  {v.createdAt && new Date(v.createdAt).toLocaleString()}
                  {v.sourceCommit && ` | Commit: ${v.sourceCommit.slice(0, 8)}`}
                </div>
                {v.status === 'DRAFT' && (
                  <div className="mt-2 flex gap-1">
                    <button
                      className="text-xs px-2 py-1 bg-yellow-100 rounded"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleTransition(v, 'VALIDATING');
                      }}
                    >
                      提交校验
                    </button>
                  </div>
                )}
                {v.status === 'PUBLISHED' && (
                  <div className="mt-2 flex gap-1">
                    <button
                      className="text-xs px-2 py-1 bg-green-100 rounded"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDownload(v);
                      }}
                    >
                      下载 (DVC)
                    </button>
                  </div>
                )}
              </div>
            ))}
            {!loading && versions.length === 0 && (
              <p className="text-gray-400 text-sm">暂无版本</p>
            )}
          </div>
        </div>

        {/* 版本详情 */}
        <div>
          <h2 className="text-lg font-semibold mb-2">版本详情</h2>
          {selectedVersion ? (
            <div className="space-y-4">
              <div className="border rounded p-4 space-y-2">
                <div className="flex justify-between">
                  <span className="text-gray-500">版本 ID</span>
                  <span className="font-mono text-sm">{selectedVersion.versionId}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">状态</span>
                  <span className={STATUS_COLORS[selectedVersion.status] ?? ''}>
                    {selectedVersion.status}
                  </span>
                </div>
                {selectedVersion.sourceCommit && (
                  <div className="flex justify-between">
                    <span className="text-gray-500">Commit</span>
                    <span className="font-mono text-sm">{selectedVersion.sourceCommit}</span>
                  </div>
                )}
                {selectedVersion.gitTag && (
                  <div className="flex justify-between">
                    <span className="text-gray-500">Git Tag</span>
                    <span className="font-mono text-sm">{selectedVersion.gitTag}</span>
                  </div>
                )}
                {selectedVersion.manifestDigest && (
                  <div className="flex justify-between">
                    <span className="text-gray-500">Manifest Digest</span>
                    <span className="font-mono text-xs">{selectedVersion.manifestDigest}</span>
                  </div>
                )}
                {selectedVersion.notes && (
                  <div className="flex justify-between">
                    <span className="text-gray-500">备注</span>
                    <span className="text-sm">{selectedVersion.notes}</span>
                  </div>
                )}
                <div className="flex justify-between">
                  <span className="text-gray-500">创建者</span>
                  <span className="text-sm">{selectedVersion.createdBy}</span>
                </div>
              </div>

              {/* 工件列表 */}
              <div>
                <h3 className="font-semibold mb-2">
                  工件列表 ({artifacts.length})
                </h3>
                {artifacts.length > 0 ? (
                  <table className="w-full text-sm border">
                    <thead className="bg-gray-50">
                      <tr>
                        <th className="text-left p-2">路径</th>
                        <th className="text-right p-2">大小</th>
                        <th className="text-right p-2">SHA-256</th>
                        <th className="text-center p-2">操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {artifacts.map((a) => (
                        <tr key={a.artifactId} className="border-t">
                          <td className="p-2 font-mono">{a.path}</td>
                          <td className="p-2 text-right">{formatBytes(a.size)}</td>
                          <td className="p-2 text-right font-mono text-xs">
                            {a.sha256.slice(0, 12)}...
                          </td>
                          <td className="p-2 text-center">
                            {selectedVersion.status === 'PUBLISHED' && (
                              <button
                                className="text-xs text-blue-600 hover:underline"
                                onClick={() =>
                                  handleDownload(selectedVersion, a.artifactId)
                                }
                              >
                                下载
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                ) : (
                  <p className="text-gray-400 text-sm">暂无工件</p>
                )}
              </div>
            </div>
          ) : (
            <p className="text-gray-400">选择一个版本查看详情</p>
          )}
        </div>
      </div>
    </div>
  );
}
