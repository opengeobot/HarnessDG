---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-069
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 8fb8c322635698c430486b4f7eb3dbe0b882a3ab
stageGatePassed: true
stageGateEvidence: 已授权（批准人 User，2026-09-29，同场批准 presign 公开端点缺陷另立后续卡）。
  差距事实链（2026-09-29 第三/四轮取证，
  见 docs/architecture/modelscope-page-gap-analysis-2026-09-21.md §6 覆盖矩阵）：
  全量 e2e 101/101 passed 之外仅剩两处点击级测试缺口——①上传中心 UI 交互
  （UploadPage 的创建会话/分片进度/刷新对账/complete/取消全链无 e2e；preview-image.spec.ts
  仅以 API 链注入 fixture，L123 注释明确「presign URL 中的 host 在宿主机不可达，必须在
  minio 容器内发起 PUT」）；②Settings 表单编辑（AssetSettingsPage 有 Vitest 冲突/失败分支，
  e2e 仅点击可达级）。
  实施前提已闭合：TASK-P0BR-068 改动已由批准人授权提交为 8fb8c32（本卡 baseCommit），
  frontend/e2e 重叠风险解除。
approvedBy: User
approvedAt: 2026-09-29T08:10:17Z
stageGateEvidenceRefs:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-068-001.yaml
requirements:
  - REQ-UPL-001
  - REQ-UPL-002
  - REQ-UI-001
acceptanceScenarios:
  - AC-P0B-UI-003
  - AC-P0B-UI-005
  - AC-P0B-UI-006
decisions:
  - DEC-008
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-UI-006|E4|真实 Compose 栈 Playwright 新 spec：上传中心完整 UI 旅程——深链进入
    /upload?assetId&versionId → 选择 fixture 文件 → 创建真实 Upload Session（服务端 PG+MinIO
    multipart）→ 浏览器 PUT 经 page.route 桩（presign 主机 minio:9000 宿主机不可达，见 §3
    可测性前提）→ 断言 Part 进度/完成态/ETag 回写/complete 门控 → 容器内真实 PUT 全 Parts →
    携带 sessionId 刷新页面 → 断言服务端对账恢复（COMPLETED 状态与真实 ETag 来自 GET session，
    非 LocalStorage）→ UI 点击 Complete 触发真实服务端核对与物化 Job → 断言 COMPLETED；
    Settings 编辑保存正路径 + 校验失败态 + 409 冲突文案（Vitest 既有断言的浏览器级复证）
  - AC-P0B-UI-003|E4|Settings 页面 Loading/Empty/Error 与上传页无深链参数时的引导空态
    （Empty + 目录链接）在浏览器级断言；上传失败路径用 page.route 桩 500/断连触发 UI 错误态
    与 Retry Part 渲染
  - AC-P0B-UI-005|E4|Settings e2e 在 zh-CN 与 en-US 两语言下断言标签、按钮与后端错误文案
    按 i18nKey 正确渲染（复用既有 locale 切换 helper）
crossCuttingPlan:
  - AUTHN|纯 e2e 测试改动：复用 frontend/e2e/support.ts 既有登录（内存 access JWT +
    refresh Cookie 口径不变）与 api helper；凭据来自 E2E_* env（gitignored deploy/compose/.env）；
    测试输出与断言消息不打印 Token 或 presign 完整查询串
  - AUTHZ|主体夹具沿用既有 author（含 asset:manage/asset:upload）；越权断言沿用
    publish-flow.spec 与 preview-image.spec 既有 403/防枚举口径；本卡不新增、不修改、
    不放宽任何权限判定
  - DB_FILTER|N/A - 不改任何列表查询与权限下推 SQL
  - STATE|测试断言 UploadSession 既有状态机的 UI 门控（complete 按钮仅全部 Parts 完成且
    OPEN 时出现；取消后回空态）；不改后端状态转换与 VersionStatus
  - IDEMPOTENCY|N/A - 不改任何写操作实现；complete 重放语义属后端既有测试面
  - CONSISTENCY|N/A - 不改物化/对账链；UI 刷新对账断言只读既有 GET session 契约响应
  - ERRORS|N/A - 不改错误映射；错误态断言以既有后端返回码/错误码为准，桩响应必须与真实
    后端错误形态（ErrorResponse schema）一致，禁止为通过而构造宽松桩
  - AUDIT|N/A - 测试改动不触碰审计实现；审计完整性沿用既有 E4 断言
  - NOTIFICATION|N/A - 不触发通知面
  - TAXONOMY_I18N|Settings/上传 e2e 文案断言 zh/en 对称（AC-P0B-UI-005）；键名取自
    frontend/src/shared/i18n 现值，不新增键
  - CONFIG|N/A - 不改任何配置装配；deploy/ 属 forbiddenPaths，presign 公开端点缺口仅登记
    移交（§3 可测性前提 + §10 后续项），不在本卡修
  - OBSERVABILITY|N/A - 不新增指标/告警
  - SECRETS|page.route 桩与日志不落盘 presign 查询串、对象存储凭据；fixture 数据不含真实
    敏感内容
