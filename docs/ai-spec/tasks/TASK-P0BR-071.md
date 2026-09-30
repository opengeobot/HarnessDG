---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-071
status: DRAFT
implementationAuthorized: false
phase: P0-B
baseCommit: 28146df623fb0df65fe9f91088613acb9f75628d
stageGatePassed: false
stageGateEvidence: 待批准。差距事实链（TASK-P0BR-069 实施期 E4 取证，2026-09-30，
  见 docs/ai-spec/tasks/evidence/EVD-P0BR-069-001.yaml notProven[1] 与
  docs/architecture/modelscope-page-gap-analysis-2026-09-21.md §7）：
  Settings 表单 e2e 切片⑨暴露两个 backend 缺陷——A（PATCH 部分字段更新静默清空
  displayName/description，违反契约 patch 语义）与 B（displayName 被清 NULL 后
  任何非空回传触发 rename→insertAlias 唯一键冲突 → PG 事务中止 25P02 → 500）。
  批准人 User 于 2026-09-30 授权起草本卡（DEC-009：起草与实施分离，实施须另行批准）。
requirements:
  - REQ-AST-005
  - REQ-COM-001
  - REQ-UI-001
acceptanceScenarios:
  - AC-P1-AST-001
  - AC-P1-AST-009
decisions:
  - DEC-009
scenarioEvidencePlan:
  - AC-P1-AST-001|E4|契约 UpdateAssetRequest 的 displayName/description 补齐「为空表示不变更」
    语义描述（与 organizationId/projectId 现句 L4667/L4670 对齐，非破坏性描述补全）；
    redocly lint + diff 命令 + 契约↔实现一致性审查（含 rename 保留 Alias 不变量与
    REQ-AST-005「重命名规则」条目对照），并以上述 E4 行为测试复证契约语义与实现一致
  - AC-P1-AST-009|E4|mvnw 单测/集成测试复证：部分字段 PATCH 不清空未提交字段；
    rename 别名写入不再触发事务中止（ON CONFLICT DO NOTHING）；空 displayName 回传由
    500 变为语义化 4xx；真实 Compose 栈 e2e（settings-form.spec.ts 切片⑧/⑨升级口径）
    + 坏夹具 ast_01m3qzgjehtr7fc54xncvz2ymv 经修复后 PATCH 恢复 display_name 成功
crossCuttingPlan:
  - AUTHN|不改认证链；e2e 复用 support.ts 既有登录口径
  - AUTHZ|不改任何权限判定；updateAsset 授权门（asset:update）保持现状
  - DB_FILTER|N/A - 不改列表查询与权限下推 SQL
  - STATE|不改 AssetStatus/VersionStatus 状态机；rename 仅涉及 displayName 字段与别名记录
  - IDEMPOTENCY|insertAlias 改为 INSERT … ON CONFLICT (old_namespace, old_name) DO NOTHING，
    以数据库语义实现幂等，替换失效的 catch DuplicateKeyException 模式
  - CONSISTENCY|rename 分支保持单事务内完成（别名 + 卡片同步 Job + 资产更新）；
    修复目标即消除「语句失败 → 事务中止 → 后续 SQL 连锁 25P02」的不一致放大
  - ERRORS|Asset.rename 空名抛 PlatformException(COMMON_INVALID_ARGUMENT)→400，
    替换裸 IllegalArgumentException（现落入 GlobalExceptionHandler 兜底 500）；
    不新增 ErrorCode 枚举值（COMMON_INVALID_ARGUMENT 已存在，ErrorCode.java L19）
  - AUDIT|updateAsset 审计摘要链路（前后受控字段摘要）不因本修复改变记录口径；
    如测试暴露审计断言偏差，据实修正断言而非放宽审计写入
  - NOTIFICATION|N/A - 不触碰通知面
  - TAXONOMY_I18N|复用既有 error.common.invalidArgument i18nKey，不新增键
  - CONFIG|N/A - 不改配置装配；deploy/ 属 forbiddenPaths
  - OBSERVABILITY|N/A - 不新增指标/告警
  - SECRETS|测试夹具与探针输出不落盘 Token/presign/凭据；build-logs 工件不入库
