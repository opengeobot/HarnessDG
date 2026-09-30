---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-070
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0805b6092af216faeb4135d4d5d7be7ea509a204
stageGatePassed: true
stageGateEvidence: 批准人 User 于 2026-09-30 授权起草本卡（goal (b) 后续选项确认）。
  差距事实链：REQ-UPL-002 L172「URL 使用公开 MinIO Endpoint」未实现——后端全部
  presign 客户端绑定容器内网端点 `http://minio:9000`（MinioConfiguration.java
  L32/L51/L79），预签名 URL 浏览器必不可达；`externalEndpoint` 属性
  （MinioProperties.java L30）仅 DvcConfigurationService L53-55 消费、presign
  路径零使用；compose.yaml L136 注入 `AIHUB_MINIO_PUBLIC_ENDPOINT` 与
  application.yml L121 读取的 `AIHUB_MINIO_EXTERNAL_ENDPOINT` 变量名不匹配，
  属性恒为空（连带 DVC remote 配置同样落回内网 host，同根因）；minio 服务
  compose.yaml L39-40 仅映射 console `127.0.0.1:9001:9001`，API 9000 未映射
  宿主机；无 MinIO CORS 配置（浏览器跨源 PUT 预检必失败）；
  UploadApplicationService L53 `PRESIGN_EXPIRY=30min` 与 REQ-UPL-002 安全节
  L184「默认 15 分钟或更短」偏差。TASK-P0BR-069 已在 `upload-flow.spec.ts`
  头部 L9-21 以 page.route 桩替代「浏览器不可达的最后一段网络」并明示移交本卡。
  work-breakdown L86 为本卡预留行（批准人 2026-09-29 批准另立）。
  实施批准与 approvedBy/approvedAt 回写、status 置 READY 由批准人在本卡 DRAFT
  审阅后另行处置（DEC-009：AI 不得在同一实施会话自行填写 approvedBy/approvedAt
  或将 DRAFT 置 READY）。
  批准人 User 于 2026-09-30 在 DRAFT 卡经人工审阅后明确批准实施，
  approvedBy/approvedAt/status 由 AI 如实登记批准结果（非自批，与 071/072 同惯例）。
approvedBy: User
approvedAt: 2026-09-30T07:30:11Z
stageGateEvidenceRefs:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-069-001.yaml
requirements:
  - REQ-UPL-002
acceptanceScenarios:
  - AC-P2-UPL-002
  - AC-P2-UPL-004
decisions:
  - DEC-008
  - DEC-009
scenarioEvidencePlan:
  - AC-P2-UPL-002|E4|真实 Compose 栈上创建 Upload Session → presign 响应 URL host
    为宿主机可达公开端点（`127.0.0.1:9000`/`localhost:9000`，非 `minio:9000`）且
    有效期 ≤ 15 分钟；浏览器 UI 点击直传真实 PUT 成功（Playwright 等待
    hostname=localhost:9000 的真实响应 200 + ETag，无 page.route 桩参与成功路径）；
    UI Complete 真实成功（服务端 MinIO ETag 对账链不变）；日志/响应不含完整预签名
    URL 口径不回归
  - AC-P2-UPL-004|E4|`upload-flow.spec.ts` 删除主机桩后全量通过：sessionId 刷新后
    服务端 Parts 对账、单 Part 失败仅重签/重传失败 Part（失败注入改为 route 仅
    `abort()` 网络层模拟，成功路径 100% 真实）、不保存 Token/预签名 URL 断言不回归；
    全量 e2e 基线（112 passed / 0 skipped）无回归
