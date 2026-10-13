---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-078
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: a0227961e085a6feae627bff7fcc276937b63b6b
stageGatePassed: true
stageGateEvidence: 已授权（批准人 User，2026-10-10，审阅 DRAFT 后批复「批准两卡，按序实施」；
  按序=077 先行、078 随后，V44 号位由 078 独占使用兑现 076「禁占 V44」约束）。差距事实链
  （2026-10-10 以 baseCommit 复证）：
  backend/frontend 全仓 grep 无 split 维度（大小写均零业务命中）；
  asset_preview 表（V16__version_transfer.sql L171-192）仅 content_type/content/rendered_html
  投影，无 split/统计结构化列；PreviewJobHandler 生成逻辑（L116-134）仅产 csv/jsonl/parquet
  样例与 text/image 分支，无行数/大小/文件数/列数统计输出；REQ-DST-DETAIL-001 L44/L95/L106
  要求 Split、统计属精确 Version 并在 Preview Tab 呈现；page-catalog PAGE-DST-001 要求
  Subset 下拉 + Split Tab 选择器（首版单 Subset，DEC-015）与统计卡片；DEC-015 已 ACCEPTED
  （2026-07-06，decidedBy User），无遗留评审门禁。
stageGateEvidenceRefs:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-068-001.yaml
requirements:
  - REQ-DST-DETAIL-001
acceptanceScenarios:
  - AC-DST-PRE-001
  - AC-DST-PRE-002
decisions:
  - DEC-008
  - DEC-015
scenarioEvidencePlan:
  - AC-DST-PRE-001|E4|真实 Compose 栈 Playwright：上传含多 Split 目录结构（train/val 或
    data 根）的 CSV 数据集版本并发布、触发预览生成后，Preview Tab 出现 Split 选择器并默认
    选中一个 Split，切换 Split 展示对应样例；统计卡片展示行数/文件数/大小/列数四元组，
    数值与后端投影一致（API 断言）且注明来源版本与生成时间
  - AC-DST-PRE-002|E4|后端单测+契约测试（目录栈运行态复证）：达行/列/大小上限按确定性截断并在投影中显式
    标记 truncated；无 split 可识别结构时投影 split 集合为空、前端退化为单默认视图不报错；
    不支持格式仍返回 PREVIEW_UNSUPPORTED_FORMAT 不伪造空成功；存量旧投影（无新分量）
    读取路径向后兼容
crossCuttingPlan:
  - AUTHN|复用既有 JWT 过滤器与 PrincipalContextHolder；getPreview/generate 两操作口径不变，
    不新增匿名面
  - AUTHZ|既有 previews GET/generate 的 requirePermission("asset:read") 不动；不新增/不放宽任何
    权限口径；投影读取仍按 assetId/versionId 绑定校验
  - DB_FILTER|N/A - 不改列表查询与权限下推；投影按单资源主键读取
  - STATE|不改变 VersionStatus 与 Job 状态机取值；预览重生成沿用既有幂等 Job 通道
    （persistent job，禁止 @Async/裸线程）
  - IDEMPOTENCY|预览生成沿用既有持久化 Job 幂等键口径；重复 generate 不产生重复投影行；
    本卡新增列均由 Job 重算覆盖写，无用户侧写入口
  - CONSISTENCY|前向迁移 V44 扩 asset_preview（新增可空列/JSONB 分量，向后兼容）；旧行新列
    NULL → 服务层投影为「无 Split 维度/统计未知」而非伪造零值；不跨系统写入
  - ERRORS|不新增 ErrorCode 枚举项；沿用 PREVIEW_UNSUPPORTED_FORMAT/PREVIEW_SOURCE_UNAVAILABLE
    与既有 Job 失败语义；解析失败按 AC-DST-PRE-002「不伪造空成功」处理
  - AUDIT|沿用预览族无审计现状（068 经批准人确立口径）；本卡无新增可外带动作，不新增审计事件；
    如验证权威认定 Job 重生成需审计，属口径变更须批准人裁定
  - NOTIFICATION|N/A - 不触发通知
  - TAXONOMY_I18N|Preview Tab Split 选择器与统计卡片新文案（含「统计未知」回退态）zh/en 同步；
    Split 名称（train/val/test 等）是数据派生值不做 i18n 键，展示原文
  - CONFIG|Split 识别规则（顶层目录名 train/validation/val/test/dev 白名单 + data 根回退）与
    统计上限（采样行数、最大文件数）为服务端常量或 PreviewProperties 内置默认，不改 deploy/；
    新增规则须经规格变更而非代码私改
  - OBSERVABILITY|沿用 PlatformMetrics 预览失败计数既有 reason 键；如统计生成耗时需观测，
    复用既有 Job 指标，不新增指标/告警
  - SECRETS|统计与样例来自源数据脱敏投影（既有 AC-DST-PRE-004 canary 约束不回归）；
    对象存储凭据、内部 Endpoint、对象键不进入投影/响应/日志
