---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-077
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: a0227961e085a6feae627bff7fcc276937b63b6b
stageGatePassed: true
stageGateEvidence: 已授权（批准人 User，2026-10-10，审阅 DRAFT 后批复「批准两卡，按序实施」；
  AUDIT 差异点——迭代计划 §4.3 清单第 3 项原文「+审计」vs 本卡沿 068 预览族无审计口径——
  按批准人选定本卡方案执行，裁定留痕见 iteration-plan §4.4 与 EVD。差距事实链
  （2026-10-10 以 baseCommit 复证）：
  frontend/src/features/assets/AssetDetailPage.tsx L448-453 Files Tab Tree 硬编码
  selectable={false}，叶子节点无任何内容浏览入口；contracts/openapi/aihub-v1.yaml
  listArtifacts（L2984-3009）仅返回扁平元数据清单，仓内不存在任何单文件内容端点
  （文本/CSV/JSON 均不可见）；REQ-DST-DETAIL-001 页面组成第 3 项要求 Files 绑定精确
  Version 并可浏览，iteration-plan-2026-09-24.md §4.3 任务清单第 3 项（G1）指定本卡号。
  实施前提：068 已闭合渲染代理先例（EVD-P0BR-068-001），工作树干净、无 allowedPaths 重叠未定项。
stageGateEvidenceRefs:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-068-001.yaml
requirements:
  - REQ-DST-DETAIL-001
acceptanceScenarios:
  - AC-DST-DETAIL-001
  - AC-DST-DETAIL-003
decisions:
  - DEC-008
scenarioEvidencePlan:
  - AC-DST-DETAIL-001|E4|真实 Compose 栈 Playwright：上传含 fixture CSV/TXT 的数据集版本并发布后，
    详情页 Files Tab 树节点可选中，选中后内容面板以纯文本渲染该 artifact 字节（与 sha256 一致的
    源内容前缀），面板不含任何内部主机名/对象键；切换 Version 后面板随版本化 Query Key 重置；
    Vitest 组件测断言选中态、text/plain 渲染（无 dangerouslySetInnerHTML）与取消选择回退空态
  - AC-DST-DETAIL-003|E4|Playwright：无 asset:read 主体直连内容端点 403、匿名 401；契约测试断言
    未知/跨绑定 artifactId 与「不存在」同一 404 防枚举语义；白名单外 mediaType（image/png、
    application/octet-stream、text/html）415；size 超 maxTextBytes 413