allowedPaths:
  - backend/src/main/java/com/aihub/asset/domain/Asset.java
  - backend/src/main/java/com/aihub/asset/application/AssetApplicationService.java
  - backend/src/main/java/com/aihub/asset/infrastructure/MyBatisAssetRepository.java
  - backend/src/test/java/com/aihub/asset/domain/AssetTest.java
  - backend/src/test/java/com/aihub/asset/application/AssetApplicationServiceTest.java
  - contracts/openapi/aihub-v1.yaml
  - frontend/e2e/settings-form.spec.ts
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/architecture/modelscope-page-gap-analysis-2026-09-21.md
  - docs/ai-spec/tasks/TASK-P0BR-071.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-071-001.yaml
forbiddenPaths:
  - deploy
  - V1__baseline.sql
  - V2__asset_catalog.sql
  - backend/src/main/java/com/aihub/bootstrap
  - worker
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-071-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-071.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-071.md -CheckChangedPaths
  - ./mvnw.cmd verify（backend 目录；asset 域单测/集成测试全绿）
  - pnpm lint（frontend 目录）
  - pnpm typecheck（frontend 目录）
  - pnpm build（frontend 目录）
  - pnpm test:e2e（frontend 目录；真实 Compose 栈，settings-form.spec.ts 升级口径通过 + 全量无回归；skip 视为 notProven）
  - npx @redocly/cli@latest lint contracts/openapi/aihub-v1.yaml --config contracts/openapi/redocly.yaml
  - git show HEAD:contracts/openapi/aihub-v1.yaml > build-logs/071-base.yaml; npx --yes @redocly/cli diff build-logs/071-base.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking（redocly diff 子命令为 067/068 已申报的上游既存缺陷；实际执行以 oasdiff breaking diff 替代取证并据实登记，沿 067 先例）
---

# TASK-P0BR-071：资产 PATCH 契约对齐与重命名别名缺陷修复（backend 缺陷 A/B）

> 状态：`DRAFT`（批准人 User 于 2026-09-30 授权**起草**；DEC-009——实施须另行批准，`implementationAuthorized: false`）
> 规则：一个任务只交付一个可独立验收的纵向结果——本卡纵向结果 = 「PATCH 部分字段更新的契约语义与后端实现对齐 + 重命名别名路径不再产生 500」。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。

## 1. 目标

TASK-P0BR-069 的 Settings 表单浏览器级测试（切片⑨）在真实 Compose 栈上确证两个 backend 缺陷，
登记于 `EVD-P0BR-069-001.yaml` notProven[1]。本卡修复该两缺陷并同步契约描述，使
`PATCH /assets/{assetId}` 的部分字段更新语义符合 REQ-AST-005 / AC-P1-AST-001（契约与实现一致）
与 AC-P1-AST-009（重命名保留 Alias）。

## 2. 需求与追踪

- 需求：`REQ-AST-005`（更新资产元数据与卡片，READY；重命名保留 Alias、并发冲突语义）、
  `REQ-COM-001`（统一响应与错误——可预期输入错误必须映射语义化 4xx，禁止兜底 500）、
  `REQ-UI-001`（公共管理端——Settings 页面编辑行为端到端可用）。
- 验收：`AC-P1-AST-001`（E3 契约审查）、`AC-P1-AST-009`（E4 重命名/别名/并发行为）。
- 上游证据：`EVD-P0BR-069-001.yaml` notProven[1]；探针留档
  `build-logs/probe-069-patch.json.log`、`build-logs/probe-069-patch2.json.log`
  （traceId `15d7037034af44bcab25e810a262ecff`，build-logs 不入库，仅存档参考）。

## 3. 取证事实（2026-09-30，HEAD `28146df`）

### 缺陷 A：PATCH 部分字段更新静默清空 displayName/description

