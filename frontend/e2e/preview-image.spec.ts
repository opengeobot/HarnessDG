/*
 * 功能: E4 图片预览闭环 E2E（TASK-P0BR-068）——通过真实 Compose 栈走完
 *       上传会话 → staging PUT → complete → UPLOAD_MATERIALIZE → VALIDATING →
 *       PENDING_REVIEW → transition PUBLISHED → dvc-cache SigV4 注入 →
 *       PREVIEW_GENERATE → artifact 渲染 → UI img 可见 的端到端链；
 *       断言前端不泄漏对象存储地址、预签名 Token 或 bucket 名。
 *       dvc-cache 注入说明：后端业务链无 dvc-cache 写入口（DVC Worker 独立进程），
 *       编排层通过 docker compose exec 在 worker 容器内用标准库 SigV4 PUT 注入，
 *       注入字节与上传字节完全相同，故 sha256/size/ETag 天然一致。
 * 时间: 2026-09-29
 * 作者: AxeXie
 */
import { test, expect, request, type APIRequestContext } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  adminToken,
  bootstrapCredentials,
  directApiGet,
  E2E_AUTHOR,
  E2E_AUTHZ_READER,
  ensureAssetVersion,
  login,
  loginAs,
  openAssetDetailViaCatalog,
  prepareFixtures,
  type E2eFixtures,
} from './support';

const __filename = fileURLToPath(import.meta.url);
const __dirnameE2e = dirname(__filename);

// compose 目录：相对 spec 路径定位到仓库 deploy/compose
const COMPOSE_DIR = resolve(__dirnameE2e, '..', '..', 'deploy', 'compose');
const FIXTURE_PNG = resolve(__dirnameE2e, 'fixtures', 'preview-sample.png');
const API_BASE = process.env.E2E_API_BASE_URL ?? 'http://localhost:8080/api/v1';

// 幂等夹具坐标（与共享资产隔离）
const PREVIEW_ASSET = {
  namespace: 'nlp',
  name: 'e2e068-preview-target',
  displayName: 'E2E 068 图片预览目标',
  format: 'PARQUET',
  modality: 'TEXT',
} as const;
const PREVIEW_VERSION = '3.0.0';

/* ------------------------------------------------------------------ */
/* 内部辅助                                                             */
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

/** 在 compose 目录下运行 docker compose 子命令，返回 stdout。 */
function dockerCompose(args: string[], stdin?: string): string {
  try {
    return execFileSync('docker', ['compose', ...args], {
      cwd: COMPOSE_DIR,
      encoding: 'utf8',
      input: stdin ?? '',
      timeout: 60_000,
      maxBuffer: 10 * 1024 * 1024,
    });
  } catch (e: unknown) {
    const err = e as { stdout?: string; stderr?: string; message?: string };
    throw new Error(
      `docker compose ${args.join(' ')} 失败:\n${err.stderr ?? err.message ?? ''}\n${err.stdout ?? ''}`,
    );
  }
}

/** 轮询直到 fn 返回 truthy 值，超时抛出。 */
async function pollUntil<T>(
  fn: () => Promise<T | null | false>,
  timeoutMs: number,
  intervalMs: number,
  label: string,
): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const result = await fn();
    if (result) return result as T;
    expect(Date.now(), `轮询超时：${label}`).toBeLessThan(deadline);
    await new Promise((r) => setTimeout(r, intervalMs));
  }
}

/** 读取 PNG fixture，返回 { bytes, sha256, size }。 */
function loadFixturePng(): { bytes: Buffer; sha256: string; size: number } {
  const bytes = readFileSync(FIXTURE_PNG);
  const sha256 = createHash('sha256').update(bytes).digest('hex');
  return { bytes, sha256, size: bytes.length };
}

/**
 * 通过 docker compose exec 将 PNG 字节 PUT 到 staging（容器内 curl -T）。
 * presign URL 中的 host 在宿主机不可达，必须在 minio 容器内发起 PUT。
 */
