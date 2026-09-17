---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-048
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: bea0a3663ed8a63e9d74da17cd0272332c52fcd9
stageGatePassed: true
stageGateEvidence: 用户批准。依据 READY 需求 REQ-TAX-001/REQ-UI-001（P0-B 公共底座与公共管理端）与 ACCEPTED 决策 DEC-013（UI 与非功能基线），修复 P0-B 范围内的受控字典选择缺陷，不新增业务能力，符合 ADR-0002 的「公共底座接入与安全整改」例外。
stageGateEvidenceRefs:
  - DEC-013
requirements:
  - REQ-TAX-001
  - REQ-UI-001
acceptanceScenarios:
  - AC-P0B-TAX-006
  - AC-P0B-UI-004
decisions:
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-UI-004|E4|浏览器登录后打开「登记资产」弹窗，framework/task/format/modality/license 五个下拉均返回非空选项且无 DICTIONARY_ITEM_NOT_FOUND；选择后成功创建资产，回读资产详情的治理字段与所选 itemCode 一致
  - AC-P0B-TAX-006|E4|zh-CN 与 en-US 两种语言下受控选择器与资产详情的字典字段均按 locale 渲染，无 404 空下拉与硬编码业务文案
crossCuttingPlan:
  - AUTHN|N/A - 不改变认证路径；受控选择器继续经统一 API Client 携带现有 JWT
  - AUTHZ|N/A - 字典读取仍要求 dictionary:read，不新增或放宽任何权限
  - DB_FILTER|N/A - 不涉及资产查询过滤或授权下推
  - STATE|不改变任何稳定状态枚举；仅对齐前端引用的 dictCode，不通过字典 API 添加状态枚举
  - IDEMPOTENCY|N/A - 只读字典查询与表单选择，无写副作用
  - CONSISTENCY|N/A - 不跨系统写入；不涉及 Gitea/MinIO/DVC
  - ERRORS|保留后端 DICTIONARY_ITEM_NOT_FOUND 稳定错误码；前端不得以静默空选项掩盖未知 dictCode，必须显式失败或告警
  - AUDIT|N/A - 字典读取与资产创建沿用既有审计；本卡不新增审计事件类型
  - NOTIFICATION|N/A - 不触发站内通知或 Webhook
  - TAXONOMY_I18N|受控值只提交 dictCode/itemCode，禁止展示名或自由值；停用项保留历史回显；每个运营文案 i18nKey 具备 zh-CN/en-US
  - CONFIG|不引入前端可配置的 dictCode 映射表；受控值必须直接引用 MVP Catalog 的稳定 dictCode
  - OBSERVABILITY|N/A - 不新增指标或告警
  - SECRETS|N/A - 不接触 Secret；证据不得包含凭据或 Token
allowedPaths:
  - frontend/src/features/assets
  - frontend/src/shared/components/ControlledSelect.tsx
  - docs/ai-spec/tasks/TASK-P0BR-048.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-048-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V6__taxonomy_dictionary.sql
  - contracts
  - deploy
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-048-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-048.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-048.md -CheckChangedPaths
  - cd frontend; pnpm lint
  - cd frontend; pnpm typecheck
  - cd frontend; pnpm test
  - cd frontend; pnpm build
  - cd frontend; pnpm test:e2e
approvedBy: User
approvedAt: 2026-09-17T04:42:50Z
---

# TASK-P0BR-048：资产表单受控字典选择恢复可用

> 状态：`READY`（用户已批准并授权实施）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-17

## 1. 目标

创建/编辑资产时，框架、任务、格式、模态、许可证五个治理字段的受控选择器能够返回真实可选项，
用户不再面对永远为空的下拉；受控值仍只提交稳定 `itemCode`。

## 2. 关联规格

```yaml
requirements: [REQ-TAX-001, REQ-UI-001]
acceptanceScenarios: [AC-P0B-TAX-006, AC-P0B-UI-004]
decisions: [DEC-013]
adrs: [ADR-0002]
openapiOperations: []
mcpTools: []
eventSchemas: []
flywayBaseline: V1
```

### 缺陷事实（已复核）

`REQ-TAX-001` 规定的 P0-B MVP Catalog 只包含 8 个 dictCode：
`model_task`、`model_framework`、`dataset_modality`、`dataset_format`、`industry_tag`、
`license_catalog`、`sensitivity_level`、`deprecation_reason`
（种子见 `backend/src/main/resources/db/migration/V6__taxonomy_dictionary.sql`）。

前端资产表单请求的 dictCode 与之不匹配，导致后端 `DictionaryApplicationService.requireTypeExists`
抛出 `DICTIONARY_ITEM_NOT_FOUND`，下拉恒为空：

