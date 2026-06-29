/**
 * 功能: 资产目录前端类型，对齐 OpenAPI AssetView / AssetSummary / 请求体契约。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */

export type AssetType = 'MODEL' | 'DATASET';
export type Visibility = 'PRIVATE' | 'INTERNAL' | 'PUBLIC';
export type AssetStatus = 'ACTIVE' | 'DEPRECATED' | 'ARCHIVED';

export interface ModelProfile {
  framework?: string | null;
  task?: string | null;
  architecture?: string | null;
}

export interface DatasetProfile {
  format?: string | null;
  modality?: string | null;
}

export interface RepositoryRef {
  fullName: string;
  htmlUrl?: string | null;
  cloneUrl?: string | null;
}

/** 资产检索摘要 */
export interface AssetSummary {
  assetId: string;
  type: AssetType;
  namespace: string;
  name: string;
  displayName?: string | null;
  description?: string | null;
  visibility: Visibility;
  status: AssetStatus;
  owners: string[];
  tags: string[];
  license?: string | null;
  framework?: string | null;
  task?: string | null;
  format?: string | null;
  modality?: string | null;
  updatedAt: string;
}

/** 资产详情 */
export interface AssetView extends AssetSummary {
  model?: ModelProfile | null;
  dataset?: DatasetProfile | null;
  repository?: RepositoryRef | null;
  createdAt: string;
}

/** 创建资产请求 */
export interface CreateAssetRequest {
  type: AssetType;
  namespace: string;
  name: string;
  displayName?: string;
  description?: string;
  visibility: Visibility;
  owners?: string[];
  tags?: string[];
  license?: string;
  model?: ModelProfile;
  dataset?: DatasetProfile;
}

/** 更新资产请求 */
export interface UpdateAssetRequest {
  displayName?: string;
  description?: string;
  visibility?: Visibility;
  owners?: string[];
  tags?: string[];
  license?: string;
  model?: ModelProfile;
  dataset?: DatasetProfile;
}

/** 检索查询参数 */
export interface AssetSearchParams {
  keyword?: string;
  type?: AssetType;
  namespace?: string;
  framework?: string;
  tag?: string;
  cursor?: string;
  limit?: number;
}
