/*
 * 功能: AC-P0B-AGT-001~005 E2E 测试——Agent 注册、列表、Tool 白名单与启停。
 * 时间: 2026-09-28
 * 作者: AxeXie
 *
 * <p>必须以引导管理员执行：前端路由守卫要求 {@code authorization:read}，后端
 * SystemAgentController 的注册要求 {@code agent:register}、启停与白名单要求
 * {@code agent:authorize}；夹具 E2E_MANAGER 只持有 authorization:read/manage，
 * 写操作必然 403，故只有引导管理员的 ADMIN_SCOPES 能同时贯通三层。
 * <p>注册成功不弹 message：AgentsPage 的 createAgent.onSuccess 只关闭表单并打开
 * 一次性凭据弹窗，因此该弹窗就是创建成功的唯一 UI 证据。凭据值本身不做断言，
 * 避免 Secret 进入测试报告。
 * <p>每个用例自建唯一 Agent（显示名含时间戳与 retry 序号），互不依赖，可单独重跑。
 */
import { test, expect, type Locator, type Page } from '@playwright/test';
import { bootstrapCredentials, loginBootstrap } from './support';

/** antd 对恰好两个汉字的按钮插入空格（「确 定」「禁 用」「启 用」），故一律用宽松正则。 */
const CONFIRM_OK = /确\s*定|OK/i;

let credentials: { username: string; password: string } | null = null;

test.beforeAll(() => {
  credentials = bootstrapCredentials();
});

/** 定位显示名对应的 Agent 行（显示名渲染为 strong 文本，agentId 渲染为 code）。 */
function agentRow(page: Page, displayName: string) {
  return page.locator('.ant-table-row').filter({ hasText: displayName });
}

/**
 * 在弹窗内按表单项标签定位控件容器。
 *
 * <p>不能用 {@code filter({ has: modal.locator('label') })}：Playwright 会把 has
 * 的基元素拼进 {@code :has()} 后代选择器，而 {@code .ant-modal-content} 是
 * {@code .ant-form-item} 的祖先，导致永不匹配（实测 30s 超时）。改用 hasText，
 * 标签文本只存在于对应 Form.Item 内，定位同样唯一。
 */
function formItem(scope: Page | Locator, label: RegExp) {
  return scope.locator('.ant-form-item').filter({ hasText: label }).first();
}

/**
 * 经真实 UI 旅程注册一个一次性 Agent，并关闭凭据弹窗。
 *
 * <p>返回的显示名对每次执行与每次 retry 都唯一，可在列表中稳定定位到该行。
 */
async function registerAgentViaUi(page: Page, tag: string): Promise<string> {
  const displayName = `E2E0928-Agent-${tag}-${Date.now().toString(36)}-${test.info().retry}`;

  await page.getByRole('button', { name: /注册 Agent|registerAgent/i }).click();
  const modal = page.locator('.ant-modal-content').filter({ hasText: /注册 Agent/ });
  await expect(modal).toBeVisible();
  await modal.getByLabel(/显示名|displayName/i).fill(displayName);
  // label 实际文案是「类型」（common.type），旧 spec 的 /Agent.*类型/ 永远匹配不到。
  await modal.getByLabel(/^类型$|agentType/i).fill('openclaw');
  await modal.getByLabel(/厂商|vendor/i).fill('OpenGeobot');
  // initialValues 已给出 0；显式改为 1 才能证明该字段真的写入并回显。
  await formItem(modal, /最大敏感级|maxSensitivityLevel/i).locator('input').fill('1');
  await modal.getByRole('button', { name: CONFIRM_OK }).click();

  const credentialModal = page
    .locator('.ant-modal-content')
    .filter({ hasText: /一次性 Agent 凭据/ });
  await expect(credentialModal).toBeVisible({ timeout: 15_000 });
  await expect(credentialModal).toContainText('凭据仅显示一次');
  await expect(credentialModal).toContainText('关闭后无法再次查看');

  await credentialModal.getByRole('button', { name: /我已妥善保存/ }).click();
  await expect(credentialModal).toBeHidden({ timeout: 10_000 });
  return displayName;
}