crossCuttingPlan:
  - AUTHN|复用既有 JWT 过滤器与 PrincipalContextHolder；新端点属受保护业务读接口，不新增匿名面；
    前端经既有 axios 实例取 Blob（068 client.ts getBlob 先例，内存 JWT 自动注入，令牌不进 URL）
  - AUTHZ|在应用服务内 requirePermission("asset:read")（fail closed，先于任何存储访问，与
    renderPreviewArtifact/DownloadApplicationService.issueTicket 同族口径）；artifact 必须属于给定
    versionId、version 必须属于给定 assetId，跨绑定一律同 404 防枚举；不新增/不放宽任何权限口径
  - DB_FILTER|N/A - 不改任何列表查询与权限下推；单资源读取按 assetId/versionId/artifactId 主键
    链路校验归属；文件树沿用既有 listArtifacts 数据（前端 buildArtifactTree 已实现，不新增树端点）
  - STATE|不改变 VersionStatus 取值；内容读取仅允许 PUBLISHED 版本（与渲染/下载同口径），
    非 PUBLISHED 返回 VERSION_STATE_NOT_ALLOWED(409) 前先完成权限检查（防状态探测）
  - IDEMPOTENCY|N/A - 纯只读 GET，无写操作、无副作用状态
  - CONSISTENCY|N/A - 单存储读（dvc-cache），对象键仅由服务端 artifact 记录的 dvcHash 经既有
    PreviewJobHandler.dvcObjectKey 推导（禁止复制漂移实现），不跨系统写入；对象缺失映射
    PREVIEW_SOURCE_UNAVAILABLE，与 068 同口径
  - ERRORS|复用 068 已新增的 PREVIEW_NOT_FOUND(404)/PREVIEW_UNSUPPORTED_FORMAT(415)/
    PREVIEW_TOO_LARGE(413)/PREVIEW_SOURCE_UNAVAILABLE(404) 与既有 ASSET_NOT_FOUND/
    VERSION_NOT_FOUND/AUTH_PERMISSION_DENIED/VERSION_STATE_NOT_ALLOWED；本卡不新增 ErrorCode 枚举项
  - AUDIT|沿用 068 经批准人确立的口径：页面内受 maxTextBytes 上限约束的内容读取等同渲染语义，
    不新增审计事件类型（DOWNLOAD_TICKET_ISSUED 对应可离线外带的完整下载票据，语义不同族）；
    如验证权威认定需审计，属口径变更须批准人裁定，不在本卡擅自扩缩
  - NOTIFICATION|N/A - 不触发通知
  - TAXONOMY_I18N|Files Tab 内容面板新增文案（选中提示、超限/不支持/失败提示）须 zh/en 同步
    （assets.filesContent* 键族）
  - CONFIG|PreviewProperties 增 maxTextBytes（默认 1 MiB，@ConfigurationProperties 内置默认值，
    不改 deploy/；application.yml 显式键仅在需要覆盖时加入本卡允许的 resources 路径）；
    内容白名单固定为 text/plain、text/csv、text/markdown、application/json、application/x-ndjson、
    application/jsonl（服务端常量；text/html 与 image/* 明确排除——前者不扩大注入面，后者已由
    068 渲染代理覆盖）；新增类型须经规格变更而非代码私改
  - OBSERVABILITY|沿用 PlatformMetrics 预览失败计数既有 reason 键（limit_exceeded/
    unsupported_format/source_unavailable），不新增指标或告警
  - SECRETS|对象存储凭据、内部 Endpoint、bucket 名、对象键、预签名查询串一律不进入响应体/日志/
    页面数据（REQ-DST-DETAIL-001 L111）；响应仅回字节流与 Content-Type
allowedPaths:
  - contracts/openapi/aihub-v1.yaml
  - backend/src/main/java/com/aihub/version/infrastructure/PreviewProperties.java
  - backend/src/main/java/com/aihub/version/application/PreviewApplicationService.java
  - backend/src/main/java/com/aihub/version/api/PreviewController.java
  - backend/src/test
  - frontend/src/features/assets/AssetDetailPage.tsx
  - frontend/src/features/assets/ArtifactContentPanel.tsx
  - frontend/src/features/assets/ArtifactContentPanel.test.tsx
  - frontend/src/shared/api/client.ts
  - frontend/src/shared/i18n
  - frontend/e2e
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/04-ui/page-catalog.md
  - docs/ai-spec/tasks/TASK-P0BR-077.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-077-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - deploy
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-077-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-077.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-077.md -CheckChangedPaths
  - ./mvnw.cmd -q verify（backend 目录；覆盖内容代理契约测试与越权/边界用例）
  - pnpm lint（frontend 目录）
  - pnpm typecheck（frontend 目录）
  - pnpm test（frontend 目录；Vitest 含 ArtifactContentPanel 用例）
  - pnpm build（frontend 目录）
  - pnpm test:e2e（frontend 目录；真实 Compose 栈 E4，含新文件内容浏览用例全量无回归）
  - npx @redocly/cli@latest lint contracts/openapi/aihub-v1.yaml --config contracts/openapi/redocly.yaml
  - npx @redocly/cli@latest diff EVD-P0BR-077-base.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking（EVD-P0BR-077-base.yaml 由 git show baseCommit 导出为变更前基线；上游 redocly diff 已移除时按 067/068 先例以 oasdiff breaking 或基线纯新增 numstat 论证非破坏，环境不可得登记 notProven）
approvedBy: User
approvedAt: 2026-10-10T06:30:00Z
---

# TASK-P0BR-077：数据集 Files 内容浏览（版本工件文本内容服务端代理）

> 状态：`READY`（批准人 User，2026-10-10 批复「批准两卡，按序实施」后回写审批位，DEC-009；AUDIT 口径按本卡方案=沿 068 预览族无审计现状，迭代计划 §4.3 原文「+审计」差异已在 stageGateEvidence 留痕）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。

## 1. 目标

有权主体在数据集详情页 Files Tab 可点击版本文件树中的文本类工件（CSV/TXT/JSON/JSONL/Markdown/NDJSON）并在页面内看到其真实内容；无权限主体、越界 artifactId、白名单外类型和超限对象全部 fail closed。修复迭代计划 §4.3 复证的 G1 断链（Tree `selectable={false}`、契约无内容端点）。

## 2. 关联规格

```yaml
requirements: [REQ-DST-DETAIL-001]
acceptanceScenarios: [AC-DST-DETAIL-001, AC-DST-DETAIL-003]
decisions: [DEC-008]
adrs: [ADR-0001]
openapiOperations: [listArtifacts（复用不改）, 新增 getArtifactContent]
mcpTools: []
eventSchemas: []
flywayBaseline: V43（无新增 Migration；纯读代理，不改任何表）
```

- `REQ-DST-DETAIL-001`（READY，E4）：页面组成第 3 项要求 Files 绑定选定 Version；L109 内容视为
  不可信数据安全渲染；L110 防枚举语义；L111 预签名查询串/内部 Endpoint 不进页面数据——
  本卡全部设计约束的直接来源。
- `AC-DST-DETAIL-001`（NORMAL）与 `AC-DST-DETAIL-003`（UNAUTHORIZED）取自
  `05-acceptance/dataset-agent-exit-catalog.md`，均要求 E4。

## 3. 范围

### 允许修改

- 契约：`GET /assets/{assetId}/versions/{versionId}/artifacts/{artifactId}/content`
  （operationId `getArtifactContent`；`x-required-permissions: [asset:read]`；200 响应
  `text/plain|text/csv|text/markdown|application/json|application/x-ndjson` 字节流；4xx 复用既有
  ErrorResponse，`x-error-codes` 列全）。登记 `x-implementation-status: implemented-unverified`。
  文件树不新增端点：`listArtifacts` 扁平清单 + 前端 `buildArtifactTree` 已满足树形展示，
  本卡仅补内容通道（迭代计划 §4.3 第 3 项「版本文件树」部分由既有实现承接，登记于此）。
- 后端：
  1. `PreviewApplicationService`：新增 `getArtifactContent(assetId, versionId, artifactId)` 用例——
     校验链与 `renderPreviewArtifact` 完全同族（权限先行 → 版本归属绑定 → PUBLISHED →
     artifact 归属 → mediaType 白名单 → size 预检 + 流式上限双保险 → 对象缺失同 404 防枚举）；
     对象键复用 `PreviewJobHandler.dvcObjectKey(dvcHash, path)` 既有算法（同包工具，禁止复制漂移实现）；
  2. `PreviewController`：新 GET 端点（纯适配器，无业务规则）；
  3. `PreviewProperties`：增 `maxTextBytes`（默认 1 MiB）。
- 前端：
  1. `AssetDetailPage.tsx` Files Tab：Tree 由 `selectable={false}` 改为可选中（仅目录不可选），
     `onSelect` 驱动内容面板；叶子 key 现含序列化 artifact（buildArtifactTree L82-125），实施时改为
     携带 artifactId 的可解析 key；
  2. 新增 `ArtifactContentPanel.tsx`：经既有 apiClient Blob 通道（068 `getBlob`）取字节流，
     `text()` 解码后以 `<pre>` 纯文本渲染（绝不 `dangerouslySetInnerHTML`——内容是不可信数据）；
     超限 413/白名单外 415/失败态给出对应文案与「使用下载获取完整内容」引导（复用既有下载票据入口）；
     组件卸载/换 artifact/换版本时释放状态；
  3. `client.ts`：如 068 `getBlob` 不能直接表达本路径形态，仅**新增**方法，不改既有方法行为。
- i18n：`assets.filesContent*` 新键 zh/en 对称。
- e2e：`frontend/e2e` 新用例（fixture CSV/TXT 随 e2e 目录入库；上传/发布链复用既有 e2e helper 与 068 fixture）。
- 文档：本卡、EVD、work-breakdown 状态列、page-catalog PAGE-DST-001/PAGE-VER-003 中「文件内容浏览」口径登记。

### 明确不在范围

- Split/统计（G5）→ `TASK-P0BR-078` 独立成卡；G4 收藏（REQ/PAGE 人类评审门禁在前）不触碰。
- 二进制文件内容展示、在线编辑、Range 分段拉取：超限一律 413 + 下载引导，不做截断流。
- 不新增审计事件类型（§5 AUDIT 口径）、不新增 ErrorCode 枚举项（068 四项已覆盖语义）。
- `DownloadApplicationService`、上传链路、CI redocly diff 既存缺陷均不改。

### 禁止变化

- 不允许把内部 Endpoint、bucket 名、对象键、presigned 查询串写入响应体、日志或前端可见 JSON。
- 不允许把文件内容以 HTML 语义渲染（XSS）；不允许用户 supplied 的路径/URL 参与对象键解析
  （对象键只从服务端 artifact 记录推导）。
- 不改变既有 `listArtifacts`、previews 三操作的行为与契约。
- 不新增权限口径、不修改任何 `x-required-permissions` 既有值；不改已应用 Migration。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-DST-DETAIL-001` | USER(有 asset:read) | PUBLISHED 版本含 text/csv artifact | Files Tab 点击该树叶子 | 内容面板纯文本渲染字节内容；页面数据不含内部主机名/对象键 | `E4` |
| `AC-DST-DETAIL-001` | USER | 同上 | 切换 Version | 面板随版本化 Query Key 重置；不再展示旧版本内容 | `E2`+`E4` |
| `AC-DST-DETAIL-003` | 匿名 | 无 Token | GET getArtifactContent | 401（过滤器 fail-closed） | `E2` |
| `AC-DST-DETAIL-003` | USER(无 asset:read) | 存在他人/无权资产 | GET | 403，先于任何存储访问；无对象存在性差异泄露 | `E2` |
| `AC-DST-DETAIL-003` | USER(有 asset:read) | artifactId 不属于该 version / version 不属于该 asset | GET | 与「不存在」同一 404 语义（防枚举） | `E2` |
| 边界 | USER | artifact mediaType=image/png、application/octet-stream 或 text/html | GET | 415 PREVIEW_UNSUPPORTED_FORMAT（白名单显式排除） | `E2` |
| 边界 | USER | artifact size > maxTextBytes | GET | 413 PREVIEW_TOO_LARGE，不缓冲全量进内存（size 预检 + 流式上限双保险） | `E2` |
| 相邻失败 | USER | dvc-cache 对象缺失 | GET | PREVIEW_SOURCE_UNAVAILABLE 稳定错误；不伪造空内容 | `E2` |

覆盖正常/边界/失败/越权；无写操作故无并发/恢复切片。

## 5. 横切要求

以 Front Matter `crossCuttingPlan` 13 键为准，不另行放宽。

## 6. 契约与数据先行

1. **OpenAPI**：新增 `getArtifactContent` 操作（response `content` 白名单 mediaType，
   `format: binary`；`x-error-codes` 按 §2 列全）；纯新增路径 → 非破坏取证必须 PASS。
2. **示例**：无既有 schema 变更；端点 description 写明「内容为不可信数据，调用方必须按纯文本呈现」。
3. **新增 Flyway 文件**：无（纯读代理，无数据变更；`version_artifact` 元数据已足够）。
4. **存量清点与回填**：不适用。
5. **索引/约束**：不变。
6. **兼容窗口与失败恢复**：后端先发（新端点），旧前端 Files Tab 本就不可选中、无回归面；
   新前端遇旧后端 → 内容端点 404 → 面板错误态 + 下载引导。回滚顺序：前端 → 契约回退
   （端点无人调用即无害）。
7. **生成类型更新**：本仓前端 API 类型手工同步（067/068 同口径）；`ArtifactContentPanel` 内
   ArtifactView 分量复用既有类型。

## 7. 实施步骤

- [x] 契约及契约测试（OpenAPI 新增 getArtifactContent + redocly lint；后端越权/边界矩阵测试）（EVD-P0BR-077-001 execution①④；redocly exit 0、ContentTest 20 用例含 415/413/404 防枚举/403/401 矩阵）
- [x] Migration：不适用（§6.3，无数据变更）（零迁移兑现——dirtyPaths 中 V44 属兄弟卡 078 产物，见 EVD 归属注释）
- [x] Application 用例（getArtifactContent 校验链；复用 dvcObjectKey 与 068 守卫实现）（execution②；asset:read 先行→归属绑定→PUBLISHED→text 白名单→maxTextBytes 预检+流式双保险→统一 404；对象键用户输入零参与）
- [x] REST Adapter（PreviewController 流式 GET，纯适配器；MCP/Worker 不涉及）（execution②；控制器零业务规则，委托应用服务）
- [x] 前端 Tree 可选中 + ArtifactContentPanel + client 通道 + i18n zh/en（execution③；getBlob 为 068 既有通道本卡零 diff 仅消费——见 EVD environmentDifferences；zh/en 各 +15）
- [x] 正常/边界/失败/越权测试（Vitest + 后端单测/契约测试）（execution④⑤；ArtifactContentPanel.test 7 用例、Vitest 50/239 全绿、mvnw 835/0/0/10 合计口径）
- [x] Compose Fixture/Verify（真实栈 e2e：fixture CSV/TXT 全链 + 泄漏红线 + 越权 403）（execution⑥⑦；artifact-content.spec.ts 4/4、与 078 合跑 8/8、UI 渲染/切换重置/错误文案/泄漏红线）
- [x] 文档同步（work-breakdown 077 状态列、page-catalog 口径登记）（work-breakdown L93 状态段 + page-catalog §4 表下实施注记 PAGE-VER-003 面）

勾选必须附 Evidence Manifest 条目；提交代码本身不能作为勾选依据。

## 8. 验证命令

```text
治理        validate-spec / validate-task-card / -CheckChangedPaths        各 exit 0
backend/    ./mvnw.cmd -q verify                                            exit 0   全 AC 后端切片
frontend/   pnpm lint; pnpm typecheck; pnpm test; pnpm build                各 exit 0  组件层
frontend/   pnpm test:e2e（真实 Compose 栈，凭据注入同 066/067/068 口径）     exit 0   E4 主证
contracts/  redocly lint + 非破坏取证（§FM 替代路径）                        exit 0   契约非破坏
```

SKIP 判定：任何因 Docker/凭据缺失导致的跳过视为 notProven，不构成 PASS（既有口径）。

## 9. 停止条件

- 批准人认定 AUDIT 口径应为内容读取新增审计事件 → 停该部分并回报（§3 明确不在范围第 3 条）；
- 工作树存在与本卡 allowedPaths 重叠且来源不明的变更 → 不得开工；
- e2e 上传链无法在现有 helper 下产出 PUBLISHED 含文本工件版本 → 不得降级为 E2/E3 交差，
  如实记 notProven 并回报；
- 发现对象键解析存在任何用户输入参与路径 → 立即停止（§3 禁止变化）；
- 需要改动 ErrorCode 枚举、权限口径或任何 forbiddenPaths 才能推进 → 停止回报等待补正裁定。

## 10. 完成报告

```text
Completed:
契约先行新增 GET .../artifacts/{artifactId}/content（getArtifactContent）；后端校验链与 068 同族落地
（PreviewProperties.maxTextBytes 1 MiB 默认 + PreviewApplicationService.getArtifactContent + PreviewController
流式 GET 纯适配器；零新增 ErrorCode、零迁移、零新增审计事件——AUDIT 沿 068 批准人口径，§9 裁定点未触发）；
前端 Files Tab Tree 可选中 + ArtifactContentPanel（getBlob → <pre> 纯文本，禁 dangerouslySetInnerHTML，
超限引导下载票据）+ i18n zh/en；work-breakdown/page-catalog 登记。完整证据见 EVD-P0BR-077-001。

Changed files:
见 EVD-P0BR-077-001 source.dirtyPaths（逐文件归属注释，兄弟卡 078 产物同工作树交叠单列）。本卡面：
contracts/openapi/aihub-v1.yaml、PreviewProperties/PreviewApplicationService/PreviewController、
PreviewApplicationServiceContentTest、AssetDetailPage.tsx、ArtifactContentPanel.tsx/.test.tsx、
i18n zh/en、e2e artifact-content.spec.ts + fixtures、work-breakdown、page-catalog、本卡、EVD。
client.ts getBlob 为 068 既有通道，本卡零 diff 仅消费（EVD environmentDifferences 说明）。

Contract/database changes:
OpenAPI 纯新增路径 getArtifactContent（response 白名单 mediaType + format: binary；两卡合计 97 增 1 删、
redocly exit 0 非破坏）；数据库零变更。

Validation run and results:
mvnw -q verify 835/0/0/10（两卡合计口径，含 ContentTest 20 用例全绿）；vitest run --maxWorkers=2
50 files/239 tests 全绿 exit 0（ArtifactContentPanel.test 7 定向全绿）；pnpm lint/typecheck/build exit 0；
redocly lint exit 0；Compose E4 真实栈 artifact-content.spec.ts 4/4、与 078 合跑 8/8（UI 链 + API 矩阵
200 字节一致性/415/413/404 防枚举/403/401）；治理三连终态：validate-spec PASS、validate-task-card PASS、
-CheckChangedPaths FAIL（15 issue，EVD 四段归因）、-CheckCompletion EXPECTED_FAIL（交接态）。

Validation not run and reasons:
oasdiff 专用工具环境不可达——沿 068/070 先例以纯新增 diff + redocly lint 等价论证；
对象键路径遍历无独立渗透 e2e（构造性论证 + notProven 登记，沿 068 同口径）；
E4 人工验收（review.accepted）不在 AI 权限内（DEC-009）。

Remaining risks or follow-ups:
① -CheckChangedPaths 15 issue 中的非产品脏目录（build-logs/playwright-report/test-results）与
iteration-plan 增量留批准人处置（.gitignore/清理）；② 卡 §4「application.yml 显式键可加入允许的
resources 路径」文述与 allowedPaths 15 项枚举的既有张力——本卡实际零直接改动、未触发，留裁定防后卡踩坑；
③ AUDIT 若批准人改判应新增内容读取审计事件 → 属 §9 停止条件，另行补正；④ 本卡为
IMPLEMENTED_UNVERIFIED：提交与 review.accepted 由验证权威处置（AI 不自提交、不自批，DEC-009）。
```
