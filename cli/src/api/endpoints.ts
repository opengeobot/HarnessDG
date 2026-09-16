// AIHub CLI API 端点定义
// 作者: AxeXie
// 创建时间: 2026-07-30
// 根据 aihub-v1.yaml 和 aihub-agent-v1.yaml 合同定义

// ─── Asset 端点 ───

/** 搜索资产 GET /api/v1/assets */
export function searchAssetsPath(): string {
  return '/assets';
}

/** 获取资产详情 GET /api/v1/assets/{assetId} */
export function getAssetPath(assetId: string): string {
  return `/assets/${assetId}`;
}

/** 创建资产 POST /api/v1/assets */
export function createAssetPath(): string {
  return '/assets';
}

/** 更新资产 PATCH /api/v1/assets/{assetId} */
export function updateAssetPath(assetId: string): string {
  return `/assets/${assetId}`;
}

// ─── 版本端点 ───

/** 版本列表 GET /api/v1/assets/{assetId}/versions (agent API) */
export function listVersionsPath(assetId: string): string {
  return `/assets/${assetId}/versions`;
}

// ─── 传输端点 ───

/** 创建上传会话 POST /api/v1/assets/{assetId}/versions/{versionId}/upload-sessions */
export function createUploadSessionPath(assetId: string, versionId: string): string {
  return `/assets/${assetId}/versions/${versionId}/upload-sessions`;
}

/** 查询上传会话 GET /api/v1/upload-sessions/{sessionId} */
export function getUploadSessionPath(sessionId: string): string {
  return `/upload-sessions/${sessionId}`;
}

/** 获取 Part 预签名 URL GET /api/v1/upload-sessions/{sessionId}/parts/{partNumber}/presign */
export function presignPartUploadPath(sessionId: string, partNumber: number): string {
  return `/upload-sessions/${sessionId}/parts/${partNumber}/presign`;
}

/** 完成上传 POST /api/v1/upload-sessions/{sessionId}/complete */
export function completeUploadSessionPath(sessionId: string): string {
  return `/upload-sessions/${sessionId}/complete`;
}

/** 取消上传 POST /api/v1/upload-sessions/{sessionId}/cancel */
export function cancelUploadSessionPath(sessionId: string): string {
  return `/upload-sessions/${sessionId}/cancel`;
}

/** 获取下载票据 GET /api/v1/versions/{versionId}/download */
export function downloadTicketPath(versionId: string, artifactId?: string): string {
  const query = artifactId ? `?artifactId=${encodeURIComponent(artifactId)}` : '';
  return `/versions/${versionId}/download${query}`;
}