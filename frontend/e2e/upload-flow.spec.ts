/*
 * 功能: E4 上传中心全旅程 E2E（TASK-P0BR-069 §5，UI-006）——真实 Compose 栈上验证
 *       空态引导 → 创建会话 → 分片上传（含真实失败与重试）→ sessionId 刷新对账
 *       （服务端数据驱动 + 无本地文件诚实阻断）→ UI Complete 真实成功
 *       （服务端 MinIO multipart ETag 对账）→ 服务端 parts/files 入库复核 → 取消终态。
 * 时间: 2026-09-29
 * 作者: AxeXie
 *
 * 桩的诚实边界（务必与卡 §3 一并阅读）：
 *   compose 栈中后端 presign 的 endpointOverride 取 minio.endpoint=http://minio:9000，
 *   该主机名在宿主机浏览器不可达；而 MinioProperties.externalEndpoint 只被
 *   DvcConfigurationService 消费、presign 路径不使用，且 compose 注入的是
 *   AIHUB_MINIO_PUBLIC_ENDPOINT、application.yml 读的是 AIHUB_MINIO_EXTERNAL_ENDPOINT
 *   （变量名不匹配 ⇒ 实际为空）。因此浏览器直达 presign URL 的真实 PUT 必然失败，
 *   该缺口属 REQ-UPL-002 实现/配置缺陷，移交 TASK-P0BR-070 契约优先修复。
 *   本 spec 用 page.route 桩替代「浏览器不可达的最后一段网络」：桩响应形态与
 *   真实 S3 网关一致（200 + 带引号 ETag 头 + CORS 头 / 500 + XML 错误体），
 *   且回写的 ETag 不是任意值，而是容器内 curl 对同一 uploadId/partNumber 真实
 *   PUT 后 MinIO 返回的 ETag。故服务端 completeMultipartUpload 的 ETag 对账是
 *   真实的——若字节未真实入存，UI「完成上传」必然失败，桩无法掩盖。
 */
import { test, expect, request, type APIRequestContext, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, mkdtempSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  adminToken,
  bootstrapCredentials,
  E2E_AUTHOR,
  ensureAssetVersion,
  login,
  loginAs,
  prepareFixtures,
  type E2eFixtures,
} from './support';

const __filename = fileURLToPath(import.meta.url);
const __dirnameE2e = dirname(__filename);

const COMPOSE_DIR = resolve(__dirnameE2e, '..', '..', 'deploy', 'compose');
const API_BASE = process.env.E2E_API_BASE_URL ?? 'http://localhost:8080/api/v1';

/** 独立 DATASET 夹具坐标（不复用共享资产，避免跨 spec 会话/版本状态污染）。 */
const UPLOAD_ASSET = {
  namespace: 'nlp',
  name: 'e2e069-upload-target',
  displayName: 'E2E 069 上传旅程目标',
  format: 'PARQUET',
  modality: 'TEXT',
} as const;
const UPLOAD_VERSION = '4.0.0';

const PART_SIZE = 5 * 1024 * 1024;
const FILE_NAME = 'e2e069-multi.bin';

/* ------------------------------------------------------------------ */
/* 载荷：6 MiB 确定性字节 → 恰好 2 个 Part（5 MiB + 1 MiB 尾片）         */
/* ------------------------------------------------------------------ */

function makePayload(): Buffer {
  const buf = Buffer.alloc(PART_SIZE + 1024 * 1024);
  for (let i = 0; i < buf.length; i += 1) {
    buf[i] = (i * 31 + 7) & 0xff;
  }
  return buf;
}

const PAYLOAD = makePayload();
const PAYLOAD_SHA256 = createHash('sha256').update(PAYLOAD).digest('hex');

/* ------------------------------------------------------------------ */
/* 内部辅助                                                            */
/* ------------------------------------------------------------------ */

