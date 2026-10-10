/*
 * 功能: E2E 共享支撑——凭据来源、登录动作、夹具主体准备与通用定位器。
 *       引导口令一律从环境变量读取，绝不写入仓库；未提供凭据时由各 spec 显式 skip。
 * 时间: 2026-09-28
 * 作者: AxeXie
 */
import { expect, request, test, type APIRequestContext, type Page } from '@playwright/test';

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
 * <p>保留应用内导航形态：它覆盖的是「导航项按权限出现」这一真实用户路径。
 * 历史上此处曾以「nginx {@code location /assets/} 遮蔽 SPA 深链」为由禁止整页加载，
 * 该缺陷已由 TASK-P0BR-073 修复（构建产物改名 {@code /static/}），深链整页加载见
 * spa-deep-link.spec.ts。
 */
export async function openAssetsViaNav(page: Page) {
  await page.locator('.ant-menu-item').filter({ hasText: /资产目录|Asset Catalog/ }).click();
  await expect(page).toHaveURL(/\/assets$/);
}

/**
 * 经侧边导航进入 Agent 接入页（mcp-integration.spec 使用）。
 *
 * <p>{@code location /} 虽有 SPA fallback，但 access JWT 仅存内存，整页加载后的续期时序
 * 不在本 spec 断言范围；与资产目录同理走应用内导航，行为确定，并顺带证明导航项按
 * {@code authorization:read} 出现。
 */
export async function openIntegrationsViaNav(page: Page) {
  await page.locator('.ant-menu-item').filter({ hasText: /Agent 接入|Agent Integration/ }).click();
  await expect(page).toHaveURL(/\/integrations$/);
}

/**
 * 从资产目录经真实点击进入资产详情页。
 *
 * <p>历史背景：nginx {@code location /assets/} 静态规则曾遮蔽 SPA 深链整页加载
 * （EVD-P0BR-050-001 limitations），本函数以应用内导航绕行。该缺陷已由
 * TASK-P0BR-073 修复（构建产物改名 {@code /static/}），深链整页加载的真实证据
 * 见 spa-deep-link.spec.ts；本函数保留目录点击路径——它覆盖「检索打到后端 +
 * 列表链接进详情」这条独立的用户旅程。
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

/**
 * 在资产目录页断言目标资产链接可见。
 *
 * <p>与 {@link openAssetDetailViaCatalog} 同因：目录默认 limit=10 倒序分页，
 * 验收栈累积的历史夹具会把目标挤出首屏。先按显示名走页内真实搜索，再断言结果——
 * 覆盖的是「检索确实打到后端」这一真实用户路径，而不是放宽断言。
 */
export async function expectAssetLinkViaSearch(page: Page, displayName: string) {
  const search = page.getByPlaceholder(/搜索名称、描述或标签|Search name, description or tags/i);
  await expect(search).toBeVisible();
  await search.fill(displayName);
  await page.keyboard.press('Enter');
  await expect(page.getByRole('link', { name: displayName })).toBeVisible({ timeout: 15_000 });
}

/** 以引导管理员凭据登录 UI；凭据缺失时 skip 当前用例（各 admin/profile spec 共用）。 */
export async function loginBootstrap(page: Page) {
  const credentials = bootstrapCredentials();
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
  await login(page, credentials!.username, credentials!.password);
}

/**
 * 在资产详情页激活「版本」Tab。
 *
 * <p>版本列表面板（含「查看全部」入口）渲染在 versions Tab 内；antd Tabs 默认
 * 不渲染未激活面板，因此点击「查看全部」前必须先切过去——这是用户本来的操作路径。
 */
export async function activateVersionsTab(page: Page) {
  const tab = page.getByRole('tab', { name: /^版本$|^Versions$/ });
  await tab.click();
  await expect(tab).toHaveAttribute('aria-selected', 'true');
}

