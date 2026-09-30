---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-072
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: e77d0d2199b9a9d9512b6cc7346905872caa9e5f
stageGatePassed: true
stageGateEvidence: 已批准起草。差距事实链（TASK-P0BR-071 收口登记 + TASK-P0BR-069
  切片⑨ E4 实测 + settings-form.spec.ts L8-11/L21-22/L357-358 三处明示「另卡跟进」）：
  AssetSettingsPage 元数据 Form 的 displayName Form.Item（L219-221）无 rules，
  清空展示名称提交仍真实发出 PATCH 并被后端 400 拒绝；前端未拦截造成用户体验缺口
  （提交后才报错、错误文案为后端校验原文而非 i18n）。TASK-P0BR-071 §7 明文
  「前端必填校验缺失不在本卡——默认另立前端小卡」，§8 follow-ups 复述同一移交；
  work-breakdown 071 行收口句同口径登记。批准人 User 于 2026-09-30 授权起草本卡，
  实施批准与 approvedBy/approvedAt 回写由批准人在本卡 DRAFT 审阅后另行处置
  （DEC-009：AI 不得在同一实施会话自行填写 approvedBy/approvedAt 或将 DRAFT 置 READY）。
  批准人 User 于 2026-09-30 在 DRAFT 卡经人工审阅后明确批准实施，
  approvedBy/approvedAt/status 由 AI 如实登记批准结果（非自批，与 071 同惯例）。
approvedBy: User
approvedAt: 2026-09-30T03:41:43Z
stageGateEvidenceRefs:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-071-001.yaml
  - docs/ai-spec/tasks/evidence/EVD-P0BR-069-001.yaml
requirements:
  - REQ-UI-001
  - REQ-COM-001
acceptanceScenarios:
  - AC-P0B-UI-003
  - AC-P0B-UI-006
decisions:
  - DEC-009
scenarioEvidencePlan:
  - AC-P0B-UI-003|E4|真实 Compose 栈上 Settings 元数据表单的「错误态」由「请求发出后
    后端 400 toast」前移为「前端 rules 拦截、字段级错误文案 i18n 显示、无 PATCH
    请求发出」；Loading/Empty/Success 既有切片⑧/⑨b/⑩保持通过；切片⑨按新行为收紧断言
    （waitForRequest 超时视为 PASS、`.ant-form-item-explain-error` 可见）
  - AC-P0B-UI-006|E4|Vitest 组件测试新增差异用例（AssetSettingsPage.test.tsx：
    清空 displayName → 提交 → 断言 mockUpdateAsset 未被调用 + 错误提示可见）；
    Playwright e2e 全量切片⑨ 真实 Compose 栈通过（111 用例基线不回归）
crossCuttingPlan:
  - AUTHN|不改认证链；e2e 复用 support.ts 既有 login 口径
  - AUTHZ|不改任何权限判定；isEditable 门（AssetSettingsPage.tsx L207）保持现状
  - DB_FILTER|N/A - 不改列表查询与权限下推 SQL
  - STATE|N/A - 不改状态机；仅在表单提交前做输入校验
  - IDEMPOTENCY|N/A - 无后端交互变更
  - CONSISTENCY|前后端校验口径对齐：前端 `required + whitespace` 拦截空/全空白
    displayName，与后端 rename 分支 `COMMON_INVALID_ARGUMENT`（071 修复）语义一致；
    后端校验保持不下线（前端校验不是唯一防线）
  - ERRORS|不新增 ErrorCode；前端 Form rules message 走 i18n 键，非直接展示后端原文
  - AUDIT|N/A - 前端拦截后 PATCH 不再发出，不触发 updateAsset 审计路径；不影响
    合法 PATCH 的审计写入
  - NOTIFICATION|N/A - 不触碰通知面
  - TAXONOMY_I18N|新增 `assets.create.displayNameRequired` 键（zh/en 双语；与既有
    `namespaceRequired`/`nameRequired`/`ownerTeamRequired` 同层同命名风格）；
    双语切换切片⑩ 现有断言无需改动（该切片不覆盖本字段错误态）
  - CONFIG|N/A - 不改配置装配；deploy/ 属 forbiddenPaths
  - OBSERVABILITY|N/A - 不新增指标/告警
  - SECRETS|无 Token/凭据落盘；build-logs 工件统一 *.log 扩展名不入库
