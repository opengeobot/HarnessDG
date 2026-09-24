/*
 * 功能: TASK-P0BR-065 / AC-P0B-UI-003+UI-006 E2E 规格——发布审批入口的导航可见性、
 *       路由守卫、审查页诚实空态与后端独立授权。夹具主体与 050 系列分离
 *       （决策端点因闭环断链不可达，本卡未用到 asset:publish）。
 *       断言为无条件式：入口缺失、守卫失效、渲染虚构行或后端放行时测试必须失败。
 *       已知限制：版本推进到 PENDING_REVIEW 的作业链在运行态断链
 *       （UPLOAD_MATERIALIZE/VERSION_VALIDATE 无入队方），正向审批闭环不可达；
 *       本 spec 断言可达面，闭环缺口如实记入 Evidence limitations（EVD-P0BR-065-001）。
 * 时间: 2026-09-22
 * 作者: AxeXie
 */
import { test, expect, request, type APIRequestContext, type Page } from '@playwright/test';
import { adminToken, bootstrapCredentials, login } from './support';

const API_BASE = process.env.E2E_API_BASE_URL ?? 'http://localhost:8080/api/v1';

/**
 * 本卡夹具作者：具备审批入口所需 asset:manage 与资产/版本创建所需的
 * asset:create、asset:update（P1 权限细化后的独立强制点；
 * 决策端点因闭环断链不可达，未用到 asset:publish）。
 */
const REVIEW_AUTHOR = {
  username: 'e2e065-review-author',
  password: 'E2e065-ReviewAuthor-01!z',
  scopes: ['project:view', 'asset:read', 'asset:create', 'asset:update', 'asset:manage'],
} as const;

const REVIEW_READER = {
  username: 'e2e065-review-reader',
  password: 'E2e065-ReviewReader-01!z',
  scopes: ['asset:read'],
} as const;

const REVIEW_ORG = { code: 'e2e065-org', name: 'E2E 065 Org' } as const;
const REVIEW_ASSET = {
  namespace: 'nlp',
  name: 'e2e065-review-target',
  displayName: 'E2E 065 审批目标资产',
} as const;

interface Fixtures {
  assetId: string;
}

let credentials: { username: string; password: string } | null = null;
let fixtures: Fixtures;