allowedPaths:
  - backend/src/main/resources/db/migration/V44__preview_split_stats.sql
  - backend/src/main/java/com/aihub/version/infrastructure/PreviewJobHandler.java
  - backend/src/main/java/com/aihub/version/infrastructure/PreviewProperties.java
  - backend/src/main/java/com/aihub/version/application/PreviewApplicationService.java
  - backend/src/main/java/com/aihub/version/api/PreviewController.java
  - backend/src/test
  - contracts/openapi/aihub-v1.yaml
  - frontend/src/features/assets/PreviewPanel.tsx
  - frontend/src/features/assets/PreviewPanel.test.tsx
  - frontend/src/features/assets/AssetDetailPage.tsx
  - frontend/src/shared/i18n
  - frontend/e2e
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/04-ui/page-catalog.md
  - docs/ai-spec/tasks/TASK-P0BR-078.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-078-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - deploy
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-078-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-078.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-078.md -CheckChangedPaths
  - ./mvnw.cmd -q verify（backend 目录；覆盖 V44 迁移、split/统计生成单测、向后兼容读取用例）
  - pnpm lint（frontend 目录）
  - pnpm typecheck（frontend 目录）
  - pnpm test（frontend 目录；Vitest 含 PreviewPanel Split/统计分支）
  - pnpm build（frontend 目录）
  - pnpm test:e2e（frontend 目录；真实 Compose 栈 E4，含 Split 切换与统计卡片用例全量无回归）
  - npx @redocly/cli@latest lint contracts/openapi/aihub-v1.yaml --config contracts/openapi/redocly.yaml
  - npx @redocly/cli@latest diff EVD-P0BR-078-base.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking（EVD-P0BR-078-base.yaml 由 git show baseCommit 导出为变更前基线；上游 redocly diff 已移除时按 067/068 先例以 oasdiff breaking 或基线纯新增 numstat 论证非破坏，getPreview 响应仅新增可选分量，环境不可得登记 notProven）
approvedBy: User
approvedAt: 2026-10-10T06:30:00Z
---

# TASK-P0BR-078：预览 Split 维度与基础统计（G5，DEC-015 落地）

> 状态：`READY`（批准人 User，2026-10-10 批复「批准两卡，按序实施」后回写审批位，DEC-009；按序=077 先行、本卡随后，V44 号位由本卡独占使用）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。

## 1. 目标

有权主体在数据集详情页 Preview Tab 看到绑定精确版本的 Split 选择器（首版单 Subset，DEC-015）与基础统计卡片（行数/大小/文件数/列数）；无 Split 结构或旧投影退化为默认视图且不报错、不伪造数值。修复迭代计划 §4.3 复证的 G5 缺口（全仓无 split 维度、无统计投影）。

## 2. 关联规格

```yaml
requirements: [REQ-DST-DETAIL-001]
acceptanceScenarios: [AC-DST-PRE-001, AC-DST-PRE-002]
decisions: [DEC-008, DEC-015]
adrs: [ADR-0001]
openapiOperations: [getPreview（响应新增可选分量）, triggerPreviewGeneration（行为不变）]
mcpTools: []
eventSchemas: []
flywayBaseline: V43 → 新增 V44__preview_split_stats.sql（前向、仅扩 asset_preview 可空列）
```

- `REQ-DST-DETAIL-001`（READY，E4）：L44「Split、Schema、字段统计属于精确 Version，不作为资产级
  自由分类字符串」；L95 Preview Tab 第 4 项；L106 绑定精确 Version。