allowedPaths:
  - frontend/src/features/assets/AssetSettingsPage.tsx
  - frontend/src/features/assets/AssetSettingsPage.test.tsx
  - frontend/src/shared/i18n/zh.json
  - frontend/src/shared/i18n/en.json
  - frontend/e2e/settings-form.spec.ts
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/architecture/modelscope-page-gap-analysis-2026-09-21.md
  - docs/ai-spec/tasks/TASK-P0BR-072.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-072-001.yaml
preExistingDirtyPaths:
  - build-logs/verify-bom.ps1|b5003338d283e84b4b6dabaf0d9e47b6d90fe18b780d0fd2e59851d467ac9b3d
forbiddenPaths:
  - backend
  - contracts
  - deploy
  - worker
  - V1__baseline.sql
  - V2__asset_catalog.sql
  - docs/ai-spec/tasks/TASK-P0BR-071.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-071-001.yaml
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-072-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-072.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-072.md -CheckChangedPaths
  - pnpm lint（frontend 目录）
  - pnpm typecheck（frontend 目录）
  - pnpm test（frontend 目录；Vitest 单测，新增用例通过且既有 5 用例不回归）
  - pnpm build（frontend 目录）
  - docker compose build frontend + docker compose up -d frontend（deploy/compose 目录；重建含新前端静态件，health 就绪）
  - pnpm test:e2e（frontend 目录；真实 Compose 栈，settings-form.spec.ts 切片⑨新口径通过 + 全量无回归；skip 视为 notProven）
---

# TASK-P0BR-072：资产 Settings 元数据表单 displayName 必填校验（前端小卡）

> 状态：`READY`（批准人 User 于 2026-09-30 批准实施，`implementationAuthorized: true`；
> DEC-009——批准权在批准人，AI 如实登记）。
> 规则：一个任务只交付一个可独立验收的纵向结果——本卡纵向结果 =
> 「AssetSettingsPage 元数据 Form 的 displayName 前端必填拦截生效，e2e 切片⑨断言按新行为收紧」。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。

## 1. 目标

TASK-P0BR-071 修复后端 PATCH 契约缺陷 A/B 并收紧切片⑨断言至精确 400 后，
`EVD-P0BR-071-001.yaml` 与卡 §7/§8 明确留下一项产品缺口：**前端 AssetSettingsPage
元数据 Form 的 `displayName` Form.Item 无 `rules`，清空后提交仍真实发出 PATCH 请求，
用户体验错误态由后端 toast 呈现**（`frontend/e2e/settings-form.spec.ts` L8-11 与
L21-22 与 L357-358 三处注释预告了本卡的收口义务）。本卡补齐前端必填校验，将错误
反馈前移至表单层，并同步更新 e2e 断言口径，使 Settings 编辑体验满足
REQ-UI-001 / AC-P0B-UI-003「错误态有可用交互」与 AC-P0B-UI-006「关键表单均有自动化证据」。

## 2. 需求与追踪

- 需求：`REQ-UI-001`（公共管理端——Settings 页面编辑行为端到端可用；表单必填校验
  是最小可用交互）、`REQ-COM-001`（统一响应与错误——前端错误展示走 i18nKey，
  与后端 COMMON_INVALID_ARGUMENT 语义一致而非直接透传后端原文）。
- 验收：`AC-P0B-UI-003`（管理页面加载/空/错误/成功四态均有可用交互——本卡将
  「错误」态由「后端 400 toast」前移为「字段级内联校验」）、
  `AC-P0B-UI-006`（前端组件测试 + 浏览器 E2E 均有自动化证据——Vitest 新用例 +
  Playwright 切片⑨新口径）。