function putStagingViaContainer(presignUrl: string, localPath: string): string {
  // 将文件复制到 minio 容器
  dockerCompose(['cp', localPath, 'minio:/tmp/e2e068_put.bin']);
  // 在容器内 PUT，捕获响应头提取 ETag
  const raw = dockerCompose([
    'exec', '-T', 'minio', 'sh', '-c',
    `curl -s -D /tmp/e2e068_resp.hdr -o /dev/null -T /tmp/e2e068_put.bin '${presignUrl}'; cat /tmp/e2e068_resp.hdr`,
  ]);
  const headers = raw.replace(/\r/g, '');
  const match = headers.match(/^etag:\s*"?([0-9a-fA-F]+)"?/im);
  expect(match, `staging PUT 未返回 ETag，响应头：${headers.slice(0, 400)}`).toBeTruthy();
  return match![1].toLowerCase();
}

/**
 * 通过 docker compose exec 在 worker 容器内向 dvc-cache PUT 图片字节（SigV4 签名）。
 * objectKey = dvcHash[0:2] + '/' + dvcHash[2:]，其中 dvcHash = sha256[0:32]。
 */
function injectDvcCache(sha256Hex: string, pngBytes: Buffer): void {
  const dvcHash = sha256Hex.substring(0, 32);
  const objectKey = `${dvcHash.substring(0, 2)}/${dvcHash.substring(2)}`;
  const b64 = pngBytes.toString('base64');

  // Python 标准库 SigV4 PUT（无第三方依赖，worker 镜像只有 python3）
  const pyScript = `
import sys, os, hashlib, hmac, datetime, urllib.request, urllib.error, base64

ak = os.environ['AWS_ACCESS_KEY_ID']
sk = os.environ['AWS_SECRET_ACCESS_KEY']
region = 'us-east-1'
service = 's3'
bucket = 'dvc-cache'
key = '${objectKey}'
body = base64.b64decode('${b64}')

def sign(key_b, msg):
    return hmac.new(key_b, msg.encode(), hashlib.sha256).digest()

def get_sig_key(key_b, dstr, region_s, service_s):
    k1 = sign(('AWS4' + key_b.decode()).encode() if isinstance(key_b, bytes) else ('AWS4' + key_b).encode(), dstr)
    k2 = sign(k1, region_s)
    k3 = sign(k2, service_s)
    return sign(k3, 'aws4_request')

t = datetime.datetime.utcnow()
amzdate = t.strftime('%Y%m%dT%H%M%SZ')
datestamp = t.strftime('%Y%m%d')

host = 'minio:9000'
canonical_uri = '/' + bucket + '/' + key
payload_hash = hashlib.sha256(body).hexdigest()

canonical_headers = (
    'host:' + host + '\\n' +
    'x-amz-content-sha256:' + payload_hash + '\\n' +
    'x-amz-date:' + amzdate + '\\n'
)
signed_headers = 'host;x-amz-content-sha256;x-amz-date'

canonical_request = '\\n'.join([
    'PUT', canonical_uri, '',
    canonical_headers, signed_headers, payload_hash,
])

scope = '/'.join([datestamp, region, service, 'aws4_request'])
string_to_sign = '\\n'.join(['AWS4-HMAC-SHA256', amzdate, scope,
    hashlib.sha256(canonical_request.encode()).hexdigest()])

signing_key = get_sig_key(sk, datestamp, region, service)
signature = hmac.new(signing_key, string_to_sign.encode(), hashlib.sha256).hexdigest()

auth = (
    'AWS4-HMAC-SHA256 Credential=' + ak + '/' + scope +
    ', SignedHeaders=' + signed_headers +
    ', Signature=' + signature
)

url = 'http://' + host + canonical_uri
req = urllib.request.Request(url, data=body, method='PUT')
req.add_header('Authorization', auth)
req.add_header('x-amz-date', amzdate)
req.add_header('x-amz-content-sha256', payload_hash)
req.add_header('Host', host)

try:
    resp = urllib.request.urlopen(req, timeout=30)
    print('PUT status=' + str(resp.status))
except urllib.error.HTTPError as e:
    print('PUT HTTPError status=' + str(e.code), file=sys.stderr)
    print(e.read().decode()[:600], file=sys.stderr)
    sys.exit(1)
`;

  const output = dockerCompose(
    ['exec', '-T', 'worker', 'python3', '-c', pyScript],
    // 凭据通过容器内环境变量，无需额外传入
  );
  expect(output, `dvc-cache SigV4 PUT 失败：${output}`).toContain('PUT status=200');
}