allowedPaths:
  - frontend/e2e
  - frontend/src/features/upload/UploadPage.tsx
  - frontend/src/features/upload/UploadPage.test.tsx
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/04-ui/page-catalog.md
  - docs/architecture/modelscope-page-gap-analysis-2026-09-21.md
  - docs/ai-spec/tasks/TASK-P0BR-069.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-069-001.yaml
preExistingDirtyPaths:
  - build-logs/verify-bom.ps1|b5003338d283e84b4b6dabaf0d9e47b6d90fe18b780d0fd2e59851d467ac9b3d
forbiddenPaths:
  - deploy
  - contracts
  - backend
  - V1__baseline.sql
  - V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-069-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-069.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-069.md -CheckChangedPaths
  - pnpm lint（frontend 目录）
  - pnpm typecheck（frontend 目录）
  - pnpm build（frontend 目录）
  - pnpm test:e2e（frontend 目录；真实 Compose 栈全量无回归 + 新 spec 通过，凭据注入同 066/067/068 口径；skip 视为 notProven）
---

# TASK-P0BR-069：上传中心与 Settings 表单的浏览器级点击测试补强（E2E-only）

> 状态：`READY`（批准人 User 于 2026-09-29 批准并授权实施，DEC-009；baseCommit `8fb8c32` = TASK-P0BR-068 提交）
> 规则：一个任务只交付一个可独立验收的纵向结果——本卡纵向结果 = 「剩余两处点击级测试缺口闭合 + 缺口定性回写」。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。

## 1. 目标

第三轮差距复核（gap-analysis §6）确认层 0 六项缺陷全部闭环、101 条 e2e 全量通过后，仅剩两处
「实现已完成、浏览器级证据缺失」的覆盖面：①上传中心 UI（真实 PUT 分片、进度、刷新对账、complete 门控、
取消）；②Settings 表单编辑（保存正路径、校验失败、乐观锁冲突、i18n）。本卡以 e2e 测试与文档为主，
把这两处从「Vitest/API 级」提升到「浏览器点击级 E4」。
实施前取证（2026-09-29）发现契约/后端一致而前端违反的产品缺陷：`aihub-v1.yaml` L3486 与
`UploadApplicationService.completeSession` L186-190 均要求 complete 携带非空 `files`
（path/sha256/size/mediaType），而 `UploadPage.completeUpload` 只发 `parts` → UI 点击 Complete 必然 400，
§5「UI Complete 真实成功」切片被阻塞。已由批准人授权补正（第五处 allowedPaths 补正，
沿用 067/ErrorCode/client.ts/minio/PreviewPanel.test 先例）：本卡纳入
`frontend/src/features/upload/UploadPage.tsx` 与 `UploadPage.test.tsx`，
**仅最小修复 complete 载荷为携带 files（浏览器端 Web Crypto 计算 sha256）**，
不改其余上传交互行为、不触碰 backend/contracts/deploy。

## 2. 关联规格

```yaml
requirements: [REQ-UPL-001, REQ-UPL-002, REQ-UI-001]
acceptanceScenarios: [AC-P0B-UI-003, AC-P0B-UI-005, AC-P0B-UI-006]
decisions: [DEC-008, DEC-013]
adrs: [ADR-0001]
openapiOperations: []（零契约变更）
mcpTools: []
eventSchemas: []
flywayBaseline: V2（无新增 Migration）
```

- `REQ-UPL-001/002`（p2-version-transfer.md L119/L158，READY/MUST/E4）：Web Upload Session 与
  Multipart Part 签名、恢复与 complete——其中 L174「页面刷新后从服务端对账已完成 Parts，
  不盲信 LocalStorage」是本卡 UI 刷新对账断言的直接口径；
