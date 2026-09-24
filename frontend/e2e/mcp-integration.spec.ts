/*
 * 功能: MCP / Agent 接入 E2E（PRD V12）——Integrations 页面渲染 agent-bundle 真实数据：
 *       MCP/OpenAPI 端点、Skill 模板、示例命令；导航项按 authorization:read 过滤；
 *       无该权限主体直连端点 403（UI 非安全边界）。
 *       断言以 directApiGet 取回的后端真值为基准比对 UI 渲染，
 *       不硬编码 publicBaseUrl（Compose 环境下 host 由 aihub.public-base-url 决定）。
 * 时间: 2026-09-24
 * 作者: AxeXie
 */
import { test, expect, request } from '@playwright/test';
import {
  adminToken,
  bootstrapCredentials,
  directApiGet,
  E2E_ASSET_OUTSIDER,
  E2E_MANAGER,
  login,
  openIntegrationsViaNav,
  prepareFixtures,
} from './support';

interface AgentBundle {
  mcpEndpointUrl?: string;
  openApiUrl?: string;
  skillTemplates?: { name: string; description: string; path: string }[];
  exampleCommands?: { label: string; command: string }[];
}

let credentials: { username: string; password: string } | null = null;
/** 后端真值基准：UI 断言全部与之比对，而非硬编码文案。 */
let bundle: AgentBundle;

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) {
    return;
  }
  const api = await request.newContext();
  try {
    // 夹具准备幂等创建 E2E_MANAGER / E2E_ASSET_OUTSIDER 等本 spec 依赖的主体。
    await prepareFixtures(await adminToken(api), api);
    // AgentBundleApplicationService.getAgentBundle 强制 authorization:read（E2E_MANAGER 具备）。
    const fetched = await directApiGet<AgentBundle>(
      '/integrations/agent-bundle',
      E2E_MANAGER.username,
      E2E_MANAGER.password,
    );
    expect(fetched.status, 'agent-bundle 端点应 200').toBe(200);
    bundle = fetched.data ?? {};
    // 后端契约下限：URL 后缀、3 个 Skill 模板与 Login 示例命令恒存在。
    expect(bundle.mcpEndpointUrl).toMatch(/\/api\/v1\/mcp$/);
    expect(bundle.openApiUrl).toMatch(/\/v3\/api-docs$/);
    expect((bundle.skillTemplates ?? []).map((s) => s.name)).toEqual(
      expect.arrayContaining(['asset-search', 'version-publish', 'upload-resume']),
    );
    expect(bundle.exampleCommands?.[0]?.command).toBe('aih login --username <user> --password <pass>');
  } finally {
    await api.dispose();
  }
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
});

test.describe('PRD V12: MCP 只读消费 E2E', () => {
  test('Agent 接入页展示连接信息（MCP 端点 / OpenAPI 文档为后端真值）', async ({ page }) => {
    await login(page, E2E_MANAGER.username, E2E_MANAGER.password);
    // 导航项出现本身即证明 authorization:read 作用域过滤放行该主体。
    await openIntegrationsViaNav(page);

    await expect(page.getByRole('heading', { name: 'Agent 接入' })).toBeVisible();
    await expect(page.getByText('MCP (Model Context Protocol) 接入状态总览')).toBeVisible();
    const connectionCard = page.locator('.ant-card').filter({ hasText: '连接信息' });
    await expect(connectionCard.getByText(bundle.mcpEndpointUrl!, { exact: true })).toBeVisible();
    await expect(connectionCard.getByText(bundle.openApiUrl!, { exact: true })).toBeVisible();
  });

  test('Skill 模板与示例命令来自 agent-bundle 真实数据', async ({ page }) => {
    await login(page, E2E_MANAGER.username, E2E_MANAGER.password);
    await openIntegrationsViaNav(page);

    const skillCard = page.locator('.ant-card').filter({ hasText: 'Skill 模板' });
    for (const name of ['asset-search', 'version-publish', 'upload-resume']) {
      await expect(skillCard.getByText(name, { exact: true })).toBeVisible();
    }

    const commandCard = page.locator('.ant-card').filter({ hasText: '示例命令' });
    await expect(commandCard.getByText('Login', { exact: true })).toBeVisible();
    // 命令文本含 <> 占位符：按后端真值精确比对，证明渲染未被截断或转义破坏。
    await expect(commandCard.getByText('aih login --username <user> --password <pass>', { exact: true })).toBeVisible();
  });

  test('无 authorization:read 的主体：导航项隐藏且直连端点 403', async ({ page }) => {
    // E2E_ASSET_OUTSIDER 仅 asset:read：navigation.ts 对 /integrations 声明
    // requiredScopes=['authorization:read']，前端必须隐藏入口；
    // 同时后端 getAgentBundle 必须独立拒绝（UI 非安全边界）。
    const denied = await directApiGet('/integrations/agent-bundle', E2E_ASSET_OUTSIDER.username, E2E_ASSET_OUTSIDER.password);
    expect(denied.status).toBe(403);

    await login(page, E2E_ASSET_OUTSIDER.username, E2E_ASSET_OUTSIDER.password);
    await expect(page.locator('.ant-menu-item').filter({ hasText: /Agent 接入/ })).toHaveCount(0);
  });
});
