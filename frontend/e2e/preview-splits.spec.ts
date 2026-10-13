/*
 * 功能: E4 多 Split 预览统计闭环 E2E（TASK-P0BR-078）——在真实 Compose 栈上走完
 *       多目录 bundle 上传（train/×2 + validation/×1 CSV）→ 物化 → PUBLISHED →
 *       dvc-cache SigV4 注入 → POST previews/generate → PREVIEW_GENERATE Job 的端到端链，
 *       随后断言：投影 splits 含 train/validation 且四元组数值与 fixture 字节精确一致、
 *       每 Split 携带脱敏样例切片 sampleRows（批准口径：切换 Split 展示对应样例）；
 *       UI 预览 Tab 渲染 Split 选择器、默认选中首个、切换后统计数值与样例行同步变化、
 *       无「统计未知」误报；页面不泄漏内部主机名/bucket/预签名 Token；
 *       previews 查询端点 403（无 asset:read）/401（匿名）。
 *       上传/注入/轮询沿用 artifact-content.spec.ts（TASK-P0BR-077）的容器内执行模式。
 * 时间: 2026-10-10
 * 作者: AxeXie
 */
import { test, expect, request, type APIRequestContext } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve, dirname } from 'node:path';
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

const COMPOSE_DIR = resolve(__dirnameE2e, '..', '..', 'deploy', 'compose');
const API_BASE = process.env.E2E_API_BASE_URL ?? 'http://localhost:8080/api/v1';

// 幂等夹具坐标（与 068 预览资产、077 内容资产隔离）
const SPLITS_ASSET = {
  namespace: 'nlp',
  name: 'e2e078-splits-target',
  displayName: 'E2E 078 多 Split 预览目标',
  format: 'CSV',
  modality: 'TEXT',
} as const;
const V = '5.0.0';

// 多 Split CSV 夹具（内联字节，path 顶层目录命中后端 Split 白名单 train/validation）
const TRAIN_A_TEXT = 'id,label\n101,e2e078-train-a-1\n102,e2e078-train-a-2\n103,e2e078-train-a-3\n';
const TRAIN_B_TEXT = 'id,label\n104,e2e078-train-b-1\n105,e2e078-train-b-2\n';
const VAL_A_TEXT = 'id,label\n201,e2e078-val-1\n202,e2e078-val-2\n';
const TRAIN_A = Buffer.from(TRAIN_A_TEXT, 'utf8');
const TRAIN_B = Buffer.from(TRAIN_B_TEXT, 'utf8');
const VAL_A = Buffer.from(VAL_A_TEXT, 'utf8');

// 期望投影数值（确定性口径：path 字典序 train/train-a < train/train-b < validation/val-a）
const EXPECT = {
  trainRows: 5, // 3 + 2（表头不计）
  trainFiles: 2,
  trainBytes: TRAIN_A.length + TRAIN_B.length,
  valRows: 2,
  valFiles: 1,
  valBytes: VAL_A.length,
  totalRows: 7,
  totalFiles: 3,
} as const;

interface SplitProjection {
  name: string;
  rowCount: number;
  fileCount: number;
  sizeBytes: number;
  columns: string[] | null;
  truncated: boolean;
  sampleRows?: Record<string, unknown>[] | null;
}

interface PreviewProjection {
  previewId?: string;
  content?: string;
  splits?: SplitProjection[] | null;
  totals?: { rowCount?: number; fileCount?: number; sizeBytes?: number; truncated?: boolean } | null;
  generatedFrom?: { versionId?: string; sourceCommit?: string | null; generatedAt?: string } | null;
}

/* ------------------------------------------------------------------ */
/* 内部辅助（沿用 artifact-content.spec.ts 模式）                       */
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

function sha256Hex(bytes: Buffer): string {
  return createHash('sha256').update(bytes).digest('hex');
}

/**
 * 通过 docker compose exec 将本地文件 PUT 到 staging（容器内 curl -T）。
 * presign URL 中的 host 在宿主机不可达，必须在 minio 容器内发起 PUT。
 */