- `Asset.updateMetadata`（Asset.java L169-209）对五个参数**无条件覆盖**：
  `organizationId`/`projectId`（L182-183 normalizeNullable）、`displayName`（L184）、
  `description`（L185）、`license`（L198）；对照 `visibility`/`owners`/`tags`/`tagIds`/
  `ownerTeamId`/profiles 均为 null-跳过（L186-207）。
- 契约 `UpdateAssetRequest`（aihub-v1.yaml L4665-4672）：`organizationId`/`projectId`
  明确「（为空表示不变更）」；**`displayName`/`description` 为裸 `{ type: string }`，变更语义留白**；
  `required` 仅 `expectedVersion`。即缺陷 A 的准确定性是「实现无条件覆盖 + 契约对这两字段语义留白」，
  前端表单全量发送（AssetSettingsPage.tsx L212-217 initialValues + values 全量提交）掩盖了该缺口。
- 触发路径（E4 实测）：PATCH 只发 `description` 不发 `displayName` →
  `AssetApplicationService.updateAsset` L190-191 `renameRequested=false` →
  L198-201 `updateMetadata(…, command.displayName()=null, …)` → `display_name` 静默写 NULL。
  坏夹具 `ast_01m3qzgjehtr7fc54xncvz2ymv` 即此成因（V2__asset_catalog.sql L15
  `display_name VARCHAR(255)` 可空，故 DB 不拦截）。

### 缺陷 B：NULL displayName 后非空回传 → 唯一键冲突 → 事务中止 → 500

- 同一服务方法 L189-196：`command.displayName() != null` 且与旧值不同 →
  `asset.rename()`（Asset.java L231-250，L234 空/blank 抛裸 `IllegalArgumentException`）→
  `assetRepository.insertAlias(assetId, namespace, oldDisplayName != null ? oldDisplayName : asset.name())`
  （L194-195）。
- `MyBatisAssetRepository.insertAlias`（L417-429）`catch DuplicateKeyException` 意图幂等跳过，
  但在 PostgreSQL 下失效：`ux_asset_alias_coordinate UNIQUE (old_namespace, old_name)`
  （V14__asset_remediation.sql L106-113）冲突使**当前事务进入 aborted 状态**（25P02），
  catch 吞掉 Java 异常后，同 `@Transactional` 的后续 SQL（`createCardSyncJob` L196、
  `assetRepository.update` L212）连锁失败 → `GlobalExceptionHandler.handleUnexpected` 兜底
  500 INTERNAL_ERROR。实测：对坏夹具 PATCH 非空 displayName 稳定 500，该资产无法经 API 修复。
- 既存测试面：`AssetTest.updateMetadataKeepsCoordinateImmutableAndChangesStatusViaDeprecate`
  （L58-60 全参调用，未覆盖 null 语义）、`AssetApplicationServiceTest.updateAssetChangesMutableMetadata`
  （L143）、`updateAssetRenamePreservesAliasAndEnqueuesCardSyncJob`（L389-399，mock 仓储，
  无法暴露真实 DB 事务中止）——两缺陷均为单测 mock 盲区，E4 才现形。

### 机理复核要求

实施时第一步须以真实 Compose/PG 复现并确认 25P02 链路（探针脚本或集成测试留证），
再动修复；若实测机理与上述推断不符，以实测为准并在 EVD 登记差异。

## 4. 修复方案（契约先行，按 AGENTS.md contract-first 顺序）

1. **契约**（contracts/openapi/aihub-v1.yaml）：`UpdateAssetRequest.displayName/description`
   补「（为空表示不变更）」描述，与 organizationId/projectId 口径一致。纯描述补全、
   无 schema 破坏；显式清空语法不在本卡范围（如需，另立契约演进卡）。
2. **缺陷 A**：`Asset.updateMetadata` 将无条件覆盖的五个参数改为与 `visibility` 一致的
   null-跳过（`if (x != null)`），实现「null = 不变更」patch 语义；坐标不可变约束不变。