crossCuttingPlan:
  - AUTHN|不改认证链；presign 端点仍以平台 JWT + session owner 校验为前提
  - AUTHZ|不改任何权限判定；presign 的 session owner + asset:upload 门
    （UploadController L72 requirePrincipalId）保持现状
  - DB_FILTER|N/A - 不改列表查询与权限下推 SQL
  - STATE|N/A - 不改 Upload Session 状态机；仅改预签名 URL 的签发端点与有效期
  - IDEMPOTENCY|N/A - complete 幂等与清单冲突逻辑不变；TTL 缩短不改变 Part 清单
    与 upload_part 行语义
  - CONSISTENCY|前后端口径对齐：PRESIGN_EXPIRY 30→15 分钟后 upload_part 落库的
    签名有效期字段同步反映新 TTL，服务端对账/重签判定（前端按 expiresAt 决策）
    保持一致；E2E 桩移除后浏览器重试路径与服务端清单一致性由既有 complete 对账守护
  - ERRORS|N/A - 不新增 ErrorCode；StorageException 失败映射不变
  - AUDIT|不改审计口径：创建/取消/完成请求审计不记录预签名 URL（REQ-UPL-001 L150、
    runbooks/security.md L34 脱敏检查项保持）
  - NOTIFICATION|N/A - 不触碰通知面
  - TAXONOMY_I18N|N/A - 无 UI 文案变更（前端源码不改）
  - CONFIG|核心面：application.yml external-endpoint 绑定名对齐（嵌套默认值兼容
    PUBLIC/EXTERNAL 双名）；compose minio 增 API 端口宿主机映射与 CORS 环境变量；
    worker 不注入公开端点（无 presign 消费面，物化/预览走容器内网）；
    .env.example 注释修正
  - OBSERVABILITY|N/A - 不新增指标/告警；既有日志脱敏口径不回归
  - SECRETS|预签名 URL 属短期凭据：不写入 Git/日志/测试夹具/AI 会话输出；
    Evidence Manifest 与 build-logs 仅记录 URL host 与签名参数名，不记录完整 URL
    与 Signature 值
allowedPaths:
  - backend/src/main/java/com/aihub/integration/minio/infrastructure/MinioConfiguration.java
  - backend/src/main/java/com/aihub/integration/minio/infrastructure/MinioStorageAdapter.java
  - backend/src/main/java/com/aihub/transfer/application/UploadApplicationService.java
  - backend/src/main/resources/application.yml
  - backend/src/test/java/com/aihub/integration/minio/infrastructure/MinioConfigurationTest.java
  - backend/src/test/java/com/aihub/integration/minio/infrastructure/MinioStorageAdapterTest.java
  - deploy/compose/compose.yaml
  - deploy/compose/.env.example
  - frontend/e2e/upload-flow.spec.ts
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-070.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-070-001.yaml
preExistingDirtyPaths:
  - build-logs/verify-bom.ps1|b5003338d283e84b4b6dabaf0d9e47b6d90fe18b780d0fd2e59851d467ac9b3d
forbiddenPaths:
  - contracts
  - worker
  - frontend/src
  - V1__baseline.sql
  - V2__asset_catalog.sql
  - docs/ai-spec/tasks/TASK-P0BR-069.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-069-001.yaml
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-070-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-070.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-070.md -CheckChangedPaths
  - ./mvnw verify（backend 目录；新增 MinioConfigurationTest 通过且既有用例不回归）
  - pnpm lint（frontend 目录）
  - pnpm typecheck（frontend 目录）
  - pnpm test（frontend 目录；Vitest 既有基线不回归，本卡不改前端源码）
  - pnpm build（frontend 目录）
  - docker compose config --quiet（deploy/compose 目录）
  - ./deploy/compose/scripts/verify.ps1（deploy/compose 目录；V01-V28 无回归）
  - pnpm test:e2e（frontend 目录；真实 Compose 栈，upload-flow.spec.ts 无桩新口径通过 + 全量基线无回归；skip 视为 notProven）
---

# TASK-P0BR-070：presign 公开端点接线与浏览器直传真实化（契约优先缺陷修复）