function putStagingViaContainer(presignUrl: string, localPath: string): string {
  dockerCompose(['cp', localPath, 'minio:/tmp/e2e078_put.bin']);
  const raw = dockerCompose([
    'exec', '-T', 'minio', 'sh', '-c',
    `curl -s -D /tmp/e2e078_resp.hdr -o /dev/null -T /tmp/e2e078_put.bin '${presignUrl}'; cat /tmp/e2e078_resp.hdr`,
  ]);
  const headers = raw.replace(/\r/g, '');
  const match = headers.match(/^etag:\s*"?([0-9a-fA-F]+)"?/im);
  expect(match, `staging PUT 未返回 ETag，响应头：${headers.slice(0, 400)}`).toBeTruthy();
  return match![1].toLowerCase();
}

/**
 * 通过 docker compose exec 在 worker 容器内向 dvc-cache PUT 文本字节（SigV4 签名）。
 * objectKey = dvcHash[0:2] + '/' + dvcHash[2:]，其中 dvcHash = sha256[0:32]。
 */
function injectDvcCache(sha256HexStr: string, bytes: Buffer): void {
  const dvcHash = sha256HexStr.substring(0, 32);
  const objectKey = `${dvcHash.substring(0, 2)}/${dvcHash.substring(2)}`;
  const b64 = bytes.toString('base64');

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

  const output = dockerCompose(['exec', '-T', 'worker', 'python3', '-c', pyScript]);
  expect(output, `dvc-cache SigV4 PUT 失败：${output}`).toContain('PUT status=200');
}

interface BundleFile {
  path: string;
  sha256: string;
  size: number;
  mediaType: string;
}

/**
 * 单 session 完成一次 bundle 上传：本地临时 bundle → 容器内 PUT → complete。
 * 多文件时物化校验跳过 session 对象 sha256 比对（bundle 字节不被解包，仅逐文件元数据入册）。
 */
async function uploadBundle(
  api: APIRequestContext,
  token: string,
  assetId: string,
  versionId: string,
  bundleTotalBytes: number,
  files: BundleFile[],
): Promise<void> {
  const bundlePath = join(tmpdir(), `e2e078_bundle_${versionId}_${Date.now()}.tar`);
  writeFileSync(bundlePath, Buffer.from(JSON.stringify({ bundle: 'e2e078', files: files.map((f) => f.path) }), 'utf8'));
  try {
    const sessionResp = await api.post(
      `${API_BASE}/assets/${assetId}/versions/${versionId}/upload-sessions`,
      { headers: bearer(token), data: { totalBytes: bundleTotalBytes, fileCount: 1 }, failOnStatusCode: false },
    );
    const sessionData = dataOf(await readJson(sessionResp)) as { sessionId?: string } | null;
    expect(sessionData?.sessionId, `创建上传会话失败 HTTP ${sessionResp.status()}`).toBeTruthy();
    const sessionId = sessionData!.sessionId!;

    const presignResp = await api.get(
      `${API_BASE}/upload-sessions/${sessionId}/parts/1/presign`,
      { headers: bearer(token), failOnStatusCode: false },
    );
    const presignData = dataOf(await readJson(presignResp)) as { url?: string } | null;
    expect(presignData?.url, `获取 presign URL 失败 HTTP ${presignResp.status()}`).toBeTruthy();

    const etag = putStagingViaContainer(presignData!.url!, bundlePath);

    const completeResp = await api.post(
      `${API_BASE}/upload-sessions/${sessionId}/complete`,
      {
        headers: bearer(token),
        data: { parts: [{ partNumber: 1, etag }], files },
        failOnStatusCode: false,
      },
    );
    expect(completeResp.status(), `complete 会话失败 HTTP ${completeResp.status()}`).toBeLessThan(300);
  } finally {
    rmSync(bundlePath, { force: true });
  }
}