| 前端请求路径 | 后端可识别 dictCode | 结果 |
| --- | --- | --- |
| `/system/dictionaries/license/items` | `license_catalog` | 404 |
| `/system/dictionaries/framework/items` | `model_framework` | 404 |
| `/system/dictionaries/task/items` | `model_task` | 404 |
| `/system/dictionaries/format/items` | `dataset_format` | 404 |
| `/system/dictionaries/modality/items` | `dataset_modality` | 404 |
| `/system/dictionaries/language/items` | 无对应 dictCode | 404 |

后端常量 `AssetApplicationService.DICT_LICENSE/DICT_FRAMEWORK/DICT_TASK/DICT_FORMAT/DICT_MODALITY`
与种子一致，因此缺陷在前端一侧。

### 语言维度的处置（本卡已决策范围）

`dataset_language` 不属于 P0-B MVP Catalog，且 `AssetRequestMapper.toDatasetProfile` 当前只映射
`(format, modality)`，`ad.language_codes` 无法经 REST 写入。因此语言筛选即使拿到字典也恒为空结果，
属 P1 `REQ-DST-TAX-001` 范围（受控标签与多值分类）。本卡只移除该无效筛选项，不新增字典类型，
不扩大数据集多值分类的写入面。

## 3. 范围

### 允许修改

- 模块：`frontend/src/features/assets`、`frontend/src/shared/components`
- 文件或目录：
  - `frontend/src/features/assets/CreateAssetModal.tsx`（5 处 dictCode 对齐到 MVP Catalog）
  - `frontend/src/features/assets/AssetsPage.tsx`（移除无对应字典、且服务端无法命中的语言筛选）
  - `frontend/src/features/assets/*.test.tsx`（新增受控字典引用回归断言）
  - `frontend/src/shared/components/ControlledSelect.tsx`（仅在需要显式暴露查询失败时最小改动）
- 契约：无变更
- Migration：无
- 页面：`PAGE-AST-002` 相关表单（当前以 Modal 形态实现）
- Fixture/Verify：不改动 `deploy/compose`

### 明确不在范围

- 不新增 `dataset_language` 或其他字典类型/字典项；
- 不实现 `REQ-DST-TAX-001` 的多值分类（`taskCodes`/`modalityCodes`/`formatCodes`/`languageCodes`）
  写入能力，归 `TASK-P1-010`；
- 不修改资产搜索的查询参数与 Facet 语义，归 `TASK-P0BR-049`；
- 不改动后端字典 API、种子 Migration 与权限判定；
- 不引入展示名或自由值提交路径。

### 禁止变化

