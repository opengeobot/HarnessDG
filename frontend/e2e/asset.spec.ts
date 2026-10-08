/*
 * 功能: P1 资产目录 E2E——目录检索→登记→详情→生命周期（弃用/归档/恢复）→缺失态。
 * 时间: 2026-09-28
 * 作者: AxeXie
 *
 * <p>历史背景：前端容器 nginx 的 {@code location /assets/} 静态规则曾遮蔽 SPA 深链，
 * 整页加载 {@code /assets}、{@code /assets/{id}} 分别得到 301/403/404（见 EVD-P0BR-050-001）。
 * 该缺陷已由 TASK-P0BR-073 修复（构建产物改名 /static/），深链整页加载见 spa-deep-link.spec.ts。
 * <p>生命周期旅程落在专用夹具资产与专用主体（E2E_LIFECYCLE_OWNER 具备 asset:deprecate）上，
 * 避免与 publish-flow.spec 的 PUBLISHED 版本冲突（归档对 publishedCount>0 返回 409）。
 */
import { test, expect, request, type Page } from '@playwright/test';
import {
  E2E_ASSET,
  E2E_AUTHOR,
  E2E_LIFECYCLE_ASSET,
  E2E_LIFECYCLE_OWNER,
  E2E_ORG,
  E2E_TEAM_NAME,
  adminToken,
  bootstrapCredentials,
  directApiStatus,
  expectAssetLinkViaSearch,
  login,
  openAssetDetailViaCatalog,
  openAssetSettingsViaNav,
  openAssetsViaNav,
  prepareFixtures,
  type E2eFixtures,
} from './support';

let credentials: { username: string; password: string } | null = null;
let fixtures: E2eFixtures;

/** antd 对恰好两个汉字的按钮插入空格（「确 定」「创 建」「设 置」），故一律用宽松正则。 */
const POPCONFIRM_OK = /确\s*定|OK|Yes/i;

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) {
    return;
  }
  const api = await request.newContext();
  try {
    fixtures = await prepareFixtures(await adminToken(api), api);
  } finally {
    await api.dispose();
  }
});

test.beforeEach(async ({ page }) => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
  await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
});

/** 在登记弹窗内按表单项标签定位控件（antd Select 的 combobox 语义落在内层 input 上）。 */
function formItem(page: Page, label: RegExp) {
  return page
    .locator('.ant-modal .ant-form-item')
    .filter({ has: page.locator('label', { hasText: label }) })
    .first();
}

/** 选择受控下拉中匹配文本的选项。 */
async function selectOption(page: Page, label: RegExp, optionText: RegExp) {
  await formItem(page, label).locator('input[role="combobox"]').click();
  await page
    .locator('.ant-select-dropdown:visible .ant-select-item-option')
    .filter({ hasText: optionText })
    .first()
    .click();
}

