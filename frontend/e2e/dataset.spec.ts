/*
 * 功能: DATASET 资产体验 E2E（TASK-P1-013 / AC-DST-DETAIL-001～004）。
 *       覆盖：目录类型过滤（Segmented DATASET）、详情页画像渲染、
 *             版本切换深链与 URL 同步、讨论 Tab 列表端点授权契约。
 *       断言原则：全部依赖 support.prepareFixtures 幂等创建的夹具资产，
 *       DATASET 夹具（E2E_DATASET：format=parquet，modality=text，INTERNAL）；
 *       版本夹具建在 DATASET 资产上（1.0.0/1.0.1 两个幂等 DRAFT 版本）：
 *       切换器用例仅依赖本 spec 私有的 DATASET 资产，避免在 version-management.spec
 *       共用的 MODEL 夹具资产上追加数据而破坏其「单 DRAFT」唯一性断言；
 *       由本 spec beforeAll 独立确保，不依赖其它 spec 的执行顺序。
 *       已知限制：E2E_AUTHOR scopes 不含 asset:discuss，UI 新建线程会 403，
 *       讨论侧仅验证 GET /assets/{assetId}/discussions 列表端点的 scope 契约
 *       （DiscussionApplicationService L106 requirePermission(ASSET_READ)）；写入侧留待补充夹具。
 * 时间: 2026-09-24
 * 作者: AxeXie
 */
import { test, expect, request } from '@playwright/test';
import {
  E2E_ASSET,
  E2E_AUTHOR,
  E2E_AUTHZ_READER,
  E2E_DATASET,
  adminToken,
  bootstrapCredentials,
  directApiGet,
  ensureAssetVersion,
  login,
  loginAs,
  openAssetsViaNav,
  openAssetDetailViaCatalog,
  prepareFixtures,
  type E2eFixtures,
} from './support';

/** 版本夹具：建在本 spec 私有的 DATASET 夹具资产上（幂等）。 */
const FIXTURE_VERSION = '1.0.0';
/** 第二个 DRAFT 版本：版本切换器需要 ≥2 个选项才能产生真实的 onChange（切换）。 */
const SECOND_VERSION = '1.0.1';

let credentials: { username: string; password: string } | null = null;
let fixtures: E2eFixtures;
let versionId: string;
let secondVersionId: string;

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) {
    return;
  }
  const api = await request.newContext();
  try {
    fixtures = await prepareFixtures(await adminToken(api), api);
    const authorToken = await loginAs(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
    // 版本夹具落在本 spec 私有的 DATASET 资产上：MODEL 夹具资产由
    // version-management.spec 断言「单 DRAFT」唯一性，此处追加版本会造成跨 spec 污染。
    versionId = await ensureAssetVersion(api, authorToken, fixtures.datasetAssetId, FIXTURE_VERSION);
    // 坐标唯一（VersionApplicationService.create → findByCoordinate），无单 DRAFT 约束，
    // 故两个 DRAFT 版本可共存，为切换器提供真实的「切到不同项」目标。
    secondVersionId = await ensureAssetVersion(api, authorToken, fixtures.datasetAssetId, SECOND_VERSION);
  } finally {
    await api.dispose();
  }
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
});