/** 幂等创建独立 DATASET 夹具资产（与 support.ts 私有 ensureDatasetAsset 相同契约，此处内联）。 */
async function ensurePreviewAsset(
  api: APIRequestContext,
  authorToken: string,
  organizationId: string,
  teamId: string,
): Promise<string> {
  const created = await api.post(`${API_BASE}/assets`, {
    headers: { ...bearer(authorToken), 'Idempotency-Key': `idmp_e2e068_asset_${PREVIEW_ASSET.name}` },
    data: {
      type: 'DATASET',
      namespace: PREVIEW_ASSET.namespace,
      name: PREVIEW_ASSET.name,
      displayName: PREVIEW_ASSET.displayName,
      visibility: 'INTERNAL',
      organizationId,
      ownerTeamId: teamId,
      dataset: { format: PREVIEW_ASSET.format, modality: PREVIEW_ASSET.modality },
    },
    failOnStatusCode: false,
  });
  const createdData = dataOf(await readJson(created)) as { assetId?: string } | null;
  if (created.status() === 201 && createdData?.assetId) {
    await waitAssetReady(api, authorToken, createdData.assetId);
    return createdData.assetId;
  }
  const listed = await api.get(`${API_BASE}/assets`, {
    headers: bearer(authorToken),
    params: { namespace: PREVIEW_ASSET.namespace, keyword: PREVIEW_ASSET.name, type: 'DATASET', size: 50 },
    failOnStatusCode: false,
  });
  const page = dataOf(await readJson(listed)) as { items?: { assetId: string; name: string }[] } | null;
  const match = page?.items?.find((it) => it.name === PREVIEW_ASSET.name);
  expect(match, `夹具资产 ${PREVIEW_ASSET.name} 无法创建（HTTP ${created.status()}）也无法查回`).toBeTruthy();
  await waitAssetReady(api, authorToken, match!.assetId);
  return match!.assetId;
}

/** 等待资产建仓完成（内联复刻 support.ts waitForAssetReady）。 */
async function waitAssetReady(
  api: APIRequestContext,
  token: string,
  assetId: string,
): Promise<void> {
  await pollUntil(
    async () => {
      const r = await api.get(`${API_BASE}/assets/${assetId}`, {
        headers: bearer(token),
        failOnStatusCode: false,
      });
      const d = dataOf(await readJson(r)) as { provisioningStatus?: string } | null;
      expect(d?.provisioningStatus, `资产 ${assetId} 建仓失败`).not.toBe('FAILED');
      return d?.provisioningStatus === 'COMPLETED' ? true : null;
    },
    60_000,
    2_000,
    `资产 ${assetId} 建仓超时`,
  );
}

/* ------------------------------------------------------------------ */
/* 测试状态                                                             */
/* ------------------------------------------------------------------ */

