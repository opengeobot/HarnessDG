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
  parameterScale?: string | null;
  precision?: string | null;
  weightFormat?: string | null;
  runtime?: string | null;
  knownRisks?: string[] | null;
  usageRestrictions?: string[] | null;
  sensitivityCode?: string | null;
}

export interface DatasetProfile {
  format?: string | null;
  modality?: string | null;
  taskCodes?: string[] | null;
  modalityCodes?: string[] | null;
  formatCodes?: string[] | null;
  languageCodes?: string[] | null;
  sensitivityCode?: string | null;
  sampleCount?: number | null;
  totalBytes?: number | null;
  sizeBucketCode?: string | null;
}

export type ProvisioningStatus = 'NONE' | 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';

export interface RepositoryRef {
  fullName: string;
  htmlUrl?: string | null;
  cloneUrl?: string | null;
}

export interface CardView {
  readme?: string | null;
  assetYaml?: string | null;
  sourceCommit?: string | null;
  untrustedContent: boolean;
}

export interface AssetFacetView {
  types: Record<string, number>;
  frameworks: Record<string, number>;
  tasks: Record<string, number>;
  formats: Record<string, number>;
  modalities: Record<string, number>;
  licenses: Record<string, number>;
  sensitivities: Record<string, number>;
  sizeBuckets: Record<string, number>;
  taskCodes: Record<string, number>;
  modalityCodes: Record<string, number>;
  formatCodes: Record<string, number>;
  languageCodes: Record<string, number>;
  totalCount: number;
}

/** 资源 ACL 视图 */
export interface ResourceAclView {
  aclId: string;
  principalId: string;
  resourceType: string;
  resourceId: string;
  permissionCodes: string[];
  createdAt: string;
}

/** 创建资产 ACL 请求 */
export interface CreateAssetAccessRequest {
  principalId: string;
  permissionCodes: string[];
}

/** 资产检索摘要 */
export interface AssetSummary {
  assetId: string;
  coordinate: string;
  type: AssetType;
  namespace: string;
  organizationId?: string | null;
  projectId?: string | null;
  name: string;
  displayName?: string | null;
  description?: string | null;
  visibility: Visibility;
  status: AssetStatus;
  owners: string[];
  tags: string[];
  tagIds: string[];
  license?: string | null;
  framework?: string | null;
  task?: string | null;
  format?: string | null;
  modality?: string | null;
  updatedAt: string;
  matchedFields?: string[];
}

/** 资产详情 */
export interface AssetView extends AssetSummary {
  model?: ModelProfile | null;
  dataset?: DatasetProfile | null;
  repository?: RepositoryRef | null;
  provisioningStatus?: ProvisioningStatus;
  card?: CardView | null;
  ownerTeamId?: string | null;
  aliases?: string[] | null;
  rowVersion: number;
  deprecationReason?: string | null;
  deprecationNote?: string | null;
  replacementAssetId?: string | null;
  createdAt: string;
}

/** 创建资产请求 */
export interface CreateAssetRequest {
  type: AssetType;
  organizationId?: string;
  projectId?: string;
  namespace: string;
  name: string;
  displayName?: string;
  description?: string;
  visibility: Visibility;
  owners?: string[];
  tags?: string[];
  tagIds?: string[];
  license?: string;
  ownerTeamId: string;
  model?: ModelProfile;
  dataset?: DatasetProfile;
}

/** 更新资产请求 */
export interface UpdateAssetRequest {
  expectedVersion: number;
  organizationId?: string;
  projectId?: string;
  displayName?: string;
  description?: string;
  visibility?: Visibility;
  tagIds?: string[];
  license?: string;
  ownerTeamId?: string;
  model?: ModelProfile;
  dataset?: DatasetProfile;
}

/** 血缘遍历方向 */
export type LineageDirection = 'up' | 'down';

/** 血缘关系边 */
export interface AssetLineageEdge {
  relationId: string;
  parentAssetId: string;
  childAssetId: string;
  relationType: string;
  hopDepth: number;
  createdBy?: string;
  createdAt?: string;
}

/** 资产血缘查询结果 */
export interface AssetLineageView {
  assetId: string;
  direction: LineageDirection;
  depth: number;
  relations: AssetLineageEdge[];
}

/** 检索查询参数 */
export interface AssetSearchParams {
  keyword?: string;
  type?: AssetType;
  namespace?: string;
  organizationId?: string;
  projectId?: string;
  visibility?: Visibility;
  status?: AssetStatus;
  teamId?: string;
  framework?: string;
  task?: string;
  format?: string;
  modality?: string;
  language?: string;
  sensitivity?: string;
  tagId?: string;
  owner?: string;
  taskCodes?: string[];
  modalityCodes?: string[];
  formatCodes?: string[];
  languageCodes?: string[];
  includeArchived?: boolean;
  cursor?: string;
  limit?: number;
}

/** 讨论线程 */
export interface ThreadView {
  threadId: string;
  assetId: string;
  title: string;
  createdBy: string;
  status: 'OPEN' | 'LOCKED' | 'CLOSED';
  commentCount: number;
  lastCommentAt?: string | null;
  createdAt: string;
}

/** 评论 */
export interface CommentView {
  commentId: string;
  threadId: string;
  parentId?: string | null;
  assetId: string;
  body: string;
  createdBy: string;
  status: 'VISIBLE' | 'HIDDEN' | 'RETRACTED';
  revisionCount: number;
  createdAt: string;
  updatedAt: string;
}