test.describe('AC-P0B-AGT-001~005: Agent 管理', () => {
  test.beforeEach(async ({ page }) => {
    test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
    await loginBootstrap(page);
    await page.goto('/admin/agents');
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 10_000 });
  });

  test('注册 Agent 后出现在列表且状态为 ACTIVE', async ({ page }) => {
    const displayName = await registerAgentViaUi(page, 'create');

    const row = agentRow(page, displayName);
    await expect(row).toBeVisible({ timeout: 10_000 });
    await expect(row.getByText('openclaw')).toBeVisible();
    await expect(row.getByText('OpenGeobot')).toBeVisible();
    await expect(row.getByText('ACTIVE')).toBeVisible();
    // AgentIdentity 落库为 ACTIVE，凭据哈希不外泄：列表任何位置都不应出现凭据明文。
    await expect(row.locator('code')).toBeVisible();
  });

  test('编辑 Tool 白名单后行内回显 Tool Tag', async ({ page }) => {
    const displayName = await registerAgentViaUi(page, 'allowlist');
    const row = agentRow(page, displayName);
    await expect(row).toBeVisible({ timeout: 10_000 });
    // 新注册 Agent 的白名单为空：先证明目标 Tool Tag 不存在，改写后的出现才有意义。
    await expect(row.locator('.ant-tag', { hasText: 'asset_search' })).toHaveCount(0);

    await row.getByRole('button', { name: /白名单|allowlist/i }).click();
    const modal = page.locator('.ant-modal-content').filter({ hasText: /编辑 Tool 白名单/ });
    await expect(modal).toBeVisible();
    // tags 模式：点击外层 .ant-select 聚焦内嵌搜索框后键入，再点选下拉里生成的选项。
    //（直接点 role=combobox 的 input 会被选中项遮挡，见 lineage.spec 实测说明。）
    const toolsItem = formItem(modal, /允许调用的 MCP Tool|allowedTools/i);
    await toolsItem.locator('.ant-select').click();
    await expect(page.locator('.ant-select-dropdown:visible')).toBeVisible();
    await page.keyboard.type('asset_search');
    // 不能用 keyboard.press('Enter')：探针三轮 --repeat-each=3 --retries=0 实测，
    // 此时 focus 已正确落在 INPUT#tools.ant-select-selection-search-input、input 值
    // 确为 asset_search，但 Enter 后 .ant-select-selection-item 数量恒为 0
    //（rc-select 在 tags 模式下新建选项未处于 active 态时会吞掉 Enter）。
    // 下拉里渲染出的可点选项实测稳定存在（OPTIONS=1），点选后 Tag 必定生成。
    const createdOption = page
      .locator('.ant-select-dropdown:visible .ant-select-item-option')
      .filter({ hasText: 'asset_search' });
    await expect(createdOption).toBeVisible();
    await createdOption.click();
    await expect(toolsItem.locator('.ant-select-selection-item', { hasText: 'asset_search' })).toBeVisible();
    // tags 模式点选成 Tag 后面板不会自动收起，展开中的 .ant-select-dropdown 会拦截
    // 「确 定」按钮的 pointer events（实测 49 次重试后 30s 超时），必须先收起面板。
    await page.keyboard.press('Escape');
    await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0);
    await modal.getByRole('button', { name: CONFIRM_OK }).click();

    await expect(page.locator('.ant-message').getByText(/白名单已更新/)).toBeVisible({
      timeout: 15_000,
    });
    await expect(modal).toBeHidden({ timeout: 10_000 });
    await expect(agentRow(page, displayName).getByText('asset_search')).toBeVisible({
      timeout: 10_000,
    });
  });

  test('禁用 Agent 吊销凭据后启用恢复 ACTIVE', async ({ page }) => {
    const displayName = await registerAgentViaUi(page, 'toggle');
    const row = agentRow(page, displayName);
    await expect(row).toBeVisible({ timeout: 10_000 });

    await row.getByRole('button', { name: /禁\s*用|disable/i }).click();
    const popconfirm = page.locator('.ant-popconfirm');
    await expect(popconfirm).toBeVisible();
    // 断言确认文案，证明「凭据与 Token 将被吊销」的告知语义存在于 UI。
    await expect(popconfirm).toContainText(/凭据与 Token 将被吊销/);
    await popconfirm.getByRole('button', { name: CONFIRM_OK }).click();

    await expect(page.locator('.ant-message').getByText(/已禁用|disabled/i)).toBeVisible({
      timeout: 15_000,
    });
    const disabledRow = agentRow(page, displayName);
    await expect(disabledRow.getByText('DISABLED')).toBeVisible({ timeout: 10_000 });
    // DISABLED 后行内按钮切换为「启用」，不再有禁用入口。
    await expect(disabledRow.getByRole('button', { name: /禁\s*用|disable/i })).toHaveCount(0);

    await disabledRow.getByRole('button', { name: /启\s*用|enable/i }).click();
    await expect(page.locator('.ant-message').getByText(/已启用|enabled/i)).toBeVisible({
      timeout: 15_000,
    });
    await expect(agentRow(page, displayName).getByText('ACTIVE')).toBeVisible({ timeout: 10_000 });
  });

  test('必填项缺失时前端拦截注册请求', async ({ page }) => {
    await page.getByRole('button', { name: /注册 Agent|registerAgent/i }).click();
    const modal = page.locator('.ant-modal-content').filter({ hasText: /注册 Agent/ });
    await expect(modal).toBeVisible();
    await modal.getByRole('button', { name: CONFIRM_OK }).click();

    // displayName 与 agentType 均为 required，未填写时不得发出写请求：弹窗必须仍打开。
    await expect(modal.locator('.ant-form-item-explain-error').first()).toBeVisible();
    await expect(modal).toBeVisible();
    await modal.getByRole('button', { name: /取\s*消|Cancel/i }).click();
    await expect(modal).toBeHidden({ timeout: 10_000 });
  });
});