> 状态：`READY`（批准人 User 于 2026-09-30 批准实施，`implementationAuthorized: true`；
> DEC-009——批准权在批准人，AI 如实登记）。
> 规则：一个任务只交付一个可独立验收的纵向结果——本卡纵向结果 =
> 「预签名 URL 使用宿主机可达公开端点且 TTL ≤ 15 分钟，`upload-flow.spec.ts`
> 删除主机桩后浏览器真实 PUT 全链通过」。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。

## 1. 目标

REQ-UPL-002 行为节 L172 明文「URL 使用公开 MinIO Endpoint」。当前实现中全部
presign 客户端绑定容器内网端点 `http://minio:9000`，预签名 URL 在宿主机浏览器
必不可达——上传中心「浏览器直传 MinIO」这一设计核心（REQ-UPL-002 安全节 L187
「Nginx/Backend 不代理文件 bytes」排除了代理转发路线）实际上从未真实工作过。
TASK-P0BR-069 只能以 `page.route` 主机桩替代最后一段网络（`upload-flow.spec.ts`
头部 L9-21 完整记述该诚实边界并明示移交本卡）。本卡修复端点接线全链（后端
presign 端点解析 → compose 变量名对齐 → 9000 宿主机映射 → MinIO CORS → TTL
对齐），并解除 e2e 桩、改浏览器真实 PUT，使 AC-P2-UPL-002/004 达到名副其实的
E4。

## 2. 需求与追踪

- 需求：`REQ-UPL-002`（READY/MUST/E4——Part 签名短 TTL、公开 Endpoint、失败
  只重签/重传失败 Part、complete 服务端对账）。
- 验收：`AC-P2-UPL-002`（p2-exit-catalog §5：URL 最小权限短 TTL；complete 核对
  MinIO metadata；日志/响应不含完整预签名 URL）、`AC-P2-UPL-004`（§7：刷新后
  服务端对账、只重签/重传失败 Part、不保存 Token/预签名 URL）。
- 决策：`DEC-008`（数据集/AI 数据工作流必须交付——浏览器与 Agent 直传是产品
  目标的一部分）、`DEC-009`（本卡受 AI 规格门禁约束）。
- 上游证据：`EVD-P0BR-069-001.yaml`（桩的诚实边界与移交登记）；
  work-breakdown L86 预留行（批准人 2026-09-29 批准另立）。
- 附带受益（不在验收范围、如实登记）：`AIHUB_MINIO_EXTERNAL_ENDPOINT` 绑定名
  修正后 `DvcConfigurationService` L53-55 的 `externalEndpoint` 优先分支开始
  生效，`/dvc/remote-config` 返回的 remote url 由 `minio:9000` 变为宿主机可达
  地址——修复 `AC-P2-DVC-001`（CLI DVC 往返）的同根因缺口；该场景的 E4 验证
  属 `verify-journey.sh` 业务旅程范围，本卡仅以单测 + 配置断言守护不回归。

## 3. 取证事实（2026-09-30，HEAD `0805b60`）

### 3.1 缺陷链（work-breakdown L86 预留行三点实证）

- [F-1] `backend/.../MinioConfiguration.java`：`s3Presigner` L79
  `.endpointOverride(URI.create(properties.getEndpoint()))`、`minioClient` L32、
  `dvcCacheMinioClient` L51 全部绑定 `aihub.minio.endpoint`——compose 下即
  `AIHUB_MINIO_ENDPOINT=http://minio:9000`（compose.yaml L135）。presign 的三条
  产出路径（S3 SDK `presignPartUpload` + MinIO SDK `presignDownload`/
  `presignUpload` 经 `clientFor(bucket)`）URL host 均为 `minio:9000`。
- [F-2] `externalEndpoint`（`MinioProperties.java` L30，注释「预签名 URL 的外部
  端点」即本卡意图）全后端仅 `DvcConfigurationService` L53-55 一处消费；
  presign 装配零使用。