- 不改变的 API/状态/权限：字典 API 形状、错误码、`dictionary:read` 要求；
- 不允许的依赖升级或重构：不新增依赖，不改动 API Client 与认证链路；
- 不允许的兼容破坏：不得把任何受控选择器退化为自由文本输入（禁止 `mode="tags"`）。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-UI-004` | 有 `asset:manage` 的用户 | 已登录且 MVP Catalog 已种子化 | 打开登记资产弹窗并展开 MODEL 与 DATASET 两种类型 | 框架/任务/格式/模态/许可证下拉均非空；浏览 Network 无 404 字典请求 | `E4` |
| `AC-P0B-UI-004` | 同上 | 已选择合法 itemCode | 提交创建资产 | 创建成功；资产详情的治理字段与所选 itemCode 一致 | `E4` |
| `AC-P0B-UI-004` | 同上 | 表单不含语言筛选项 | 使用搜索页 | 搜索页不再展示恒为空的无效筛选；其余筛选（类型/敏感度）仍可用 | `E2/E4` |
| `AC-P0B-UI-004` | 审查者 | 未来再次出现 dictCode 漂移 | 运行前端测试 | 回归断言失败并指出漂移的 URL，防止静默空下拉 | `E2` |
| `AC-P0B-TAX-006` | 有 `asset:read` 的用户 | 存在带治理字段的资产 | 在 zh-CN / en-US 间切换并查看表单与详情 | 下拉标签与详情字段按 locale 渲染；受控值仍为稳定 itemCode | `E4` |
| `AC-P0B-TAX-006` | 同上 | 同上 | 触发一次字典请求失败（如后端不可用） | 页面给出可见错误说明而非静默空白 | `E2/E4` |

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：受控选择器继续经统一 API Client 携带现有 JWT，不新增匿名路径 |
| Permission/Scope/ACL | N/A：字典读取仍要求 `dictionary:read`，不放宽亦不收紧 |
| 数据库权限下推 | N/A：不涉及资产查询过滤或授权下推 |
| 状态前置条件 | 不改变任何稳定状态枚举；禁止经字典 API 添加状态值 |
| 幂等与并发 | N/A：只读字典查询与表单选择，无写副作用 |
| 事务与外部一致性 | N/A：不跨系统写入 |
| 错误码 | 保留 `DICTIONARY_ITEM_NOT_FOUND`；前端不得用静默空选项掩盖未知 dictCode |
| 审计事件 | N/A：沿用既有字典读取与资产创建审计，不新增事件类型 |
| 通知 | N/A：不触发站内通知或 Webhook |
| 字典/标签/i18n | 只提交 `itemCode`；停用项保留历史回显；文案经 i18nKey，具备 zh-CN/en-US |
| 配置 | 不引入前端可配置的 dictCode 映射表，直接引用 MVP Catalog 稳定 code |
| 指标/Trace/告警 | N/A：不新增指标或告警 |
| Secret/日志脱敏 | N/A：不接触 Secret；证据不得包含凭据或 Token |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无；
2. 示例请求、响应和错误：无新增接口，沿用 `GET /system/dictionaries/{dictCode}/items`；
3. 新增 Flyway 文件：无（不新增字典类型，避免越过 P0-B 目录范围）；
4. 存量清点与回填：无需回填，现有资产治理字段未受影响；
5. 索引、唯一约束、外键及理由：无；
6. 兼容窗口与失败恢复：前端改动只修正请求的 dictCode，对已正常工作的敏感度筛选无影响；
7. 生成类型更新：无。

## 7. 实施步骤

- [ ] 契约及契约测试
- [ ] Migration 及空库/升级迁移测试
- [ ] Application/Domain 用例
- [ ] REST/MCP/Worker Adapter
- [ ] 前端页面与状态
- [ ] 正常/边界/失败/越权/恢复测试
- [ ] Compose Fixture/Verify
- [ ] Runbook/追踪矩阵

勾选必须附 Evidence Manifest 条目；提交代码本身不能作为勾选依据。

实施路径：

1. 在 `CreateAssetModal.tsx` 把 5 处字典路径改为 MVP Catalog 的稳定 dictCode；
2. 在 `AssetsPage.tsx` 移除无对应字典且服务端无数据可命中的语言筛选，保留类型与敏感度筛选；
3. 新增回归断言：固定资产表单与搜索页使用的字典 URL 常量，出现漂移即失败；
4. 在干净 Compose 栈上以真实字典数据执行浏览器验证，覆盖两种类型表单与两种 locale。

## 8. 验证命令

```text
cd frontend; pnpm lint
cd frontend; pnpm typecheck
cd frontend; pnpm test
cd frontend; pnpm build
cd frontend; pnpm test:e2e
```

每条命令声明：

- 预期退出码：全部 0；`pnpm test:e2e` 需要已启动的后端（Compose 或本地 dev），未启动时记为 SKIP
  并在 Evidence `limitations.skippedTests` 中列出，不得记为 PASS；
- 覆盖的 AC ID：`AC-P0B-UI-004`、`AC-P0B-TAX-006`；
- 所需依赖：Node 20+、pnpm、Playwright 浏览器；E4 需要可用的 Compose 栈与真实字典数据；
- SKIP 判定：无可用后端时 E4 场景不可 SKIP 为 PASS，只能交接为 `IMPLEMENTED_UNVERIFIED`；
- 报告或产物路径：`frontend/coverage`、Playwright 报告、
  `docs/ai-spec/tasks/evidence/EVD-P0BR-048-001.yaml`。

路径触发强制命令：修改 frontend 必须包含 `pnpm lint/typecheck/test/build`。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-048.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 规范事实冲突（例如 MVP Catalog 与前端引用无法在不新增字典的前提下对齐）；
- 存在未关闭的产品决策（例如是否新增 `dataset_language` 属 P1 决策）；
- 需要改变 ADR 固定的技术路线或事实源；
- 需要修改 V1/V2 或其他已应用 Migration；
- 需要降低权限、校验、约束或测试；
- 无法产生任务要求的最低证据等级；
- 工作树存在与任务重叠且来源不明的变更。

## 10. 完成报告

```text
Completed:
Changed files:
Requirements and scenarios satisfied:
Contract/database changes:
Validation run and results:
Evidence artifacts:
Validation not run and reasons:
Remaining risks or follow-ups:
```

报告中必须区分 `E1`、`E2`、`E3`、`E4`、`E5`，不得用低等级证据概括高等级结论。
