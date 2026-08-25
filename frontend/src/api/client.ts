// ModelHub API 客户端 — 统一 envelope 解包 + Bearer/Cookie 双认证 + 401 自动刷新
// 时间：2026-08-21  作者：AxeXie
// 契约依据：prd/v1/contracts/openapi-v1.yaml + specs/04-api-contract.md

/** 统一成功 envelope（04 §2.1）。 */
export interface Envelope<T> {
  code: 'OK';
  message: string;
  data: T;
  traceId: string;
}

/** 统一错误 envelope（04 §2.2）。 */
export interface ApiError {
  code: string;
  message: string;
  details?: Array<{ field: string; reason: string }>;
  traceId?: string;
}

/** 稳定错误码（04 §2.2）。 */
export const ErrorCodes = {
  UNAUTHENTICATED: 'UNAUTHENTICATED',
  CSRF_INVALID: 'CSRF_INVALID',
  FORBIDDEN: 'FORBIDDEN',
  RESOURCE_NOT_FOUND: 'RESOURCE_NOT_FOUND',
  CONFLICT: 'CONFLICT',
  IDEMPOTENCY_CONFLICT: 'IDEMPOTENCY_CONFLICT',
  INVALID_STATE_TRANSITION: 'INVALID_STATE_TRANSITION',
  PRECONDITION_FAILED: 'PRECONDITION_FAILED',
  VALIDATION_FAILED: 'VALIDATION_FAILED',
  METADATA_SCHEMA_INVALID: 'METADATA_SCHEMA_INVALID',
  RATE_LIMITED: 'RATE_LIMITED',
  DEPENDENCY_UNAVAILABLE: 'DEPENDENCY_UNAVAILABLE',
  FEATURE_DISABLED: 'FEATURE_DISABLED',
  CONTENT_REJECTED: 'CONTENT_REJECTED',
} as const;

export class ApiRequestError extends Error {
  readonly status: number;
  readonly apiCode: string;
  readonly details: ApiError['details'];
  readonly traceId?: string;

  constructor(status: number, apiCode: string, message: string,
              details: ApiError['details'], traceId?: string) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.apiCode = apiCode;
    this.details = details;
    this.traceId = traceId;
  }
}

// ---------- 会话令牌管理 ----------

interface TokenState {
  accessToken: string;
  csrfToken: string;
  user: User | null;
}

let tokenState: TokenState = { accessToken: '', csrfToken: '', user: null };

/** 从 register/login 响应初始化会话（cookie 由浏览器自动设置，这里只存 accessToken/csrf）。 */
export function setSession(accessToken: string, csrfToken: string, user: User): void {
  tokenState = { accessToken, csrfToken, user };
  onSessionChange?.();
}

export function clearSession(): void {
  tokenState = { accessToken: '', csrfToken: '', user: null };
  onSessionChange?.();
}

export function currentAccessToken(): string {
  return tokenState.accessToken;
}

export function currentUser(): User | null {
  return tokenState.user;
}

let onSessionChange: (() => void) | null = null;
/** AuthContext 注册会话变化回调，驱动 React 重渲染。 */
export function setSessionChangeListener(fn: () => void): void {
  onSessionChange = fn;
}

// ---------- 刷新令牌（401 时自动调用一次，失败则登出） ----------

let refreshing: Promise<boolean> | null = null;