- [F-3] 变量名不匹配：compose.yaml L136 注入 `AIHUB_MINIO_PUBLIC_ENDPOINT:
  ${MINIO_PUBLIC_URL}`，application.yml L121 读
  `${AIHUB_MINIO_EXTERNAL_ENDPOINT:}` → `externalEndpoint` 恒为空字符串，
  连 DvcConfigurationService 的既有优先分支也实际落回内网 host（同根因）。

### 3.2 新发现（超出预留行，全部实证）

- [F-4] **9000 未映射宿主机**：compose.yaml minio `ports:` 仅
  `"127.0.0.1:9001:9001"`（console）。即便端点名对齐，`.env.example` L50 的
  `MINIO_PUBLIC_URL=http://localhost:9000` 在宿主机依然不可达——接线修复必须
  与端口映射同修，否则缺陷换形不换质。
- [F-5] **无 CORS 配置**：全仓无 `MINIO_API_CORS_*`/`MINIO_SERVER_URL` 命中。
  浏览器从 `http://localhost:8080`（nginx 入口）跨源 PUT
  `http://localhost:9000` 必发 OPTIONS 预检；且 ETag 非 CORS safelisted 响应头，
  需 `Access-Control-Expose-Headers: ETag` 前端 `getResponseHeader('ETag')`
  才可读——069 桩的 `CORS_HEADERS`（spec L158-164）正是按此形态模拟，注释自证
  「真实公开端点同样需要 CORS」。
- [F-6] **nginx 代理路线被需求排除**：REQ-UPL-002 安全节 L187「Nginx/Backend
  不代理文件 bytes」⇒ 不能以 `nginx /s3/` 反代替代 9000 映射 + CORS；且 S3
  SigV4 预签名 URL 的 canonical request 包含 Host——事后改写 URL host 会使
  签名失效。唯一正确路线是签发起点即用公开端点（`S3Presigner` 的
  endpointOverride 仅决定 URL host，签名计算由持公开端点的 presign 客户端完成；
  MinIO Java SDK 无独立 presign endpoint 选项，`getPresignedObjectUrl` 基于
  builder endpoint ⇒ 需独立 presign 专用 MinioClient Bean）。
- [F-7] **TTL 口径偏差**：`UploadApplicationService` L53
  `PRESIGN_EXPIRY = Duration.ofMinutes(30)` vs REQ-UPL-002 安全节 L184
  「Presigned TTL 默认 15 分钟或更短」。下载票据 `DownloadApplicationService`
  L37 `TICKET_TTL=15min` 已合规。本卡将 Part 签名对齐至 15 分钟。
- [F-8] **前端消费方不改源码即受益**：`UploadPage.tsx` L215-234
  `presignAndUploadPart`（GET presign → `uploadPartWithProgress` PUT，内置一次
  自动重试）与 `VersionPage.tsx` L116-117 `window.open(ticket.presignedUrl)`
  均直接使用后端返回 URL——端点修复后前端零改动。

### 3.3 不受影响的邻接面（反向实证）

- `deploy/compose/scripts/verify.ps1` V02/V03 走容器内 `mc alias set local
  http://localhost:9000`（容器网络），9000 宿主机映射与 CORS 环境变量不改变其
  行为；V01-V28 基线须无回归。
- worker（compose.yaml L149-173）仅 `AIHUB_MINIO_ENDPOINT`，物化/预览读取走
  容器内网 ops 路径、无 presign 消费面——不注入公开端点（worker 环境
  `externalEndpoint` 为空 ⇒ presign 专用客户端 Bean 回退内网端点，装配安全；
  与现行 `dvcCacheMinioClient` L47-48 回退模式同构）。
- `contracts/openapi/aihub-v1.yaml`：presign 端点 L3451 与
  `DownloadTicket.presignedUrl` L5224 已存在；URL host/TTL 属部署与实现口径，
  **无契约变更**（`x-implementation-status` 上调策略仍属治理遗留，不在本卡）。
