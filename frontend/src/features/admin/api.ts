/**
 * 功能: 公共管理端 (/admin/*) API 调用，经统一 apiClient 访问 /api/v1/system/* 端点。
 *       写操作按契约携带 Idempotency-Key 请求头；页面禁止直接 fetch/拼 URL。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { apiClient } from '@/shared/api';
import type { CursorPage } from '@/shared/types';
import type {
  AddOrganizationMemberRequest,
  AddTeamMemberRequest,
  AgentView,
  AuditLogView,
  ConfigurationView,
  CreateAgentRequest,
  CreateDictionaryItemRequest,
  CreateOrganizationRequest,
  CreateProjectRequest,
  CreateResourceAclRequest,
  CreateRoleBindingRequest,
  CreateRoleRequest,
  CreateTagRequest,
  CreateTeamRequest,
  CreateUserRequest,
  CreatedAgent,
  DictionaryItemView,
  DictionaryTypeView,
  JobView,
  ListAuditLogsParams,
  ListJobsParams,
  ListNotificationsParams,
  ListTagsParams,
  ListUsersParams,
  MetricsSummary,
  NotificationView,
  OrganizationMemberView,
  OrganizationView,
  PageResultUser,
  PermissionView,
  ProjectView,
  ResetPasswordRequest,
  ResourceAclView,
  RoleBindingView,
  RoleView,
  TagView,
  TeamMemberView,
  TeamView,
  UpdateAgentToolAllowlistRequest,
  UpdateConfigurationRequest,
  UpdateDictionaryItemRequest,
  UpdateRoleRequest,
  UpdateTagRequest,
  UpdateTeamRequest,
  UpdateUserRequest,
} from './types';

/** 生成当前 Principal 范围内唯一的写请求幂等键 */
function newIdempotencyKey(): string {
  const cryptoRef = globalThis.crypto;
  if (cryptoRef && typeof cryptoRef.randomUUID === 'function') {
    return `idmp_${cryptoRef.randomUUID()}`;
  }
  return `idmp_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 12)}`;
}

/** 携带幂等键的写请求配置 */
function idempotent() {
  return { headers: { 'Idempotency-Key': newIdempotencyKey() } };
}

/* ---------------- 用户 ---------------- */
export function listUsers(params: ListUsersParams): Promise<PageResultUser> {
  return apiClient.get<PageResultUser>('/system/users', { params });
}

export function createUser(payload: CreateUserRequest) {
  return apiClient.post('/system/users', payload, idempotent());
}

export function updateUser(userId: string, payload: UpdateUserRequest) {
  return apiClient.patch(`/system/users/${userId}`, payload);
}

export function enableUser(userId: string) {
  return apiClient.post(`/system/users/${userId}:enable`, undefined, idempotent());
}

export function disableUser(userId: string) {
  return apiClient.post(`/system/users/${userId}:disable`, undefined, idempotent());
}

export function resetUserPassword(userId: string, payload: ResetPasswordRequest) {
  return apiClient.post(`/system/users/${userId}:reset-password`, payload);
}

/* ---------------- Agent ---------------- */
export function listAgents(): Promise<AgentView[]> {
  return apiClient.get<AgentView[]>('/system/agents');
}

export function createAgent(payload: CreateAgentRequest): Promise<CreatedAgent> {
  return apiClient.post<CreatedAgent>('/system/agents', payload, idempotent());
}

export function enableAgent(agentId: string) {
  return apiClient.post(`/system/agents/${agentId}:enable`);
}

export function disableAgent(agentId: string) {
  return apiClient.post(`/system/agents/${agentId}:disable`);
}

export function updateAgentToolAllowlist(
  agentId: string,
  payload: UpdateAgentToolAllowlistRequest,
) {
  return apiClient.put(`/system/agents/${agentId}/tool-allowlist`, payload);
}

/* ---------------- 组织/项目 ---------------- */
export function listOrganizations(): Promise<OrganizationView[]> {
  return apiClient.get<OrganizationView[]>('/system/organizations');
}