function bearer(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

function dataOf(body: unknown): unknown {
  if (body && typeof body === 'object' && 'data' in body) {
    return (body as { data: unknown }).data;
  }
  return null;
}

async function readJson(resp: { json: () => Promise<unknown> }): Promise<unknown> {
  return resp.json().catch(() => null);
}

async function loginApi(api: APIRequestContext, username: string, password: string): Promise<string> {
  const resp = await api.post(`${API_BASE}/auth/login`, {
    data: { username, password },
    failOnStatusCode: false,
  });
  const data = dataOf(await readJson(resp)) as { accessToken?: string } | null;
  expect(data?.accessToken, `登录 ${username} 失败（HTTP ${resp.status()}）`).toBeTruthy();
  return data!.accessToken!;
}

/** 在 compose 目录下执行 docker compose 子命令，返回 stdout。 */
function dockerCompose(args: string[], stdin?: string): string {
  try {
    return execFileSync('docker', ['compose', ...args], {
      cwd: COMPOSE_DIR,
      encoding: 'utf8',
      input: stdin ?? '',
      timeout: 120_000,
      maxBuffer: 10 * 1024 * 1024,
    });
  } catch (e: unknown) {
    const err = e as { stdout?: string; stderr?: string; message?: string };
    throw new Error(
      `docker compose ${args.join(' ')} 失败:\n${err.stderr ?? err.message ?? ''}\n${err.stdout ?? ''}`,
    );
  }
}

/** presign URL 的 host 在宿主机不可达，必须在 minio 容器内发起真实 PUT；返回真实 ETag。 */
function putPartViaContainer(presignUrl: string, localPath: string): string {
  dockerCompose(['cp', localPath, 'minio:/tmp/e2e069_put.bin']);
  const raw = dockerCompose([
    'exec',
    '-T',
    'minio',
    'sh',
    '-c',
    `curl -s -D /tmp/e2e069_resp.hdr -o /dev/null -T /tmp/e2e069_put.bin '${presignUrl}'; cat /tmp/e2e069_resp.hdr`,
  ]);
  const headers = raw.replace(/\r/g, '');
  const match = headers.match(/^etag:\s*"?([0-9a-fA-F]+)"?/im);
  expect(match, `staging 分片 PUT 未返回 ETag，响应头：${headers.slice(0, 400)}`).toBeTruthy();
  return match![1].toLowerCase();
}

/** 逐 Part 走真实 API presign（服务端落 upload_part 行）+ 容器真实 PUT，收集服务端 ETag。 */
async function stageRealParts(
  api: APIRequestContext,
  token: string,
  sessionId: string,
  perPartFile: Map<number, string>,
): Promise<Map<number, string>> {
  const etags = new Map<number, string>();
  for (const [partNumber, filePath] of perPartFile) {
    const presignResp = await api.get(
      `${API_BASE}/upload-sessions/${sessionId}/parts/${partNumber}/presign`,
      { headers: bearer(token), failOnStatusCode: false },
    );
    const presignData = dataOf(await readJson(presignResp)) as { url?: string } | null;
    expect(presignData?.url, `Part ${partNumber} presign 失败 HTTP ${presignResp.status()}`).toBeTruthy();
    etags.set(partNumber, putPartViaContainer(presignData!.url!, filePath));
  }
  return etags;
}

const CORS_HEADERS: Record<string, string> = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'PUT, GET, HEAD',
  'Access-Control-Allow-Headers': '*',
  // ETag 不是 CORS safelisted 响应头：不显式 expose 则跨源 XHR getResponseHeader('ETag') 为 null
  'Access-Control-Expose-Headers': 'ETag',
};

/**
 * 浏览器 PUT 桩：替代不可达的最后一段网络，响应形态与真实 S3 网关一致。
 * failCounts 按 Part 计次消耗——UploadPage.presignAndUploadPart 内置一次自动重试，
 * 故置 2 才会让该 Part 真正进入 failed（首轮 + 自动重试各失败一次）。
 */