test.describe('DATASET 资产体验（E2E 050 夹具）', () => {
  // ── 用例 1：目录 Segmented 类型过滤 ──────────────────────────────────────
  // AssetsPage Segmented<TypeFilter> DATASET 选项 label=t('assets.dataset')；
  // 后端 AssetSearchDao L182 appendEquals("a.type", 'DATASET') 保证结果集仅含 DATASET 行，
  // 故 DATASET 夹具必须出现、MODEL 夹具链接必须为 0（非因分页，而因类型条件）。
  test('目录类型过滤——切「数据集」后 DATASET 夹具出现、MODEL 夹具消失', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAssetsViaNav(page);

    // antd Segmented 的可点目标是 .ant-segmented-item（label 包裹层）；
    // getByRole('radio') 命中的是隐藏的 ant-segmented-item-input，不可交互。
    await page.locator('.ant-segmented-item').filter({ hasText: /数据集|Dataset/ }).click();
    await page.waitForLoadState('networkidle');

    await expect(
      page.getByRole('link', { name: E2E_DATASET.displayName }),
    ).toBeVisible({ timeout: 10_000 });
    await expect(
      page.getByRole('link', { name: E2E_ASSET.displayName }),
    ).toHaveCount(0);
  });

  // ── 用例 2：详情页数据集画像渲染 ────────────────────────────────────────
  // AssetDetailPage L331-365 {asset.dataset && <Card title=数据集画像>}；
  // 后端 AssetView.datasetView 仅在 datasetProfile!=null 时返回 {format, modality, …}。
  // 夹具值 format/modality 来自 E2E_DATASET 常量，断言卡片确实渲染这两个真实值。
  test('详情页「数据集画像」Card 渲染 format/modality 字段值', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    // 客户端导航进入详情：page.goto('/assets/...') 整页加载会被前端容器 nginx 的
    // `location /assets/` 静态规则遮蔽为 404（既有基础设施缺陷，见 EVD-P0BR-050-001）。
    await openAssetDetailViaCatalog(page, E2E_DATASET.displayName);

    const profileCard = page
      .locator('.ant-card')
      .filter({ hasText: /数据集画像|Dataset Profile/ });
    await expect(profileCard).toBeVisible({ timeout: 10_000 });
    await expect(profileCard).toContainText(E2E_DATASET.format);
    await expect(profileCard).toContainText(E2E_DATASET.modality);
  });

  // ── 用例 3：版本切换器驱动 URL 同步（AC-DST-DETAIL-002）─────────────────
  // AssetDetailPage L155 selectedVersionId=searchParams.get('version')（初始 URL 无该参数）；
  // L532-543 顶部 Select（该页唯一）value=effectiveVersionId（无 URL 参数时回退 versions[0]），
  // onChange=(v)=>setParam('version', v)。受控 Select 点击「当前已选项」不会触发 onChange，
  // 故夹具提供 1.0.0/1.0.1 两个 DRAFT 版本；用例读取当前回显后切换到「另一个」，
  // URL 必须写入 ?version=<目标id> 且选择器回显切换——不依赖后端 created_at DESC 的时间戳先后。
  test('版本切换——页内选择不同版本后 URL 写入 version=<id> 且选择器回显', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAssetDetailViaCatalog(page, E2E_DATASET.displayName);

    // 进入详情时 URL 不携带 version 参数（初始态），交互后写入——变化本身即证明同步契约。
    await expect(page).not.toHaveURL(/version=/);
    const switcher = page.locator('.ant-select-selection-item');
    await expect(switcher).toBeVisible({ timeout: 10_000 });
    const current = await switcher.innerText();
    expect(current, `选择器回显应为夹具版本之一，实际「${current}」`).toMatch(/1\.0\.[01]/);
    const switchedToFirst = current.includes(SECOND_VERSION);
    const targetLabel = switchedToFirst ? FIXTURE_VERSION : SECOND_VERSION;
    const targetVersionId = switchedToFirst ? versionId : secondVersionId;

    // 该页仅有版本切换一个 Select（已 Grep 核实 AssetDetailPage 全部 Select 实例）。
    await page.locator('.ant-select-selector').click();
    await page
      .locator('.ant-select-item-option')
      .filter({ hasText: targetLabel })
      .click();

    await expect(page).toHaveURL(new RegExp(`version=${targetVersionId}`), { timeout: 10_000 });
    await expect(page.locator('.ant-select-selection-item')).toContainText(targetLabel);
  });

  // ── 用例 4：讨论 Tab 列表端点授权契约 ──────────────────────────────────
  // DiscussionPanel L271 listThreads → GET /assets/{assetId}/discussions；
  // 后端 L106 仅 requirePermission(ASSET_READ)（scope 门，先于逐行 ACL）：
  //   - E2E_AUTHOR（有 asset:read）→ 200；
  //   - E2E_AUTHZ_READER（无 asset:read）→ 403；
  // UI 侧页内切换讨论 Tab（AssetDetailPage L722-727 Tabs onChange=setParam('tab')）：
  // URL 写入 ?tab=discussions，且夹具无讨论线程 →「暂无讨论」空状态可见。
  test('讨论 Tab——持 asset:read 列表 200、无 scope 403、页内切换空态渲染', async ({ page }) => {
    const authorResp = await directApiGet<{ items?: unknown[] }>(
      `/assets/${fixtures.datasetAssetId}/discussions?limit=50`,
      E2E_AUTHOR.username,
      E2E_AUTHOR.password,
    );
    expect(authorResp.status, 'E2E_AUTHOR 有 asset:read，讨论列表应 200').toBe(200);

    const readerResp = await directApiGet(
      `/assets/${fixtures.datasetAssetId}/discussions?limit=50`,
      E2E_AUTHZ_READER.username,
      E2E_AUTHZ_READER.password,
    );
    expect(readerResp.status, '无 asset:read 的主体讨论列表应 403').toBe(403);

    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAssetDetailViaCatalog(page, E2E_DATASET.displayName);
    await page.getByRole('tab', { name: /讨论|Discussions/ }).click();
    await expect(page).toHaveURL(/tab=discussions/, { timeout: 10_000 });
    await expect(
      page.locator('.ant-empty').filter({ hasText: /暂无讨论|No discussions/i }),
    ).toBeVisible({ timeout: 10_000 });
  });
});
