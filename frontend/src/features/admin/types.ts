/**
 * 功能: 公共管理端 (/admin/*) 前端类型定义，对齐 OpenAPI 契约（camelCase）。
 *       覆盖用户、Agent、组织/项目、角色/权限/绑定/ACL、字典、标签、配置、任务、审计、通知、指标。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */

/** 主体类型 */
export type PrincipalType = 'USER' | 'AGENT' | 'SERVICE' | 'API_CLIENT' | 'WORKER';
/** 主体/组织/项目通用状态 */
export type PrincipalStatus = 'ACTIVE' | 'LOCKED' | 'DISABLED';
/** 本地用户状态 */
export type UserStatus = 'PENDING_ACTIVATION' | 'ACTIVE' | 'LOCKED' | 'DISABLED';
/** 分类治理状态 */
export type TaxonomyStatus = 'ACTIVE' | 'DISABLED';
/** 角色类型 */
export type RoleType = 'SYSTEM' | 'CUSTOM';
/** 授权作用域类型 */
export type ScopeType = 'PLATFORM' | 'ORGANIZATION' | 'PROJECT' | 'ASSET';
/** 标签作用域类型 */
export type TagScopeType = 'PLATFORM' | 'ORGANIZATION';
/** 任务状态 */
export type JobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'RETRY_WAIT' | 'DEAD' | 'CANCELLED';
/** 审计结果 */
export type AuditResult = 'SUCCEEDED' | 'FAILED' | 'DENIED';
/** 配置值类型 */
export type ConfigValueType = 'STRING' | 'INTEGER' | 'LONG' | 'BOOLEAN' | 'DURATION' | 'JSON';