async function installStagingPutStub(
  page: Page,
  etags: Map<number, string>,
  failCounts: Map<number, number>,
  calls: number[],
): Promise<void> {
  await page.route(
    (url) => url.hostname === 'minio' && url.pathname.startsWith('/asset-staging/'),
    async (route) => {
      const req = route.request();
      // 跨源 PUT 必发预检；真实公开端点同样需要 CORS，否则浏览器直传根本不成立
      if (req.method() === 'OPTIONS') {
        await route.fulfill({ status: 204, headers: CORS_HEADERS });
        return;
      }
      if (req.method() !== 'PUT') {
        await route.fallback();
        return;
      }
      const partParam = new URL(req.url()).searchParams.get('partNumber');
      expect(partParam, 'presign URL 应携带 partNumber 查询参数').toBeTruthy();
      const partNumber = Number(partParam);
      calls.push(partNumber);

      const remaining = failCounts.get(partNumber) ?? 0;
      if (remaining > 0) {
        failCounts.set(partNumber, remaining - 1);
        await route.fulfill({
          status: 500,
          headers: { ...CORS_HEADERS, 'Content-Type': 'application/xml' },
          body:
            '<?xml version="1.0" encoding="UTF-8"?><Error><Code>InternalError</Code>' +
            '<Message>We encountered an internal error, please try again.</Message></Error>',
        });
        return;
      }

      const etag = etags.get(partNumber);
      expect(etag, `桩缺少 Part ${partNumber} 的服务端 ETag，无法同值回写`).toBeTruthy();
      await route.fulfill({ status: 200, headers: { ...CORS_HEADERS, ETag: `"${etag}"` } });
    },
  );
}

const startUploadButton = (page: Page) => page.getByRole('button', { name: /开始上传|Start Upload/ });
const completeUploadButton = (page: Page) => page.getByRole('button', { name: /完成上传|Complete Upload/ });
const partDetailsCard = (page: Page, completed: number, total: number) =>
  page.locator('.ant-card').filter({ hasText: new RegExp(`分片详情 \\(${completed}/${total}\\)`) });
const partTag = (page: Page, status: string) =>
  page.locator('.ant-tag').filter({ hasText: new RegExp(`^${status}$`) });

/** 泄漏红线：页面文本不得出现对象存储地址、bucket、presign/SigV4 细节。 */
async function expectNoStorageLeaks(page: Page): Promise<void> {
  const bodyText = await page.locator('body').innerText();
  expect(bodyText).not.toContain('minio:9000');
  expect(bodyText).not.toContain('asset-staging');
  expect(bodyText).not.toContain('X-Amz-');
  expect(bodyText).not.toContain('AWS4-HMAC-SHA256');
}

/** 幂等创建独立 DATASET 夹具资产（201 直取，否则按坐标查回）。 */
async function ensureUploadAsset(
  api: APIRequestContext,
  authorToken: string,
  organizationId: string,
  teamId: string,
): Promise<string> {
  const created = await api.post(`${API_BASE}/assets`, {
    headers: { ...bearer(authorToken), 'Idempotency-Key': `idmp_e2e069_asset_${UPLOAD_ASSET.name}` },
    data: {
      type: 'DATASET',
      namespace: UPLOAD_ASSET.namespace,
      name: UPLOAD_ASSET.name,
      displayName: UPLOAD_ASSET.displayName,
      visibility: 'INTERNAL',
      organizationId,
      ownerTeamId: teamId,
      dataset: { format: UPLOAD_ASSET.format, modality: UPLOAD_ASSET.modality },
    },
    failOnStatusCode: false,
  });
  const createdData = dataOf(await readJson(created)) as { assetId?: string } | null;
  if (created.status() === 201 && createdData?.assetId) {
    return createdData.assetId;
  }
  const listed = await api.get(`${API_BASE}/assets`, {
    headers: bearer(authorToken),
    params: { namespace: UPLOAD_ASSET.namespace, keyword: UPLOAD_ASSET.name, type: 'DATASET', size: 50 },
    failOnStatusCode: false,
  });
  const page = dataOf(await readJson(listed)) as { items?: { assetId: string; name: string }[] } | null;
  const match = page?.items?.find((it) => it.name === UPLOAD_ASSET.name);
  expect(match, `夹具资产 ${UPLOAD_ASSET.name} 无法创建（HTTP ${created.status()}）也无法查回`).toBeTruthy();
  return match!.assetId;
}