export function createOrganization(payload: CreateOrganizationRequest): Promise<OrganizationView> {
  return apiClient.post<OrganizationView>('/system/organizations', payload, idempotent());
}

export function listOrganizationMembers(organizationId: string): Promise<OrganizationMemberView[]> {
  return apiClient.get<OrganizationMemberView[]>(
    `/system/organizations/${organizationId}/members`,
  );
}

export function addOrganizationMember(
  organizationId: string,
  payload: AddOrganizationMemberRequest,
) {
  return apiClient.post(
    `/system/organizations/${organizationId}/members`,
    payload,
    idempotent(),
  );
}

export function removeOrganizationMember(organizationId: string, principalId: string) {
  return apiClient.delete(
    `/system/organizations/${organizationId}/members/${principalId}`,
  );
}

export function listOrganizationProjects(organizationId: string): Promise<ProjectView[]> {
  return apiClient.get<ProjectView[]>(
    `/system/organizations/${organizationId}/projects`,
  );
}

export function createProject(organizationId: string, payload: CreateProjectRequest): Promise<ProjectView> {
  return apiClient.post<ProjectView>(
    `/system/organizations/${organizationId}/projects`,
    payload,
    idempotent(),
  );
}

/* ---------------- 角色/权限/绑定/ACL ---------------- */
export function listRoles(): Promise<RoleView[]> {
  return apiClient.get<RoleView[]>('/system/roles');
}

export function createRole(payload: CreateRoleRequest): Promise<RoleView> {
  return apiClient.post<RoleView>('/system/roles', payload, idempotent());
}

export function updateRole(roleId: string, payload: UpdateRoleRequest): Promise<RoleView> {
  return apiClient.patch<RoleView>(`/system/roles/${roleId}`, payload);
}

export function deleteRole(roleId: string) {
  return apiClient.delete(`/system/roles/${roleId}`);
}

export function listPermissions(): Promise<PermissionView[]> {
  return apiClient.get<PermissionView[]>('/system/permissions');
}

export function listRoleBindings(): Promise<RoleBindingView[]> {
  return apiClient.get<RoleBindingView[]>('/system/role-bindings');
}

export function createRoleBinding(payload: CreateRoleBindingRequest): Promise<RoleBindingView> {
  return apiClient.post<RoleBindingView>('/system/role-bindings', payload, idempotent());
}

export function deleteRoleBinding(bindingId: string) {
  return apiClient.delete(`/system/role-bindings/${bindingId}`);
}

export function listResourceAcls(): Promise<ResourceAclView[]> {
  return apiClient.get<ResourceAclView[]>('/system/resource-acls');
}

export function createResourceAcl(payload: CreateResourceAclRequest): Promise<ResourceAclView> {
  return apiClient.post<ResourceAclView>('/system/resource-acls', payload, idempotent());
}

export function deleteResourceAcl(aclId: string) {
  return apiClient.delete(`/system/resource-acls/${aclId}`);
}

/* ---------------- 字典 ---------------- */
export function listDictionaries(): Promise<DictionaryTypeView[]> {
  return apiClient.get<DictionaryTypeView[]>('/system/dictionaries');
}

export function listDictionaryItems(dictCode: string): Promise<DictionaryItemView[]> {
  return apiClient.get<DictionaryItemView[]>(`/system/dictionaries/${dictCode}/items`);
}

export function createDictionaryItem(
  dictCode: string,
  payload: CreateDictionaryItemRequest,
): Promise<DictionaryItemView> {
  return apiClient.post<DictionaryItemView>(
    `/system/dictionaries/${dictCode}/items`,
    payload,
    idempotent(),
  );
}

export function updateDictionaryItem(
  dictCode: string,
  itemCode: string,
  payload: UpdateDictionaryItemRequest,
): Promise<DictionaryItemView> {
  return apiClient.patch<DictionaryItemView>(
    `/system/dictionaries/${dictCode}/items/${itemCode}`,
    payload,
  );
}