let credentials: { username: string; password: string } | null = null;
let dockerAvailable = false;
let fixtures: E2eFixtures & { previewAssetId: string; versionId: string } | null = null;
/** 幂等快速路径标志：版本已 PUBLISHED 且预览已有 artifactId 时跳过链。 */
let chainAlreadyDone = false;

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) return;

  // 探测 docker 是否可用（与 e2e 其他 spec 凭据缺失 skip 模式对称）
  try {
    execFileSync('docker', ['compose', 'version'], {
      cwd: COMPOSE_DIR,
      stdio: 'pipe',
      timeout: 10_000,
    });
    dockerAvailable = true;
  } catch {
    dockerAvailable = false;
    return;
  }

  const api = await request.newContext();
  try {
    const base = await prepareFixtures(await adminToken(api), api);
    const authorToken = await loginAs(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
    const previewAssetId = await ensurePreviewAsset(api, authorToken, base.organizationId, base.teamId);
    const versionId = await ensureAssetVersion(api, authorToken, previewAssetId, PREVIEW_VERSION);

    // 幂等快速路径：版本已 PUBLISHED 且预览已含 artifactId → 跳过重复执行链
    const verResp = await api.get(`${API_BASE}/assets/${previewAssetId}/versions/${versionId}`, {
      headers: bearer(authorToken),
      failOnStatusCode: false,
    });
    const verData = dataOf(await readJson(verResp)) as { status?: string } | null;
    if (verData?.status === 'PUBLISHED') {
      const prevResp = await api.get(
        `${API_BASE}/assets/${previewAssetId}/previews?versionId=${versionId}`,
        { headers: bearer(authorToken), failOnStatusCode: false },
      );
      const prevData = dataOf(await readJson(prevResp)) as { content?: string } | null;
      if (prevData?.content) {
        try {
          const parsed = JSON.parse(prevData.content) as { artifactId?: string };
          if (parsed.artifactId) {
            chainAlreadyDone = true;
          }
        } catch {
          /* ignore parse error */
        }
      }
    }

    fixtures = { ...base, previewAssetId, versionId };
  } finally {
    await api.dispose();
  }
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
  test.skip(!dockerAvailable, 'docker 不可用，跳过 E4 图片预览闭环');
  test.skip(!fixtures, '夹具准备失败');
});