/* ------------------------------------------------------------------ */
/* 测试状态                                                            */
/* ------------------------------------------------------------------ */

let credentials: { username: string; password: string } | null = null;
let dockerAvailable = false;
let fixtures: E2eFixtures & { uploadAssetId: string; versionId: string } | null = null;
/** 容器真实 PUT 后收集的服务端 ETag：桩同值回写，使 complete 对账真实成立。 */
const realEtags = new Map<number, string>();

/*
 * 切片间共享的旅程状态。同文件按声明顺序串行（workers=1、fullyParallel=false），
 * 但 Playwright 在失败重试用例时会重启 worker 进程、模块内存态丢失，
 * 故 sessionId + ETag 落盘到系统临时目录（不入库）。若存在上次运行的陈旧状态，
 * 后续切片的 UI/服务端断言会如实失败暴露，绝不静默跳过。
 */
interface JourneyState {
  sessionId: string;
  etags: Record<string, string>;
}
const JOURNEY_STATE_FILE = join(tmpdir(), 'e2e069-upload-journey.json');

function saveJourneyState(sessionId: string, etags: Map<number, string>): void {
  const state: JourneyState = {
    sessionId,
    etags: Object.fromEntries([...etags].map(([k, v]) => [String(k), v])),
  };
  writeFileSync(JOURNEY_STATE_FILE, JSON.stringify(state));
}

function loadJourneyState(): JourneyState | null {
  if (!existsSync(JOURNEY_STATE_FILE)) return null;
  try {
    const parsed = JSON.parse(readFileSync(JOURNEY_STATE_FILE, 'utf8')) as JourneyState;
    if (!parsed?.sessionId) return null;
    realEtags.clear();
    for (const [k, v] of Object.entries(parsed.etags ?? {})) realEtags.set(Number(k), v);
    return parsed;
  } catch {
    return null;
  }
}

/** ⑤⑥ 前置：读取切片②建立的会话；缺失即如实失败（skip 会掩盖断链）。 */
function requireJourney(): JourneyState {
  const state = loadJourneyState();
  expect(state, '前置切片②未建立会话（journey 状态缺失），请先保证切片②通过').toBeTruthy();
  return state!;
}

/** ⑤⑥ 前置：服务端会话必须仍为 OPEN——陈旧/已终态会话会在这里如实暴露。 */
async function expectServerSessionOpen(sessionId: string): Promise<void> {
  const api = await request.newContext();
  try {
    const token = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
    const resp = await api.get(`${API_BASE}/upload-sessions/${sessionId}`, {
      headers: bearer(token),
      failOnStatusCode: false,
    });
    expect(resp.status(), `旅程会话 ${sessionId} 查询返回 HTTP ${resp.status()}`).toBe(200);
    const sess = dataOf(await readJson(resp)) as { status?: string } | null;
    expect(
      sess?.status,
      `旅程会话应为 OPEN（陈旧状态或其他运行污染会在此暴露，实际：${sess?.status}）`,
    ).toBe('OPEN');
  } finally {
    await api.dispose();
  }
}

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) return;

  try {
    execFileSync('docker', ['compose', 'version'], { cwd: COMPOSE_DIR, stdio: 'pipe', timeout: 10_000 });
    dockerAvailable = true;
  } catch {
    dockerAvailable = false;
    return;
  }

  const api = await request.newContext();
  try {
    const base = await prepareFixtures(await adminToken(api), api);
    const authorToken = await loginAs(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
    const uploadAssetId = await ensureUploadAsset(api, authorToken, base.organizationId, base.teamId);
    const versionId = await ensureAssetVersion(api, authorToken, uploadAssetId, UPLOAD_VERSION);
    fixtures = { ...base, uploadAssetId, versionId };
  } finally {
    await api.dispose();
  }
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
  test.skip(!dockerAvailable, 'docker 不可用，跳过 E4 上传全旅程');
  test.skip(!fixtures, '夹具准备失败');
});