3. **缺陷 B**：
   - `MyBatisAssetRepository.insertAlias` 改为
     `INSERT INTO asset_alias (…) VALUES (…) ON CONFLICT (old_namespace, old_name) DO NOTHING`，
     删除 catch DuplicateKeyException 模式（数据库语义幂等，不触发语句失败）；
   - `Asset.rename` 空名校验改抛 `PlatformException(ErrorCode.COMMON_INVALID_ARGUMENT)` → 400
     （现裸 `IllegalArgumentException` 落兜底 500）；
   - rename 分支保持单事务（别名 + CardSyncJob + update 原子），不改 Saga 边界。
4. **测试**：
   - `AssetTest`：新增「updateMetadata 传 null 不清空 displayName/description/organizationId/
     projectId/license」与「rename 空 displayName 抛 PlatformException」用例；
   - `AssetApplicationServiceTest`：新增「仅 description 的 PATCH 不触发 rename 且保留 displayName」；
   - `frontend/e2e/settings-form.spec.ts` 切片⑨升级：仅发 description → 200 且远端
     displayName 保留（缺陷 A 回归防线）；清空展示名称提交 → 断言语义化 4xx（原
     「≥400 且探针实测 500」口径收紧为 400）；
   - 集成级：对真实 PG 复现旧数据（displayName NULL）后 PATCH 非空名 → 200 且别名落表
     （若既有测试基建支持 Testcontainers/真实库则加断言，否则以 e2e + 坏夹具恢复实证）。
5. **坏夹具清理**：`ast_01m3qzgjehtr7fc54xncvz2ymv` 在缺陷修复部署后，经真实 PATCH 恢复
   `displayName`（走 API 验证修复有效，不留 SQL 后门）；恢复过程与结果写入 EVD。
   `settings-form.spec.ts` L28 注释与夹具准备同步更新。
6. **文档**：gap-analysis §7 缺陷登记句追加闭环指针；work-breakdown 071 行状态更新；
   契约 PATCH 端点 `x-implementation-status` 若因本卡验证得以从 implemented-unverified
   上调，据实修改（仅据实，不预判）。

## 5. 垂直切片与验收

| # | 切片 | 证据 |
|---|------|------|
| 1 | 真实栈复现 25P02 机理并留证（修复前） | EVD 记录 + 探针/测试日志 |
| 2 | 契约 displayName/description 语义描述补全 + redocly lint/diff 取证 | 契约审查记录 + lint PASS |
| 3 | updateMetadata null=不变更 + AssetTest/ServiceTest 新用例 | mvnw verify |
| 4 | insertAlias ON CONFLICT DO NOTHING + rename 400 化 | mvnw verify |
| 5 | settings-form.spec.ts 切片⑨口径升级（200 保留 / 400） | pnpm test:e2e 全量无回归 |
| 6 | 坏夹具经 API 恢复 + EVD 全量登记 | E4 实测记录 |

## 6. 门禁清单（实施收口时逐项勾选）

- [ ] validate-spec / validate-task-card（含 -CheckChangedPaths）PASS
- [ ] backend `./mvnw.cmd verify` PASS（asset 域无回归）
- [ ] `npx @redocly/cli lint contracts/openapi/aihub-v1.yaml` PASS
- [ ] 真实 Compose 栈 `pnpm test:e2e` 全量 PASS、零 skip（skip 视为 notProven）
- [ ] EVD-P0BR-071-001.yaml 完成且 secrets 脱敏确认

## 7. 越界与禁止事项

- 不改 deploy/、V1/V2 迁移、bootstrap 异常处理器、worker；不新增 ErrorCode 枚举。
- 不放宽任何既有断言；不删除坏夹具（须修复后走 API 恢复，保留缺陷现场价值）。
- 前端必填校验缺失（切片⑨卡面偏差的产品缺口）**不在本卡**——是否并案由批准人裁定，
  默认另立前端小卡。
- presign 公开端点仍归 `TASK-P0BR-070`（预留行），本卡不触碰。

## 8. 完成报告

（实施后由执行人按 AGENTS.md 六段式填写：Completed / Changed files / Contract/database
changes / Validation run and results / Validation not run and reasons / Remaining risks。）