/* ---------------- 标签 ---------------- */
export function listTags(params: ListTagsParams): Promise<TagView[]> {
  return apiClient.get<TagView[]>('/system/tags', { params });
}

export function createTag(payload: CreateTagRequest): Promise<TagView> {
  return apiClient.post<TagView>('/system/tags', payload, idempotent());
}

export function updateTag(tagId: string, payload: UpdateTagRequest): Promise<TagView> {
  return apiClient.patch<TagView>(`/system/tags/${tagId}`, payload);
}

export function enableTag(tagId: string) {
  return apiClient.post(`/system/tags/${tagId}:enable`);
}

export function disableTag(tagId: string) {
  return apiClient.post(`/system/tags/${tagId}:disable`);
}

/* ---------------- 配置 ---------------- */
export function listConfigurations(): Promise<ConfigurationView[]> {
  return apiClient.get<ConfigurationView[]>('/system/configurations');
}

export function updateConfiguration(
  configKey: string,
  payload: UpdateConfigurationRequest,
): Promise<ConfigurationView> {
  return apiClient.put<ConfigurationView>(`/system/configurations/${configKey}`, payload);
}

/* ---------------- 任务 ---------------- */
export function listJobs(params: ListJobsParams): Promise<CursorPage<JobView>> {
  return apiClient.get<CursorPage<JobView>>('/system/jobs', { params });
}

export function retryJob(jobId: string) {
  return apiClient.post(`/system/jobs/${jobId}:retry`, undefined, idempotent());
}

export function cancelJob(jobId: string) {
  return apiClient.post(`/system/jobs/${jobId}:cancel`, undefined, idempotent());
}

/* ---------------- 审计 ---------------- */
export function listAuditLogs(params: ListAuditLogsParams): Promise<CursorPage<AuditLogView>> {
  return apiClient.get<CursorPage<AuditLogView>>('/system/audit-logs', { params });
}

/* ---------------- 通知 ---------------- */
export function listNotifications(
  params: ListNotificationsParams,
): Promise<CursorPage<NotificationView>> {
  return apiClient.get<CursorPage<NotificationView>>('/system/notifications', { params });
}

export function markNotificationRead(notificationId: string) {
  return apiClient.post(`/system/notifications/${notificationId}:read`);
}

/* ---------------- 指标 ---------------- */
export function getMetricsSummary(): Promise<MetricsSummary> {
  return apiClient.get<MetricsSummary>('/system/metrics/summary');
}

/* ---------------- Team ---------------- */
export function listTeams(organizationId: string): Promise<TeamView[]> {
  return apiClient.get<TeamView[]>(`/system/organizations/${organizationId}/teams`);
}

export function createTeam(organizationId: string, payload: CreateTeamRequest): Promise<TeamView> {
  return apiClient.post<TeamView>(
    `/system/organizations/${organizationId}/teams`,
    payload,
    idempotent(),
  );
}

export function updateTeam(
  organizationId: string,
  teamId: string,
  payload: UpdateTeamRequest,
): Promise<TeamView> {
  return apiClient.put<TeamView>(
    `/system/organizations/${organizationId}/teams/${teamId}`,
    payload,
  );
}

export function listTeamMembers(
  organizationId: string,
  teamId: string,
): Promise<TeamMemberView[]> {
  return apiClient.get<TeamMemberView[]>(
    `/system/organizations/${organizationId}/teams/${teamId}/members`,
  );
}

export function addTeamMember(
  organizationId: string,
  teamId: string,
  payload: AddTeamMemberRequest,
): Promise<TeamMemberView> {
  return apiClient.post<TeamMemberView>(
    `/system/organizations/${organizationId}/teams/${teamId}/members`,
    payload,
    idempotent(),
  );
}

export function removeTeamMember(
  organizationId: string,
  teamId: string,
  principalId: string,
) {
  return apiClient.delete(
    `/system/organizations/${organizationId}/teams/${teamId}/members/${principalId}`,
  );
}