test.describe('上传中心全旅程（TASK-P0BR-069 UI-006）', () => {
  /* ---- 切片 ①：无参深链——诚实空态 ---- */
  test('① 无 assetId/versionId 时渲染深链引导空态', async ({ page }) => {
    test.setTimeout(120_000);
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await page.goto('/upload');

    const main = page.getByRole('main');
    await expect(main.getByText('上传中心', { exact: true }).first()).toBeVisible();
    await expect(
      page.getByText(/请从资产详情页进入，或携带 \?assetId=&versionId= 查询参数/),
    ).toBeVisible();
    await expect(page.getByRole('link', { name: /资产目录|Asset Catalog/ })).toBeVisible();
    // 空态不得渲染任何会话或分片结构
    await expect(page.getByText(/分片详情/)).toHaveCount(0);
    await expect(page.getByText('会话信息')).toHaveCount(0);
  });

  /* ---- 切片 ②③④：创建会话 → Part 真实失败 + 自动重试耗尽 → 手动重试成功 ---- */
  test('② 创建会话渲染 2 分片；④ 失败标记与重试；③ 全部分片完成放开 Complete', async ({ page }) => {
    test.setTimeout(300_000);
    const { uploadAssetId, versionId } = fixtures!;
    // 清理上一轮运行的陈旧旅程状态，防止 ⑤⑥ 误读已失效会话
    if (existsSync(JOURNEY_STATE_FILE)) unlinkSync(JOURNEY_STATE_FILE);
    realEtags.clear();

    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await page.goto(`/upload?assetId=${uploadAssetId}&versionId=${versionId}`);

    // 选择 6 MiB 文件 → 2 个 Part
    await page.locator('input[type="file"]').first().setInputFiles({
      name: FILE_NAME,
      mimeType: 'application/octet-stream',
      buffer: PAYLOAD,
    });
    await expect(page.getByText('1 个文件')).toBeVisible();
    await expect(page.getByText('共 6.0 MB')).toBeVisible();

    // 创建会话：断言真实 POST 响应体（服务端入库的 sessionId/totalBytes）
    const [createdResp] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/upload-sessions') && r.request().method() === 'POST',
        { timeout: 30_000 },
      ),
      page.getByRole('button', { name: /创建上传会话/ }).click(),
    ]);
    expect(createdResp.status(), `创建会话端点返回 HTTP ${createdResp.status()}`).toBeLessThan(300);
    const created = dataOf(await createdResp.json()) as {
      sessionId?: string;
      status?: string;
      totalBytes?: number;
      fileCount?: number;
    } | null;
    expect(created?.sessionId, '创建会话响应缺少 sessionId').toBeTruthy();
    const sessionId = created!.sessionId!;
    expect(created?.status, '新建会话应为 OPEN').toBe('OPEN');
    expect(created?.totalBytes, '服务端 totalBytes 应为 6 MiB').toBe(PAYLOAD.length);
    expect(created?.fileCount, '服务端 fileCount 应为 1').toBe(1);

    await expect(page.getByText(/上传会话已创建/)).toBeVisible();
    await expect(page.getByText('会话信息')).toBeVisible();
    // sessionId 同时渲染在 <code> 与消息文案中，精确匹配避免 strict-mode 冲突
    await expect(page.getByText(sessionId, { exact: true })).toBeVisible();
    await expect(partDetailsCard(page, 0, 2)).toBeVisible();

    // 容器真实 PUT 逐 Part 入存，收集服务端 ETag 供桩同值回写
    const api = await request.newContext();
    const authorToken = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
    const sliceDir = mkdtempSync(join(tmpdir(), 'e2e069-upload-'));
    const perPartFile = new Map<number, string>([
      [1, join(sliceDir, 'part1.bin')],
      [2, join(sliceDir, 'part2.bin')],
    ]);
    writeFileSync(perPartFile.get(1)!, PAYLOAD.subarray(0, PART_SIZE));
    writeFileSync(perPartFile.get(2)!, PAYLOAD.subarray(PART_SIZE));
    for (const [k, v] of await stageRealParts(api, authorToken, sessionId, perPartFile)) {
      realEtags.set(k, v);
    }
    // 两个分片字节不同 ⇒ 服务端 ETag 必不同（防桩退化）
    expect(realEtags.get(1)).toBeTruthy();
    expect(realEtags.get(2)).toBeTruthy();
    expect(realEtags.get(1)).not.toBe(realEtags.get(2));
    saveJourneyState(sessionId, realEtags);

    // 桩：Part 2 连续失败 2 次（首轮 + 内置自动重试各一次），之后转成功
    const calls: number[] = [];
    await installStagingPutStub(page, realEtags, new Map([[2, 2]]), calls);

    await startUploadButton(page).click();
    // Part 1 completed、Part 2 failed
    await expect(partTag(page, 'failed').first()).toBeVisible({ timeout: 90_000 });
    await expect(partTag(page, 'completed')).toHaveCount(1);
    await expect(partTag(page, 'failed')).toHaveCount(1);
    // 内置自动重试确实发生：Part 2 至少被打两次
    expect(
      calls.filter((n) => n === 2).length,
      'Part 2 应经历 presignAndUploadPart 内置自动重试',
    ).toBeGreaterThanOrEqual(2);
    // 失败分片出现行内「重试」；未全部分片完成时「完成上传」门控不得放开
    await expect(page.getByRole('button', { name: /^重\s*试$/ })).toBeVisible();
    await expect(completeUploadButton(page)).toHaveCount(0);
    await expect(partDetailsCard(page, 1, 2)).toBeVisible();

    // 手动重试 Part 2：桩转成功 → 2/2 → allPartsComplete → Complete 门控放开
    await page.getByRole('button', { name: /重试 Part 2/ }).click();
    await expect(page.getByText(/所有分片上传完成/)).toBeVisible({ timeout: 90_000 });
    await expect(partTag(page, 'completed')).toHaveCount(2);
    await expect(partDetailsCard(page, 2, 2)).toBeVisible();
    await expect(completeUploadButton(page)).toBeVisible();

    await expectNoStorageLeaks(page);
    await api.dispose();
  });

  /* ---- 切片 ⑤：sessionId 刷新——服务端数据驱动对账 + 无文件诚实阻断 ---- */
  test('⑤ 刷新恢复：服务端驱动分片视图、无本地进度、无文件阻断续传', async ({ page }) => {
    test.setTimeout(180_000);
    const { uploadAssetId, versionId } = fixtures!;
    const journey = requireJourney();
    await expectServerSessionOpen(journey.sessionId);

    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await page.goto(
      `/upload?assetId=${uploadAssetId}&versionId=${versionId}&sessionId=${journey.sessionId}`,
    );

    // StrictMode 下恢复 effect 可能双执行产生两条消息，断言取 first
    await expect(page.getByText(/已恢复上传会话/).first()).toBeVisible();
    await expect(page.getByText('会话信息')).toBeVisible();
    await expect(page.getByText(journey.sessionId, { exact: true })).toBeVisible();
    // 服务端 upload_part.etag 仅在 complete→markUploaded 后入库，故恢复视图分片为 PENDING，
    // 上一轮浏览器内存里的进度不残留（对账由服务端数据驱动，而非本地假设）。
    await expect(partDetailsCard(page, 0, 2)).toBeVisible();
    await expect(partTag(page, 'completed')).toHaveCount(0);
    // OPEN 态提供「重新选择本地文件」入口
    await expect(page.getByText(/重新选择本地文件以继续上传/)).toBeVisible();

    // 无本地文件点击开始上传：诚实阻断，不得伪造字节续传
    await startUploadButton(page).click();
    await expect(page.getByText(/请先选择本地文件后再继续上传/)).toBeVisible();
    await expectNoStorageLeaks(page);
  });

  /* ---- 切片 ⑥：重选文件 → 续传 → UI Complete 真实成功 → 服务端复核 ---- */
  test('⑥ Complete 真实成功：服务端 MinIO ETag 对账通过、PROCESSING + materializeJobId', async ({ page }) => {
    test.setTimeout(300_000);
    const { uploadAssetId, versionId } = fixtures!;
    const journey = requireJourney();
    expect(realEtags.get(1) && realEtags.get(2), '前置切片未收集服务端 ETag').toBeTruthy();
    await expectServerSessionOpen(journey.sessionId);

    // 桩：无失败计划，同值回写服务端 ETag
    await installStagingPutStub(page, realEtags, new Map(), []);

    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await page.goto(
      `/upload?assetId=${uploadAssetId}&versionId=${versionId}&sessionId=${journey.sessionId}`,
    );
    await expect(partDetailsCard(page, 0, 2)).toBeVisible();

    // 重选同一文件 → 续传 2 个 Part
    await page.locator('input[type="file"]').first().setInputFiles({
      name: FILE_NAME,
      mimeType: 'application/octet-stream',
      buffer: PAYLOAD,
    });
    await startUploadButton(page).click();
    await expect(partDetailsCard(page, 2, 2)).toBeVisible({ timeout: 90_000 });

    // UI Complete：真实 POST complete——服务端以 parts ETag 向 MinIO 对账，字节未真实入存必失败
    const [completeResp] = await Promise.all([
      page.waitForResponse((r) => r.url().includes(`/upload-sessions/${journey.sessionId}/complete`), {
        timeout: 90_000,
      }),
      completeUploadButton(page).click(),
    ]);
    const rawBody = await completeResp.text();
    expect(
      completeResp.status(),
      `complete 返回 HTTP ${completeResp.status()}：${rawBody.slice(0, 300)}`,
    ).toBeLessThan(300);
    const completed = dataOf(JSON.parse(rawBody) as unknown) as {
      status?: string;
      materializeJobId?: string | null;
    } | null;
    // 后端契约：complete 返回 from(session, jobId)；GET 复查恒 null，不可混用同一路径断言
    expect(completed?.status, 'complete 响应 status 应为 PROCESSING').toBe('PROCESSING');
    expect(
      completed?.materializeJobId,
      'complete 响应应携带 materializeJobId（物化 Job 已进持久队列）',
    ).toBeTruthy();

    await expect(page.getByText(/上传已完成，等待物化 Worker 处理/)).toBeVisible();
    await expectNoStorageLeaks(page);

    // 服务端复查：parts COMPLETED + 真实 ETag；files 含用户文件 COMPLETED + 真实 sha256
    const api = await request.newContext();
    try {
      const authorToken = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
      const resp = await api.get(`${API_BASE}/upload-sessions/${journey.sessionId}`, {
        headers: bearer(authorToken),
        failOnStatusCode: false,
      });
      expect(resp.status(), `会话复查返回 HTTP ${resp.status()}`).toBe(200);
      const sess = dataOf(await readJson(resp)) as {
        status?: string;
        materializeJobId?: string | null;
        parts?: { partNumber: number; etag?: string; status?: string }[];
        files?: { path: string; sha256?: string; size?: number; status?: string }[];
      } | null;
      expect(sess?.status, '服务端会话应为 PROCESSING').toBe('PROCESSING');
      // GET 视图恒不带 materializeJobId（toViewWithParts 传 null）：如实记录，不据此断言缺失
      expect(sess?.materializeJobId ?? null, 'GET 视图不应携带 materializeJobId').toBeNull();
      for (const partNumber of [1, 2]) {
        const part = sess?.parts?.find((p) => p.partNumber === partNumber);
        expect(part?.status, `Part ${partNumber} 服务端应为 COMPLETED`).toBe('COMPLETED');
        expect(
          part?.etag?.replace(/"/g, '').toLowerCase(),
          `Part ${partNumber} 服务端 ETag 应与容器真实 PUT 的 ETag 一致`,
        ).toBe(realEtags.get(partNumber));
      }
      const file = sess?.files?.find((f) => f.path === FILE_NAME);
      expect(file, `服务端 files 应包含用户文件 ${FILE_NAME}（内部 bundle 行不外露）`).toBeTruthy();
      expect(file!.status, '用户文件应入库为 COMPLETED').toBe('COMPLETED');
      expect(file!.sha256, '服务端 sha256 应与前端 Web Crypto 计算的真实摘要一致').toBe(PAYLOAD_SHA256);
      expect(file!.size, '服务端 size 应与真实字节数一致').toBe(PAYLOAD.length);
      // 内部 bundle 占位行不得出现在 files 视图
      expect(
        sess?.files?.some((f) => f.path === '_session_bundle'),
        '内部 _session_bundle 行不应暴露在 files 视图',
      ).toBe(false);
    } finally {
      await api.dispose();
    }
  });

  /* ---- 切片 ⑦：取消——真实 cancel 端点 + 回创建空态 + 服务端 CANCELLED ---- */
  test('⑦ 取消会话：回到创建空态且服务端终态为 CANCELLED', async ({ page }) => {
    test.setTimeout(180_000);
    const { uploadAssetId, versionId } = fixtures!;

    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await page.goto(`/upload?assetId=${uploadAssetId}&versionId=${versionId}`);

    await page.locator('input[type="file"]').first().setInputFiles({
      name: 'e2e069-cancel.bin',
      mimeType: 'application/octet-stream',
      buffer: Buffer.alloc(1024, 0xab),
    });
    const [createdResp] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/upload-sessions') && r.request().method() === 'POST',
        { timeout: 30_000 },
      ),
      page.getByRole('button', { name: /创建上传会话/ }).click(),
    ]);
    const created = dataOf(await createdResp.json()) as { sessionId?: string } | null;
    expect(created?.sessionId, '取消旅程的前置会话创建失败').toBeTruthy();
    const cancelSessionId = created!.sessionId!;
    await expect(partDetailsCard(page, 0, 1)).toBeVisible();

    const [cancelResp] = await Promise.all([
      page.waitForResponse((r) => r.url().includes(`/upload-sessions/${cancelSessionId}/cancel`), {
        timeout: 30_000,
      }),
      page.getByRole('button', { name: /^取\s*消$/ }).click(),
    ]);
    const cancelBody = await cancelResp.text();
    expect(cancelResp.status(), `cancel 端点返回 HTTP ${cancelResp.status()}：${cancelBody.slice(0, 300)}`).toBeLessThan(300);
    const cancelled = dataOf(JSON.parse(cancelBody) as unknown) as { status?: string } | null;
    expect(cancelled?.status, 'cancel 响应应返回 CANCELLED 终态').toBe('CANCELLED');

    // UI 回到创建空态
    await expect(page.getByText(/上传已取消/)).toBeVisible();
    await expect(page.getByText('会话信息')).toHaveCount(0);
    await expect(page.getByText(/分片详情/)).toHaveCount(0);
    await expect(page.getByRole('button', { name: /创建上传会话/ })).toBeVisible();

    // 服务端复查：终态不可逆
    const api = await request.newContext();
    try {
      const authorToken = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
      const resp = await api.get(`${API_BASE}/upload-sessions/${cancelSessionId}`, {
        headers: bearer(authorToken),
        failOnStatusCode: false,
      });
      expect(resp.status(), `取消会话复查返回 HTTP ${resp.status()}`).toBe(200);
      const sess = dataOf(await readJson(resp)) as { status?: string } | null;
      expect(sess?.status, '取消后的服务端会话应为 CANCELLED').toBe('CANCELLED');

      // 终态会话再取 presign 必须被拒（ensureSessionOpen fail-closed）
      const presign = await api.get(`${API_BASE}/upload-sessions/${cancelSessionId}/parts/1/presign`, {
        headers: bearer(authorToken),
        failOnStatusCode: false,
      });
      expect(presign.status(), 'CANCELLED 会话不应再签发 presign').toBeGreaterThanOrEqual(400);
    } finally {
      await api.dispose();
    }
    await expectNoStorageLeaks(page);
  });
});
