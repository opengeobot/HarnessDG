---
schemaVersion: harnessdg.task/v1
taskId: TASK-<PHASE>-<number>
status: DRAFT
implementationAuthorized: false
phase: P0-B
baseCommit: <40-char-git-sha>
stageGatePassed: false
stageGateEvidence: <human-readable-summary>
stageGateEvidenceRefs:
  - DEC-...
requirements:
  - REQ-...
acceptanceScenarios:
  - AC-...
decisions:
  - DEC-...
scenarioEvidencePlan:
  - AC-...|E4|<expected-test-report-or-runtime-artifact>
crossCuttingPlan:
  - AUTHN|<requirement-or-N/A-with-reason>
  - AUTHZ|<requirement-or-N/A-with-reason>
  - DB_FILTER|<requirement-or-N/A-with-reason>
  - STATE|<requirement-or-N/A-with-reason>
  - IDEMPOTENCY|<requirement-or-N/A-with-reason>
  - CONSISTENCY|<requirement-or-N/A-with-reason>
  - ERRORS|<requirement-or-N/A-with-reason>
  - AUDIT|<requirement-or-N/A-with-reason>
  - NOTIFICATION|<requirement-or-N/A-with-reason>
  - TAXONOMY_I18N|<requirement-or-N/A-with-reason>
  - CONFIG|<requirement-or-N/A-with-reason>
  - OBSERVABILITY|<requirement-or-N/A-with-reason>
  - SECRETS|<requirement-or-N/A-with-reason>
allowedPaths:
  - <narrow/file/or/module/path>
  - docs/ai-spec/tasks/evidence/EVD-<task>-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-<task>-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-<PHASE>-<number>.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-<PHASE>-<number>.md -CheckChangedPaths
  - <task-specific-test-command>
approvedBy: <human-or-controlled-approval>
approvedAt: <UTC-timestamp>
---

# TASK-<phase>-<number>：<一个可观察结果>

> 状态：`DRAFT`
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。

## 1. 目标

用一句话描述用户或外部系统能够观察到的结果。

## 2. 关联规格

```yaml
requirements: [REQ-...]
acceptanceScenarios: [AC-...]
decisions: [DEC-...]
adrs: [ADR-...]
openapiOperations: [...]
mcpTools: [...]
eventSchemas: [...]
flywayBaseline: V...
```

所有需求必须是 `READY`，所有决策必须是 `ACCEPTED`。否则任务不得进入实施。
正文 ID 必须与 Front Matter 完全一致。AI 可以起草 DRAFT，但不得填写批准人并授权自身实施。
`stageGateEvidenceRefs` 只能引用本 Task 已声明的 Accepted Decision 或仓库内真实 Evidence 文件。
`scenarioEvidencePlan` 必须以 `AC-ID|E1-E5|预期产物` 逐条覆盖 Front Matter 的全部 AC。
`crossCuttingPlan` 使用模板中的 13 个稳定机器键；每项必须写具体要求，或写 `N/A -` 加理由。

## 3. 范围

### 允许修改

- 模块：
- 文件或目录：
- 契约：
- Migration：
- 页面：
- Fixture/Verify：

### 明确不在范围

- ...

### 禁止变化

- 不改变的 API/状态/权限：
- 不允许的依赖升级或重构：
- 不允许的兼容破坏：

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-...` |  |  |  |  | `E2/E3/E4` |

必须覆盖正常、边界、失败、越权；涉及异步/并发时还须覆盖恢复和并发。

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal |  |
| Permission/Scope/ACL |  |
| 数据库权限下推 |  |
| 状态前置条件 |  |
| 幂等与并发 |  |
| 事务与外部一致性 |  |
| 错误码 |  |
| 审计事件 |  |
| 通知 |  |
| 字典/标签/i18n |  |
| 配置 |  |
| 指标/Trace/告警 |  |
| Secret/日志脱敏 |  |

任何一项若不适用，填写 `N/A` 并说明理由；不得留空。

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：
2. 示例请求、响应和错误：
3. 新增 Flyway 文件：
4. 存量清点与回填：
5. 索引、唯一约束、外键及理由：
6. 兼容窗口与失败恢复：
7. 生成类型更新：

禁止修改已应用 Migration。

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

## 8. 验证命令

```text
<精确工作目录和命令>
```

每条命令声明：

- 预期退出码；
- 覆盖的 AC ID；
- 所需依赖；
- SKIP 判定；
- 报告或产物路径。

精确命令同时写入 Front Matter `requiredValidationCommands`；至少包含三条治理校验和一条本任务
专用测试/验收命令。正文描述不能替代机器字段。

路径触发强制命令：修改 backend 必须包含 Maven wrapper `verify`；修改 frontend 必须包含
`pnpm lint/typecheck/test/build`；修改 OpenAPI 必须包含 lint 和 blocking diff；修改 Compose 必须
包含 config 与仓库 verify 脚本。任务领取前如果项目还没有这些命令，先建立并批准测试基线，不能
用构建命令替代行为测试。

实施前固定执行：

```powershell
./docs/ai-spec/tools/validate-spec.ps1
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath <this-task-card>
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath <this-task-card> -CheckChangedPaths
```

需要声明“完成”时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath <this-task-card> -CheckCompletion
```

`-CheckCompletion` 要求工作树干净、Evidence Commit 等于当前 HEAD、每条 AC 均有足够等级的 PASS
证据、没有 SKIP/notProven、已完成 Secret 检查并由验证责任方接受。未通过时最多交接为
`IMPLEMENTED_UNVERIFIED`。

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 规范事实冲突；
- 存在未关闭的产品决策；
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
