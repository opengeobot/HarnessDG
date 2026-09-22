---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-000A
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements: []
acceptanceScenarios: []
decisions:
  - DEC-010
scenarioEvidencePlan: []
crossCuttingPlan:
  - AUTHN|N/A - infrastructure prerequisite only
  - AUTHZ|N/A - no authorization logic
  - DB_FILTER|N/A - no database
  - STATE|N/A - no state machine
  - IDEMPOTENCY|N/A - no persistent side effect
  - CONSISTENCY|N/A - no transaction
  - ERRORS|N/A - infrastructure check
  - AUDIT|N/A - no business audit
  - NOTIFICATION|N/A - no notification
  - TAXONOMY_I18N|N/A - no governed value
  - CONFIG|N/A - no deployment config
  - OBSERVABILITY|N/A - no runtime service
  - SECRETS|N/A - no secret handling
allowedPaths:
  - deploy/compose/scripts
  - .github/workflows/ci.yml
  - AGENTS.md
preExistingDirtyPaths: []
forbiddenPaths:
  - backend
  - frontend
  - contracts
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E1
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR000A-001.yaml
requiredValidationCommands:
  - pwsh --version
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
approvedBy: null
approvedAt: null
---

# TASK-P0BR-000A：安装并验证 pwsh 在 Linux 开发环境和 CI 中可用

> 状态：`READY`
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。

## 1. 目标

确保 PowerShell Core（pwsh）在 Linux 开发环境和 CI 中可用，使 `validate-task-card.ps1` 和 `validate-spec.ps1` 能正常执行。

## 2. 关联规格

```yaml
requirements: []
acceptanceScenarios: []
decisions: [DEC-010]
adrs: [ADR-0002]
```

## 3. 范围

### 允许修改

- CI 配置：`.github/workflows/ci.yml`（添加 pwsh 安装步骤）
- Compose 脚本：`deploy/compose/scripts/`（确保 .sh 替代方案可用）
- AGENTS.md：记录 pwsh 安装要求

### 明确不在范围

- 产品代码、契约、Migration

### 禁止变化

- 不改变任何业务逻辑

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| — | 开发者 | Linux 开发环境 | 执行 `pwsh --version` | 输出 PowerShell 版本号，退出码 0 | `E1` |
| — | CI | Ubuntu runner | 执行 `validate-task-card.ps1` | 正常解析并退出 | `E1` |

## 5. 横切要求

全部 N/A（基础设施前置，无业务逻辑）。

## 6. 契约与数据先行

无。

## 7. 实施步骤

- [ ] 验证 pwsh 安装成功
- [ ] CI 配置添加 pwsh 安装步骤
- [ ] validate-spec.ps1 可正常执行

## 8. 验证命令

```text
pwsh --version
# 预期：输出版本号，退出码 0

pwsh ./docs/ai-spec/tools/validate-spec.ps1
# 预期：PASS，退出码 0
```

## 9. 停止条件

无特殊停止条件。

## 10. 完成报告

```text
Completed:
Changed files:
Validation run and results:
```