/** 轮询版本至 PENDING_REVIEW（物化 + 校验完成）。 */
async function waitPendingReview(
  api: APIRequestContext,
  token: string,
  assetId: string,
  versionId: string,
): Promise<void> {
  await pollUntil(
    async () => {
      const r = await api.get(`${API_BASE}/assets/${assetId}/versions/${versionId}`, {
        headers: bearer(token),
        failOnStatusCode: false,
      });
      const d = dataOf(await readJson(r)) as { status?: string } | null;
      const s = d?.status;
      if (s === 'PENDING_REVIEW' || s === 'PUBLISHED') return true;
      expect(s, `版本进入意外状态：${s}`).not.toBe('REJECTED');
      return null;
    },
    120_000,
    3_000,
    `版本 ${versionId} 等待 PENDING_REVIEW 超时`,
  );
}

/** transition 到 PUBLISHED（409 视为并发已推进，确认状态）。 */
async function publishVersion(
  api: APIRequestContext,
  token: string,
  assetId: string,
  versionId: string,
): Promise<void> {
  const curResp = await api.get(`${API_BASE}/assets/${assetId}/versions/${versionId}`, {
    headers: bearer(token),
    failOnStatusCode: false,
  });
  const curData = dataOf(await readJson(curResp)) as { status?: string } | null;
  if (curData?.status === 'PUBLISHED') return;
  const transResp = await api.post(
    `${API_BASE}/assets/${assetId}/versions/${versionId}/transition`,
    { headers: bearer(token), data: { targetStatus: 'PUBLISHED' }, failOnStatusCode: false },
  );
  expect([200, 204, 409], `transition 到 PUBLISHED 失败 HTTP ${transResp.status()}`).toContain(transResp.status());
  if (transResp.status() === 409) {
    const confirmResp = await api.get(`${API_BASE}/assets/${assetId}/versions/${versionId}`, {
      headers: bearer(token),
      failOnStatusCode: false,
    });
    const cd = dataOf(await readJson(confirmResp)) as { status?: string } | null;
    expect(cd?.status, `transition 返回 409 但状态非 PUBLISHED：${cd?.status}`).toBe('PUBLISHED');
  }
}

interface ArtifactEntry {
  artifactId: string;
  versionId: string;
  path: string;
  sha256: string;
  size: number;
  mediaType: string | null;
}

/** 列出版本工件，返回 path → ArtifactEntry 映射。 */
async function listArtifactMap(
  api: APIRequestContext,
  token: string,
  assetId: string,
  versionId: string,
): Promise<Map<string, ArtifactEntry>> {
  const r = await api.get(`${API_BASE}/assets/${assetId}/versions/${versionId}/artifacts`, {
    headers: bearer(token),
    failOnStatusCode: false,
  });
  expect(r.status(), `列出版本工件失败 HTTP ${r.status()}`).toBe(200);
  const list = dataOf(await readJson(r)) as ArtifactEntry[] | null;
  const map = new Map<string, ArtifactEntry>();
  (list ?? []).forEach((a) => map.set(a.path, a));
  return map;
}

/** 轮询工件列表直到包含全部期望 path（物化入库完成的标志）。 */
async function waitArtifacts(
  api: APIRequestContext,
  token: string,
  assetId: string,
  versionId: string,
  paths: string[],
): Promise<Map<string, ArtifactEntry>> {
  return pollUntil(
    async () => {
      const map = await listArtifactMap(api, token, assetId, versionId);
      return paths.every((p) => map.has(p)) ? map : null;
    },
    120_000,
    3_000,
    `版本 ${versionId} 工件入库超时（期望 ${paths.join(', ')}）`,
  );
}

const SPLIT_PATHS = ['train/train-a.csv', 'train/train-b.csv', 'validation/val-a.csv'];

/** 版本已 PUBLISHED 且工件列表含期望 path → 上传链可跳过（幂等重跑快速路径）。 */
async function versionChainDone(
  api: APIRequestContext,
  token: string,
  assetId: string,
  versionId: string,
): Promise<boolean> {
  const r = await api.get(`${API_BASE}/assets/${assetId}/versions/${versionId}`, {
    headers: bearer(token),
    failOnStatusCode: false,
  });
  const d = dataOf(await readJson(r)) as { status?: string } | null;
  if (d?.status !== 'PUBLISHED') return false;
  try {
    const map = await listArtifactMap(api, token, assetId, versionId);
    return SPLIT_PATHS.every((p) => map.has(p));
  } catch {
    return false;
  }
}

