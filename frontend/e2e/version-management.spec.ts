/*
 * 功能: 版本中心（/version）E2E——深链真实渲染、版本选择详情、路由守卫与后端独立授权。
 *       挂线 support.ts 夹具（E2E 050 资产 + 幂等 DRAFT 版本），断言为无条件式：
 *       数据缺失、渲染假象或后端放行时测试必须失败。
 *       已知限制：DRAFT→VALIDATING 之后的校验/审批作业链在运行态断链
 *       （与 review-workflow.spec 同一缺口），正向推进不点击，缺口记入 Evidence limitations。
 * 时间: 2026-09-24
 * 作者: AxeXie
 */
import { test, expect, request, type APIRequestContext } from '@playwright/test';
import {
  adminToken,
  bootstrapCredentials,
  E2E_ASSET,
  E2E_AUTHOR,
  E2E_AUTHZ_READER,
  login,
  prepareFixtures,
  type E2eFixtures,
} from './support';

const API_BASE = process.env.E2E_API_BASE_URL ?? 'http://localhost:8080/api/v1';

/** 夹具版本：与 review-workflow 的 065 资产分离，本 spec 使用 050 资产上的 1.0.0。 */
const FIXTURE_VERSION = '1.0.0';

interface Fixtures extends E2eFixtures {
  versionId: string;
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

/** 幂等确保夹具资产上存在 DRAFT 版本 1.0.0，并查回其 versionId。 */
async function ensureDraftVersion(api: APIRequestContext, authorToken: string, assetId: string): Promise<string> {
  const listed = await api.get(`${API_BASE}/assets/${assetId}/versions?limit=50`, {
    headers: bearer(authorToken),
    failOnStatusCode: false,
  });
  const page = dataOf(await readJson(listed)) as
    | { items?: { versionId: string; version: string; status: string }[] }
    | null;
  const existing = page?.items?.find((v) => v.version === FIXTURE_VERSION);
  if (existing) {
    return existing.versionId;
  }
  const created = await api.post(
    `${API_BASE}/assets/${assetId}/versions`,
    { headers: bearer(authorToken), data: { version: FIXTURE_VERSION }, failOnStatusCode: false },
  );
  expect(created.status(), `创建夹具 DRAFT 版本返回 HTTP ${created.status()}`).toBe(201);
  const data = dataOf(await readJson(created)) as { versionId?: string } | null;
  expect(data?.versionId, '创建响应缺少 versionId').toBeTruthy();
  return data!.versionId!;
}

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) {
    return;
  }
  const api = await request.newContext();
  try {
    const base = await prepareFixtures(await adminToken(api), api);
    const authorToken = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
    const versionId = await ensureDraftVersion(api, authorToken, base.assetId);
    fixtures = { ...base, versionId };
  } finally {
    await api.dispose();
  }
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
});

test.describe('版本中心：真实渲染（E2E 050 夹具）', () => {
  test('无深链时显示诚实引导而非虚构数据', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await page.goto('/version');

    // antd Card title 渲染为 div：文本级定位并限定 main（侧边菜单含「版本中心」同文案）。
    const main = page.getByRole('main');
    await expect(main.getByText('版本中心', { exact: true })).toBeVisible();
    await expect(page.getByText(/请从资产详情页进入|Open from asset detail/)).toBeVisible();
    // 未加载任何资产上下文时不得出现版本行或状态 Tag。
    await expect(main.getByText(FIXTURE_VERSION, { exact: true })).toHaveCount(0);
  });

  test('深链真实资产：渲染夹具版本行与 DRAFT 状态，无错误提示', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await page.goto(`/version?assetId=${fixtures.assetId}`);

    const main = page.getByRole('main');
    await expect(main.getByText(fixtures.assetId, { exact: true })).toBeVisible();
    await expect(main.getByText(/版本列表|Version List/)).toBeVisible();
    await expect(main.getByText(FIXTURE_VERSION, { exact: true })).toBeVisible({ timeout: 10_000 });
    await expect(main.getByText('DRAFT', { exact: true })).toBeVisible();
    await expect(main.getByText(/暂无版本|No versions/)).toHaveCount(0);
    await expect(page.getByRole('alert')).toHaveCount(0);
  });

  test('选择版本：详情面板展示后端真实 versionId 与状态', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await page.goto(`/version?assetId=${fixtures.assetId}`);

    const main = page.getByRole('main');
    await main.getByText(FIXTURE_VERSION, { exact: true }).click({ timeout: 10_000 });
    await expect(main.getByText(/版本详情|Version Detail/)).toBeVisible();
    await expect(main.getByText(fixtures.versionId, { exact: true })).toBeVisible({ timeout: 10_000 });
    await expect(page.getByRole('alert')).toHaveCount(0);
  });
});

test.describe('版本中心：授权强制（UI 非安全边界）', () => {
  test('无 asset:read 的主体访问 /version 被 RouteGuard 拒绝', async ({ page }) => {
    await login(page, E2E_AUTHZ_READER.username, E2E_AUTHZ_READER.password);
    await page.goto(`/version?assetId=${fixtures.assetId}`);

    await expect(page.getByText(/缺少访问权限|Access denied/)).toBeVisible({ timeout: 10_000 });
  });

  test('无 asset:manage 的主体直连状态推进端点 → 403（后端独立拒绝）', async () => {
    const api = await request.newContext();
    try {
      const readerToken = await loginApi(api, E2E_AUTHZ_READER.username, E2E_AUTHZ_READER.password);
      const denied = await api.post(
        `${API_BASE}/assets/${fixtures.assetId}/versions/${fixtures.versionId}/transition`,
        { headers: bearer(readerToken), data: { targetStatus: 'VALIDATING' }, failOnStatusCode: false },
      );
      expect(denied.status()).toBe(403);
      const body = (await readJson(denied)) as { code?: string } | null;
      expect(body?.code).toBe('AUTH_PERMISSION_DENIED');
    } finally {
      await api.dispose();
    }
  });

  test('夹具资产对作者可见、坐标与 support 常量一致', async () => {
    // 守护夹具契约本身：若 prepareFixtures 与常量漂移，上述 UI 断言会失去意义。
    const api = await request.newContext();
    try {
      const authorToken = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
      const listed = await api.get(`${API_BASE}/assets/${fixtures.assetId}`, {
        headers: bearer(authorToken),
        failOnStatusCode: false,
      });
      expect(listed.status()).toBe(200);
      const data = dataOf(await readJson(listed)) as { name?: string; namespace?: string } | null;
      expect(data?.name).toBe(E2E_ASSET.name);
      expect(data?.namespace).toBe(E2E_ASSET.namespace);
    } finally {
      await api.dispose();
    }
  });
});