/**
 * 从资产详情页进入「资产设置」独立路由页。
 *
 * <p>设置页是 {@code /assets/{assetId}/settings} 的独立路由（AssetSettingsPage 由
 * {@code useParams} 取 assetId，页内无返回导航）；073 修复后深链整页加载亦可达，
 * 本入口保留详情页「设置」Tab 链接按钮——即用户真实操作路径。
 */
export async function openAssetSettingsViaNav(page: Page) {
  const tab = page.getByRole('tab', { name: /^设\s*置$|^Settings$/ });
  await tab.click();
  await expect(tab).toHaveAttribute('aria-selected', 'true');
  await page.getByRole('button', { name: /设\s*置|Settings/ }).click();
  await expect(page).toHaveURL(/\/assets\/[^/]+\/settings$/);
  await expect(page.locator('.ant-card').filter({ hasText: /生命周期|Lifecycle/ }).first()).toBeVisible();
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
 * 资产所有者主体：具备资产全生命周期所需的细粒度 Scope——P1 权限细化后
 * 创建（{@code asset:create}）与建版本（{@code asset:update}）是独立强制点，
 * 不再由 {@code asset:manage} 覆盖；同时保留 {@code asset:manage} 供审批/预览入口。
 * 刻意不含 {@code authorization:read}，用于证明导航按权限过滤。
 *
 * <p>注意：后端无 scope 更新 REST 端点，已存在的旧 scopes 夹具用户不会被幂等逻辑热更；
 * 修改本列表后须清理验收栈中的同名用户（见 EVD limitations 记录）。
 */
export const E2E_AUTHOR = {
  username: 'e2e050-asset-owner',
  password: 'E2e050-Author-01!z',
  scopes: ['project:view', 'asset:read', 'asset:create', 'asset:update', 'asset:manage'],
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

/**
 * 目录隔离最小主体：仅有 {@code asset:read}，刻意不是 e2e050-org 组织成员，
 * 且不含 {@code authorization:manage}。
 *
 * <p>用于证明行级 INTERNAL 过滤对普通主体生效：AssetSearchDao.appendAccessScopeFilter
 * 首行对 platformAdmin（AuthorizationService 以 authorization:manage 判定）直接放行全部行，
 * 故隔离用例不可复用 E2E_MANAGER，必须用此无管理 Scope 的最小主体。
 */
export const E2E_ASSET_OUTSIDER = {
  username: 'e2e050-outsider',
  password: 'E2e050-AssetOutsider-01!z',
  scopes: ['asset:read'],
} as const;

/**
 * 讨论主体：具备 {@code asset:read} + {@code asset:discuss} + {@code asset:moderate}。
 *
 * <p>后端契约：createThread/createComment 强制 asset:discuss，lockThread/unlockThread
 * 强制 asset:moderate，listThreads 仅需 asset:read；现有 E2E_AUTHOR / E2E_MANAGER 均不含
 * discuss/moderate，故讨论 UI 旅程必须使用此独立主体。需为组织成员以获得 INTERNAL 资产访问。
 */
export const E2E_DISCUSSANT = {
  username: 'e2e050-discussant',
  password: 'E2e050-Discussant-01!z',
  scopes: ['asset:read', 'asset:discuss', 'asset:moderate'],
} as const;

/**
 * 生命周期主体：具备 {@code asset:deprecate}。
 *
 * <p>后端契约：AssetApplicationService.deprecateAsset 强制 {@code asset:deprecate}
 * （archive/restore 为 {@code asset:manage}，delete 为 {@code asset:delete}），
 * 而 E2E_AUTHOR 不含该 Scope。后端无 scope 更新 REST 端点（SystemUserController 的
 * PATCH 仅接受 displayName/email/locale），已落库的夹具用户无法热更，
 * 故新增此独立主体而不是修改 E2E_AUTHOR——后者会让全部既有绿基线用例失去主体。
 */
export const E2E_LIFECYCLE_OWNER = {
  username: 'e2e0928-lifecycle-owner',
  password: 'E2e0928-Lifecycle-01!z',
  scopes: ['project:view', 'asset:read', 'asset:create', 'asset:update', 'asset:manage', 'asset:deprecate'],
} as const;

/**
 * 组织管理主体：{@code project:view} + {@code organization:manage}。
 *
 * <p>后端契约：listOrganizations 仅要求 project:view 并按成员关系过滤，
 * listOrganizationMembers/addOrganizationMember 要求 organization:manage。
 * D-10 把行内「成员管理」入口按后一口径门控后，需要一个合法持权主体证明
 * 收紧没有把管理员一并挡在门外；既有夹具无人持该 Scope，且已落库用户
 * 无法热更 Scope（PATCH 仅接受 displayName/email/locale），故新增独立主体。
 */
export const E2E_ORG_MANAGER = {
  username: 'e2e0928-org-manager',
  password: 'E2e0928-OrgManager-01!z',
  scopes: ['project:view', 'organization:manage'],
} as const;

/**
 * 凭据管理主体：仅持 {@code token:create}，刻意不含 authorization:read。
 *
 * <p>后端契约：listPats 要求 token:create（PatApplicationService 服务层
 * requirePermission 已 fail-closed）。D-09 把 /access 守卫口径从
 * authorization:read 收敛到 token:create 后，此主体用于证明合法主体不被误挡。
 */
export const E2E_TOKEN_MANAGER = {
  username: 'e2e0928-token-manager',
  password: 'E2e0928-TokenManager-01!z',
  scopes: ['token:create'],
} as const;

/**
 * 通知只读主体：仅持 {@code notification:read}，刻意不含 system:observe。
 *
 * <p>后端契约：Outbox/Webhook 投递端点要求 system:observe，用户通知仅要求
 * notification:read。D-08 把「系统管理」Tab 按 system:observe 门控后，
 * 此主体用于证明仅持读权限的主体不再触发该取数。
 */
export const E2E_NOTIFICATION_READER = {
  username: 'e2e0928-notification-reader',
  password: 'E2e0928-NotifyReader-01!z',
  scopes: ['notification:read'],
} as const;

/**
 * 运维只读主体：具备平台运维类 Scope。
 *
 * <p>引导管理员 JWT 只携带粗粒度 Scope，前端路由守卫会拒绝其访问这些页面；
 * 且后端对应 Controller 逐端点强制细粒度 Scope——ConfigurationController
 * 强制 system:configure、JobController/IdempotencyController 强制 job:read、
 * SystemAlertController/SystemDiagnosticsController/NotificationAdminController
 * 强制 system:observe、AuditLogController 强制 audit:read、NotificationController
 * 强制 notification:read（以上编码均已在 Permissions.java 注册，非幽灵权限码）。
 * 故必须显式列出，用于逐项点击验收栈中从未被 E2E 覆盖过的管理端页面。
 *
 * <p>刻意不含 user:read / authorization:read / dictionary:read：
 * 用于反向证明侧边导航与路由守卫是逐项判定，而非有任一管理 Scope 即整体放开。
 */
export const E2E_OPS_OBSERVER = {
  username: 'e2e0928-ops-observer',
  password: 'E2e0928-OpsObserver-01!z',
  scopes: [
    'job:read',
    'system:observe',
    'system:configure',
    'audit:read',
    'notification:read',
  ],
} as const;

export const E2E_ORG = { code: 'e2e050-org', name: 'E2E 050 Org' } as const;
export const E2E_TEAM_NAME = 'E2E050 Team' as const;
export const E2E_ASSET = {
  namespace: 'nlp',
  name: 'e2e050-version-target',
  displayName: 'E2E 050 版本入口目标',
} as const;

/**
 * DATASET 夹具坐标：数据集体验 E2E（dataset.spec）使用，画像字段断言依赖这些值。
 *
 * <p>format/modality 必须是 dataset_format / dataset_modality 字典的 ACTIVE itemCode
 * （V6__taxonomy_dictionary.sql 种子为大写：PARQUET、TEXT）。后端写入路径
 * AssetApplicationService.validateGovernanceFields → DictionaryApplicationService.validateItemCode
 * 按 itemCode 精确匹配（大小写敏感），小写值会被 400 DICTIONARY_VALUE_INVALID 拒绝。
 */
export const E2E_DATASET = {
  namespace: 'nlp',
  name: 'e2e050-dataset-target',
  displayName: 'E2E 050 数据集入口目标',
  format: 'PARQUET',
  modality: 'TEXT',
} as const;

/**
 * 发布链 E2E（publish-flow.spec）专属 MODEL 夹具。
 *
 * <p>既有 spec 对 E2E_ASSET / E2E_DATASET 的版本列表与默认选中项存在隐含独占假设
 * （dataset.spec 断言选择器回显 /1\.0\.[01]/、version-management.spec 精确匹配唯一 DRAFT Tag），
 * 故新状态（额外 DRAFT 版本）的测试必须落在独立资产上，避免跨 spec 夹具污染。
 */
export const E2E_PUBLISH_ASSET = {
  namespace: 'nlp',
  name: 'e2e050-publish-target',
  displayName: 'E2E 050 发布链目标',
} as const;

/**
 * 发布链 E2E 专属 DATASET 夹具（DVC 入口/凭据断言使用）。
 */
export const E2E_DVC_DATASET = {
  namespace: 'nlp',
  name: 'e2e050-dvc-target',
  displayName: 'E2E 050 DVC 目标',
  format: 'PARQUET',
  modality: 'TEXT',
} as const;

/**
 * 生命周期 E2E（asset.spec 弃用/归档/恢复旅程）专属 MODEL 夹具。
 *
 * <p>刻意不复用 E2E_ASSET：publish-flow.spec 会在其上留下 PUBLISHED 版本，而
 * AssetApplicationService.archiveAsset 对 publishedCount>0 的资产返回 409
 * ASSET_HAS_ACTIVE_VERSIONS——归档步骤必然失败。本资产禁止发布版本。
 */
export const E2E_LIFECYCLE_ASSET = {
  namespace: 'nlp',
  name: 'e2e0928-lifecycle-target',
  displayName: 'E2E 0928 生命周期目标',
} as const;

export interface E2eFixtures {
  organizationId: string;
  teamId: string;
  assetId: string;
  /** DATASET 类型夹具资产（带画像字段），供 dataset.spec 使用。 */
  datasetAssetId: string;
  authorPrincipalId: string;
  managerPrincipalId: string;
  /** 讨论主体 principalId：ThreadView.createdBy 返回的是 principalId 而非 username（discussion.spec 断言用）。 */
  discussantPrincipalId: string;
  /** 生命周期旅程专属资产（asset.spec 弃用/归档/恢复使用），不受发布链版本影响。 */
  lifecycleAssetId: string;
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

/**
 * 等待资产建仓完成（provisioningStatus == COMPLETED）。
 *
 * <p>后端契约：AssetSearchDao BASE_SELECT 以 `AND a.provisioning_status = 'COMPLETED'`
 * 过滤目录检索——新建资产在异步建仓完成前不出现在目录（用例的搜索/类型过滤断言必然失败）。
 * 建仓由持久 Job 驱动（JobConfiguration 默认 worker 间隔 5s），GET /assets/{assetId}
 * 详情端点不受该门槛限制，故用详情轮询。FAILED 视为夹具契约异常立即失败；
 * 上限 60s 防挂死。
 */
async function waitForAssetReady(
  api: APIRequestContext,
  token: string,
  assetId: string,
): Promise<void> {
  const deadline = Date.now() + 60_000;
  let last: string | undefined;
  for (;;) {
    const resp = await api.get(`${API_BASE}/assets/${assetId}`, {
      headers: bearer(token),
      failOnStatusCode: false,
    });
    const data = dataOf(await readJson(resp)) as { provisioningStatus?: string } | null;
    last = data?.provisioningStatus;
    if (last === 'COMPLETED') {
      return;
    }
    expect(last, `资产 ${assetId} 建仓进入失败态`).not.toBe('FAILED');
    expect(Date.now(), `资产 ${assetId} 建仓等待超时（最后状态 ${last ?? 'unknown'}）`).toBeLessThan(deadline);
    await new Promise((resolve) => setTimeout(resolve, 2_000));
  }
}

/** 幂等创建夹具资产；已存在时按坐标查回。返回前确保建仓 COMPLETED。 */
async function ensureAsset(
  api: APIRequestContext,
  authorToken: string,
  organizationId: string,
  teamId: string,
  coordinates: { namespace: string; name: string; displayName: string } = E2E_ASSET,
): Promise<string> {
  const created = await api.post(`${API_BASE}/assets`, {
    headers: { ...bearer(authorToken), 'Idempotency-Key': `idmp_e2e050_asset_${coordinates.name}` },
    data: {
      type: 'MODEL',
      namespace: coordinates.namespace,
      name: coordinates.name,
      displayName: coordinates.displayName,
      visibility: 'INTERNAL',
      organizationId,
      ownerTeamId: teamId,
    },
    failOnStatusCode: false,
  });
  const createdData = dataOf(await readJson(created)) as { assetId?: string } | null;
  if (created.status() === 201 && createdData?.assetId) {
    await waitForAssetReady(api, authorToken, createdData.assetId);
    return createdData.assetId;
  }

  const listed = await api.get(`${API_BASE}/assets`, {
    headers: bearer(authorToken),
    // includeArchived：夹具资产可能被上一次中断的运行留在 ARCHIVED 态，默认检索不返回该状态，
    // 会使「按坐标查回」失败；查回属于夹具幂等逻辑，不放宽业务断言。
    params: { namespace: coordinates.namespace, keyword: coordinates.name, size: 50, includeArchived: true },
    failOnStatusCode: false,
  });
  const page = dataOf(await readJson(listed)) as { items?: { assetId: string; name: string }[] } | null;
  const match = page?.items?.find((item) => item.name === coordinates.name);
  expect(match, `夹具资产 ${coordinates.name} 既无法创建（HTTP ${created.status()}）也无法查回`).toBeTruthy();
  await waitForAssetReady(api, authorToken, match!.assetId);
  return match!.assetId;
}

/**
 * 幂等创建 DATASET 夹具资产（带 dataset 画像）。
 *
 * <p>后端契约：CreateAssetRequest.DatasetInput(format, modality, …)；Asset 仅在
 * type==DATASET 时采纳 profile（Asset.java L205），故画像断言必须落在 DATASET 资产上。
 * 返回前确保建仓 COMPLETED。
 */
async function ensureDatasetAsset(
  api: APIRequestContext,
  authorToken: string,
  organizationId: string,
  teamId: string,
  coordinates: {
    namespace: string;
    name: string;
    displayName: string;
    format: string;
    modality: string;
  } = E2E_DATASET,
): Promise<string> {
  const created = await api.post(`${API_BASE}/assets`, {
    headers: { ...bearer(authorToken), 'Idempotency-Key': `idmp_e2e050_asset_${coordinates.name}` },
    data: {
      type: 'DATASET',
      namespace: coordinates.namespace,
      name: coordinates.name,
      displayName: coordinates.displayName,
      visibility: 'INTERNAL',
      organizationId,
      ownerTeamId: teamId,
      dataset: { format: coordinates.format, modality: coordinates.modality },
    },
    failOnStatusCode: false,
  });
  const createdData = dataOf(await readJson(created)) as { assetId?: string } | null;
  if (created.status() === 201 && createdData?.assetId) {
    await waitForAssetReady(api, authorToken, createdData.assetId);
    return createdData.assetId;
  }

  const listed = await api.get(`${API_BASE}/assets`, {
    headers: bearer(authorToken),
    params: { namespace: coordinates.namespace, keyword: coordinates.name, type: 'DATASET', size: 50 },
    failOnStatusCode: false,
  });
  const page = dataOf(await readJson(listed)) as { items?: { assetId: string; name: string }[] } | null;
  const match = page?.items?.find((item) => item.name === coordinates.name);
  expect(match, `DATASET 夹具资产 ${coordinates.name} 既无法创建（HTTP ${created.status()}）也无法查回`).toBeTruthy();
  await waitForAssetReady(api, authorToken, match!.assetId);
  return match!.assetId;
}

/**
 * 幂等确保血缘关系边存在（lineage.spec 使用）。
 *
 * <p>契约：POST /assets/{parentAssetId}/relations，body {@code {childAssetId, relationType}}，
 * 需 asset:manage（E2E_AUTHOR 具备）。(parent, child, type) 唯一，重复创建返回 409，
 * 幂等夹具予以容忍。
 */
export async function ensureLineageRelation(
  api: APIRequestContext,
  token: string,
  parentAssetId: string,
  childAssetId: string,
  relationType: string,
): Promise<void> {
  const response = await api.post(`${API_BASE}/assets/${parentAssetId}/relations`, {
    headers: bearer(token),
    data: { childAssetId, relationType },
    failOnStatusCode: false,
  });
  expect([201, 409], `创建血缘边 ${parentAssetId}->${childAssetId}(${relationType}) 返回 HTTP ${response.status()}`).toContain(
    response.status(),
  );
}

/**
 * 为发布链 spec（publish-flow）幂等创建其专属夹具资产。
 *
 * <p>刻意不复用 E2E_ASSET / E2E_DATASET：dataset.spec 与 version-management.spec 对它们的
 * 版本列表存在隐含独占假设（默认选中项回显 /1\.0\.[01]/、唯一 DRAFT Tag），
 * 在其上追加 DRAFT 版本会造成跨 spec 污染。新状态测试一律落在本函数返回的独立资产上。
 */
export async function ensurePublishFlowAssets(
  api: APIRequestContext,
  base: Pick<E2eFixtures, 'organizationId' | 'teamId'>,
  authorToken: string,
): Promise<{ publishAssetId: string; dvcAssetId: string }> {
  const publishAssetId = await ensureAsset(
    api, authorToken, base.organizationId, base.teamId, E2E_PUBLISH_ASSET,
  );
  const dvcAssetId = await ensureDatasetAsset(
    api, authorToken, base.organizationId, base.teamId, E2E_DVC_DATASET,
  );
  return { publishAssetId, dvcAssetId };
}

/**
 * 幂等把资产状态归一为 ACTIVE。
 *
 * <p>生命周期旅程以「弃用 → 归档 → 恢复」为顺序，中途归档会使资产不再出现在目录默认
 * 结果集（AssetApplicationService.searchAssets 默认 statuses={ACTIVE, DEPRECATED}），
 * 用例一旦失败中断，下一次运行便无法经 UI 回到该资产。故夹具准备阶段直接调用
 * POST /assets/{id}/restore：ACTIVE 态返回 409（状态机拒绝），容忍即可。
 */
async function ensureAssetActive(
  api: APIRequestContext,
  token: string,
  assetId: string,
): Promise<void> {
  const response = await api.post(`${API_BASE}/assets/${assetId}/restore`, {
    headers: bearer(token),
    data: {},
    failOnStatusCode: false,
  });
  expect([200, 409], `把资产 ${assetId} 归一为 ACTIVE 返回 HTTP ${response.status()}`).toContain(
    response.status(),
  );
}

/** 以指定凭据登录并返回 access Token（供 spec 在 beforeAll 做夹具写入）。 */
export async function loginAs(
  api: APIRequestContext,
  username: string,
  password: string,
): Promise<string> {
  return loginApi(api, username, password);
}

/**
 * 幂等确保资产上存在指定版本，并返回其 versionId。
 *
 * <p>创建走 POST /assets/{assetId}/versions（需 asset:update）；已存在时按版本号查回。
 * 首次创建必须 201，否则视为夹具契约异常。
 */
export async function ensureAssetVersion(
  api: APIRequestContext,
  token: string,
  assetId: string,
  version: string,
): Promise<string> {
  const findExisting = async (): Promise<string | null> => {
    const listed = await api.get(`${API_BASE}/assets/${assetId}/versions?limit=50`, {
      headers: bearer(token),
      failOnStatusCode: false,
    });
    const page = dataOf(await readJson(listed)) as
      | { items?: { versionId: string; version: string }[] }
      | null;
    return page?.items?.find((v) => v.version === version)?.versionId ?? null;
  };

  const existing = await findExisting();
  if (existing) {
    return existing;
  }
  const created = await api.post(
    `${API_BASE}/assets/${assetId}/versions`,
    { headers: bearer(token), data: { version }, failOnStatusCode: false },
  );
  expect(created.status(), `创建版本 ${version} 返回 HTTP ${created.status()}`).toBe(201);
  const id = await findExisting();
  expect(id, `版本 ${version} 创建后无法查回 versionId`).toBeTruthy();
  return id!;
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
    // 刻意不将 outsider 加入组织：目录隔离用例依赖其"非成员 + 非平台管理员"身份。
    await ensureUser(api, adminToken, E2E_ASSET_OUTSIDER);
    const discussantPrincipalId = await ensureUser(api, adminToken, E2E_DISCUSSANT);
    const lifecyclePrincipalId = await ensureUser(api, adminToken, E2E_LIFECYCLE_OWNER);
    // 运维只读主体：只用于点击运维类管理页面，不需要组织成员身份（相关端点仅按 Scope 判定）。
    await ensureUser(api, adminToken, E2E_OPS_OBSERVER);
    // 组织管理主体与凭据主体：仅按 Scope 判定页面可见性；但 listOrganizations 对
    // 非 platformAdmin 主体按成员关系过滤，故 org-manager 必须入组织才能看到夹具组织行。
    const orgManagerPrincipalId = await ensureUser(api, adminToken, E2E_ORG_MANAGER);
    await ensureUser(api, adminToken, E2E_TOKEN_MANAGER);
    // 通知只读主体：仅验证「我的通知」成功态与系统管理 Tab 不渲染，不需要组织成员身份。
    await ensureUser(api, adminToken, E2E_NOTIFICATION_READER);
    await ensureOrganizationMember(api, adminToken, organizationId, authorPrincipalId);
    await ensureOrganizationMember(api, adminToken, organizationId, orgManagerPrincipalId);
    // 讨论主体必须是组织成员，否则 INTERNAL 资产行级过滤会拒绝 listThreads。
    await ensureOrganizationMember(api, adminToken, organizationId, discussantPrincipalId);
    // 生命周期主体同理需要组织成员身份，才能通过目录行级过滤看到 INTERNAL 夹具资产。
    await ensureOrganizationMember(api, adminToken, organizationId, lifecyclePrincipalId);
    const authorToken = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
    const assetId = await ensureAsset(api, authorToken, organizationId, teamId);
    const datasetAssetId = await ensureDatasetAsset(api, authorToken, organizationId, teamId);
    const lifecycleToken = await loginApi(
      api, E2E_LIFECYCLE_OWNER.username, E2E_LIFECYCLE_OWNER.password,
    );
    const lifecycleAssetId = await ensureAsset(
      api, lifecycleToken, organizationId, teamId, E2E_LIFECYCLE_ASSET,
    );
    await ensureAssetActive(api, lifecycleToken, lifecycleAssetId);
    return {
      organizationId, teamId, assetId, datasetAssetId,
      authorPrincipalId, managerPrincipalId, discussantPrincipalId,
      lifecycleAssetId,
    };
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
