import api from './api';

export interface LoginParams {
  username: string;
  password: string;
}

export interface LoginResult {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  userInfo: {
    id: number;
    username: string;
    displayName: string;
    email: string;
    avatar: string;
    preferredLocale: string;
    roles: string[];
  };
}

export const authApi = {
  login: (params: LoginParams) => api.post<any, { data: LoginResult }>('/auth/login', params),
  refresh: (refreshToken: string) => api.post<any, { data: LoginResult }>('/auth/refresh', { refreshToken }),
  me: () => api.get<any, { data: LoginResult['userInfo'] }>('/auth/me'),
};

export const ontologyApi = {
  listEntities: (params?: { domain?: string; status?: string; keyword?: string; page?: number; size?: number }) =>
    api.get('/ontology/entities', { params }),
  getEntity: (id: number) => api.get(`/ontology/entities/${id}`),
  createEntity: (data: any) => api.post('/ontology/entities', data),
  updateEntity: (id: number, data: any) => api.put(`/ontology/entities/${id}`, data),
  deleteEntity: (id: number) => api.delete(`/ontology/entities/${id}`),
  listMetrics: (entityId: number) => api.get(`/ontology/entities/${entityId}/metrics`),
  createMetric: (data: any) => api.post('/ontology/metrics', data),
  listDimensions: (entityId: number) => api.get(`/ontology/entities/${entityId}/dimensions`),
  createDimension: (data: any) => api.post('/ontology/dimensions', data),
};

export const taskApi = {
  listTasks: (params?: { taskType?: string; status?: string; page?: number; size?: number }) =>
    api.get('/tasks', { params }),
  getTask: (id: number) => api.get(`/tasks/${id}`),
  createTask: (data: any) => api.post('/tasks', data),
  startTask: (id: number) => api.post(`/tasks/${id}/start`),
};

export const agentApi = {
  chat: (sessionId: string, message: string, context?: Record<string, any>) =>
    api.post('/agent/chat', { sessionId, message, context }),
  intent: (query: string) => api.post('/agent/intent', { query }),
};

export const dictApi = {
  listGroups: (params?: { category?: string; status?: string }) =>
    api.get('/dict/groups', { params }),
  getGroup: (code: string) => api.get(`/dict/groups/${code}`),
  createGroup: (data: any) => api.post('/dict/groups', data),
  updateGroup: (code: string, data: any) => api.put(`/dict/groups/${code}`, data),
  deleteGroup: (code: string) => api.delete(`/dict/groups/${code}`),

  listItems: (groupCode: string) => api.get(`/dict/items/${groupCode}`),
  listItemTree: (groupCode: string) => api.get(`/dict/items/${groupCode}/tree`),
  batchItems: (groupCodes: string[]) =>
    api.post('/dict/items/batch', { groupCodes }),
  searchItems: (keyword: string, groupCode?: string) =>
    api.get('/dict/search', { params: { keyword, groupCode } }),

  createItem: (data: any) => api.post('/dict/items', data),
  updateItem: (id: number, data: any) => api.put(`/dict/items/${id}`, data),
  deleteItem: (id: number) => api.delete(`/dict/items/${id}`),
  batchCreateItems: (groupCode: string, items: any[]) =>
    api.post(`/dict/items/${groupCode}/batch`, items),

  exportGroup: (groupCode: string) =>
    api.get('/dict/export', { params: { groupCode } }),
  importGroup: (data: any, overwrite = false) =>
    api.post('/dict/import', data, { params: { overwrite } }),
};

// 用户管理 API
export const userApi = {
  listUsers: (params?: { keyword?: string; status?: string; page?: number; size?: number }) =>
    api.get('/users', { params }),
  getUser: (id: number) => api.get(`/users/${id}`),
  createUser: (data: any) => api.post('/users', data),
  updateUser: (id: number, data: any) => api.put(`/users/${id}`, data),
  deleteUser: (id: number) => api.delete(`/users/${id}`),
  setUserStatus: (id: number, status: string) =>
    api.put(`/users/${id}/status`, { status }),
  resetPassword: (id: number, newPassword: string) =>
    api.put(`/users/${id}/password`, { newPassword }),
  changeMyPassword: (oldPassword: string, newPassword: string) =>
    api.put('/users/me/password', { oldPassword, newPassword }),
  assignRoles: (id: number, roleIds: number[]) =>
    api.put(`/users/${id}/roles`, { roleIds }),
};

export const roleApi = {
  listRoles: () => api.get('/roles'),
  getRole: (id: number) => api.get(`/roles/${id}`),
  createRole: (data: any) => api.post('/roles', data),
  updateRole: (id: number, data: any) => api.put(`/roles/${id}`, data),
  deleteRole: (id: number) => api.delete(`/roles/${id}`),
  assignPermissions: (id: number, permissions: any[]) =>
    api.put(`/roles/${id}/permissions`, { permissions }),
  listPermissions: (roleId?: number) =>
    api.get('/permissions', { params: roleId ? { roleId } : undefined }),
};

// 系统配置 API
export const configApi = {
  listConfigs: (params?: { category?: string; keyword?: string; environment?: string; page?: number; size?: number }) =>
    api.get('/config', { params }),
  getConfig: (key: string) => api.get(`/config/${key}`),
  createConfig: (data: any) => api.post('/config', data),
  updateConfig: (key: string, data: any) => api.put(`/config/${key}`, data),
  deleteConfig: (key: string) => api.delete(`/config/${key}`),
  getHistory: (key: string) => api.get(`/config/${key}/history`),
};

