---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-001
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-COM-001
acceptanceScenarios:
  - AC-P0B-ENG-002
  - AC-P0B-ENG-003
decisions:
  - DEC-019
  - DEC-020
scenarioEvidencePlan:
  - AC-P0B-ENG-002|E1|OpenAPI lint passes with x-implementation-status and x-error-codes on all operations
  - AC-P0B-ENG-003|E1|Breaking diff check blocks unapproved changes in CI
crossCuttingPlan:
  - AUTHN|N/A - contract-level change only
  - AUTHZ|N/A - no authorization logic change
  - DB_FILTER|N/A - no database change
  - STATE|N/A - no state machine
  - IDEMPOTENCY|N/A - no persistent side effect
  - CONSISTENCY|N/A - no transaction
  - ERRORS|Add x-error-codes extension to each OpenAPI endpoint per DEC-019
  - AUDIT|N/A - no business audit
  - NOTIFICATION|N/A - no notification
  - TAXONOMY_I18N|N/A - no governed value
  - CONFIG|N/A - no deployment config
  - OBSERVABILITY|N/A - no runtime service
  - SECRETS|N/A - no secret handling
allowedPaths:
  - contracts/openapi/aihub-v1.yaml
  - contracts/openapi/aihub-agent-v1.yaml
  - contracts/mcp/tools.yaml
  - contracts/events/events-v1.yaml
  - .github/workflows/ci.yml
  - backend/src/test/java/com/aihub/arch
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/java
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E1
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR001-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-001.md
  - npx @redocly/cli lint contracts/openapi/aihub-v1.yaml
approvedBy: null
approvedAt: null
---

# TASK-P0BR-001：OpenAPI 实现状态同步、x-error-codes 与 breaking diff 阻断

> 状态：`READY`

## 1. 目标

将 OpenAPI 契约中所有 `x-implementation-status` 与真实代码同步为 `implemented`；为每个端点添加 `x-error-codes` 扩展（DEC-019）；CI 中 breaking diff 检查设为阻断。

## 2. 关联规格

```yaml
requirements: [REQ-COM-001]
acceptanceScenarios: [AC-P0B-ENG-002, AC-P0B-ENG-003]
decisions: [DEC-019, DEC-020]
openapiOperations: [all operations in aihub-v1.yaml, aihub-agent-v1.yaml]
```

## 3. 范围

### 允许修改

- `contracts/openapi/`：更新 x-implementation-status、添加 x-error-codes
- `contracts/mcp/tools.yaml`：同步实现状态
- `contracts/events/events-v1.yaml`：同步实现状态
- `.github/workflows/ci.yml`：breaking diff 设为阻断

### 明确不在范围

- 后端产品代码、Migration

### 禁止变化

- 不修改已有 API 行为签名

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-ENG-002` | CI | OpenAPI YAML | lint + example validation | 所有操作有 operationId、权限、错误、审计和实现状态 | `E1` |
| `AC-P0B-ENG-003` | CI | 基线契约 | breaking diff | 未关联 ADR 的破坏变更阻断流程 | `E1` |

## 5. 横切要求

仅修改契约元数据，不影响运行时行为。

## 6. 契约与数据先行

1. 每个 operation 的 `x-implementation-status` 从 `planned-p0-b` 更新为 `implemented`
2. 每个 operation 添加 `x-error-codes` 列表引用 error-catalog.md 中的错误码
3. CI 中 `spectral` 或 `oasdiff` breaking diff 移除 `continue-on-error: true`

## 7. 实施步骤

- [ ] 更新 aihub-v1.yaml 所有操作的 x-implementation-status
- [ ] 添加 x-error-codes 扩展到每个操作
- [ ] 更新 aihub-agent-v1.yaml
- [ ] CI breaking diff 设为阻断
- [ ] OpenAPI lint 通过

## 8. 验证命令

```text
npx @redocly/cli lint contracts/openapi/aihub-v1.yaml
# 预期：valid，退出码 0

npx @redocly/cli lint contracts/openapi/aihub-agent-v1.yaml
# 预期：valid，退出码 0
```

## 9. 停止条件

无。

## 10. 完成报告

```text
Completed:
Changed files:
Validation run and results:
```
