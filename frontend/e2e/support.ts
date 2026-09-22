/*
 * 功能: E2E 共享支撑——凭据来源、登录动作、夹具主体准备与通用定位器。
 *       引导口令一律从环境变量读取，绝不写入仓库；未提供凭据时由各 spec 显式 skip。
 * 时间: 2026-09-18
 * 作者: AxeXie
 */
import { expect, request, type APIRequestContext, type Page } from '@playwright/test';

const API_BASE = process.env.E2E_API_BASE_URL ?? 'http://localhost:8080/api/v1';

/** 引导管理员凭据。来源为运行环境；缺失时返回 null，调用方必须 skip 而非放行。 */
export function bootstrapCredentials(): { username: string; password: string } | null {
  const username = process.env.E2E_ADMIN_USERNAME;
  const password = process.env.E2E_ADMIN_PASSWORD;
  return username && password ? { username, password } : null;
}

/**
 * 登录按钮定位器。
 *
 * <p>antd 会在仅两个字符的按钮文案之间插入空格（「登录」实际渲染为「登 录」），
 * 故使用允许空白的正则而非字面量匹配；这不是放宽断言，只是修正定位器。
 */
export function loginButton(page: Page) {
  return page.getByRole('button', { name: /登\s*录|log\s*in|sign\s*in/i });
}

/** 以给定凭据登录并等待离开登录页（强制改密门的解除由夹具准备阶段完成）。 */
export async function login(page: Page, username: string, password: string) {
  await page.goto('/login');
  await page.getByLabel(/用户名|username/i).fill(username);
  await page.getByLabel(/密码|password/i).fill(password);
  await loginButton(page).click();
  await expect(page).not.toHaveURL(/\/login/);
}

/**
 * 经侧边导航进入资产目录。
 *
 * <p>同 {@link openAssetDetailViaCatalog}：不使用整页加载 `/assets`，因为前端容器 nginx 的
 * `location /assets/` 静态资源规则会遮蔽该 SPA 路径（`/assets` 还会 301 到丢失端口的绝对 URL）。
 */
export async function openAssetsViaNav(page: Page) {
  await page.locator('.ant-menu-item').filter({ hasText: /资产目录|Asset Catalog/ }).click();
  await expect(page).toHaveURL(/\/assets$/);
}

/**
 * 从资产目录经真实点击进入资产详情页。
 *
 * <p>刻意不使用 {@code page.goto('/assets/...')} 做整页加载：前端容器 nginx 的
 * {@code location /assets/} 静态资源规则会遮蔽 SPA 深链（详见 EVD-P0BR-050-001 的
 * limitations）。本函数走用户在应用内真实可行的路径，不掩盖该既有基础设施缺陷。
 *
 * <p>目录页默认按创建时间倒序分页（limit=10）：验收栈累积的历史夹具资产会把目标资产
 * 挤出首屏，直接找链接必然间歇失败。先使用页内真实搜索框按显示名过滤，再点击结果——
 * 这是用户本来的操作路径，不是放宽断言。
 */
export async function openAssetDetailViaCatalog(page: Page, displayName: string) {
  await openAssetsViaNav(page);
  await page
    .getByPlaceholder(/搜索名称、描述或标签|Search name, description or tags/i)
    .fill(displayName);
  await page.keyboard.press('Enter');
  await page.getByRole('link', { name: displayName }).click();
  await expect(page.getByText(/页面不存在|Page not found/)).toHaveCount(0);
}

/** 在资产详情页点击版本列表的「查看全部」，返回该按钮定位器。 */
export function viewAllVersionsButton(page: Page) {
  return page
    .locator('.ant-card')
    .filter({ hasText: /版本列表|Version List/ })
    .getByRole('button', { name: /查看全部|View All/ });
}

/* ------------------------------------------------------------------ */
/* 夹具主体与数据：仅用于本地 Compose 验收栈，口令为测试夹具口令，      */
/* 不属于平台 Secret；引导口令仍严格来自环境变量。                     */
/* ------------------------------------------------------------------ */