- `MinioStorageAdapterTest` L61 `presignPartUploadUsesS3Presigner` 以 mock
  `S3Presigner` 验证调用形态——Bean 构造变更需同步适配注入签名，断言语义不变。

## 4. 修复方案

1. **绑定名对齐**（application.yml L121）：
   `external-endpoint: ${AIHUB_MINIO_PUBLIC_ENDPOINT:${AIHUB_MINIO_EXTERNAL_ENDPOINT:}}`
   ——以 compose 注入名为准、嵌套默认值兼容 EXTERNAL 旧名（本地原生进程运行
   口径不破）。
2. **presign 端点解析**（MinioConfiguration.java）：新增私有
   `resolvePresignEndpoint(properties)`＝`externalEndpoint` 非空白则用之、否则
   回退 `endpoint`；`s3Presigner` L79 改用解析结果；`s3Client`/`minioClient`/
   `dvcCacheMinioClient` 三个**执行 ops** 的客户端保持内网端点不变。
3. **presign 专用 MinioClient**（MinioConfiguration.java + MinioStorageAdapter.java）：
   新增 `presignMinioClient` 与 `presignDvcCacheMinioClient` 两个 Bean（公开端点 +
   各自凭据，凭据回退语义与 L44-54 现有模式一致）；`MinioStorageAdapter` 的
   `presignDownload`/`presignUpload` L184-215 由 `clientFor(bucket)` 切换为按桶
   路由的 presign 客户端（`dvc-cache` 仍走 scoped 凭据——068 凭据隔离口径不回归）。
4. **TTL 对齐**（UploadApplicationService.java L53）：`PRESIGN_EXPIRY`
   30 → `Duration.ofMinutes(15)`。
5. **compose 接线**（deploy/compose/compose.yaml minio 服务）：`ports` 增
   `"127.0.0.1:9000:9000"`（与 9001 console 同口径仅回环，不暴露局域网）；
   `environment` 增 `MINIO_API_CORS_ALLOW_ORIGIN: ${MINIO_CORS_ALLOW_ORIGIN:-http://localhost:8080,http://localhost:5173}`、
   `MINIO_API_CORS_ALLOW_METHODS: GET, PUT, HEAD`、
   `MINIO_API_CORS_ALLOW_HEADERS: '*'`、
   `MINIO_API_CORS_EXPOSE_HEADERS: ETag, Content-Range`（dev/验收基线口径；
   生产多域名属部署面另议，卡 §7 登记）。
6. **.env.example**（L49-50）：注释修正为「经宿主机 9000 端口直连（不经 Nginx，
   REQ-UPL-002 L187 不代理 bytes）」并新增 `MINIO_CORS_ALLOW_ORIGIN` 示例。
7. **e2e 解除桩**（frontend/e2e/upload-flow.spec.ts）：删除
   `installStagingPutStub` 的 `page.route(hostname==='minio')` 主机桩及头部
   L9-21 移交注释（改为 070 收口叙述）；浏览器 PUT 断言等待
   `hostname==='localhost' && port===9000` 的真实响应；单 Part 失败注入改为仅
   对指定请求 `route.abort()`（网络层故障模拟，成功路径零桩——卡 §5 如实登记
   该边界：HTTP 500 + XML 错误体的应用层故障形态由既有 Vitest/服务端测试覆盖）；
   `stageRealParts`/`putPartViaContainer`（服务端对账场景的 API 侧预置通道）保留。
8. **后端测试**（新增 MinioConfigurationTest.java + 适配
   MinioStorageAdapterTest.java）：断言 `externalEndpoint` 配置时 presign 客户端
   用公开端点、ops 客户端保持内网、未配置时全回退内网（worker 语义）；
   adapter presign 路由到 presign 客户端且 dvc-cache 凭据隔离不变。