/* ---------------- 用户 ---------------- */
export interface UserView {
  userId: string;
  principalId: string;
  username: string;
  displayName: string;
  email?: string | null;
  locale: string;
  status: UserStatus;
  forcePasswordChange?: boolean;
  lastLoginAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PageResultUser {
  items: UserView[];
  page: number;
  size: number;
  total: number;
}

export interface ListUsersParams {
  keyword?: string;
  status?: UserStatus;
  page?: number;
  size?: number;
}

export interface CreateUserRequest {
  username: string;
  displayName: string;
  email?: string;
  locale?: string;
  temporaryPassword: string;
}

export interface UpdateUserRequest {
  displayName?: string;
  email?: string | null;
  locale?: string;
}

export interface ResetPasswordRequest {
  temporaryPassword: string;
}

/* ---------------- Agent ---------------- */
export interface AgentView {
  agentId: string;
  principalId: string;
  displayName: string;
  agentType: string;
  vendor?: string | null;
  status: PrincipalStatus;
  maxSensitivityLevel: number;
  scopes: string[];
  toolAllowlist: string[];
}

export interface CreateAgentRequest {
  displayName: string;
  agentType: string;
  vendor?: string | null;
  maxSensitivityLevel: number;
  scopes: string[];
}

export interface CreatedAgent extends AgentView {
  credential: string;
}

export interface UpdateAgentToolAllowlistRequest {
  tools: string[];
}

/* ---------------- 组织/项目 ---------------- */
export interface OrganizationView {
  organizationId: string;
  code: string;
  name: string;
  giteaOrganization?: string | null;
  status: PrincipalStatus;
  createdAt: string;
}

export interface CreateOrganizationRequest {
  code: string;
  name: string;
  giteaOrganization?: string | null;
}

export interface OrganizationMemberView {
  organizationId: string;
  principalId: string;
  joinedAt: string;
}

export interface AddOrganizationMemberRequest {
  principalId: string;
}

export interface ProjectView {
  projectId: string;
  organizationId: string;
  code: string;
  name: string;
  status: PrincipalStatus;
  createdAt: string;
}

export interface CreateProjectRequest {
  code: string;
  name: string;
}

/* ---------------- Team ---------------- */
export interface TeamView {
  teamId: string;
  organizationId: string;
  name: string;
  description: string;
  status: PrincipalStatus;
  createdAt: string;
}

export interface TeamMemberView {
  teamId: string;
  principalId: string;
  role: string;
  joinedAt: string;
}

export interface CreateTeamRequest {
  name: string;
  description?: string;
}

export interface UpdateTeamRequest {
  name?: string;
  description?: string;
  status?: PrincipalStatus;
}

export interface AddTeamMemberRequest {
  principalId: string;
  role?: string;
}

/* ---------------- 角色/权限/绑定/ACL ---------------- */
export interface PermissionView {
  permissionCode: string;
  resource: string;
  action: string;
  i18nKey: string;
}

export interface RoleView {
  roleId: string;
  roleCode: string;
  roleName: string;
  roleType: RoleType;
  permissionCodes: string[];
  version: number;
}

export interface CreateRoleRequest {
  roleCode: string;
  roleName: string;
  permissionCodes: string[];
}

export interface UpdateRoleRequest {
  roleName?: string;
  permissionCodes?: string[];
  expectedVersion: number;
}

export interface RoleBindingView {
  bindingId: string;
  principalId: string;
  roleId: string;
  scopeType: ScopeType;
  scopeId?: string | null;
  createdAt: string;
}

export interface CreateRoleBindingRequest {
  principalId: string;
  roleId: string;
  scopeType: ScopeType;
  scopeId?: string | null;
}

export interface ResourceAclView {
  aclId: string;
  principalId: string;
  resourceType: string;
  resourceId: string;
  permissionCodes: string[];
  createdAt: string;
}

export interface CreateResourceAclRequest {
  principalId: string;
  resourceType: string;
  resourceId: string;
  permissionCodes: string[];
}

/* ---------------- 字典 ---------------- */
export interface DictionaryTypeView {
  dictCode: string;
  i18nKey: string;
  version: number;
  status: TaxonomyStatus;
}

export interface DictionaryItemView {
  dictCode: string;
  itemCode: string;
  i18nKey: string;
  sortOrder: number;
  status: TaxonomyStatus;
  version: number;
}

export interface CreateDictionaryItemRequest {
  itemCode: string;
  i18nKey: string;
  sortOrder?: number;
}

export interface UpdateDictionaryItemRequest {
  i18nKey?: string;
  sortOrder?: number;
  status?: TaxonomyStatus;
  expectedVersion?: number;
}

/* ---------------- 标签 ---------------- */
export interface TagView {
  tagId: string;
  scopeType: TagScopeType;
  scopeId?: string | null;
  tagCode: string;
  displayName: string;
  i18nKey: string;
  color?: string | null;
  status: TaxonomyStatus;
  version: number;
}

export interface ListTagsParams {
  scopeType?: TagScopeType;
  scopeId?: string;
  status?: TaxonomyStatus;
  keyword?: string;
}

export interface CreateTagRequest {
  scopeType: TagScopeType;
  scopeId?: string | null;
  tagCode: string;
  displayName: string;
  i18nKey: string;
  color?: string | null;
}

export interface UpdateTagRequest {
  displayName?: string;
  i18nKey?: string;
  color?: string | null;
  expectedVersion: number;
}

/* ---------------- 配置 ---------------- */
export interface ConfigurationView {
  configKey: string;
  valueType: ConfigValueType;
  value: unknown;
  hotReloadable: boolean;
  version: number;
}

export interface UpdateConfigurationRequest {
  value: unknown;
  expectedVersion: number;
  confirmation?: string | null;
}

/* ---------------- 任务 ---------------- */
export interface JobView {
  jobId: string;
  jobType: string;
  status: JobStatus;
  retryCount: number;
  nextRunAt?: string | null;
  errorCode?: string | null;
  traceId?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ListJobsParams {
  status?: JobStatus;
  cursor?: string;
  limit?: number;
}

/* ---------------- 审计 ---------------- */
export interface AuditLogView {
  auditId: string;
  principalId: string;
  action: string;
  resourceType: string;
  resourceId: string;
  result: AuditResult;
  errorCode?: string | null;
  requestId?: string | null;
  traceId?: string | null;
  occurredAt: string;
}

export interface ListAuditLogsParams {
  principalId?: string;
  action?: string;
  resourceId?: string;
  cursor?: string;
  limit?: number;
}

/* ---------------- 通知 ---------------- */
export interface NotificationView {
  notificationId: string;
  eventType: string;
  i18nKey: string;
  parameters?: Record<string, unknown>;
  status: 'UNREAD' | 'READ';
  createdAt: string;
  readAt?: string | null;
}

export interface ListNotificationsParams {
  unreadOnly?: boolean;
  cursor?: string;
  limit?: number;
}

/* ---------------- 通知管理（Admin） ---------------- */
export interface OutboxEventView {
  eventId: string;
  aggregateType: string;
  aggregateId: string;
  eventType: string;
  occurredAt: string;
  processed: boolean;
  traceId?: string | null;
}

export interface WebhookDeliveryView {
  deliveryId: string;
  eventId: string;
  targetUrl: string;
  status: 'PENDING' | 'DELIVERED' | 'FAILED' | 'DEAD';
  attempts: number;
  lastResponseCode?: number | null;
  lastError?: string | null;
  createdAt: string;
  updatedAt: string;
}

/* ---------------- 指标 ---------------- */
export interface MetricsSummary {
  generatedAt: string;
  api: Record<string, unknown>;
  jobs: Record<string, unknown>;
  dependencies: Record<string, unknown>;
}