/** 读取预览投影（404/无内容返回 null）。 */
async function fetchProjection(
  api: APIRequestContext,
  token: string,
  assetId: string,
  versionId: string,
): Promise<PreviewProjection | null> {
  const r = await api.get(`${API_BASE}/assets/${assetId}/previews?versionId=${versionId}`, {
    headers: bearer(token),
    failOnStatusCode: false,
  });
  if (r.status() !== 200) return null;
  const d = dataOf(await readJson(r)) as PreviewProjection | null;
  return d?.content ? d : null;
}

/** 投影含 train+validation 两个 Split → 生成链可跳过（幂等重跑快速路径）。 */
function projectionDone(p: PreviewProjection | null): boolean {
  const names = new Set((p?.splits ?? []).map((s) => s.name));
  return names.has('train') && names.has('validation');
}

/** 幂等创建独立 DATASET 夹具资产（与 077 spec 相同契约，此处内联）。 */
async function ensureSplitsAsset(
  api: APIRequestContext,
  authorToken: string,
  organizationId: string,
  teamId: string,
): Promise<string> {
  const created = await api.post(`${API_BASE}/assets`, {
    headers: { ...bearer(authorToken), 'Idempotency-Key': `idmp_e2e078_asset_${SPLITS_ASSET.name}` },
    data: {
      type: 'DATASET',
      namespace: SPLITS_ASSET.namespace,
      name: SPLITS_ASSET.name,
      displayName: SPLITS_ASSET.displayName,
      visibility: 'INTERNAL',
      organizationId,
      ownerTeamId: teamId,
      dataset: { format: SPLITS_ASSET.format, modality: SPLITS_ASSET.modality },
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
    params: { namespace: SPLITS_ASSET.namespace, keyword: SPLITS_ASSET.name, type: 'DATASET', size: 50 },
    failOnStatusCode: false,
  });
  const page = dataOf(await readJson(listed)) as { items?: { assetId: string; name: string }[] } | null;
  const match = page?.items?.find((it) => it.name === SPLITS_ASSET.name);
  expect(match, `夹具资产 ${SPLITS_ASSET.name} 无法创建（HTTP ${created.status()}）也无法查回`).toBeTruthy();
  await waitAssetReady(api, authorToken, match!.assetId);
  return match!.assetId;
}

/** 等待资产建仓完成（内联复刻 support.ts waitForAssetReady）。 */
async function waitAssetReady(api: APIRequestContext, token: string, assetId: string): Promise<void> {
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
let fixtures: (E2eFixtures & { assetId: string; versionId: string }) | null = null;
let chainAlreadyDone = false;
let previewAlreadyDone = false;

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
    const assetId = await ensureSplitsAsset(api, authorToken, base.organizationId, base.teamId);
    const versionId = await ensureAssetVersion(api, authorToken, assetId, V);

    chainAlreadyDone = await versionChainDone(api, authorToken, assetId, versionId);
    previewAlreadyDone =
      chainAlreadyDone && projectionDone(await fetchProjection(api, authorToken, assetId, versionId));

    fixtures = { ...base, assetId, versionId };
  } finally {
    await api.dispose();
  }
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
  test.skip(!dockerAvailable, 'docker 不可用，跳过 E4 多 Split 预览闭环');
  test.skip(!fixtures, '夹具准备失败');
});

// 链步骤间有先后依赖，保持声明顺序执行
test.describe.configure({ mode: 'serial' });