9. **治理文档**：work-breakdown L86 行实施状态回写；EVD-P0BR-070-001.yaml。

## 5. 垂直切片与验收

| # | 切片 | 证据 |
|---|------|------|
| 1 | application.yml 绑定名 + MinioConfiguration presign 端点解析 + presign 双客户端 + adapter 路由 + TTL 15min | MinioConfigurationTest 新用例 + `mvnw verify` 全绿不回归 |
| 2 | compose：9000 回环映射 + CORS 四变量 + .env.example 注释 | `docker compose config --quiet` + `verify.ps1` V01-V28 无回归 + 栈起后 OPTIONS 预检 curl 取证（Expose-Headers 含 ETag） |
| 3 | e2e 解除主机桩、浏览器真实 PUT | `pnpm test:e2e` upload-flow 全切片通过 + 全量 112 基线不回归（skip/notProven 不可接受）；presign URL host 断言 `localhost:9000` |
| 4 | AC-P2-UPL-002/004 证据绑定（含 TTL≤15min、刷新对账、失败重传、日志无完整 URL） | EVD-P0BR-070-001.yaml（secrets 脱敏确认：仅记录 host 与参数名） |
| 5 | work-breakdown 回写 + 本卡 §8 完成报告 | 治理三连（validate-spec / plain / -CheckChangedPaths） |

## 6. 门禁清单（实施收口时逐项勾选）

- [x] validate-spec / validate-task-card（含 -CheckChangedPaths）PASS
- [x] backend `./mvnw verify` PASS（新增测试 + 既有 777 基线不回归；实得 surefire 788/0/0/6，新增 MinioConfigurationTest 6 + adapter 适配）
- [x] frontend `pnpm lint / typecheck / test / build` PASS（不改前端源码，纯基线守护；test 以 `--no-file-parallelism` 串行 228/228，并行轮 ProfilePage 2 用例负载性超时已登记 EVD limitations）
- [x] `docker compose config --quiet` + V01-V28 PASS（经 Git bash 执行 `./scripts/verify.sh`——verify.ps1 头注自证未同步 V12+，verify.sh 为仓库权威完整面；37 项全 PASS 零 SKIP）
- [x] 真实 Compose 栈：backend/minio 重建后 upload-flow.spec.ts 无桩新口径 +
      全量 e2e 基线不回归（8080：upload-flow 7/7 全绿、111/112——唯一失败为
      settings-form⑧ reload 撞既存 nginx /assets/ 遮蔽、非本卡面；
      5173 历史基线口径：112 passed / 0 failed / 0 skipped）
- [x] EVD-P0BR-070-001.yaml 完成且预签名 URL/凭据脱敏确认
- [x] work-breakdown 070 行状态回写

## 7. 越界与禁止事项

- **不改 contracts**：URL host/TTL 属实现与部署口径，OpenAPI 无变更；
  `x-implementation-status` 上调仍待统一策略（治理遗留）。
- **不改 frontend/src**：前端直接消费后端 URL，零改动即受益（F-8）；若 e2e 真实
  直传暴露前端新缺陷，触发停止条件、另卡处置。
- **不改 worker**：无 presign 消费面（§3.3）；DVC 物化继续走容器内网。
- **不改 V1/V2 迁移、bootstrap、nginx.conf**：nginx 代理 bytes 路线被
  REQ-UPL-002 L187 明文排除（F-6），本卡不以反代替代直连。
- **不放宽凭据隔离**：presign 客户端沿用主/scoped 双凭据分桶路由（068 口径）；
  不以 root 凭据统一替代。
- **9000 仅回环映射**（`127.0.0.1:9000`）：不得放开为 `0.0.0.0` 暴露局域网；
  生产多域名/HTTPS 与 CORS 全量口径属部署面，另卡/另 ADR 裁定。
- **e2e 桩解除不得反向放宽**：失败注入仅可用网络层 `abort()` 模拟并如实登记
  边界；禁止以新桩替代真实 PUT、禁止 skip/降级任何既有断言。