test.describe('P1 资产目录完整生命周期', () => {
  test('资产列表页经导航加载并显示检索控件', async ({ page }) => {
    await openAssetsViaNav(page);
    // 限定到页面标题：裸 getByText 会同时命中侧边栏菜单项「资产目录」。
    await expect(page.getByRole('heading', { name: /资产目录|Asset Catalog/ })).toBeVisible();
    // 类型切换控件：Segmented 的「模型/数据集」与表格行的类型 Tag 文本相同，
    // 必须限定到 Segmented 容器，否则 strict violation（实测命中 5 个元素）。
    const segmented = page.locator('.ant-segmented');
    await expect(segmented.getByTitle('全部')).toBeVisible();
    await expect(segmented.getByTitle('模型')).toBeVisible();
    await expect(segmented.getByTitle('数据集')).toBeVisible();
    // 真实夹具资产经页内搜索可见（证明检索确实打到后端而非空壳页面）。
    // 不断言默认结果集：验收栈累积的历史资产会把夹具挤出首屏分页（limit=10），
    // 与 openAssetDetailViaCatalog 处的既有说明同因；搜索后断言更贴近用户真实路径。
    await expectAssetLinkViaSearch(page, E2E_ASSET.displayName);
  });

  test('通过弹窗登记新资产', async ({ page }) => {
    const suffix = Date.now().toString(36);
    await openAssetsViaNav(page);
    await page.getByRole('button', { name: /登记资产|Register Asset/ }).click();
    await expect(page.locator('.ant-modal-title')).toHaveText(/登记资产|Register/);

    await selectOption(page, /所属组织|Organization/, new RegExp(E2E_ORG.name));
    await selectOption(page, /Owner 团队|Owner Team/, new RegExp(`${E2E_TEAM_NAME}\\s*\\(`));
    await page.getByLabel(/命名空间|Namespace/).fill('nlp');
    await page.getByLabel(/^名称$|^Name$/).fill(`e2e-0928-${suffix}`);
    // 该字段标签是 assets.create.displayNameLabel =「展示名称」，不是「显示名」。
    await page.getByLabel(/展示名称|displayName/i).fill(`E2E 0928 登记 ${suffix}`);

    await page.getByRole('button', { name: /创\s*建|Create/ }).click();

    // 成功提示回显 namespace/name，证明写入走真实 REST 契约
    await expect(page.locator('.ant-message').getByText(new RegExp(`nlp/e2e-0928-${suffix}`))).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.locator('.ant-modal-title')).toHaveCount(0);
  });

  test('搜索与过滤资产', async ({ page }) => {
    await openAssetsViaNav(page);
    const searchInput = page.getByPlaceholder(/搜索名称、描述或标签|Search name, description or tags/i);
    await searchInput.fill(E2E_ASSET.displayName);
    await searchInput.press('Enter');

    // 命中真实夹具资产，且过滤面板字段齐备
    await expect(page.getByRole('link', { name: E2E_ASSET.displayName })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/语言|Language/)).toBeVisible();
    await expect(page.getByText(/敏感等级|Sensitivity/)).toBeVisible();
  });

  test('资产详情页展示完整信息', async ({ page }) => {
    await openAssetDetailViaCatalog(page, E2E_ASSET.displayName);
    // 「坐标」标签同时出现在基本信息卡与 Quick Use 卡（两处真实渲染），
    // 裸 getByText 必然 strict violation，故定位到基本信息卡内的标签。
    const basic = page.locator('.ant-card').filter({ hasText: /基本信息|Basic Info/ }).first();
    await expect(basic).toBeVisible();
    await expect(basic.getByText(/坐标|Coordinate/)).toBeVisible();
    // namespace 与 name 在页面上只以坐标串出现（aria 快照：cell 文本为
    // aih://nlp/model/e2e050-version-target），因此按坐标断言而非独立文本节点。
    await expect(basic.getByRole('cell').filter({ hasText: /^aih:\/\// })).toContainText(
      `aih://${E2E_ASSET.namespace}/model/${E2E_ASSET.name}`,
    );
  });

  test('资产生命周期操作: 弃用→归档→恢复', async ({ page }) => {
    // 独立主体：asset:deprecate 是弃用的强制点，E2E_AUTHOR 不具备
    await login(page, E2E_LIFECYCLE_OWNER.username, E2E_LIFECYCLE_OWNER.password);
    await openAssetDetailViaCatalog(page, E2E_LIFECYCLE_ASSET.displayName);
    await openAssetSettingsViaNav(page);

    const lifecycle = page.locator('.ant-card').filter({ hasText: /生命周期|Lifecycle/ }).first();
    await expect(lifecycle).toBeVisible();

    // 弃用（原因可选，此处留空走后端默认）
    await lifecycle.getByRole('button', { name: /弃用|Deprecate/ }).click();
    await page.getByRole('button', { name: POPCONFIRM_OK }).click();
    await expect(page.locator('.ant-message').getByText(/已弃用|Deprecated/)).toBeVisible({ timeout: 15_000 });

    // 归档：DEPRECATED 态提供归档入口
    await expect(lifecycle.getByRole('button', { name: /归\s*档|Archive/ })).toBeVisible({ timeout: 15_000 });
    await lifecycle.getByRole('button', { name: /归\s*档|Archive/ }).click();
    await page.getByRole('button', { name: POPCONFIRM_OK }).click();
    await expect(page.locator('.ant-message').getByText(/已归档|Archived/)).toBeVisible({ timeout: 15_000 });

    // 恢复：ARCHIVED 态提供恢复入口，恢复后回到 ACTIVE（保证下一次运行仍可从目录进入）
    await expect(lifecycle.getByRole('button', { name: /恢复|Restore/ })).toBeVisible({ timeout: 15_000 });
    await lifecycle.getByRole('button', { name: /恢复|Restore/ }).click();
    await page.getByRole('button', { name: POPCONFIRM_OK }).click();
    await expect(page.locator('.ant-message').getByText(/已恢复|Restored/)).toBeVisible({ timeout: 15_000 });

    // 恢复后必须能重新从目录检索到该资产（ACTIVE 态对普通用户可见）。
    await openAssetsViaNav(page);
    await expectAssetLinkViaSearch(page, E2E_LIFECYCLE_ASSET.displayName);
  });

  test('缺失资产在 UI 与后端均返回不存在', async ({ page }) => {
    // 后端独立判定：不可访问与不存在统一 404 ASSET_NOT_FOUND（防枚举，见 AssetAccessPolicy）
    const status = await directApiStatus(
      '/assets/00000000000000000000000000as',
      E2E_AUTHOR.username,
      E2E_AUTHOR.password,
    );
    expect(status).toBe(404);

    // UI 判定：真实深链整页加载（nginx 遮蔽已由 TASK-P0BR-073 修复），
    // route-mock 仅拦截 API 注入 404，使页面渲染空态的路径确定可复现。
    await page.route('**/api/v1/assets/*', async (route) => {
      await route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'ASSET_NOT_FOUND', message: 'asset not found', retryable: false }),
      });
    });
    await page.goto(`/assets/${fixtures.assetId}`);
    await expect(page.getByText(/资产不存在|not found|不存在/i).first()).toBeVisible({ timeout: 15_000 });
  });
});