- 上游证据：`EVD-P0BR-071-001.yaml`；`EVD-P0BR-069-001.yaml` notProven[1]；
  `TASK-P0BR-071.md` §7 L211-212 与 §8 follow-ups L296-297。

## 3. 取证事实（2026-09-30，HEAD `e77d0d2`）

### 缺口点

- `frontend/src/features/assets/AssetSettingsPage.tsx` L219-221：
  `<Form.Item label={t('assets.create.displayNameLabel')} name="displayName"><Input /></Form.Item>`
  **无 `rules`**；同页 description（L222-224）、visibility（L225-233）也未加 rules，
  但 visibility 由 Select 天然非空、description 后端可空，故本卡仅补 displayName。
- 同页 profile Form（L247-，`isEditable` 分支）与元数据 Form 是两个独立表单，
  本卡不动。

### 仓内惯例

- 前端必填校验惯例：`rules={[{ required: true, message: t('assets.create.xxxRequired') }]}`
  见 `CreateAssetForm.tsx` L148（namespace）、L161（name）、L188（ownerTeam）；
  `AssetAccessPage.tsx` L148 同风格。
- `CreateAssetForm.tsx` L170 的 displayName **也无 rules**——但创建端点契约
  `CreateAssetRequest.displayName` 允许为空（后端会自动回落 name），与 PATCH rename
  空名 400 的语义不同；本卡只改 Settings 页，不改创建页，避免语义混淆
  （若创建页也需前端拦截，属额外产品裁定，另卡处置）。

### i18n 键位

- `frontend/src/shared/i18n/zh.json` L257- L `assets.create` 段：已有
  `namespaceRequired`（L260）/`nameRequired`（L262）/`ownerTeamRequired`（L271），
  无 `displayNameRequired`；`en.json` 同段镜像。
- 新增键走同段同命名，双语句式对齐：zh `请输入展示名称`、en
  `Please enter display name`。

### e2e 联动

- `frontend/e2e/settings-form.spec.ts` 切片⑨ L349-384 现断言：请求真实发出 →
  400 → `COMMON_INVALID_ARGUMENT` → `.ant-message` 含后端校验原文 `displayName
  must not be blank` → 远端 `displayName` 保留。
- 头部注释 L8-11「诚实边界」与 L21-22「若未来补上前端拦截，waitForResponse 会超时
  如实红，提醒同步更新断言」、切片⑨内 L357-358 重复同一预告——本卡是这三处注释所
  指「另卡」，须同步更新：切片⑨改为「清空展示名称 → 点击保存 → **无 PATCH 请求
  发出**（Playwright 断言 `waitForRequest` 短超时捕获为 0 次） + `.ant-form-item-explain-error`
  可见 i18n 文案 + 无 `.ant-message` toast + 远端 `displayName` 保留」；头部注释与
  L357-358 改为「已由 TASK-P0BR-072 补齐前端拦截，本切片断言收紧至『无请求发出』」。
- 其余切片（①/②/⑧/⑨b/⑩）不受影响，保持既有断言。

### Vitest 现有结构

- `frontend/src/features/assets/AssetSettingsPage.test.tsx` L28-51 mock `./api` 六函数
  （`mockUpdateAsset` 等），5 既有用例均通过 `renderWithProviders` 挂载。
- 新增用例沿用同结构：等待 `ast_001` 加载 → `fireEvent.change` 将 displayName 输入框
  清空 → 点击「保存」按钮 → 断言 `mockUpdateAsset` 未被调用 + 错误文案「请输入展示名称」
  可见（i18n 默认中文环境）。

## 4. 修复方案

1. **i18n**（`zh.json`/`en.json`）：在 `assets.create` 段新增 `displayNameRequired`
   键（紧邻 `nameRequired` 之后、`displayNameLabel` 之前，保持双语同位序）。