- 本卡不追溯 069/071/072 已提交工件（TASK-P0BR-069.md 与 EVD-P0BR-069-001.yaml
  在 forbiddenPaths）。

## 8. 完成报告

实施日期 2026-09-30（HEAD 基点 `0805b60`；本卡改动未提交，提交由批准人授权后执行，
DEC-009）。§4 九步全部落地，与卡面方案一致；唯一步骤 5 微调：CORS 默认值扩为
双 Origin csv（`http://localhost:8080,http://localhost:5173`）——取证发现 8080 口径
浏览器真实 PUT 与 5173 历史 112 基线口径需同时放行（MinIO
`MINIO_API_CORS_ALLOW_ORIGIN` 原生支持 csv，官方文档与源码实证），仍属
「dev/验收基线口径」批准面；`.env.example` 同步注释。

```text
Completed:
  REQ-UPL-002 L172 缺陷链全链闭环——presign URL 宿主机可达（localhost:9000）、
  TTL ≤ 15min、浏览器真实 PUT 无桩、AC-P2-UPL-002/004 达 E4。
Changed files:
  backend: MinioConfiguration.java（resolvePresignEndpoint + presign 双客户端）、
    MinioStorageAdapter.java（presign 按桶路由，dvc-cache scoped 隔离不变）、
    UploadApplicationService.java（TTL 30→15min）、application.yml（绑定名对齐）、
    MinioConfigurationTest.java（新增 6 用例）、MinioStorageAdapterTest.java（适配）
  deploy: compose.yaml（9000 回环 + CORS 四变量双 Origin）、.env.example
  frontend: e2e/upload-flow.spec.ts（删主机桩、真实 PUT、abort() 注入、脱敏断言）
  docs: 本卡、EVD-P0BR-070-001.yaml、work-breakdown L86 回写
Contract/database changes:
  零契约、零迁移、零前端源码、零 nginx、零 worker（卡 §7 边界全程遵守；
  nginx /assets/ 遮蔽为既存缺陷仅登记移交，见下）
Validation run and results:
  ./mvnw verify BUILD SUCCESS（surefire 788/0/0/6；failsafe 123 IT 条件跳过同 071 口径）；
  docker compose config --quiet PASS；verify.sh（Git bash，权威完整面）V01-V28
  全 37 项 PASS 零 SKIP；OPTIONS 预检双 Origin 204 + 真实 PUT 403 响应头
  Expose Etag/X-Amz* 取证；e2e 双口径——8080：upload-flow 7/7 全绿、总 111/112
  （唯一失败 settings-form⑧ reload 撞既存 nginx /assets/ 遮蔽，curl 实证 404、
  本卡零 nginx 改动、5173 同 spec 全绿，非本卡面回归）；5173（历史基线口径）：
  全量 112 passed / 0 failed / 0 skipped exit 0；Vitest 串行 228/228、
  lint/typecheck/build exit 0；治理三连 PASS（-CheckCompletion 交接态 EXPECTED_FAIL）
Validation not run and reasons:
  verify.ps1 未跑：pwsh 不可用 + 其头注自证未同步 V12+（verify.sh 为仓库权威、
  已以更完整面覆盖）；oasdiff/redocly 未跑：零契约改动
Remaining risks or follow-ups:
  ①既存 nginx「location /assets/」遮蔽 SPA 深链（整页加载 404）——建议另立
    基础设施卡（try_files 回退或分离构建产物路径）；
  ②生产多域名 CORS/HTTPS 与 9000 暴露策略属部署面另议（卡 §7）；
  ③/dvc/remote-config 附带受益的 E4 属 verify-journey.sh 旅程范围；
  ④本卡 E4 人工验收（review.accepted）与提交待验证权威/批准人处置——
    当前为 IMPLEMENTED_UNVERIFIED。
```