test.describe('E4 图片预览闭环（TASK-P0BR-068）', () => {
  test('完整上传→物化→发布→注入→预览生成→UI 渲染链', async ({ page }) => {
    test.setTimeout(300_000); // 5 分钟覆盖全链（compose 默认 30s 不够）

    const { previewAssetId, versionId } = fixtures!;
    const api = await request.newContext();
    const authorToken = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);

    if (!chainAlreadyDone) {
      /* ---- 1. 上传链 ---- */
      const { sha256, size } = loadFixturePng();

      // 1a. 创建上传会话
      const sessionResp = await api.post(
        `${API_BASE}/assets/${previewAssetId}/versions/${versionId}/upload-sessions`,
        {
          headers: bearer(authorToken),
          data: { totalBytes: size, fileCount: 1 },
          failOnStatusCode: false,
        },
      );
      const sessionData = dataOf(await readJson(sessionResp)) as { sessionId?: string } | null;
      expect(sessionData?.sessionId, `创建上传会话失败 HTTP ${sessionResp.status()}`).toBeTruthy();
      const sessionId = sessionData!.sessionId!;

      // 1b. 获取 presign URL
      const presignResp = await api.get(
        `${API_BASE}/upload-sessions/${sessionId}/parts/1/presign`,
        { headers: bearer(authorToken), failOnStatusCode: false },
      );
      const presignData = dataOf(await readJson(presignResp)) as { url?: string } | null;
      expect(presignData?.url, `获取 presign URL 失败 HTTP ${presignResp.status()}`).toBeTruthy();

      // 1c. 在 minio 容器内 PUT staging 对象，提取 ETag
      const etag = putStagingViaContainer(presignData!.url!, FIXTURE_PNG);

      // 1d. 完成上传会话
      const completeResp = await api.post(
        `${API_BASE}/upload-sessions/${sessionId}/complete`,
        {
          headers: bearer(authorToken),
          data: {
            parts: [{ partNumber: 1, etag }],
            files: [{ path: 'preview-sample.png', sha256, size, mediaType: 'image/png' }],
          },
          failOnStatusCode: false,
        },
      );
      expect(completeResp.status(), `complete 会话失败 HTTP ${completeResp.status()}`).toBeLessThan(300);

      /* ---- 2. 轮询版本至 PENDING_REVIEW ---- */
      await pollUntil(
        async () => {
          const r = await api.get(
            `${API_BASE}/assets/${previewAssetId}/versions/${versionId}`,
            { headers: bearer(authorToken), failOnStatusCode: false },
          );
          const d = dataOf(await readJson(r)) as { status?: string } | null;
          const s = d?.status;
          // DRAFT/VALIDATING 继续等；PENDING_REVIEW 或 PUBLISHED 命中
          if (s === 'PENDING_REVIEW' || s === 'PUBLISHED') return true;
          // 失败态立即报错
          expect(s, `版本进入意外状态：${s}`).not.toBe('REJECTED');
          return null;
        },
        120_000,
        3_000,
        `版本 ${versionId} 等待 PENDING_REVIEW 超时`,
      );

      /* ---- 3. transition 到 PUBLISHED ---- */
      const curResp = await api.get(
        `${API_BASE}/assets/${previewAssetId}/versions/${versionId}`,
        { headers: bearer(authorToken), failOnStatusCode: false },
      );
      const curData = dataOf(await readJson(curResp)) as { status?: string } | null;
      if (curData?.status !== 'PUBLISHED') {
        const transResp = await api.post(
          `${API_BASE}/assets/${previewAssetId}/versions/${versionId}/transition`,
          { headers: bearer(authorToken), data: { targetStatus: 'PUBLISHED' }, failOnStatusCode: false },
        );
        expect(
          [200, 204, 409],
          `transition 到 PUBLISHED 失败 HTTP ${transResp.status()}`,
        ).toContain(transResp.status());
        // 409 说明并发执行已推进，确认状态即可
        if (transResp.status() === 409) {
          const confirmResp = await api.get(
            `${API_BASE}/assets/${previewAssetId}/versions/${versionId}`,
            { headers: bearer(authorToken), failOnStatusCode: false },
          );
          const cd = dataOf(await readJson(confirmResp)) as { status?: string } | null;
          expect(cd?.status, `transition 返回 409 但状态非 PUBLISHED：${cd?.status}`).toBe('PUBLISHED');
        }
      }

      /* ---- 4. SigV4 注入 dvc-cache ---- */
      injectDvcCache(sha256, readFileSync(FIXTURE_PNG));

      /* ---- 5. 触发 PREVIEW_GENERATE ---- */
      const genResp = await api.post(
        `${API_BASE}/assets/${previewAssetId}/previews/generate`,
        { headers: bearer(authorToken), data: { versionId }, failOnStatusCode: false },
      );
      expect(genResp.status(), `触发预览生成失败 HTTP ${genResp.status()}`).toBeLessThan(300);

      /* ---- 6. 轮询预览内容包含 artifactId ---- */
      await pollUntil(
        async () => {
          const r = await api.get(
            `${API_BASE}/assets/${previewAssetId}/previews?versionId=${versionId}`,
            { headers: bearer(authorToken), failOnStatusCode: false },
          );
          if (r.status() === 404) return null; // 预览尚未入库
          const d = dataOf(await readJson(r)) as { content?: string } | null;
          if (!d?.content) return null;
          try {
            const parsed = JSON.parse(d.content) as { artifactId?: string };
            return parsed.artifactId ? true : null;
          } catch {
            return null;
          }
        },
        90_000,
        3_000,
        `预览 artifactId 等待超时`,
      );
    }

    await api.dispose();

    /* ---- 7. UI 断言：登录 → 导航 → Tab → img 可见 ---- */
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAssetDetailViaCatalog(page, PREVIEW_ASSET.displayName);

    // 激活「预览」Tab（antd 两字之间插空格）
    const previewTab = page.getByRole('tab', { name: /^预\s*览$|^Preview$/ });
    await previewTab.click();
    await expect(previewTab).toHaveAttribute('aria-selected', 'true');

    // img 元素可见且 src 以 blob: 开头（代理 URL 经 createObjectURL 生成）
    const imgLocator = page.locator('img[src^="blob:"]');
    await expect(imgLocator).toBeVisible({ timeout: 15_000 });

    // 图片自然尺寸 > 0（真正解码，非 1x1 占位符）
    const naturalSize = await imgLocator.evaluate((el) => {
      const img = el as HTMLImageElement;
      return { w: img.naturalWidth, h: img.naturalHeight };
    });
    expect(naturalSize.w).toBeGreaterThan(0);
    expect(naturalSize.h).toBeGreaterThan(0);

    // alt 文本（i18n: version.preview）
    await expect(imgLocator).toHaveAttribute('alt', /数据预览|Data Preview/);

    /* ---- 8. 泄漏红线：UI 不暴露对象存储细节 ---- */
    const bodyText = await page.locator('body').innerText();
    // 不含 MinIO 内部地址
    expect(bodyText).not.toContain('minio:9000');
    expect(bodyText).not.toContain('localhost:9000');
    // 不含 bucket 名
    expect(bodyText).not.toContain('dvc-cache');
    expect(bodyText).not.toContain('asset-staging');
    // 不含预签名 Token
    expect(bodyText).not.toContain('X-Amz-');
    // 不含 SigV4 Credential
    expect(bodyText).not.toContain('AWS4-HMAC-SHA256');

    // 页面 img 元素本身不含后端地址
    const imgSrc = await imgLocator.getAttribute('src');
    expect(imgSrc?.startsWith('blob:')).toBe(true);
    expect(imgSrc).not.toContain('minio');
    expect(imgSrc).not.toContain('localhost:9000');
  });

  test('artifact 代理端点返回正确 PNG 字节（纯 API 验证）', async () => {
    test.setTimeout(60_000);

    const { previewAssetId, versionId } = fixtures!;
    const api = await request.newContext();
    try {
      const authorToken = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);

      // 获取预览内容中的 artifactId
      const prevResp = await api.get(
        `${API_BASE}/assets/${previewAssetId}/previews?versionId=${versionId}`,
        { headers: bearer(authorToken), failOnStatusCode: false },
      );
      expect(prevResp.status(), `预览查询返回 HTTP ${prevResp.status()}`).toBe(200);
      const prevData = dataOf(await readJson(prevResp)) as { content?: string } | null;
      expect(prevData?.content, '预览内容为空').toBeTruthy();

      const parsed = JSON.parse(prevData!.content!) as { artifactId?: string };
      expect(parsed.artifactId, 'artifactId 缺失').toBeTruthy();

      // 直接调用 GET /artifact 端点
      const artResp = await api.get(
        `${API_BASE}/assets/${previewAssetId}/previews/artifact?versionId=${versionId}&artifactId=${parsed.artifactId}`,
        { headers: bearer(authorToken), failOnStatusCode: false },
      );
      expect(artResp.status(), `artifact 代理返回 HTTP ${artResp.status()}`).toBe(200);
      expect(artResp.headers()['content-type']).toContain('image/png');

      const body = await artResp.body();
      const expected = readFileSync(FIXTURE_PNG);
      expect(body.length, 'artifact 字节长度与 fixture 不一致').toBe(expected.length);
      expect(
        createHash('sha256').update(body).digest('hex'),
        'artifact 字节 sha256 与 fixture 不一致',
      ).toBe(createHash('sha256').update(expected).digest('hex'));

      // 未授权主体（无 asset:read）→ 403：PreviewApplicationService.renderPreviewArtifact
      // 首行 requirePermission("asset:read")，E2E_AUTHZ_READER 仅持有 authorization:read。
      const unauth = await directApiGet<{ code?: string }>(
        `/assets/${previewAssetId}/previews/artifact?versionId=${versionId}&artifactId=${parsed.artifactId}`,
        E2E_AUTHZ_READER.username,
        E2E_AUTHZ_READER.password,
      );
      expect(unauth.status, `未授权主体 artifact 应被拒绝，实际 ${unauth.status}`).toBe(403);
    } finally {
      await api.dispose();
    }
  });
});
