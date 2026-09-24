/*
 * 功能: 资产血缘 E2E——父子边双向展示、方向切换真实生效、空态、后端独立授权。
 *       夹具经 REST 契约建边（E2E_ASSET(parent) --DERIVED_FROM--> E2E_DATASET(child)）；
 *       UI 旅程从资产目录 → 详情「血缘」Tab → 血缘页，全程应用内导航。
 *       断言全部取自 AssetLineagePage 当前代码与后端契约
 *       （AssetLineageQueryService: down→findByParentAssetId / up→findByChildAssetId），
 *       无条件式：夹具缺失或数据不符即失败。
 * 时间: 2026-09-24
 * 作者: AxeXie
 */
import { test, expect, request, type Page } from '@playwright/test';
import {
  adminToken,
  bootstrapCredentials,
  directApiStatus,
  E2E_ASSET,
  E2E_AUTHOR,
  E2E_AUTHZ_READER,
  E2E_DATASET,
  ensureLineageRelation,
  login,
  loginAs,
  openAssetDetailViaCatalog,
  prepareFixtures,
  type E2eFixtures,
} from './support';

const RELATION_TYPE = 'DERIVED_FROM';

let credentials: { username: string; password: string } | null = null;
let fixtures: E2eFixtures;

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) {
    return;
  }
  const api = await request.newContext();
  try {
    fixtures = await prepareFixtures(await adminToken(api), api);
    // 建边需 asset:manage（E2E_AUTHOR 具备）；幂等：重复创建 409 视为前提已满足。
    const authorToken = await loginAs(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await ensureLineageRelation(api, authorToken, fixtures.assetId, fixtures.datasetAssetId, RELATION_TYPE);
  } finally {
    await api.dispose();
  }
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
});

/** 从资产详情页的「血缘」Tab 真实点击进入血缘页（应用内导航的唯一入口）。 */
async function openLineagePage(page: Page, displayName: string, assetId: string) {
  await openAssetDetailViaCatalog(page, displayName);
  await page.getByRole('tab', { name: /^血缘$|^Lineage$/i }).click();
  // antd 两字按钮渲染为「血 缘」，用允许空白的正则定位（修正定位器，非放宽断言）。
  await page.getByRole('button', { name: /^血\s*缘$|^Lineage$/i }).click();
  await expect(page).toHaveURL(new RegExp(`/assets/${assetId}/lineage$`));
  await expect(page.getByText('资产血缘')).toBeVisible();
}

/** 切换方向 Select（其 label 恒为「上游/下游」，可与深度 Select 区分）。 */
async function switchDirection(page: Page, label: '上游' | '下游') {
  // 点击 .ant-select 外层而非 role=combobox 的 input：antd 选中项 span 会遮挡 input（publish-flow 实测）。
  const selector = page.locator('.ant-select').filter({ hasText: /上游|下游/ });
  await selector.click();
  await page
    .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
    .filter({ hasText: label })
    .click();
  await expect(selector).toContainText(label);
}

/** 断言恰好一行 DERIVED_FROM 边，且父/子列链接指向预期资产。 */
async function expectDerivedEdge(page: Page, parentAssetId: string, childAssetId: string) {
  const rows = page.locator('.ant-table-row').filter({ hasText: RELATION_TYPE });
  await expect(rows).toHaveCount(1);
  const row = rows.first();
  await expect(row.getByText(RELATION_TYPE, { exact: true })).toBeVisible();
  await expect(row.getByRole('link', { name: parentAssetId })).toBeVisible();
  await expect(row.getByRole('link', { name: childAssetId })).toBeVisible();
}

test.describe('资产血缘页面', () => {
  test('父资产视角：默认下游展示 DERIVED_FROM 边，切上游后为空（方向真实生效）', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openLineagePage(page, E2E_ASSET.displayName, fixtures.assetId);

    // 默认 direction=down → findByParentAssetId：应命中夹具边。
    await expectDerivedEdge(page, fixtures.assetId, fixtures.datasetAssetId);

    // 切到 up → findByChildAssetId(assetId)：本夹具中 assetId 只作父方，必须为空。
    // 若前端未把 direction 传给后端，此断言将失败——这是方向参数生效的反证。
    await switchDirection(page, '上游');
    await expect(page.getByText('暂无血缘关系')).toBeVisible();
    await expect(page.locator('.ant-table-row').filter({ hasText: RELATION_TYPE })).toHaveCount(0);
  });

  test('子资产视角：下游为空，切上游展示同一条边（双向闭环）', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openLineagePage(page, E2E_DATASET.displayName, fixtures.datasetAssetId);

    // dataset 只作 child：down 视角无边（全后端仅 relations POST 一个写点，无自动建边）。
    await expect(page.getByText('暂无血缘关系')).toBeVisible();

    await switchDirection(page, '上游');
    await expectDerivedEdge(page, fixtures.assetId, fixtures.datasetAssetId);
  });

  test('血缘表父/子列链接可导航到关联资产详情', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openLineagePage(page, E2E_ASSET.displayName, fixtures.assetId);
    await expectDerivedEdge(page, fixtures.assetId, fixtures.datasetAssetId);

    // 子列 Link → dataset 资产详情（SPA 内路由跳转，不经 nginx /assets/ 静态规则）。
    await page.locator('.ant-table-row').getByRole('link', { name: fixtures.datasetAssetId }).click();
    await expect(page).toHaveURL(new RegExp(`/assets/${fixtures.datasetAssetId}$`));
    await expect(page.getByRole('heading', { name: E2E_DATASET.displayName })).toBeVisible();
  });

  test('无 asset:read 的主体直连 lineage 端点 → 403（UI 非安全边界）', async () => {
    // AssetLineageQueryService.queryLineage 首行 requirePermission(ASSET_READ)；
    // E2E_AUTHZ_READER 仅持有 authorization:read，后端必须独立拒绝。
    const status = await directApiStatus(
      `/assets/${fixtures.assetId}/lineage`,
      E2E_AUTHZ_READER.username,
      E2E_AUTHZ_READER.password,
    );
    expect(status).toBe(403);
  });
});