2. **前端表单**（`AssetSettingsPage.tsx` L219-221）：为 displayName `Form.Item` 添加
   `rules={[{ required: true, whitespace: true, message: t('assets.create.displayNameRequired') }]}`
   ——`whitespace: true` 保证全空格亦被拦截，与后端 rename 空/blank 校验（071 修复）
   语义一致。
3. **Vitest**（`AssetSettingsPage.test.tsx`）：新增 1 个差异用例「清空 displayName
   提交：mockUpdateAsset 未被调用 + 字段级错误文案可见」；沿用现有 mock 结构，
   不新增测试基建。
4. **e2e**（`settings-form.spec.ts`）：切片⑨按 §3「e2e 联动」段收紧断言（无 PATCH
   请求 + `.ant-form-item-explain-error` + 无 toast + 远端保留），同步更新头部
   「诚实边界」段与 L357-358 注释为本卡收口口径。
5. **文档**：`work-breakdown.md` 072 行新增（状态按实施终态填写，未收口时留 DRAFT 指针）；
   `gap-analysis §7` 追加本卡指针行。

## 5. 垂直切片与验收

| # | 切片 | 证据 |
|---|------|------|
| 1 | i18n `displayNameRequired` 新键（zh/en） | 文件 diff + pnpm typecheck |
| 2 | AssetSettingsPage.tsx L219 添加 rules | 文件 diff + pnpm lint/typecheck/build |
| 3 | Vitest 新增拦截用例 | pnpm test（新用例 PASS + 既有 5 用例不回归） |
| 4 | e2e 切片⑨口径收紧 + 注释同步 | 前端容器重建 + pnpm test:e2e 全量无回归（111 基线） |
| 5 | work-breakdown + gap-analysis 指针 + EVD | 本卡 §8 完成报告 |

## 6. 门禁清单（实施收口时逐项勾选）

- [x] validate-spec / validate-task-card（含 -CheckChangedPaths）PASS
- [x] frontend `pnpm lint / typecheck / test / build` 全 PASS（Vitest 新用例 2 + 既有 5 = 7 passed；串行全量 49 files / 228 tests 全绿）
- [x] 真实 Compose 栈：前端镜像重建 health 就绪 → `pnpm test:e2e` 全量 112 passed / 0 skipped
      （= 071 基线 111 + 本卡新增⑨c；切片⑨新口径 PASS）
- [x] EVD-P0BR-072-001.yaml 完成且 secrets 脱敏确认（redactionChecked: true）
- [x] work-breakdown 072 行 + gap-analysis §7 指针登记

## 7. 越界与禁止事项

- **不改 backend**：071 已修复 rename 空名精确 400；本卡是**前端 UX 前移**，不
  替代后端校验，后端 400 防线必须保持（前后端双防线）。
- **不改 contracts**：`UpdateAssetRequest.displayName` 契约描述已在 071 补齐
  「为空表示不变更」语义；本卡不改契约。
- **不改 CreateAssetForm.tsx**：创建端点 displayName 可空（后端回落 name），与
  Settings rename 语义不同；若创建页需前端拦截属额外产品裁定，另卡处置。