// 审计日志 API
export const auditApi = {
  listLogs: (params?: {
    operator?: string;
    resourceType?: string;
    action?: string;
    status?: string;
    traceId?: string;
    startTime?: string;
    endTime?: string;
    page?: number;
    size?: number;
  }) => api.get('/audit/logs', { params }),
  getLog: (id: number) => api.get(`/audit/logs/${id}`),
  getTrace: (traceId: string) => api.get(`/audit/trace/${traceId}`),
};

// Phase 2: 审批中心 API
export const approvalApi = {
  listTemplates: (params?: { page?: number; size?: number }) =>
    api.get('/approval/templates', { params }),
  getTemplate: (id: number) => api.get(`/approval/templates/${id}`),
  createTemplate: (data: any) => api.post('/approval/templates', data),
  updateTemplate: (id: number, data: any) => api.put(`/approval/templates/${id}`, data),
  deleteTemplate: (id: number) => api.delete(`/approval/templates/${id}`),

  listInstances: (params?: { templateId?: number; status?: string; applicantId?: number; page?: number; size?: number }) =>
    api.get('/approval/instances', { params }),
  getInstance: (id: number) => api.get(`/approval/instances/${id}`),
  createInstance: (data: any) => api.post('/approval/instances', data),
  approveInstance: (id: number, action: 'approve' | 'reject', comment?: string) =>
    api.put(`/approval/instances/${id}/${action}`, { comment }),

  listSteps: (instanceId: number) => api.get(`/approval/instances/${instanceId}/steps`),
};

// Phase 2: 质量规则 API
export const qualityApi = {
  listRules: (params?: { ruleType?: string; targetTable?: string; enabled?: boolean; page?: number; size?: number }) =>
    api.get('/quality/rules', { params }),
  getRule: (id: number) => api.get(`/quality/rules/${id}`),
  createRule: (data: any) => api.post('/quality/rules', data),
  updateRule: (id: number, data: any) => api.put(`/quality/rules/${id}`, data),
  deleteRule: (id: number) => api.delete(`/quality/rules/${id}`),
  toggleRule: (id: number, enabled: boolean) => api.put(`/quality/rules/${id}/enabled`, { enabled }),

  listChecks: (params?: { ruleId?: number; status?: string; startTime?: string; endTime?: string; page?: number; size?: number }) =>
    api.get('/quality/checks', { params }),
  runCheck: (ruleId: number) => api.post(`/quality/rules/${ruleId}/check`),
  getCheckResult: (id: number) => api.get(`/quality/checks/${id}`),
};

// Phase 2: 血缘图 API
export const lineageApi = {
  listNodes: (params?: { nodeType?: string; keyword?: string }) =>
    api.get('/lineage/nodes', { params }),
  getNode: (id: number) => api.get(`/lineage/nodes/${id}`),
  createNode: (data: any) => api.post('/lineage/nodes', data),
  updateNode: (id: number, data: any) => api.put(`/lineage/nodes/${id}`, data),
  deleteNode: (id: number) => api.delete(`/lineage/nodes/${id}`),

  listEdges: (params?: { upstreamNodeId?: number; downstreamNodeId?: number }) =>
    api.get('/lineage/edges', { params }),
  getEdge: (id: number) => api.get(`/lineage/edges/${id}`),
  createEdge: (data: any) => api.post('/lineage/edges', data),
  updateEdge: (id: number, data: any) => api.put(`/lineage/edges/${id}`, data),
  deleteEdge: (id: number) => api.delete(`/lineage/edges/${id}`),

  getLineageByMetric: (metricId: number) => api.get(`/lineage/metric/${metricId}`),
  getLineageByEntity: (entityId: number) => api.get(`/lineage/entity/${entityId}`),
};

// Phase 2: 数据接入 API
export const ingestionApi = {
  listDataSources: (params?: { sourceType?: string; status?: string; page?: number; size?: number }) =>
    api.get('/datasource/sources', { params }),
  getDataSource: (id: number) => api.get(`/datasource/sources/${id}`),
  createDataSource: (data: any) => api.post('/datasource/sources', data),
  updateDataSource: (id: number, data: any) => api.put(`/datasource/sources/${id}`, data),
  deleteDataSource: (id: number) => api.delete(`/datasource/sources/${id}`),
  testConnection: (id: number) => api.post(`/datasource/sources/${id}/test`),

  listTasks: (params?: { sourceId?: number; taskType?: string; status?: string; page?: number; size?: number }) =>
    api.get('/datasource/tasks', { params }),
  getTask: (id: number) => api.get(`/datasource/tasks/${id}`),
  createTask: (data: any) => api.post('/datasource/tasks', data),
  updateTask: (id: number, data: any) => api.put(`/datasource/tasks/${id}`, data),
  deleteTask: (id: number) => api.delete(`/datasource/tasks/${id}`),
  startTask: (id: number) => api.post(`/datasource/tasks/${id}/start`),
  stopTask: (id: number) => api.post(`/datasource/tasks/${id}/stop`),
};

// Phase 2: 周报 API
export const reportApi = {
  listReports: (params?: { startDate?: string; endDate?: string; page?: number; size?: number }) =>
    api.get('/report/weekly', { params }),
  getReport: (id: number) => api.get(`/report/weekly/${id}`),
  createReport: (data: any) => api.post('/report/weekly', data),
  updateReport: (id: number, data: any) => api.put(`/report/weekly/${id}`, data),
  deleteReport: (id: number) => api.delete(`/report/weekly/${id}`),
};

// Phase 2: 异常诊断 API (Agent)
export const diagnosisApi = {
  analyzeException: (data: any) => api.post('/agent/diagnosis/analyze', data),
  getRecommendations: (diagnosisId: number) => api.get(`/agent/diagnosis/${diagnosisId}/recommendations`),
  applyFix: (data: any) => api.post('/agent/diagnosis/apply-fix', data),
};
