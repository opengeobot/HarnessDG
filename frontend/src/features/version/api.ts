/**
 * 功能: 版本管理 API 调用，经统一 client 访问后端版本/工件/DVC 资源。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { apiClient } from '@/shared/api';
import type { CursorPage } from '@/shared/types';
import type {
  ArtifactView,
  DvcCredentials,
  DvcRemoteConfig,
  DownloadTicket,
  VersionView,
} from './types';

/** 列出版本（游标分页） */
export function listVersions(
  assetId: string,
  cursor?: string,
  limit = 20,
): Promise<CursorPage<VersionView>> {
  return apiClient.get<CursorPage<VersionView>>(`/assets/${assetId}/versions`, {
    params: { cursor, limit },
  });
}

/** 获取版本详情 */
export function getVersion(assetId: string, versionId: string): Promise<VersionView> {
  return apiClient.get<VersionView>(`/assets/${assetId}/versions/${versionId}`);
}

/** 创建草稿版本 */
export function createDraftVersion(
  assetId: string,
  version: string,
  notes?: string,
): Promise<VersionView> {
  return apiClient.post<VersionView>(`/assets/${assetId}/versions`, {
    version,
    notes,
  });
}

/** 推进版本状态 */
export function transitionVersion(
  versionId: string,
  targetStatus: string,
): Promise<VersionView> {
  return apiClient.post<VersionView>(`/versions/${versionId}/transition`, {
    targetStatus,
  });
}

/** 发布版本 */
export function publishVersion(
  versionId: string,
  gitTag: string,
): Promise<VersionView> {
  return apiClient.post<VersionView>(`/versions/${versionId}/publish`, {
    gitTag,
  });
}

/** 列出版本工件 */
export function listArtifacts(assetId: string, versionId: string): Promise<ArtifactView[]> {
  return apiClient.get<ArtifactView[]>(`/assets/${assetId}/versions/${versionId}/artifacts`);
}

/** 签发下载票据 */
export function issueDownloadTicket(
  versionId: string,
  artifactId?: string,
): Promise<DownloadTicket> {
  return apiClient.get<DownloadTicket>(`/versions/${versionId}/download`, {
    params: { artifactId },
  });
}

/** 获取 DVC Remote 配置 */
export function getDvcConfig(assetId: string): Promise<DvcRemoteConfig> {
  return apiClient.get<DvcRemoteConfig>(`/assets/${assetId}/dvc/config`);
}

/** 签发 DVC 凭据 */
export function getDvcCredentials(assetId: string): Promise<DvcCredentials> {
  return apiClient.get<DvcCredentials>(`/assets/${assetId}/dvc/credentials`);
}