- **不改 deploy/**、V1/V2 迁移、bootstrap、worker。
- **不放宽任何既有断言**；切片⑨按新行为**收紧**（无请求发出、无 toast），
  非「skip 或降级」。
- 不新增 ErrorCode；不新增 i18n 键位段（仅 `assets.create.displayNameRequired` 一键）。
- 本卡不追溯 071 已提交工件（TASK-P0BR-071.md 与 EVD-P0BR-071-001.yaml 均在
  forbiddenPaths）；work-breakdown 071 行不追改。

## 8. 完成报告

实施完成于 2026-09-30（baseCommit `e77d0d2` 之上的交接态工作树，提交由批准人处置）。
纵向结果达成：AssetSettingsPage 元数据 Form 的 displayName 前端必填拦截生效，
e2e 切片⑨按新真实行为收紧，后端 400 双防线由新增 API 级切片⑨c 守护。

```text
Completed:
- 切片 1：i18n 新增 `assets.create.displayNameRequired`（zh「请输入展示名称」/
  en「Please enter display name」，与 nameRequired 同段同位序）。
- 切片 2：AssetSettingsPage.tsx L219 displayName Form.Item 添加
  `rules=[{ required: true, whitespace: true, message: t('assets.create.displayNameRequired') }]`。
- 切片 3：AssetSettingsPage.test.tsx 新增 2 用例（清空提交 / 仅空白提交均被拦截、
  mockUpdateAsset 未被调用、错误文案可见）；定向 7 passed。
- 切片 4：settings-form.spec.ts 切片⑨收紧为「无 PATCH 请求发出（request 监听器 0 命中）
  + .ant-form-item-explain-error 可见 + 远端值保留」；新增切片⑨c（API 直发空白
  displayName → 精确 400 + COMMON_INVALID_ARGUMENT + rowVersion 不推进）保住 071
  后端防线口径；头部「诚实边界」注释同步为 072 收口叙述。
- 切片 5：EVD-P0BR-072-001.yaml、work-breakdown 072 行、gap-analysis §7 缺口③闭环指针。

Changed files:
- frontend/src/features/assets/AssetSettingsPage.tsx
- frontend/src/features/assets/AssetSettingsPage.test.tsx
- frontend/src/shared/i18n/zh.json
- frontend/src/shared/i18n/en.json
- frontend/e2e/settings-form.spec.ts
- docs/ai-spec/02-delivery/work-breakdown.md
- docs/architecture/modelscope-page-gap-analysis-2026-09-21.md
- docs/ai-spec/tasks/TASK-P0BR-072.md
- docs/ai-spec/tasks/evidence/EVD-P0BR-072-001.yaml
（build-logs/*.log 取证工件按 .gitignore 惯例不入库；verify-bom.ps1 为卡 FM
preExistingDirtyPaths 登记的既存脏路径，非本卡改动。）

Contract/database changes:
- 无。契约（UpdateAssetRequest 语义）已在 071 补齐；本卡无 OpenAPI/事件/迁移变更。

Validation run and results:
- validate-spec.ps1 / validate-task-card.ps1：PASS（governance-072-ready.log；
  收口三连见 governance-072-close.log）。
- pnpm lint / typecheck：exit 0（lint 2 warnings 既存）。
- pnpm test：并行首跑 16 failed 全为 5000ms 环境性超时（散布 12 个无关文件，
  072-vitest-full.log 如实留档）；`--no-file-parallelism` 串行复跑
  49 files / 228 tests 全绿（072-vitest-full-serial.log，exit 0）。
- pnpm build：exit 0（3257 modules / 22.20s）。
- docker compose build frontend + up -d frontend：Built / Up（8080 探活 200）。
- 定向 e2e settings-form：6 passed（12.2s）。
- 全量 pnpm test:e2e（真实 Compose 栈）：112 passed / 0 failed / 0 skipped
  （3.0m，exit 0 = 071 基线 111 + 本卡新增⑨c）。

Validation not run and reasons:
- -CheckCompletion：设计内 EXPECTED_FAIL（交接态 dirtyWorktree=true、
  review.accepted=false；DEC-009 提交与验收须由批准人/验证权威处置）。
- backend mvnw verify / 契约 lint：本卡未触达 backend 与 contracts（forbiddenPaths），
  沿用 071 终态证据。

Remaining risks or follow-ups:
- 提交授权待批准人处置（AI 不自提交）；提交后 EVD source 需由验证权威随 review 更新。
- CreateAssetForm displayName 无 rules 属创建/更新语义差异（后端回落 name），
  按卡 §7 不在本卡；如需拦截另卡裁定。
- Vitest 并行首跑的资源性超时为本机验证窗口现象（串行全绿），未改 testTimeout、
  未放宽任何断言；若 CI 复现可另立工程卡评估基线超时配置。
- 五卡（067/068/069/071/072）review.accepted 待验证权威统一裁定。
```
