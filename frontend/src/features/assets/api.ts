/**
 * 功能: 资产目录 API 调用，经统一 client 访问后端 /api/v1 资产资源。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { apiClient } from '@/shared/api';
import type { CursorPage } from '@/shared/types';
import type {
  AssetFacetView,
  AssetLineageView,
  AssetSearchParams,
  AssetSummary,
  AssetView,
  CommentView,
  CreateAssetRequest,
  CreateAssetAccessRequest,
  ResourceAclView,
  ThreadView,
  UpdateAssetRequest,
} from './types';

/** 检索资产摘要（游标分页） */
export function searchAssets(
  params: AssetSearchParams,
): Promise<CursorPage<AssetSummary>> {
  return apiClient.get<CursorPage<AssetSummary>>('/assets', { params });
}

/** 查询资产详情 */
export function getAsset(assetId: string): Promise<AssetView> {
  return apiClient.get<AssetView>(`/assets/${assetId}`);
}

/** 登记新资产 */
export function createAsset(payload: CreateAssetRequest): Promise<AssetView> {
  return apiClient.post<AssetView>('/assets', payload);
}

/** 更新资产可变元数据 */
export function updateAsset(
  assetId: string,
  payload: UpdateAssetRequest,
): Promise<AssetView> {
  return apiClient.patch<AssetView>(`/assets/${assetId}`, payload);
}

/** 查询资产血缘 */
export function getAssetLineage(
  assetId: string,
  params?: { depth?: number; direction?: 'up' | 'down' },
): Promise<AssetLineageView> {
  return apiClient.get(`/assets/${assetId}/lineage`, { params });
}

/** 逻辑删除资产 */
export function deleteAsset(assetId: string): Promise<void> {
  return apiClient.delete<void>(`/assets/${assetId}`);
}

/** 查询 Facet 统计 */
export function getAssetFacets(
  keyword?: string,
  type?: string,
): Promise<AssetFacetView> {
  return apiClient.get<AssetFacetView>('/assets/facets', {
    params: { keyword, type },
  });
}

/** 列出资产 ACL */
export function listAssetAccess(assetId: string): Promise<ResourceAclView[]> {
  return apiClient.get<ResourceAclView[]>(`/assets/${assetId}/access`);
}

/** 创建资产 ACL */
export function createAssetAccess(
  assetId: string,
  payload: CreateAssetAccessRequest,
): Promise<ResourceAclView> {
  return apiClient.post<ResourceAclView>(`/assets/${assetId}/access`, payload);
}

/** 删除资产 ACL */
export function deleteAssetAccess(assetId: string, aclId: string): Promise<void> {
  return apiClient.delete<void>(`/assets/${assetId}/access/${aclId}`);
}

/** 弃用资产：支持弃用原因与替代资产 */
export function deprecateAsset(
  assetId: string,
  body?: {
    deprecationReason?: string;
    deprecationNote?: string;
    replacementAssetId?: string;
  },
): Promise<AssetView> {
  return apiClient.post<AssetView>(`/assets/${assetId}/deprecate`, body ?? {});
}

/** 归档资产 */
export function archiveAsset(assetId: string): Promise<AssetView> {
  return apiClient.post<AssetView>(`/assets/${assetId}/archive`);
}

/** 恢复资产 */
export function restoreAsset(assetId: string): Promise<AssetView> {
  return apiClient.post<AssetView>(`/assets/${assetId}/restore`);
}

// ---- Discussion API ----

/** 创建讨论线程 */
export function createThread(
  assetId: string,
  title: string,
): Promise<ThreadView> {
  return apiClient.post<ThreadView>(`/assets/${assetId}/discussions`, { title });
}

/** 列出讨论线程 */
export function listThreads(
  assetId: string,
  cursor?: string,
  limit = 20,
): Promise<CursorPage<ThreadView>> {
  return apiClient.get<CursorPage<ThreadView>>(
    `/assets/${assetId}/discussions`,
    { params: { cursor, limit } },
  );
}

/** 发表评论 */
export function createComment(
  threadId: string,
  body: string,
  parentId?: string,
): Promise<CommentView> {
  return apiClient.post<CommentView>(
    `/discussions/${threadId}/comments`,
    { body, parentId },
  );
}

/** 读取评论列表 */
export function listComments(
  threadId: string,
  cursor?: string,
  limit = 50,
): Promise<CommentView[]> {
  return apiClient.get<CommentView[]>(
    `/discussions/${threadId}/comments`,
    { params: { cursor, limit } },
  );
}

/** 编辑评论 */
export function editComment(
  threadId: string,
  commentId: string,
  body: string,
): Promise<CommentView> {
  return apiClient.patch<CommentView>(
    `/discussions/${threadId}/comments/${commentId}`,
    { body },
  );
}

/** 撤回评论（作者） */
export function retractComment(
  threadId: string,
  commentId: string,
): Promise<CommentView> {
  return apiClient.post<CommentView>(
    `/discussions/${threadId}/comments/${commentId}/retract`,
  );
}

/** 隐藏评论（Moderator） */
export function hideComment(
  threadId: string,
  commentId: string,
): Promise<CommentView> {
  return apiClient.post<CommentView>(
    `/discussions/${threadId}/comments/${commentId}/hide`,
  );
}

/** 恢复评论（Moderator） */
export function unhideComment(
  threadId: string,
  commentId: string,
): Promise<CommentView> {
  return apiClient.post<CommentView>(
    `/discussions/${threadId}/comments/${commentId}/unhide`,
  );
}

/** 锁定线程 */
export function lockThread(
  threadId: string,
): Promise<ThreadView> {
  return apiClient.post<ThreadView>(`/discussions/${threadId}/lock`);
}

/** 解锁线程 */
export function unlockThread(
  threadId: string,
): Promise<ThreadView> {
  return apiClient.post<ThreadView>(`/discussions/${threadId}/unlock`);
}