function bearer(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

function dataOf(body: unknown): unknown {
  if (body && typeof body === 'object' && 'data' in body) {
    return (body as { data: unknown }).data;
  }
  return null;
}

async function readJson(response: { json: () => Promise<unknown> }): Promise<unknown> {
  return response.json().catch(() => null);
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

/** 幂等创建用户并解除首登改密门；已存在时跳过（scope 差异不热更，干净栈首次创建为准）。 */
async function ensureUser(
  api: APIRequestContext,
  admin: string,
  user: { username: string; password: string; scopes: readonly string[] },
): Promise<void> {
  const created = await api.post(`${API_BASE}/system/users`, {
    headers: { ...bearer(admin), 'Idempotency-Key': `idmp_e2e065_user_${user.username}` },
    data: {
      username: user.username,
      displayName: `E2E 065 ${user.username}`,
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
    const token = await loginApi(api, user.username, user.password);
    const me = await api.get(`${API_BASE}/me`, { headers: bearer(token), failOnStatusCode: false });
    const meData = dataOf(await readJson(me)) as { forcePasswordChange?: boolean } | null;
    if (meData?.forcePasswordChange === true) {
      const changed = await api.put(`${API_BASE}/me/password`, {
        headers: bearer(token),
        data: { currentPassword: user.password, newPassword: user.password },
        failOnStatusCode: false,
      });
      expect(changed.status(), `夹具主体 ${user.username} 解除强制改密门失败`).toBe(200);
    }
  }
}

/**
 * 准备本卡夹具：组织、Team、双主体与带 DRAFT 版本的资产。
 *
 * <p>与 support.prepareFixtures 同一惯例：全部幂等、所有写入走真实 REST 契约，不直接改库。
 */
async function prepareReviewFixtures(admin: string, api: APIRequestContext): Promise<Fixtures> {
  const orgs = await api.get(`${API_BASE}/system/organizations`, {
    headers: bearer(admin),
    failOnStatusCode: false,
  });
  const orgList = (dataOf(await readJson(orgs)) ?? []) as { code: string; organizationId: string }[];
  let organizationId = orgList.find((o) => o.code === REVIEW_ORG.code)?.organizationId;
  if (!organizationId) {
    const created = await api.post(`${API_BASE}/system/organizations`, {
      headers: { ...bearer(admin), 'Idempotency-Key': `idmp_e2e065_org_${REVIEW_ORG.code}` },
      data: { code: REVIEW_ORG.code, name: REVIEW_ORG.name },
      failOnStatusCode: false,
    });
    const data = dataOf(await readJson(created)) as { organizationId?: string } | null;
    organizationId =
      data?.organizationId ??
      ((await api
        .get(`${API_BASE}/system/organizations`, { headers: bearer(admin), failOnStatusCode: false })
        .then(async (r) =>
          ((dataOf(await readJson(r)) ?? []) as { code: string; organizationId: string }[]).find(
            (o) => o.code === REVIEW_ORG.code,
          )?.organizationId,
        )) as string | undefined);
    expect(organizationId, `夹具组织 ${REVIEW_ORG.code} 无法创建或查回`).toBeTruthy();
  }

  let teamId: string | undefined;
  const teams = await api.get(`${API_BASE}/system/organizations/${organizationId}/teams`, {
    headers: bearer(admin),
    failOnStatusCode: false,
  });
  const teamList = (dataOf(await readJson(teams)) ?? []) as { name: string; teamId: string }[];
  teamId = teamList.find((t) => t.name === 'E2E065 Team')?.teamId;
  if (!teamId) {
    const created = await api.post(`${API_BASE}/system/organizations/${organizationId}/teams`, {
      headers: { ...bearer(admin), 'Idempotency-Key': 'idmp_e2e065_team' },
      data: { name: 'E2E065 Team' },
      failOnStatusCode: false,
    });
    const data = dataOf(await readJson(created)) as { teamId?: string } | null;
    teamId = data?.teamId;
    expect(teamId, `夹具 Team 创建失败（HTTP ${created.status()}）`).toBeTruthy();
  }

  await ensureUser(api, admin, REVIEW_AUTHOR);
  await ensureUser(api, admin, REVIEW_READER);

  const users = await api.get(`${API_BASE}/system/users`, {
    headers: bearer(admin),
    params: { keyword: REVIEW_AUTHOR.username, size: 50 },
    failOnStatusCode: false,
  });
  const page = dataOf(await readJson(users)) as { items?: { username: string; principalId: string }[] } | null;
  const principalId = page?.items?.find((u) => u.username === REVIEW_AUTHOR.username)?.principalId;
  expect(principalId, `无法查回 ${REVIEW_AUTHOR.username} 的 principalId`).toBeTruthy();
  const member = await api.post(`${API_BASE}/system/organizations/${organizationId}/members`, {
    headers: { ...bearer(admin), 'Idempotency-Key': `idmp_e2e065_member_${principalId}` },
    data: { principalId },
    failOnStatusCode: false,
  });
  expect([201, 409], `加入组织返回 HTTP ${member.status()}`).toContain(member.status());

  const authorToken = await loginApi(api, REVIEW_AUTHOR.username, REVIEW_AUTHOR.password);
  let assetId: string | undefined;
  const listed = await api.get(`${API_BASE}/assets`, {
    headers: bearer(authorToken),
    params: { namespace: REVIEW_ASSET.namespace, keyword: REVIEW_ASSET.name, size: 50 },
    failOnStatusCode: false,
  });
  const assetPage = dataOf(await readJson(listed)) as { items?: { name: string; assetId: string }[] } | null;
  assetId = assetPage?.items?.find((a) => a.name === REVIEW_ASSET.name)?.assetId;
  if (!assetId) {
    const created = await api.post(`${API_BASE}/assets`, {
      headers: { ...bearer(authorToken), 'Idempotency-Key': `idmp_e2e065_asset_${REVIEW_ASSET.name}` },
      data: {
        type: 'MODEL',
        namespace: REVIEW_ASSET.namespace,
        name: REVIEW_ASSET.name,
        displayName: REVIEW_ASSET.displayName,
        visibility: 'INTERNAL',
        organizationId,
        ownerTeamId: teamId,
      },
      failOnStatusCode: false,
    });
    expect(created.status(), `创建夹具资产失败`).toBe(201);
    const data = dataOf(await readJson(created)) as { assetId?: string } | null;
    assetId = data?.assetId;
  }
  expect(assetId, `夹具资产 ${REVIEW_ASSET.name} 无法创建或查回`).toBeTruthy();

  // 幂等确保存在一个 DRAFT 版本：真实 REST 能推进到的最高状态即 DRAFT（闭环断链见头注）。
  const versions = await api.get(`${API_BASE}/assets/${assetId}/versions?limit=50`, {
    headers: bearer(authorToken),
    failOnStatusCode: false,
  });
  const vpage = dataOf(await readJson(versions)) as { items?: { version: string }[] } | null;
  const hasDraft = (vpage?.items ?? []).some((v) => v.version === '1.0.0');
  if (!hasDraft) {
    const draft = await api.post(
      `${API_BASE}/assets/${assetId}/versions`,
      { headers: bearer(authorToken), data: { version: '1.0.0' }, failOnStatusCode: false },
    );
    expect([200, 201, 409], `创建 DRAFT 版本返回 HTTP ${draft.status()}`).toContain(draft.status());
  }
  return { assetId: assetId! };
}

/** 侧边导航叶子入口。 */
function navEntry(page: Page, label: RegExp) {
  return page.locator('.ant-menu-item').filter({ hasText: label });
}

/** 语言切换（复用 i18n.spec.ts 的既有入口）。 */
async function switchLanguage(page: Page, label: '中文' | 'English') {
  await page.getByTestId('language-switcher').click();
  await page.getByRole('menuitem', { name: label }).click();
}

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) {
    return;
  }
  const api = await request.newContext();
  try {
    fixtures = await prepareReviewFixtures(await adminToken(api), api);
  } finally {
    await api.dispose();
  }
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
});

test.describe('AC-P0B-UI-006: 发布审批入口导航可见性随 scope 变化', () => {
  test('具备 asset:manage 的主体看到发布审批与上传中心入口', async ({ page }) => {
    await login(page, REVIEW_AUTHOR.username, REVIEW_AUTHOR.password);

    await expect(navEntry(page, /发布审批|Release Review/)).toBeVisible();
    await expect(navEntry(page, /上传中心|Upload Center/)).toBeVisible();
  });

  test('仅具备 asset:read 的主体看不到发布审批与上传中心，但可见资产目录', async ({ page }) => {
    await login(page, REVIEW_READER.username, REVIEW_READER.password);

    // 逐项过滤而非整体开关：asset:read 入口在、asset:manage 入口不在。
    await expect(navEntry(page, /资产目录|Asset Catalog/)).toBeVisible();
    await expect(navEntry(page, /发布审批|Release Review/)).toHaveCount(0);
    await expect(navEntry(page, /上传中心|Upload Center/)).toHaveCount(0);
  });

  test('无 asset:manage 的主体直接访问 /review 被 RouteGuard 拒绝', async ({ page }) => {
    // REVIEW_READER 仅有 asset:read：导航中不显示入口，直连 URL 也必须被守卫拦截。
    // 后端独立拒绝（UI 非安全边界）由下方授权用例以 403 实证。
    await login(page, REVIEW_READER.username, REVIEW_READER.password);
    await page.goto('/review');

    await expect(page.getByText(/缺少访问权限|Access denied/)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByRole('heading', { name: /发布审批|Release Review/ })).toHaveCount(0);
  });
});

test.describe('AC-P0B-UI-003: 审查页真实查询与诚实状态', () => {
  test('夹具作者打开 /review：可达、深链诚实提示；真实资产深链后不捏造发布请求行', async ({ page }) => {
    // e9d65b7 重构后 /review 为 ?assetId= 深链驱动（无页内输入框/查询按钮），
    // 断言按当前真实形态：无深链→提示；有深链→GET publish-requests 真实空列表。
    await login(page, REVIEW_AUTHOR.username, REVIEW_AUTHOR.password);

    await navEntry(page, /发布审批|Release Review/).click();
    await expect(page).toHaveURL(/\/review(\?|$)/);
    await expect(page.getByText(/页面不存在|Page not found/)).toHaveCount(0);

    // 无深链时的诚实提示（version.deepLinkHint）与通往资产目录的引导。
    await expect(page.getByText(/请从资产详情页进入|Open from asset detail/)).toBeVisible();

    // 真实夹具资产深链：夹具只创建了 DRAFT 版本（运行态断链，见头注），
    // 后端 GET publish-requests 必须返回空，页面显示「暂无发布请求」而非捏造可审批行。
    await page.goto(`/review?assetId=${fixtures.assetId}`);
    await expect(page.getByText(/暂无发布请求|No publish requests/)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/提交发布请求已提交|发布请求已提交|Publish request submitted/)).toHaveCount(0);
    await expect(page.getByRole('button', { name: /重新提交|Resubmit/ })).toHaveCount(0);
  });

  test('页面文案随语言切换（无硬编码残留）', async ({ page }) => {
    await login(page, REVIEW_AUTHOR.username, REVIEW_AUTHOR.password);
    await page.goto('/review');
    // antd Card title 渲染为 div 而非 heading role：用文本级定位并限定 main 区域
    // （侧边导航菜单同样含「发布审批」文案，避免 strict 冲突）。
    const main = page.getByRole('main');
    await expect(main.getByText('发布审批', { exact: true })).toBeVisible();
    await expect(page.getByText(/请从资产详情页进入/)).toBeVisible();

    await switchLanguage(page, 'English');
    await expect(main.getByText('Release Review', { exact: true })).toBeVisible();
    await expect(page.getByText(/Open from asset detail/)).toBeVisible();
    await expect(main.getByText('发布审批')).toHaveCount(0);
  });

  test('404 页文案随语言切换（D6 硬编码整改的正向证据）', async ({ page }) => {
    await login(page, REVIEW_AUTHOR.username, REVIEW_AUTHOR.password);
    await page.goto('/no-such-route-065');

    // 整改前 NotFoundPage 为硬编码中文：英文界面下必须不再出现中文标题。
    await expect(page.getByText('页面不存在', { exact: true })).toBeVisible();
    await switchLanguage(page, 'English');
    await expect(page.getByText('Page not found', { exact: true })).toBeVisible();
    await expect(page.getByText('页面不存在', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Back to Asset Catalog' })).toBeVisible();
  });
});

test.describe('AC-P0B-UI-003: 后端独立授权与失败路径（UI 非安全边界）', () => {
  test('无 asset:manage 的主体直连提交发布请求 → 403（决策端授权在断链下仍可证）', async () => {
    const api = await request.newContext();
    try {
      const token = await loginApi(api, REVIEW_READER.username, REVIEW_READER.password);
      const versions = await api.get(`${API_BASE}/assets/${fixtures.assetId}/versions?limit=50`, {
        headers: bearer(await loginApi(api, REVIEW_AUTHOR.username, REVIEW_AUTHOR.password)),
        failOnStatusCode: false,
      });
      const data = dataOf(await readJson(versions)) as { items?: { versionId: string; status: string }[] } | null;
      const draft = data?.items?.find((v) => v.status === 'DRAFT');
      expect(draft, '夹具应存在 DRAFT 版本（准备阶段创建）').toBeTruthy();

      const denied = await api.post(`${API_BASE}/versions/${draft!.versionId}/publish-requests`, {
        headers: bearer(token),
        failOnStatusCode: false,
      });
      expect(denied.status()).toBe(403);
      const body = (await readJson(denied)) as { code?: string } | null;
      expect(body?.code).toBe('AUTH_PERMISSION_DENIED');
    } finally {
      await api.dispose();
    }
  });

  test('DRAFT 版本提交发布请求：后端拒绝且 UI 显示错误而非假成功（正向闭环断链证据）', async ({
    page,
  }) => {
    // 后端侧：业务状态机要求 PENDING_REVIEW，而 PENDING_REVIEW 因
    // UPLOAD_MATERIALIZE/VERSION_VALIDATE 作业无入队方而运行态不可达。
    // 状态机违规已由后端契约化为 409 VERSION_STATE_NOT_ALLOWED
    // （PublishApplicationService 注释：此前裸 IllegalStateException 兜底 500 会误导客户端重试），
    // 断言按契约口径 409 + 错误码，属对齐整改而非放宽。
    const api = await request.newContext();
    let draftVersionId: string | undefined;
    try {
      const authorToken = await loginApi(api, REVIEW_AUTHOR.username, REVIEW_AUTHOR.password);
      const versions = await api.get(`${API_BASE}/assets/${fixtures.assetId}/versions?limit=50`, {
        headers: bearer(authorToken),
        failOnStatusCode: false,
      });
      const data = dataOf(await readJson(versions)) as { items?: { versionId: string; status: string }[] } | null;
      draftVersionId = data?.items?.find((v) => v.status === 'DRAFT')?.versionId;
      expect(draftVersionId, '夹具应存在 DRAFT 版本').toBeTruthy();

      const res = await api.post(`${API_BASE}/versions/${draftVersionId}/publish-requests`, {
        headers: bearer(authorToken),
        failOnStatusCode: false,
      });
      expect(res.status()).toBe(409); // 契约口径 VERSION_STATE_NOT_ALLOWED；闭环缺口仍记入 Evidence limitations
      const errBody = (await readJson(res)) as { code?: string } | null;
      expect(errBody?.code).toBe('VERSION_STATE_NOT_ALLOWED');
    } finally {
      await api.dispose();
    }

    // UI 侧：审查页只渲染后端真实返回的发布请求行；夹具 DRAFT 版本不存在发布请求，
    // 深链后列表保持诚实空态，不存在把 DRAFT 假提交成功的路径。
    await login(page, REVIEW_AUTHOR.username, REVIEW_AUTHOR.password);
    await page.goto(`/review?assetId=${fixtures.assetId}`);
    await expect(page.getByText(/暂无发布请求|No publish requests/)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByRole('alert')).toHaveCount(0);
    await expect(page.getByText(/发布请求已提交|Publish request submitted/)).toHaveCount(0);
  });
});
