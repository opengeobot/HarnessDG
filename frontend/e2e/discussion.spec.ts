/*
 * 功能: 讨论/评论 E2E——建线程、发评论、锁定/解锁 Moderation 全旅程，
 *       以及无 asset:discuss 主体直连后端被拒（UI 非安全边界）。
 *       夹具主体 E2E_DISCUSSANT 具备 asset:read + asset:discuss + asset:moderate
 *       （DiscussionController/CommentController 三个独立强制点）。
 *       线程标题带运行唯一时间戳：后端 listThreads 按 createdAt 倒序，
 *       新建线程必现首行，断言不受验收栈历史线程干扰。
 *       断言全部取自 DiscussionPanel 当前代码与 zh.json 文案，无条件式。
 * 时间: 2026-09-24
 * 作者: AxeXie
 */
import { test, expect, request, type Page } from '@playwright/test';
import {
  adminToken,
  bootstrapCredentials,
  E2E_ASSET,
  E2E_ASSET_OUTSIDER,
  E2E_DISCUSSANT,
  login,
  loginAs,
  openAssetDetailViaCatalog,
  prepareFixtures,
  type E2eFixtures,
} from './support';

let credentials: { username: string; password: string } | null = null;
let fixtures: E2eFixtures;

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) {
    return;
  }
  const api = await request.newContext();
  try {
    // prepareFixtures 幂等创建 E2E_DISCUSSANT 并将其加入组织
    //（INTERNAL 资产行级过滤要求讨论主体是组织成员，否则 listThreads 拒绝）。
    fixtures = await prepareFixtures(await adminToken(api), api);
  } finally {
    await api.dispose();
  }
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
});

/** 登录讨论主体并进入 E2E_ASSET 详情页的「讨论」Tab。 */
async function openDiscussionTab(page: Page) {
  await login(page, E2E_DISCUSSANT.username, E2E_DISCUSSANT.password);
  await openAssetDetailViaCatalog(page, E2E_ASSET.displayName);
  await page.getByRole('tab', { name: /^讨\s*论$|^Discussion/i }).click();
  // DiscussionPanel Card 标题；同时证明线程区已渲染（面板级空态或列表必现其一）。
  await expect(page.getByText('暂无讨论').or(page.locator('.ant-list-item').first())).toBeVisible();
}

/** 断言某条成功 toast 真实出现。
 *  antd message 容器会同时残留多条提示（如登录后的「登录成功」），
 *  必须按文本过滤定位到目标消息，否则违反 strict mode。 */
function expectSuccessMessage(page: Page, text: string) {
  return expect(
    page.locator('.ant-message-success').filter({ hasText: text }),
  ).toBeVisible({ timeout: 10_000 });
}

/** 以唯一标题新建线程并断言成功反馈 + 首行回显。 */
async function createThread(page: Page, title: string) {
  await page.getByPlaceholder('新建讨论标题').fill(title);
  // antd 两字按钮渲染为「新 建」（zh.json discussion.newThread）；
  // 全锚定排除版本区「新建草稿版本」（修正定位器，非放宽断言）。
  await page.getByRole('button', { name: /^新\s*建$/ }).click();
  await expectSuccessMessage(page, '线程已创建');
  // 后端 orderByDesc(createdAt)：新线程必现首行。
  const firstItem = page.locator('.ant-list-item').first();
  await expect(firstItem.getByText(title, { exact: true })).toBeVisible();
  return firstItem;
}

test.describe('讨论与评论流程', () => {
  test('资产详情页存在讨论区域并可新建线程', async ({ page }) => {
    await openDiscussionTab(page);
    const title = `E2E 线程 ${Date.now()}`;
    const item = await createThread(page, title);
    // 线程行渲染状态 Tag（OPEN）与创建者（ThreadView.createdBy = principalId）。
    await expect(item.getByText('OPEN', { exact: true })).toBeVisible();
    await expect(item.getByText(fixtures.discussantPrincipalId)).toBeVisible();
  });

  test('选中线程后发表评论：成功反馈与评论回显、线程计数联动', async ({ page }) => {
    await openDiscussionTab(page);
    const title = `E2E 评论线程 ${Date.now()}`;
    const item = await createThread(page, title);
    await item.click();

    // 选中后渲染「评论 - {标题}」Card。
    await expect(page.getByText(`评论 - ${title}`)).toBeVisible();
    // 新线程无评论：空态必须先出现，证明后续回显来自本次发表而非历史数据。
    await expect(page.getByText('暂无评论')).toBeVisible();

    const body = `E2E 评论内容 ${Date.now()}`;
    await page.getByPlaceholder('输入评论...').fill(body);
    // 「发表」同为两字按钮；全锚定（回复区的「发表」按钮默认不渲染）。
    await page.getByRole('button', { name: /^发\s*表$/ }).click();
    await expectSuccessMessage(page, '评论已发表');
    await expect(page.getByText(body, { exact: true })).toBeVisible();
    // 线程列表 commentCount 随 invalidateQueries 刷新为 1。
    await expect(item.getByText(/1\s*评论/)).toBeVisible();
  });

  test('Moderation：锁定线程进入 LOCKED，解锁恢复 OPEN', async ({ page }) => {
    await openDiscussionTab(page);
    const item = await createThread(page, `E2E 锁定线程 ${Date.now()}`);

    // canModerate(asset:moderate) → 行尾出现「锁定」按钮（Popconfirm 包裹）。
    await item.getByRole('button', { name: /锁\s*定/ }).click();
    const popconfirm = page.locator('.ant-popconfirm');
    await expect(popconfirm).toBeVisible();
    await expect(popconfirm).toContainText('确认锁定此讨论线程？');
    // zhCN Popconfirm 确认按钮为两字「确 定」——空格陷阱同登录按钮。
    await popconfirm.getByRole('button', { name: /确\s*定|OK/i }).click();
    await expectSuccessMessage(page, '线程已锁定');
    await expect(item.getByText('LOCKED', { exact: true })).toBeVisible();

    // LOCKED 后按钮切换为「解锁」（直接 mutation，无 Popconfirm）。
    await item.getByRole('button', { name: /解\s*锁/ }).click();
    await expectSuccessMessage(page, '线程已解锁');
    await expect(item.getByText('OPEN', { exact: true })).toBeVisible();
  });

  test('无 asset:discuss 的主体直连建线程 → 403（UI 非安全边界）', async () => {
    // DiscussionApplicationService.createThread L83 首行 requirePermission(ASSET_DISCUSS)；
    // E2E_ASSET_OUTSIDER 仅持有 asset:read（可读线程列表），POST 必须被后端独立拒绝。
    const status = await directApiPostThread(
      `/assets/${fixtures.assetId}/discussions`,
      { title: `should-not-exist-${Date.now()}` },
      E2E_ASSET_OUTSIDER.username,
      E2E_ASSET_OUTSIDER.password,
    );
    expect(status).toBe(403);
  });
});

/** 以指定主体 POST 建线程并返回状态码（directApiStatus 只支持 GET，写强制点需 POST）。 */
async function directApiPostThread(
  path: string,
  data: Record<string, string>,
  username: string,
  password: string,
): Promise<number> {
  const API_BASE = process.env.E2E_API_BASE_URL ?? 'http://localhost:8080/api/v1';
  const api = await request.newContext();
  try {
    const token = await loginAs(api, username, password);
    const response = await api.post(`${API_BASE}${path}`, {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data,
      failOnStatusCode: false,
    });
    return response.status();
  } finally {
    await api.dispose();
  }
}