/**
 * 资产所有者主体：具备 {@code asset:manage}（资产创建/更新的实际强制点）与 {@code project:view}，
 * 刻意不含 {@code authorization:read}，用于证明导航按权限过滤。
 */
export const E2E_AUTHOR = {
  username: 'e2e050-asset-owner',
  password: 'E2e050-Author-01!z',
  scopes: ['project:view', 'asset:read', 'asset:manage'],
} as const;

/**
 * 授权管理主体：同时具备授权读/写、字典、标签与项目管理 Scope。
 *
 * <p>引导管理员的 JWT 只携带粗粒度 Scope（user/authorization/agent），
 * 前端路由守卫会拒绝其访问字典与资产页面；故 E2E 使用显式列出所需 Scope 的最小管理主体。
 */
export const E2E_MANAGER = {
  username: 'e2e050-admin',
  password: 'E2e050-Tmp-01!z',
  scopes: [
    'project:view',
    'asset:read',
    'dictionary:read',
    'tag:read',
    'authorization:read',
    'authorization:manage',
    'user:read',
  ],
} as const;

/**
 * 授权只读主体：仅有 {@code authorization:read}，刻意不含 {@code project:view}。
 *
 * <p>用于证明导航按权限过滤是逐项判定而非整体开关：该主体应看到角色绑定/资源 ACL 入口，
 * 但看不到 Team 入口。
 */
export const E2E_AUTHZ_READER = {
  username: 'e2e050-authz-reader',
  password: 'E2e050-AuthzReader-01!z',
  scopes: ['authorization:read'],
} as const;

export const E2E_ORG = { code: 'e2e050-org', name: 'E2E 050 Org' } as const;
export const E2E_TEAM_NAME = 'E2E050 Team' as const;
export const E2E_ASSET = {
  namespace: 'nlp',
  name: 'e2e050-version-target',
  displayName: 'E2E 050 版本入口目标',
} as const;

export interface E2eFixtures {
  organizationId: string;
  teamId: string;
  assetId: string;
  authorPrincipalId: string;
  managerPrincipalId: string;
}

