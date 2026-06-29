/**
 * 功能: 资产目录 API 调用，经统一 client 访问后端 /api/v1 资产资源。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { apiClient } from '@/shared/api';
import type { CursorPage } from '@/shared/types';
import type {
  AssetSearchParams,
  AssetSummary,
  AssetView,
  CreateAssetRequest,
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

/** 逻辑删除资产 */
export function deleteAsset(assetId: string): Promise<void> {
  return apiClient.delete<void>(`/assets/${assetId}`);
}