- `REQ-UI-001`（p0b-platform-services.md L373，READY/MUST/E4）：「关键页面有
  Vitest/Testing Library 和浏览器 E2E/视觉证据」「页面存在或 Vite build 不构成完成」；
- `AC-P0B-UI-003/005/006`（p0b-exit-catalog.md L137/L139/L140）。

## 3. 可测性前提与已知缺口登记（取证事实，2026-09-29）

1. **presign 主机在 compose 栈对浏览器不可达**：`MinioConfiguration.s3Presigner` 的
   `endpointOverride` 取 `properties.getEndpoint()`（compose 中为 `http://minio:9000`）；
   `MinioProperties.externalEndpoint` 只被 `DvcConfigurationService`（DVC remote 配置文本）消费，
   presign 路径不使用。且 compose.yaml L136 注入的 env 名是 `AIHUB_MINIO_PUBLIC_ENDPOINT`，
   而 application.yml L121 读的是 `AIHUB_MINIO_EXTERNAL_ENDPOINT`——两者不匹配，
   `externalEndpoint` 在 compose 栈实际为空。compose 亦仅暴露 minio `127.0.0.1:9001`（console）。
   ⇒ 浏览器对 presign URL 的真实 PUT 在此部署形态下**必失败**（preview-image.spec.ts L123 已实证并
   改为容器内 curl PUT）。
2. **由此产生的 REQ-UPL-002 L172 缺口（「URL 使用公开 MinIO Endpoint」）属实现/配置缺陷**，
   修复需 backend presign 端点重写 + deploy/compose env 接线 + 契约口径，超出本卡 E2E-only 范围
   且 deploy 属 forbiddenPaths——本卡只登记移交（§10），不擅自修复、不为此放宽 e2e 断言。
3. **本卡的诚实边界**：浏览器 PUT 用 `page.route` 桩（桩响应形态与真实 MinIO 一致：200 + ETag 头；
   失败路径桩 500/断连），覆盖的是**前端交互真实行为**（分片切分、进度事件、ETag 回读、门控、
   刷新对账、错误态）；**字节真实入存 + 服务端 complete 核对**经「容器内真实 PUT（沿用
   preview-image.spec 既有 helper 模式）→ UI 刷新对账 → UI 点击 Complete 真实成功」闭环，
   不伪造服务端结果。桩只替代浏览器不可达的最后一段网络。

## 4. 范围

### 允许修改

- `frontend/e2e`：新增 `upload-flow.spec.ts`、`settings-form.spec.ts`（含所需 fixture 与小 helper；
  如需抽取 preview-image.spec 中容器内 PUT 逻辑为共享 helper，仅限 e2e 目录内重构，断言强度不降）；
- `frontend/src/features/upload/UploadPage.tsx` + `UploadPage.test.tsx`（批准补正，§1 取证缺陷）：
  complete 载荷最小修复为携带 `files`（浏览器端对选定文件计算真实 sha256 + path/size/mediaType，
  对齐契约 L3486），并补对应 Vitest 断言；不改其余上传交互、门控、恢复或错误处理逻辑；
- 文档：本卡、EVD、work-breakdown 状态列、gap-analysis §6 覆盖矩阵更新、page-catalog 的
  `PAGE-UPL-002`/`PAGE-ADM-*` 相关行（如口径需同步）；
- 全量回归：`pnpm test:e2e` 在真实 Compose 栈通过（新用例并入后总数增加、零 skip、零 failed）。

### 明确不在范围

- 除批准补正的 `features/upload/UploadPage.tsx`/`UploadPage.test.tsx`（仅 complete 载荷 files 修复）外，
  不改 `frontend/src` 其余实现（含 AssetSettingsPage）、`backend`、`contracts`、`deploy`；
- 不修 presign 公开端点缺陷（§3.2 移交独立卡）；
- 不做 Preview Split/基础统计（REQ-DST-DETAIL-001 L95，另一独立卡）、不做 G1 逐文件浏览
  （体验增强，已定性）、不做订阅中心/收藏。

### 禁止变化

- 禁止用桩响应掩盖真实服务端失败：UI 点击 Complete 的成功路径必须走真实 complete 端点且服务端
  核对通过；若真实链不可达则如实记 notProven，不得降级为桩成功；
