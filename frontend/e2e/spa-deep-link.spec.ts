/*
 * 功能: TASK-P0BR-073 E2E——资产域六条 SPA 深链的整页加载验收。
 * 时间: 2026-10-08
 * 作者: AxeXie
 *
 * 运行前提: docker compose up（前端容器 8080 权威口径）+ E2E_* 环境变量。
 *
 * <p>背景：前端容器 nginx 的 {@code location /assets/} 静态规则曾遮蔽
 * {@code /assets/:assetId...} 六条业务路由的整页加载（刷新/收藏/分享/新标签），
 * 全部返回 nginx 原生 404。TASK-P0BR-073 将构建产物目录改名 /static/ 后，
 * 本用例族以真实用户形态逐条验证：登录（refresh Cookie 落盘）→ page.goto 整页加载
 * 深链 → 应用经 refresh-Cookie 会话恢复渲染目标页，而非 404 或「页面不存在」兜底。
 *
 * <p>夹具口径：版本类用例复用 dataset.spec 同型幂等夹具
 * （ensureAssetVersion 在 E2E_DATASET 资产上确保 1.0.0，两 spec 幂等共存），
 * 刻意不在 E2E_ASSET（MODEL）上追加版本——version-management.spec 断言其
 * 「单 DRAFT」唯一性，跨 spec 写入会造成污染。
 */
import { test, expect, request, type Page } from '@playwright/test';
import {
  E2E_ASSET,
  E2E_AUTHOR,
  adminToken,
  bootstrapCredentials,
  ensureAssetVersion,
  login,
  loginAs,
  prepareFixtures,
  type E2eFixtures,
} from './support';

const FIXTURE_VERSION = '1.0.0';

let credentials: { username: string; password: string } | null = null;
let fixtures: E2eFixtures;
let versionId: string;

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) {
    return;
  }
  const api = await request.newContext();
  try {
    fixtures = await prepareFixtures(await adminToken(api), api);
    const authorToken = await loginAs(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
    versionId = await ensureAssetVersion(api, authorToken, fixtures.datasetAssetId, FIXTURE_VERSION);
  } finally {
    await api.dispose();
  }
});

test.beforeEach(async ({ page }) => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
  // 登录先行：整页加载深链时 access Token 已随页面销毁，会话必须由 refresh Cookie 恢复
  await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
});

/** 深链落地断言：URL 未被重定向、目标页特征可见、SPA 404 兜底未出现。 */
async function expectDeepLink(page: Page, url: RegExp, marker: RegExp | string) {
  await expect(page).toHaveURL(url);
  const notFound = page.getByText('页面不存在');
  await expect(notFound).toHaveCount(0);
  await expect(page.getByText(marker).first()).toBeVisible({ timeout: 15_000 });
}

test('深链整页加载：资产详情 /assets/{assetId}', async ({ page }) => {
  await page.goto(`/assets/${fixtures.assetId}`);
  await expectDeepLink(page, new RegExp(`/assets/${fixtures.assetId}$`), E2E_ASSET.displayName);
});

test('深链整页加载：资产设置 /assets/{assetId}/settings', async ({ page }) => {
  await page.goto(`/assets/${fixtures.assetId}/settings`);
  await expectDeepLink(page, /\/settings$/, '资产设置');
});

test('深链整页加载：资产血缘 /assets/{assetId}/lineage', async ({ page }) => {
  await page.goto(`/assets/${fixtures.assetId}/lineage`);
  await expectDeepLink(page, /\/lineage$/, '资产血缘');
});

test('深链整页加载：访问授权 /assets/{assetId}/access', async ({ page }) => {
  await page.goto(`/assets/${fixtures.assetId}/access`);
  await expectDeepLink(page, /\/access$/, '访问授权');
});

test('深链整页加载：版本列表 /assets/{assetId}/versions', async ({ page }) => {
  await page.goto(`/assets/${fixtures.datasetAssetId}/versions`);
  await expectDeepLink(page, /\/versions$/, '版本列表');
});

test('深链整页加载：版本详情 /assets/{assetId}/versions/{versionId}', async ({ page }) => {
  await page.goto(`/assets/${fixtures.datasetAssetId}/versions/${versionId}`);
  await expectDeepLink(page, new RegExp(`/versions/${versionId}$`), /版本详情/);
});