async function tryRefresh(): Promise<boolean> {
  if (refreshing) return refreshing;
  refreshing = (async () => {
    try {
      // refresh 端点要求：mh_refresh cookie（HttpOnly，浏览器自动带）+ X-CSRF-Token（= mh_csrf）
      // 04 §3：refresh/logout 必须同时携带 Refresh Cookie 与匹配的 X-CSRF-Token 并校验 Origin。
      const csrf = readCookie('mh_csrf');
      const resp = await fetch('/api/v1/auth/refresh', {
        method: 'POST',
        credentials: 'include',
        headers: { 'X-CSRF-Token': csrf || tokenState.csrfToken },
      });
      if (!resp.ok) return false;
      const body = (await resp.json()) as Envelope<AuthData>;
      setSession(body.data.accessToken, body.data.csrfToken, body.data.user);
      return true;
    } catch {
      return false;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

function readCookie(name: string): string {
  const m = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return m ? decodeURIComponent(m[1]) : '';
}

// ---------- 核心请求 ----------

interface RequestOptions {
  method?: string;
  body?: unknown;
  /** 幂等键（POST 创建类必需，04 §10）。 */
  idempotencyKey?: string;
  /** If-Match（条件更新，04 §10）。 */
  ifMatch?: string;
  /** 双提交 CSRF token（refresh/logout 必需，04 §3）。 */
  csrfToken?: string;
  query?: Record<string, QueryValue>;
  /** 跳过 401 自动刷新（避免 refresh 自身递归）。 */
  skipRefresh?: boolean;
  signal?: AbortSignal;
}

/** 查询参数值：数组展开为重复参数（framework/tag/scene/capability 多值筛选）。 */
export type QueryValue = string | number | boolean | string[] | undefined;

async function rawRequest<T>(path: string, opts: RequestOptions): Promise<T> {
  const headers: Record<string, string> = {};
  if (tokenState.accessToken) {
    headers['Authorization'] = `Bearer ${tokenState.accessToken}`;
  }
  if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (opts.idempotencyKey) headers['Idempotency-Key'] = opts.idempotencyKey;
  if (opts.ifMatch) headers['If-Match'] = opts.ifMatch;
  if (opts.csrfToken) headers['X-CSRF-Token'] = opts.csrfToken;

  const qs = buildQuery(opts.query);
  const url = `/api/v1${path}${qs}`;

  let resp: Response;
  try {
    resp = await fetch(url, {
      method: opts.method ?? 'GET',
      headers,
      credentials: 'include',
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
      signal: opts.signal,
    });
  } catch (e) {
    throw new ApiRequestError(0, 'NETWORK_ERROR', '网络请求失败', undefined);
  }

  // 401 且可刷新 → 刷新后重试一次（整页刷新后内存 csrfToken 为空，但 mh_csrf cookie 仍在，09 §11）
  if (resp.status === 401 && !opts.skipRefresh && (tokenState.csrfToken || readCookie('mh_csrf'))) {
    const ok = await tryRefresh();
    if (ok) {
      return rawRequest<T>(path, { ...opts, skipRefresh: true });
    }
  }

  // 204 无响应体
  if (resp.status === 204) return undefined as T;

  const text = await resp.text();
  let parsed: unknown = null;
  if (text) {
    try { parsed = JSON.parse(text); } catch { /* 非 JSON */ }
  }

  if (!resp.ok) {
    const err = (parsed ?? {}) as Partial<ApiError>;
    throw new ApiRequestError(
      resp.status,
      err.code ?? 'UNKNOWN',
      err.message ?? resp.statusText,
      err.details,
      err.traceId,
    );
  }

  const env = parsed as Envelope<T>;
  return env.data;
}

function buildQuery(query?: Record<string, QueryValue>): string {
  if (!query) return '';
  const parts: string[] = [];
  for (const [k, v] of Object.entries(query)) {
    if (v === undefined || v === '') continue;
    if (Array.isArray(v)) {
      for (const item of v) {
        if (item !== undefined && item !== '') {
          parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(item)}`);
        }
      }
      continue;
    }
    parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
  }
  return parts.length ? `?${parts.join('&')}` : '';
}

// ---------- 类型化端点封装（04 §3/§5/§7） ----------

export const api = {
  // Auth
  register: (body: { username: string; password: string; nickname?: string }) =>
    rawRequest<AuthData>('/auth/register', { method: 'POST', body, idempotencyKey: uuid() }),
  login: (body: { username: string; password: string }) =>
    rawRequest<AuthData>('/auth/login', { method: 'POST', body }),
  me: () => rawRequest<User>('/auth/me', {}),
  // refresh/logout 必须携带匹配的 X-CSRF-Token（= mh_csrf 双提交 token，04 §3）
  logout: () => rawRequest<void>('/auth/logout', {
    method: 'POST', skipRefresh: true, csrfToken: readCookie('mh_csrf') || tokenState.csrfToken,
  }),
  logoutAll: () => rawRequest<void>('/auth/logout-all', {
    method: 'POST', skipRefresh: true, csrfToken: readCookie('mh_csrf') || tokenState.csrfToken,
  }),
  changePassword: (body: { oldPassword: string; newPassword: string }) =>
    rawRequest<void>('/auth/change-password', { method: 'POST', body, idempotencyKey: uuid() }),

  // Repositories
  listRepositories: (query: RepoListQuery, signal?: AbortSignal) =>
    rawRequest<Page<Repository>>('/repositories', {
      query: query as Record<string, QueryValue>, signal,
    }),
  getRepository: (repoId: string) => rawRequest<Repository>(`/repositories/${repoId}`, {}),
  resolveRepo: (typeKey: string, namespace: string, name: string) =>
    rawRequest<Repository>(`/repositories/resolve/${typeKey}/${namespace}/${name}`, {}),
  relatedRepositories: (repoId: string, page = 1, pageSize = 6) =>
    rawRequest<Page<Repository>>(`/repositories/${repoId}/related`, { query: { page, pageSize } }),
  createRepo: (body: CreateRepoRequest) =>
    rawRequest<Repository>('/repositories', { method: 'POST', body, idempotencyKey: uuid() }),
  patchRepo: (repoId: string, body: Record<string, unknown>, ifMatch: string) =>
    rawRequest<Repository>(`/repositories/${repoId}`, { method: 'PATCH', body, ifMatch }),
  deleteRepo: (repoId: string, ifMatch: string) =>
    rawRequest<void>(`/repositories/${repoId}`, {
      method: 'DELETE', ifMatch, idempotencyKey: uuid(),
    }),

  // Likes / Favorites（幂等，禁止 Toggle，04 §5）
  like: (repoId: string) =>
    rawRequest<RelationshipState>(`/repositories/${repoId}/likes`, { method: 'POST', body: {} }),
  unlike: (repoId: string) =>
    rawRequest<void>(`/repositories/${repoId}/likes`, { method: 'DELETE' }),
  favorite: (repoId: string) =>
    rawRequest<RelationshipState>(`/repositories/${repoId}/favorite`, { method: 'POST', body: {} }),
  unfavorite: (repoId: string) =>
    rawRequest<void>(`/repositories/${repoId}/favorite`, { method: 'DELETE' }),

  // Files / Upload / Download
  // GET /files 返回 FilePageEnvelope：data={items,nextCursor}（04 §7），不是裸数组
  listFiles: (repoId: string, branch?: string, path?: string) =>
    rawRequest<FilePage>(`/repositories/${repoId}/files`, {
      query: { branch: branch ?? 'main', path },
    }),
  createDownloadSession: (repoId: string, fileId: string) =>
    rawRequest<DownloadSession>(
      `/repositories/${repoId}/files/${fileId}/download-sessions`,
      { method: 'POST', body: {}, idempotencyKey: uuid() },
    ),
  initiateUpload: (body: InitiateUploadRequest) =>
    rawRequest<UploadSession>(`/repositories/${body.repoId}/uploads`, {
      method: 'POST', body, idempotencyKey: uuid(),
    }),
  uploadStatus: (uploadId: string) => rawRequest<UploadSession>(`/uploads/${uploadId}`, {}),
  completeUpload: (uploadId: string) =>
    rawRequest<UploadSession>(`/uploads/${uploadId}:complete`, { method: 'POST', body: {}, idempotencyKey: uuid() }),
  abortUpload: (uploadId: string) =>
    rawRequest<UploadSession>(`/uploads/${uploadId}:abort`, { method: 'POST', body: {}, idempotencyKey: uuid() }),

  // Feedbacks（详情页交流反馈，04 §5）
  listFeedbacks: (repoId: string, cursor?: string, limit = 20) =>
    rawRequest<CursorPage<Feedback>>(`/repositories/${repoId}/feedbacks`, {
      query: { cursor, limit },
    }),
  createFeedback: (repoId: string, content: string) =>
    rawRequest<Feedback>(`/repositories/${repoId}/feedbacks`, {
      method: 'POST', body: { content }, idempotencyKey: uuid(),
    }),

  // Me
  meRepositories: (tab: 'created' | 'likes' | 'favorites', page = 1, pageSize = 12, type?: string) =>
    rawRequest<Page<Repository>>('/me/repositories', { query: { tab, page, pageSize, type } }),
  myAccessRequests: (status?: string) =>
    rawRequest<CursorPage<AccessRequest>>('/me/access-requests', { query: { status } }),

  // Access Requests
  requestAccess: (repoId: string, reason: string) =>
    rawRequest<AccessRequest>(`/repositories/${repoId}/access-requests`, {
      method: 'POST', body: { reason }, idempotencyKey: uuid(),
    }),
  /** 所有者/维护者查看仓库的申请列表（09 §6.3；非维护者 403）。 */
  repoAccessRequests: (repoId: string) =>
    rawRequest<CursorPage<AccessRequest>>(`/repositories/${repoId}/access-requests`, {}),
  approveAccess: (repoId: string, requestId: string, ifMatch: string) =>
    rawRequest<AccessRequest>(`/repositories/${repoId}/access-requests/${requestId}:approve`, {
      method: 'POST', body: {}, ifMatch, idempotencyKey: uuid(),
    }),
  rejectAccess: (repoId: string, requestId: string, ifMatch: string) =>
    rawRequest<AccessRequest>(`/repositories/${repoId}/access-requests/${requestId}:reject`, {
      method: 'POST', body: {}, ifMatch, idempotencyKey: uuid(),
    }),
  revokeAccess: (repoId: string, requestId: string, ifMatch: string) =>
    rawRequest<AccessRequest>(`/repositories/${repoId}/access-requests/${requestId}:revoke`, {
      method: 'POST', body: {}, ifMatch, idempotencyKey: uuid(),
    }),

  // Resource Types & Metadata
  resourceTypes: () => rawRequest<{ items: ResourceType[] }>('/resource-types', {}),
  resourceTypeSchema: (typeKey: string, version?: number) =>
    rawRequest<ResourceTypeSchema>(`/resource-types/${typeKey}/schema`, { query: { version } }),
  metadataOptions: () => rawRequest<MetadataOptions>('/metadata/options', {}),
  hotSearches: () => rawRequest<HotSearches>('/metadata/hot-searches', {}),

  // Organizations（后端返回 PageResult，列表项含 repoCounts，09 §5.2）
  listOrganizations: (page = 1, pageSize = 20) =>
    rawRequest<Page<OrganizationListItem>>('/organizations', { query: { page, pageSize } }),
};

// ---------- 工具 ----------

export function uuid(): string {
  if (crypto.randomUUID) return crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

// ---------- 类型（契约 04 / openapi） ----------

export interface User {
  id: string;
  namespaceId: string;
  username: string;
  nickname: string;
  avatarUrl?: string;
  status: 'active' | 'locked' | 'disabled';
  platformRoles: Array<'platform_admin' | 'platform_auditor'>;
  version: number;
}

export interface AuthData {
  accessToken: string;
  expiresIn: number;
  csrfToken: string;
  user: User;
}

export interface Page<T> {
  total: number;
  page: number;
  pageSize: number;
  items: T[];
}

export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
}

export interface Repository {
  id: string;
  type: string;
  namespace: string;
  name: string;
  displayName: string;
  description?: string;
  visibility: 'public' | 'organization' | 'private';
  gated: boolean;
  stats: { likes: number; favorites: number; downloads: number; visits: number; fileCount: number };
  lifecycleStatus: 'provisioning' | 'draft' | 'active' | 'archived' | 'deleting' | 'deleted' | 'purging' | 'purged' | 'failed';
  metadata?: Record<string, unknown>;
  metadataSchemaVersion: number;
  version: number;
  createdAt: string;
  updatedAt: string;
  etag?: string;
}

export interface CreateRepoRequest {
  namespaceId: string;
  name: string;
  type: string;
  displayName?: string;
  description?: string;
  visibility?: 'public' | 'organization' | 'private';
  gated?: boolean;
  metadata?: Record<string, unknown>;
  metadataSchemaVersion: number;
}

export interface RepoListQuery {
  type?: string;
  keyword?: string;
  task?: string;
  framework?: string[];
  tag?: string[];
  scene?: string[];
  capability?: string[];
  architecture?: string;
  language?: string;
  license?: string;
  apiStatus?: string;
  org?: string;
  gated?: boolean;
  mcp?: boolean;
  deployable?: boolean;
  featured?: boolean;
  sort?: 'relevance-v1' | 'updatedAt-desc' | 'downloads-desc' | 'likes-desc' | 'visits-desc' | 'hot-desc';
  page?: number;
  pageSize?: number;
  facet?: string[];
}

export interface RelationshipState {
  active: boolean;
}

export interface FileNode {
  id: string;
  name: string;
  path: string;
  type: 'file' | 'directory';
  size?: number;
  sha256?: string;
  commitSha?: string;
}

/** GET /files 响应 data（04 FilePageEnvelope）。 */
export interface FilePage {
  items: FileNode[];
  nextCursor: string | null;
}

export interface DownloadSession {
  url: string;
  expiresAt: string;
}

export interface UploadSession {
  id: string;
  status: string;
  parts?: Array<{ partNumber: number; url?: string; etag?: string }>;
}

export interface InitiateUploadRequest {
  /** 目标仓库（路径路由用，04 §6 上传） */
  repoId: string;
  branch: string;
  baseCommitSha: string;
  fileName: string;
  sizeBytes: number;
  sha256: string;
  mimeType: string;
  partSize?: number;
}

export interface AccessRequest {
  id: string;
  repositoryId: string;
  requesterId: string;
  status: 'pending' | 'approved' | 'rejected' | 'revoked' | 'expired' | 'withdrawn';
  reason?: string;
  reviewerId?: string;
  grantExpiresAt?: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ResourceType {
  typeKey: string;
  currentVersion: number;
  displayName: string;
  capabilities?: string[];
}

/** 契约 ResourceTypeSchema（/resource-types/{typeKey}/schema），筛选组数据驱动来源。 */
export interface ResourceTypeSchema {
  typeKey: string;
  version: number;
  metadataSchema: Record<string, unknown>;
  uiSchema?: Record<string, unknown>;
  filePolicy?: Record<string, unknown>;
  defaultVisibility?: string;
  allowedWorkflows?: string[];
  facets?: Array<Record<string, unknown>>;
  checksum?: string;
  status?: string;
  publishedAt?: string;
}

export interface TaxonomyOption {
  key: string;
  displayName: string;
  parentKey: string | null;
  status: string;
}

export interface MetadataOptions {
  version?: string;
  taxonomies: Record<string, TaxonomyOption[]>;
}

/** 契约 HotSearches（/metadata/hot-searches）。 */
export interface HotSearches {
  version: string;
  words: string[];
}

export interface Organization {
  id: string;
  namespaceId: string;
  slug: string;
  name: string;
  description?: string;
  status: string;
  version: number;
  etag?: string;
  createdAt?: string;
}

/** 组织列表项：列表端点额外返回 repoCounts（typeKey → 计数）。 */
export interface OrganizationListItem extends Organization {
  repoCounts?: Record<string, number>;
}

/** 交流反馈（契约 Feedback）。 */
export interface Feedback {
  id: string;
  author: string;
  content: string;
  createdAt: string;
}