- `DEC-015`（ACCEPTED，2026-07-06）：首版仅单 Subset，Split Tab 选择器 + 统计卡片为验收口径——
  本卡直接实施，无需再评审。
- `AC-DST-PRE-001`（E4）要求投影含 Schema、Split、脱敏样例和统计并显示 source digest/时间；
  `AC-DST-PRE-002`（E3/E4）约束确定性截断与「不伪造空成功」。

## 3. 范围

### 允许修改

- 数据：`V44__preview_split_stats.sql` 前向扩展 `asset_preview`——新增可空 JSONB 列
  `split_stats`（结构 `{ splits: [{ name, rowCount, fileCount, sizeBytes, columns }],
  totals: {...}, generatedFrom: { versionId, sourceCommit, generatedAt } }`；表/列注释齐全）。
  不回填存量行（NULL = 旧投影，服务层显式投影为「无 Split 维度/统计未知」）。
- 后端：
  1. `PreviewJobHandler`：csv/jsonl（parquet 如超出现有依赖能力则按 AC-DST-PRE-002 记
    unsupported 分支，禁止引入新解析依赖——若批准人裁定需引依赖，须补正后另行实施）生成时按
    artifact `path` 顶层目录识别 Split（白名单 train/validation/val/test/dev，其余归 default），
    在确定性上限内统计行数/列数/文件数/字节数并随投影写入 `split_stats`；
  2. `PreviewApplicationService`：getPreview 响应组装携带 split_stats（NULL → 显式空态字段）；
  3. `PreviewProperties`：Split 目录白名单与统计上限（采样行数、最大文件数、maxStatsBytes）。
- 契约：`getPreview` 200 响应 schema 新增可选分量（splits/totals/generatedFrom）；纯增量非破坏。
- 前端：`PreviewPanel.tsx` 增 Split 选择器（antd Segmented/Tabs，仅当 splits 非空）与统计卡片
  （行数/大小/文件数/列数四元组 + 生成时间与来源版本注记）；旧投影/无 Split → 现有样例视图不变，
  统计区显示「统计未知 · 重新生成预览」引导；换 Split 时样例区按投影内对应切片渲染。
- i18n：`version.previewSplit*`、`version.previewStats*` 新键 zh/en 对称。
- e2e：多 Split 目录 fixture CSV 全链（上传→发布→generate→UI 断言选择器与统计数值）。
- 文档：本卡、EVD、work-breakdown 状态列、page-catalog PAGE-DST-001 实施状态登记。

### 明确不在范围

- 多 Subset（版本级 Subset 实体）：DEC-015 首版单 Subset，本卡仅 Split。
- 字段级分布统计、直方图、PII 深度检测扩展：属后续波次；AC-DST-PRE-004 canary 现状不得回归。
- G1 文件内容浏览（`TASK-P0BR-077`）、G4 收藏不触碰；`listArtifacts`/下载链路不改。
- 不改任何已应用 Migration；不新增 ErrorCode 枚举项、权限口径。

### 禁止变化