- 禁止 skip/fixme 规避、禁止放宽既有断言或权限口径；
- 禁止在测试日志/断言消息输出 presign 完整查询串、凭据（SECRETS）。

## 5. 行为切片

| 场景 ID | Actor | When | Then | 最低证据 |
| --- | --- | --- | --- | --- |
| `AC-P0B-UI-006` | author | 无参数进 /upload | 引导空态（Empty + 目录链接），无裸报错 | E4 |
| `AC-P0B-UI-006` | author | 深链 + 选文件 + 创建会话 | 真实 POST upload-sessions 成功；Session 卡显示 sessionId/totalBytes/fileCount；Parts 明细渲染 | E4 |
| `AC-P0B-UI-006` | author | 点击开始上传（PUT 桩） | 每 Part 进度→completed、ETag 回写、complete 按钮仅全完成后出现 | E4 |
| `AC-P0B-UI-006` | author | PUT 桩 500/断连 | 失败 Part 标 failed + Retry Part 渲染；重试仅重传失败 Part | E4 |
| `AC-P0B-UI-006` | author | 容器内真实 PUT 全 Parts 后带 sessionId 刷新 | 对账恢复：Parts 状态与 ETag 来自服务端 GET（非本地态）；提示 sessionRestored | E4 |
| `AC-P0B-UI-006` | author | UI 点击 Complete | 真实 complete 成功；状态转 COMPLETED；物化 Job 创建可经 session 查询证实 | E4 |
| `AC-P0B-UI-006` | author | 取消 | 真实 cancel；UI 回创建会话空态 | E4 |
| `AC-P0B-UI-003/005/006` | author | Settings 编辑显示名/简介保存 | 真实 PATCH 成功；重载持久化；zh/en 双语标签与文案断言 | E4 |
| `AC-P0B-UI-003` | author | Settings 必填清空提交 | 前端校验错误态渲染，无请求发出 | E4 |
| `AC-P0B-UI-006` | author | 乐观锁冲突（先经 API 改 version 使 updatedAt 落后） | 保存返回 409 时页面显示冲突错误、不覆盖远端值 | E4 |

覆盖正常/边界/失败/恢复；越权（他人 session）沿用 publish-flow/preview-image 既有 403 口径不重复。

## 6. 实施步骤

- [x] 前置：068 已由批准人提交（`8fb8c32`）；本卡置 READY 重绑 baseCommit
- [x] e2e：upload-flow.spec.ts（含桩 PUT helper 与容器内真实 PUT 复用）— 5 用例，定向轮次全过
- [x] e2e：settings-form.spec.ts（含 zh/en 双语与 409 冲突路径）— 4 用例，定向轮次全过
- [x] 全量 pnpm test:e2e（真实栈、凭据注入、零 skip）— 110 passed / 0 failed / 0 skipped，exit 0
- [x] EVD + work-breakdown + gap-analysis §6 + page-catalog 同步 — EVD-P0BR-069-001.yaml 已建；
      page-catalog 评估后无需改动（口径不冲突，见 EVD notProven 末条）
- [x] 治理三连（validate-spec / validate-task-card / -CheckChangedPaths）— 均 PASS
      （CheckChangedPaths 首跑 FAIL 28 项：build-logs 归档 .log 化、test-results 删除、
      autocrlf 假改动 checkout、verify-bom.ps1 登记 preExistingDirtyPaths 后复跑 PASS）

## 7. 验证命令

```text
frontend/  pnpm test:e2e（真实 Compose 栈）        exit 0，全量 passed/0 failed/0 skipped
治理       validate-spec / validate-task-card / -CheckChangedPaths   各 exit 0
```

SKIP 判定：任何因 Docker/凭据缺失导致的跳过视为 notProven，不构成 PASS（066/067/068 既有口径；
2026-09-29 教训：未注入 E2E_ADMIN_* 时全量 101 skipped 且 exit 0——skip≠通过，必须核验 passed 数）。

## 8. 停止条件

- 068 改动未提交、frontend/e2e 归属重叠未清 → 不得开工；
- 服务端真实 complete 链在 compose 栈不可达且无法以「容器内 PUT + UI 对账」闭合 → 如实 notProven 回报，
  不得以桩 complete 交差；
- e2e 暴露产品代码缺陷需要改 frontend/src/backend → 立即停止并回报（本卡 forbiddenPaths 覆盖之，
  缺陷修复须另立卡）；