function bearer(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

async function readJson(response: { json: () => Promise<unknown> }): Promise<unknown> {
  return response.json().catch(() => null);
}

/** 从统一响应包裹中取出 data 字段。 */
function dataOf(body: unknown): unknown {
  if (body && typeof body === 'object' && 'data' in body) {
    return (body as { data: unknown }).data;
  }
  return null;
}

async function loginApi(api: APIRequestContext, username: string, password: string): Promise<string> {
  const response = await api.post(`${API_BASE}/auth/login`, {
    data: { username, password },
    failOnStatusCode: false,
  });
  const data = dataOf(await readJson(response)) as { accessToken?: string } | null;
  const token = data?.accessToken;
  expect(token, `夹具主体 ${username} 登录失败（HTTP ${response.status()}）`).toBeTruthy();
  return token as string;
}

/** 解除首登强制改密门（轮换为同值），返回改密后重新签发的 Token。 */
async function resolvePasswordGate(
  api: APIRequestContext,
  username: string,
  password: string,
  token: string,
): Promise<void> {
  const me = await api.get(`${API_BASE}/me`, { headers: bearer(token), failOnStatusCode: false });
  const meData = dataOf(await readJson(me)) as { forcePasswordChange?: boolean } | null;
  if (meData?.forcePasswordChange !== true) {
    return;
  }
  const changed = await api.put(`${API_BASE}/me/password`, {
    headers: bearer(token),
    data: { currentPassword: password, newPassword: password },
    failOnStatusCode: false,
  });
  expect(changed.status(), `夹具主体 ${username} 解除强制改密门失败`).toBe(200);
}

/** 幂等创建本地用户；已存在时按 username 查回 principalId。 */
async function ensureUser(
  api: APIRequestContext,
  adminToken: string,
  user: { username: string; password: string; scopes: readonly string[] },
): Promise<string> {
  const created = await api.post(`${API_BASE}/system/users`, {
    headers: { ...bearer(adminToken), 'Idempotency-Key': `idmp_e2e050_user_${user.username}` },
    data: {
      username: user.username,
      displayName: `E2E 050 ${user.username}`,
      locale: 'zh-CN',
      temporaryPassword: user.password,
      scopes: [...user.scopes],
    },
    failOnStatusCode: false,
  });
  expect([201, 409], `创建夹具用户 ${user.username} 返回 HTTP ${created.status()}`).toContain(
    created.status(),
  );

  if (created.status() === 201) {
    await resolvePasswordGate(api, user.username, user.password, await loginApi(api, user.username, user.password));
  }

  const listed = await api.get(`${API_BASE}/system/users`, {
    headers: bearer(adminToken),
    params: { keyword: user.username, size: 50 },
    failOnStatusCode: false,
  });
  const page = dataOf(await readJson(listed)) as { items?: { username: string; principalId: string }[] } | null;
  const match = page?.items?.find((item) => item.username === user.username);
  expect(match, `无法查回夹具用户 ${user.username} 的 principalId`).toBeTruthy();
  return match!.principalId;
}

/** 幂等创建组织；已存在时按 code 查回。 */
async function ensureOrganization(api: APIRequestContext, adminToken: string): Promise<string> {
  const created = await api.post(`${API_BASE}/system/organizations`, {
    headers: { ...bearer(adminToken), 'Idempotency-Key': `idmp_e2e050_org_${E2E_ORG.code}` },
    data: { code: E2E_ORG.code, name: E2E_ORG.name },
    failOnStatusCode: false,
  });
  const createdData = dataOf(await readJson(created)) as { organizationId?: string } | null;
  if (created.status() === 201 && createdData?.organizationId) {
    return createdData.organizationId;
  }

  const listed = await api.get(`${API_BASE}/system/organizations`, {
    headers: bearer(adminToken),
    failOnStatusCode: false,
  });
  const organizations = dataOf(await readJson(listed)) as { code: string; organizationId: string }[] | null;
  const match = organizations?.find((item) => item.code === E2E_ORG.code);
  expect(
    match,
    `夹具组织 ${E2E_ORG.code} 既无法创建（HTTP ${created.status()}）也无法查回`,
  ).toBeTruthy();
  return match!.organizationId;
}

/** 幂等创建 Team；已存在时按名称查回。 */
async function ensureTeam(
  api: APIRequestContext,
  adminToken: string,
  organizationId: string,
): Promise<string> {
  const created = await api.post(`${API_BASE}/system/organizations/${organizationId}/teams`, {
    headers: { ...bearer(adminToken), 'Idempotency-Key': `idmp_e2e050_team_${E2E_TEAM_NAME}` },
    data: { name: E2E_TEAM_NAME },
    failOnStatusCode: false,
  });
  const createdData = dataOf(await readJson(created)) as { teamId?: string } | null;
  if (created.status() === 201 && createdData?.teamId) {
    return createdData.teamId;
  }

  const listed = await api.get(`${API_BASE}/system/organizations/${organizationId}/teams`, {
    headers: bearer(adminToken),
    failOnStatusCode: false,
  });
  const teams = dataOf(await readJson(listed)) as { teamId: string; name: string }[] | null;
  const match = teams?.find((item) => item.name === E2E_TEAM_NAME);
  expect(match, `夹具 Team ${E2E_TEAM_NAME} 既无法创建（HTTP ${created.status()}）也无法查回`).toBeTruthy();
  return match!.teamId;
}

/** 把主体加入组织（幂等：已存在时后端返回 409）。 */
async function ensureOrganizationMember(
  api: APIRequestContext,
  adminToken: string,
  organizationId: string,
  principalId: string,
): Promise<void> {
  const response = await api.post(`${API_BASE}/system/organizations/${organizationId}/members`, {
    headers: {
      ...bearer(adminToken),
      'Idempotency-Key': `idmp_e2e050_member_${principalId}`,
    },
    data: { principalId },
    failOnStatusCode: false,
  });
  expect([201, 409], `把 ${principalId} 加入组织返回 HTTP ${response.status()}`).toContain(response.status());
}

/** 幂等创建夹具资产；已存在时按坐标查回。 */
async function ensureAsset(
  api: APIRequestContext,
  authorToken: string,
  organizationId: string,
  teamId: string,
): Promise<string> {
  const created = await api.post(`${API_BASE}/assets`, {
    headers: { ...bearer(authorToken), 'Idempotency-Key': `idmp_e2e050_asset_${E2E_ASSET.name}` },
    data: {
      type: 'MODEL',
      namespace: E2E_ASSET.namespace,
      name: E2E_ASSET.name,
      displayName: E2E_ASSET.displayName,
      visibility: 'INTERNAL',
      organizationId,
      ownerTeamId: teamId,
    },
    failOnStatusCode: false,
  });
  const createdData = dataOf(await readJson(created)) as { assetId?: string } | null;
  if (created.status() === 201 && createdData?.assetId) {
    return createdData.assetId;
  }

  const listed = await api.get(`${API_BASE}/assets`, {
    headers: bearer(authorToken),
    params: { namespace: E2E_ASSET.namespace, keyword: E2E_ASSET.name, size: 50 },
    failOnStatusCode: false,
  });
  const page = dataOf(await readJson(listed)) as { items?: { assetId: string; name: string }[] } | null;
  const match = page?.items?.find((item) => item.name === E2E_ASSET.name);
  expect(match, `夹具资产 ${E2E_ASSET.name} 既无法创建（HTTP ${created.status()}）也无法查回`).toBeTruthy();
  return match!.assetId;
}

/**
 * 准备 E2E 夹具：组织、Team、资产作者主体与可访问资产。
 *
 * <p>全部步骤幂等，可重复运行；所有写入都走真实 REST 契约，不直接改库。
 */
export async function prepareFixtures(adminToken: string, apiRequest?: APIRequestContext): Promise<E2eFixtures> {
  const api = apiRequest ?? (await request.newContext());
  try {
    const organizationId = await ensureOrganization(api, adminToken);
    const teamId = await ensureTeam(api, adminToken, organizationId);
    const authorPrincipalId = await ensureUser(api, adminToken, E2E_AUTHOR);
    const managerPrincipalId = await ensureUser(api, adminToken, E2E_MANAGER);
    await ensureUser(api, adminToken, E2E_AUTHZ_READER);
    await ensureOrganizationMember(api, adminToken, organizationId, authorPrincipalId);
    const authorToken = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
    const assetId = await ensureAsset(api, authorToken, organizationId, teamId);
    return { organizationId, teamId, assetId, authorPrincipalId, managerPrincipalId };
  } finally {
    if (!apiRequest) {
      await api.dispose();
    }
  }
}

/** 以引导管理员换取 API Token。 */
export async function adminToken(api: APIRequestContext): Promise<string> {
  const credentials = bootstrapCredentials();
  expect(credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD').toBeTruthy();
  return loginApi(api, credentials!.username, credentials!.password);
}

/**
 * 以指定主体直接调用后端端点，返回状态码。
 *
 * <p>用于证明「UI 不是安全边界」：后端独立于前端守卫仍拒绝未授权主体。
 * 浏览器内存中的 access Token 不可被 {@code page.request} 复用，故此处重新登录取 Token。
 */
export async function directApiStatus(
  path: string,
  username: string,
  password: string,
): Promise<number> {
  const response = await directApiGet(path, username, password);
  return response.status;
}

/** 以指定主体直接调用后端端点，返回状态码与解包后的 data。 */
export async function directApiGet<T = unknown>(
  path: string,
  username: string,
  password: string,
): Promise<{ status: number; data: T | null }> {
  const api = await request.newContext();
  try {
    const token = await loginApi(api, username, password);
    const response = await api.get(`${API_BASE}${path}`, {
      headers: bearer(token),
      failOnStatusCode: false,
    });
    const data = dataOf(await readJson(response)) as T | null;
    return { status: response.status(), data };
  } finally {
    await api.dispose();
  }
}