- 不允许对无 Split 结构或旧投影伪造零值统计（「未知」≠「0」，AC-DST-PRE-002 语义）。
- 统计必须来自服务端投影写入，前端不得自行拉取全量数据计算（上限约束在 Job 侧强制执行）。
- 样例脱敏路径（既有实现）不得绕过或弱化；canary 泄漏必须保持 0。
- 不引入新的第三方解析依赖（parquet 能力边界如不满足，走 unsupported 分支并如实登记）。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-DST-PRE-001` | USER(有 asset:read) | PUBLISHED 版本含 train/val 目录结构 CSV，已 generate | 打开 Preview Tab | Split 选择器出现且默认选中；切换 Split 样例随之变化；统计卡片四元组与投影一致并显示生成时间/source 版本 | `E4` |
| `AC-DST-PRE-001` | USER | 同上 | 重新 generate | Job 幂等（不产生重复投影行）；split_stats 覆盖更新 | `E2` |
| `AC-DST-PRE-002` | 系统 | 数据达采样行数/最大文件数上限 | generate | 确定性截断且投影标记 truncated；统计注明采样口径 | `E2` |
| `AC-DST-PRE-002` | USER | 无 Split 目录结构（data 根平铺） | 打开 Preview Tab | splits 归入 default 单值或空；选择器不渲染或单值禁用；页面不报错 | `E2`+`E4` |
| `AC-DST-PRE-002` | USER | 旧投影（V44 前行，split_stats NULL） | 打开 Preview Tab | 「统计未知 + 重新生成引导」空态；既有样例渲染不回归 | `E2` |
| `AC-DST-PRE-003` 相邻 | USER(无 asset:read) | 他人资产 | GET getPreview | 403 现状不回归（本卡不改权限面） | `E2` |

覆盖正常/边界/失败/越权相邻；写路径仅 Job 重算，幂等切片如上。

## 5. 横切要求

以 Front Matter `crossCuttingPlan` 13 键为准，不另行放宽。

## 6. 契约与数据先行

1. **Flyway**：`V44__preview_split_stats.sql`——`ALTER TABLE asset_preview ADD COLUMN split_stats JSONB`
   （可空，无默认值；COMMENT ON TABLE/COLUMN 齐全）；正向 V44、回滚=不回填不删列；
   Compose 全新栈与存量栈迁移路径均在 e2e 前验证（`docker compose` 起栈 migrate 成功）。
2. **OpenAPI**：getPreview 响应 schema 新增可选分量；非破坏取证（067/068 先例路径）。
3. **示例**：投影 JSON 结构在契约 description 与后端测试中断言同源，禁止双处漂移定义。
4. **存量清点与回填**：存量 asset_preview 行 split_stats 保持 NULL，前端空态承接（§3）；
   不做数据回填（重新 generate 即产出新投影）。
5. **索引/约束**：不变（单行读写按 preview_id 主键）。
6. **兼容窗口与失败恢复**：后端+迁移先发（旧前端忽略新响应分量，无回归面）；新前端遇旧后端 →
   响应无 splits → 空态渲染。回滚顺序：前端 → 后端（V44 列留存无害，forward-only 不回滚）。
7. **生成类型更新**：前端 API 类型手工同步（067/068 同口径）。

## 7. 实施步骤

- [x] V44 迁移 + 表/列注释 + Compose 迁移验证（EVD-P0BR-078-001 execution①；COMMENT ON 齐全，活栈 migrate 成功）
- [x] 契约 getPreview 响应增量分量 + lint/非破坏取证（execution④；redocly exit 0，两卡合计 97+/1-、纯新增非破坏）
- [x] PreviewJobHandler Split 识别与统计生成（确定性上限、截断标记）（execution②；白名单 split-dirs、max-stats-* 上限、truncated 标记、per-Split 脱敏 sampleRows 经批准人批复）
- [x] PreviewApplicationService/Controller 投影组装（execution③；split_stats NULL→无 splits 分量，不伪造；PreviewQueryPort/Repository 扩展——见 EVD changedPathValidation enumeration 缺口裁定项）
- [x] 前端 Split 选择器 + 统计卡片 + 旧投影空态 + i18n zh/en（execution⑥；PreviewPanel + 空态「统计未知」，zh/en 各 +15）
- [x] 正常/边界/失败/幂等测试（Vitest + 后端单测）（execution⑤⑥⑦；StatsTest/JobHandlerTest/PropertiesTest、PreviewPanel.test 14 用例、Vitest 50/239 全绿、mvnw 835/0/0/10 合计口径）
- [x] Compose Fixture/Verify（多 Split fixture 全链 E4）（execution⑧⑨⑩；preview-splits.spec.ts 4/4、合跑 8/8、psql split_stats 物证）
- [x] 文档同步（work-breakdown 078 状态列、page-catalog PAGE-DST-001 登记）（work-breakdown L94 状态段 + page-catalog §4 表下实施注记）

勾选必须附 Evidence Manifest 条目；提交代码本身不能作为勾选依据。

## 8. 验证命令

```text
治理        validate-spec / validate-task-card / -CheckChangedPaths          各 exit 0
backend/    ./mvnw.cmd -q verify（含 Flyway V44 迁移测试）                    exit 0   全 AC 后端切片
frontend/   pnpm lint; pnpm typecheck; pnpm test; pnpm build                  各 exit 0  组件层
frontend/   pnpm test:e2e（真实 Compose 栈，凭据注入同 066/067/068 口径）      exit 0   E4 主证
contracts/  redocly lint + 非破坏取证                                          exit 0   契约非破坏
```

SKIP 判定：任何因 Docker/凭据缺失导致的跳过视为 notProven，不构成 PASS（既有口径）。

## 9. 停止条件

- parquet 统计必须引入新解析依赖才能达标 → 停止该格式分支并按 unsupported 如实登记，
  引依赖须批准人补正裁定（§3 禁止变化）；
- 批准人认定存量投影需回填或统计口径应进 asset 级 → 超出本卡范围，回报后另行建卡；
- e2e 多 Split fixture 链无法在现有 helper 下产出 → 不得降级交差，如实记 notProven 并回报；
- 发现脱敏/canary 路径为统计让路的任何弱化倾向 → 立即停止（§3 禁止变化）；
- 工作树存在与本卡 allowedPaths 重叠且来源不明的变更；
- 需改 forbiddenPaths 或既有权限口径才能推进 → 停止回报等待补正裁定。

## 10. 完成报告

```text
Completed:
V44 迁移（asset_preview.split_stats 可空 JSONB，表/列注释齐全，forward-only）；PreviewJobHandler 按顶层目录
白名单识别 Split、确定性上限内统计（rowCount/fileCount/sizeBytes/columns/truncated）并累积 per-Split 脱敏
sampleRows（批准人批复口径，与主样例同 sanitize 路径）；getPreview 组装 splits/totals/generatedFrom 可选分量
（NULL/不可解析→降级 unknown 不伪造零值）；契约增量 + redocly 非破坏取证；前端 PreviewPanel Split 选择器 +
统计卡片 + 旧投影空态 + i18n zh/en；work-breakdown/page-catalog 登记。完整证据见 EVD-P0BR-078-001。