- page.route 桩与真实 MinIO 响应形态出现无法对齐的分歧（ETag 头大小写/引号语义等）→ 停止并回报。

## 9. 交接清单

本卡完成时须同步登记：①presign 公开端点缺口（REQ-UPL-002 L172）移交后续卡
（批准人 2026-09-29 已批准另立契约优先新卡：backend presign 端点 + deploy env 接线 + 契约口径，
登记于 work-breakdown `TASK-P0BR-070` 预留行）；
②Preview Split/基础统计（REQ-DST-DETAIL-001 L95）独立卡；③G1 定性修正已回写 gap-analysis §6
（Files=Artifact 树 REQ 口径已满足，逐文件内容浏览为体验增强）。

## 10. 完成报告

**结论：两处点击级测试缺口闭合（IMPLEMENTED_UNVERIFIED，待验证权威验收）。**
证据见 EVD-P0BR-069-001.yaml（E4）。

- 交付：`frontend/e2e/upload-flow.spec.ts`（切片①-⑦，5 用例）、
  `frontend/e2e/settings-form.spec.ts`（切片⑧-⑩，4 用例）、
  `UploadPage.tsx`/`.test.tsx` 批准补正（complete 载荷携带浏览器端 Web Crypto 真实 sha256 的
  files，对齐契约 L3486；本地无文件诚实阻断；+2 Vitest 用例）。
- 全量 E4：`110 tests → 110 passed / 0 failed / 0 skipped`（3.2m，exit 0，= 068 基线 101 + 9）；
  lint 0 errors / typecheck exit 0 / build ✓ 28.47s / Vitest 49 files / 226 tests。
  本轮入口为 Vite dev 5173 + 真实 Compose 后端（`E2E_BASE_URL` 未设的 config 回退，
  与 066/067 同口径；068 为 nginx 8080 容器前端）。
- 治理：validate-spec / validate-task-card / -CheckChangedPaths 三连 PASS；`-CheckCompletion`
  按交接态如实 EXPECTED_FAIL（未提交 + review.accepted 须权威填写，DEC-009）。

**§8 停止条件命中与移交（须另立卡，本卡未越径修复）**：
1. [backend 缺陷 A] `Asset.updateMetadata`（Asset.java L182-198）对 displayName/description/
   organizationId/projectId/license 无条件覆盖（null 也写），与「为空表示不变更」契约矛盾；
   [backend 缺陷 B] displayName 被清 NULL 后任何非空回传触发 rename→insertAlias 与
   `ux_asset_alias_coordinate` 唯一键冲突 → 事务中止 → 500。证据：
   `build-logs/probe-069-patch.json.log` / `probe-069-patch2.json.log`（traceId
   15d7037034af44bcab25e810a262ecff）。库内坏夹具 `ast_01m3qzgjehtr7fc54xncvz2ymv`
   （display_name 已 NULL）建议随该卡清理。
2. 卡 §5 切片⑨「必填清空提交 → 无请求发出」为卡面与实现的事实偏差：AssetSettingsPage 无必填
   rules，请求真实发出并由后端拒绝。未放宽断言，按真实行为固化为诚实口径；前端必填校验缺失
   属产品缺口。
3. presign 公开端点（REQ-UPL-002 L172）→ `TASK-P0BR-070` 预留行（已登记 work-breakdown）；
   Preview Split/基础统计、G1 逐文件浏览仍为独立后续项。

Completed: 上传中心与 Settings 表单浏览器级 E4 覆盖（9 新用例）+ UploadPage complete 缺陷补正
Changed files: frontend/e2e/{upload-flow,settings-form}.spec.ts、frontend/src/features/upload/UploadPage.{tsx,test.tsx}、本卡、EVD-069、work-breakdown、gap-analysis
Contract/database changes: 无（零契约、零迁移、零 i18n 新键）
Validation run and results: 全量 e2e 110/110 passed 0 skipped exit 0；Vitest 226；build ✓；lint/typecheck 0 错误；治理三连 PASS
Validation not run and reasons: mvnw verify / redocly lint 未跑（本卡零后端、零契约改动）；-CheckCompletion 为交接态 EXPECTED_FAIL
Remaining risks or follow-ups: backend 缺陷 A/B 与 presign 公开端点须另立卡；切片⑨前端必填校验缺口；069 提交与 review.accepted 待验证权威