test.describe('E4 多 Split 预览统计闭环（TASK-P0BR-078）', () => {
  test('多目录上传→物化→发布→dvc-cache 注入链', async () => {
    test.setTimeout(300_000);
    const { assetId, versionId } = fixtures!;
    const api = await request.newContext();
    try {
      const token = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
      if (chainAlreadyDone) return;

      await uploadBundle(
        api,
        token,
        assetId,
        versionId,
        TRAIN_A.length + TRAIN_B.length + VAL_A.length,
        [
          { path: 'train/train-a.csv', sha256: sha256Hex(TRAIN_A), size: TRAIN_A.length, mediaType: 'text/csv' },
          { path: 'train/train-b.csv', sha256: sha256Hex(TRAIN_B), size: TRAIN_B.length, mediaType: 'text/csv' },
          { path: 'validation/val-a.csv', sha256: sha256Hex(VAL_A), size: VAL_A.length, mediaType: 'text/csv' },
        ],
      );

      await waitPendingReview(api, token, assetId, versionId);
      await publishVersion(api, token, assetId, versionId);
      const artifacts = await waitArtifacts(api, token, assetId, versionId, SPLIT_PATHS);
      // 注入实际字节（sha256 与物化记录一致 → dvcObjectKey 命中，统计与样例取数依赖）
      injectDvcCache(artifacts.get('train/train-a.csv')!.sha256, TRAIN_A);
      injectDvcCache(artifacts.get('train/train-b.csv')!.sha256, TRAIN_B);
      injectDvcCache(artifacts.get('validation/val-a.csv')!.sha256, VAL_A);
    } finally {
      await api.dispose();
    }
  });

  test('PREVIEW_GENERATE 投影：per-Split 四元组 + 脱敏样例切片与 fixture 精确一致', async () => {
    test.setTimeout(180_000);
    const { assetId, versionId } = fixtures!;
    const api = await request.newContext();
    try {
      const token = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);

      if (!previewAlreadyDone) {
        const genResp = await api.post(
          `${API_BASE}/assets/${assetId}/previews/generate`,
          { headers: bearer(token), data: { versionId }, failOnStatusCode: false },
        );
        expect(genResp.status(), `触发预览生成失败 HTTP ${genResp.status()}`).toBeLessThan(300);
        await pollUntil(
          async () => (projectionDone(await fetchProjection(api, token, assetId, versionId)) ? true : null),
          120_000,
          3_000,
          '预览投影等待 train/validation 超时（Job 可能失败）',
        );
      }

      const p = await fetchProjection(api, token, assetId, versionId);
      expect(p, '预览投影缺失').toBeTruthy();
      const splits = p!.splits ?? [];
      const byName = new Map(splits.map((s) => [s.name, s]));
      expect([...byName.keys()].sort()).toEqual(['train', 'validation']);
      expect(splits.map((s) => s.name)).toEqual(['train', 'validation']); // path 字典序确定性

      const train = byName.get('train')!;
      expect(train.rowCount).toBe(EXPECT.trainRows);
      expect(train.fileCount).toBe(EXPECT.trainFiles);
      expect(train.sizeBytes).toBe(EXPECT.trainBytes);
      expect(train.columns).toEqual(['id', 'label']);
      expect(train.truncated).toBe(false);
      // 批准口径：Split 携带本切片脱敏样例（首文件行优先，跨文件累积）
      expect(train.sampleRows).toBeTruthy();
      expect(train.sampleRows!.length).toBe(EXPECT.trainRows);
      expect(train.sampleRows![0]).toMatchObject({ id: '101', label: 'e2e078-train-a-1' });
      expect(train.sampleRows![3]).toMatchObject({ label: 'e2e078-train-b-1' });

      const val = byName.get('validation')!;
      expect(val.rowCount).toBe(EXPECT.valRows);
      expect(val.fileCount).toBe(EXPECT.valFiles);
      expect(val.sizeBytes).toBe(EXPECT.valBytes);
      expect(val.columns).toEqual(['id', 'label']);
      expect(val.truncated).toBe(false);
      expect(val.sampleRows!.length).toBe(EXPECT.valRows);
      expect(val.sampleRows![0]).toMatchObject({ label: 'e2e078-val-1' });

      expect(p!.totals?.rowCount).toBe(EXPECT.totalRows);
      expect(p!.totals?.fileCount).toBe(EXPECT.totalFiles);
      expect(p!.totals?.sizeBytes).toBe(EXPECT.trainBytes + EXPECT.valBytes);
      expect(p!.totals?.truncated).toBe(false);

      expect(p!.generatedFrom?.versionId).toBe(versionId);
      expect(p!.generatedFrom?.generatedAt).toBeTruthy();

      // 样例值不得泄漏内部存储细节（脱敏路径 + 投影整体红线）
      const projectionText = JSON.stringify(p);
      expect(projectionText).not.toContain('dvc-cache');
      expect(projectionText).not.toContain('minio:9000');
      expect(projectionText).not.toContain('X-Amz-');
    } finally {
      await api.dispose();
    }
  });

  test('UI：Split 选择器默认首个 → 切换后统计与样例同步 → 无未知误报 → 泄漏红线', async ({ page }) => {
    test.setTimeout(120_000);
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAssetDetailViaCatalog(page, SPLITS_ASSET.displayName);

    // 激活「预览」Tab（antd 两字之间插空格）
    const previewTab = page.getByRole('tab', { name: /^预\s*览$|^Preview$/ });
    await previewTab.click();
    await expect(previewTab).toHaveAttribute('aria-selected', 'true');

    // Split 选择器渲染（多 Split 才出现）+ 统计未知不得出现
    await expect(page.getByText('数据切分（Split）')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/统计未知|statistics unknown/i)).toHaveCount(0);
    await expect(page.getByText('统计已按上限截断')).toHaveCount(0);

    const statistic = (label: RegExp) => page.locator('.ant-statistic').filter({ hasText: label });
    await expect(statistic(/行数|Rows/)).toContainText(String(EXPECT.trainRows));
    await expect(statistic(/文件数|Files/)).toContainText(String(EXPECT.trainFiles));

    // 默认选中首个 Split（train）：样例含 train 标记、不含 val 标记
    const segmented = page.locator('.ant-segmented');
    await expect(segmented).toBeVisible();
    await expect(page.getByText('e2e078-train-a-1')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText('e2e078-val-1')).toHaveCount(0);

    // 切换 validation：统计数值 + 样例行整体换成 validation 切片
    await segmented.getByText('validation', { exact: true }).click();
    await expect(page.getByText('e2e078-val-1')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText('e2e078-train-a-1')).toHaveCount(0);
    await expect(statistic(/行数|Rows/)).toContainText(String(EXPECT.valRows));
    await expect(statistic(/文件数|Files/)).toContainText(String(EXPECT.valFiles));

    // 切回 train：切片恢复（用户选择生效且可逆）
    await segmented.getByText('train', { exact: true }).click();
    await expect(page.getByText('e2e078-train-a-1')).toBeVisible({ timeout: 20_000 });
    await expect(statistic(/行数|Rows/)).toContainText(String(EXPECT.trainRows));

    // 来源注记：generatedFrom 呈现 versionId 或 commit 摘要（非伪造）
    await expect(page.getByText(/统计生成于|Generated from/i)).toBeVisible();

    // 泄漏红线：页面数据不含内部主机名 / bucket / 预签名 Token
    const bodyText = await page.locator('body').innerText();
    expect(bodyText).not.toContain('minio:9000');
    expect(bodyText).not.toContain('localhost:9000');
    expect(bodyText).not.toContain('dvc-cache');
    expect(bodyText).not.toContain('asset-staging');
    expect(bodyText).not.toContain('X-Amz-');
    expect(bodyText).not.toContain('AWS4-HMAC-SHA256');
  });

  test('previews 查询权限：无 asset:read → 403；匿名 → 401', async () => {
    test.setTimeout(60_000);
    const { assetId, versionId } = fixtures!;

    const forbidden = await directApiGet(
      `/assets/${assetId}/previews?versionId=${versionId}`,
      E2E_AUTHZ_READER.username,
      E2E_AUTHZ_READER.password,
    );
    expect(forbidden.status, `未授权主体应被拒绝，实际 ${forbidden.status}`).toBe(403);

    const api = await request.newContext();
    try {
      const anon = await api.get(`${API_BASE}/assets/${assetId}/previews?versionId=${versionId}`, {
        failOnStatusCode: false,
      });
      expect(anon.status(), `匿名访问应 401，实际 ${anon.status()}`).toBe(401);
    } finally {
      await api.dispose();
    }
  });
});