Changed files:
见 EVD-P0BR-078-001 source.dirtyPaths（38 条逐文件归属注释）。本卡面：V44、PreviewJobHandler、PreviewQueryPort、
PreviewQueryRepository、PreviewProperties、PreviewApplicationService、PreviewController、application.yml、
backend 测试 5 文件、contracts/openapi/aihub-v1.yaml、PreviewPanel.tsx/.test.tsx、AssetDetailPage.tsx、
i18n zh/en、e2e preview-splits.spec.ts + fixture、work-breakdown、page-catalog、本卡、EVD。
（其中 PreviewQueryPort/PreviewQueryRepository/application.yml 不在 allowedPaths 枚举——见下裁定项。）

Contract/database changes:
OpenAPI getPreview 响应新增可选 splits/totals/generatedFrom（两卡合计 97 增 1 删、纯新增非破坏，redocly exit 0）；
Flyway V44 前向迁移一列（可空、无回填）。

Validation run and results:
mvnw -q verify 835/0/0/10（两卡合计口径，SKIP=testcontainers Windows 既有条件跳过）；vitest run --maxWorkers=2
50 files/239 tests 全绿 exit 0；pnpm lint/typecheck/build exit 0；redocly lint exit 0；Compose E4 真实栈
preview-splits.spec.ts 4/4、与 077 合跑 8/8；psql split_stats 物证与 e2e 断言一致；治理三连终态：validate-spec
PASS（148 docs/738 IDs）、validate-task-card PASS、-CheckChangedPaths FAIL（13 issue，EVD 逐条归因）、
-CheckCompletion EXPECTED_FAIL（交接态）。

Validation not run and reasons:
oasdiff breaking-change 专用工具环境不可达——沿 068/070 先例以「纯新增 diff + redocly lint」等价论证替代；
E4 人工验收（review.accepted）不在 AI 权限内（DEC-009），留验证权威。

Remaining risks or follow-ups:
① allowedPaths enumeration 缺口（PreviewQueryPort/PreviewQueryRepository/application.yml 为本卡必要实现面）
——未隐式扩卡，留批准人裁定（补正扩卡或改写卡面）；② build-logs/playwright-report/test-results 非产品脏目录
建议 .gitignore 或清理裁定；③ parquet 统计维持 unsupported（引依赖须另行批准，§9 停止条件未触发）；
④ 本卡为 IMPLEMENTED_UNVERIFIED：提交与 review.accepted 由验证权威处置（AI 不自提交、不自批，DEC-009）。
```
