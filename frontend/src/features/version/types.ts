/**
 * 功能: 版本管理前端类型，对齐后端 VersionView / ArtifactView 契约。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */

export type VersionStatus =
  | 'DRAFT'
  | 'VALIDATING'
  | 'PENDING_REVIEW'
  | 'PUBLISHED'
  | 'DEPRECATED'
  | 'ARCHIVED';

export interface VersionView {
  versionId: string;
  assetId: string;
  version: string;
  status: VersionStatus;
  sourceCommit?: string | null;
  manifestDigest?: string | null;
  gitTag?: string | null;
  publishedAt?: string | null;
  publishedBy?: string | null;
  notes?: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface ArtifactView {
  artifactId: string;
  versionId: string;
  path: string;
  dvcFile?: string | null;
  dvcHash?: string | null;
  sha256: string;
  size: number;
  mediaType?: string | null;
}

export interface DownloadTicket {
  method: 'PRESIGNED_URL' | 'GIT_DVC';
  presignedUrl?: string | null;
  expiresAt?: string | null;
  fileName?: string | null;
  fileSize?: number | null;
}

export interface DvcRemoteConfig {
  bucket: string;
  endpoint: string;
  type: string;
  prefix: string;
  expiresAt: string;
}

export interface DvcCredentials {
  accessKey: string;
  secretKey: string;
  expiresAt: string;
  bucket: string;
  prefix: string;
}
