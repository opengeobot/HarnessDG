---
schemaVersion: harnessdg.task/v1
taskId: TASK-P4-005
status: READY
implementationAuthorized: true
phase: P4
baseCommit: 59f40d28dab5a9fb5fdeb5cb29080219b10ee4a6
stageGatePassed: true
stageGateEvidence: P3 release governance gap closure committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-API-AGT-001
acceptanceScenarios:
  - AC-P4-API-001
decisions:
  - DEC-010
scenarioEvidencePlan:
  - AC-P4-API-001|E4|Read-only and contribution Agent OpenAPI profiles import with breaking diff gate
crossCuttingPlan:
  - AUTHN|Agent JWT same as REST/MCP authentication
  - AUTHZ|Write endpoints excluded from default read-only profile
  - DB_FILTER|N/A - contract generation only
  - STATE|N/A - contract metadata
  - IDEMPOTENCY|Document Idempotency-Key on write endpoints in contribution profile
  - CONSISTENCY|Profiles reference same operation IDs as authoritative OpenAPI
  - ERRORS|Unified error schema across read and contribution profiles
  - AUDIT|N/A - contract metadata
  - NOTIFICATION|N/A - contract metadata
  - TAXONOMY_I18N|N/A - stable enum codes in schemas
  - CONFIG|N/A - no deployment config
  - OBSERVABILITY|N/A - contract task
  - SECRETS|N/A - no secrets in generated examples
allowedPaths:
  - contracts/openapi/aihub-agent-v1.yaml
  - contracts/openapi/aihub-v1.yaml
  - scripts/openapi
  - docs/ai-spec/tasks/evidence/EVD-P4005-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P4005-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-005.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-005.md -CheckChangedPaths
  - npx @redocly/cli lint contracts/openapi/aihub-agent-v1.yaml
  - npx @redocly/cli diff contracts/openapi/aihub-agent-v1.yaml contracts/openapi/aihub-agent-v1.yaml --fail-on-breaking
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P4-005：Agent 友好 OpenAPI 裁剪 Profile

> 状态：`READY`

## 1. 目标

实现 REQ-API-AGT-001 从权威 OpenAPI 生成只读与 contribution 两个 Agent API Profile。

## 2. 关联规格

```yaml
requirements: ['REQ-API-AGT-001']
acceptanceScenarios: ['AC-P4-API-001']
decisions: ['DEC-010']
```

## 3. 范围

### 允许修改

- 见 Front Matter `allowedPaths`。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P4-API-001` | Operator | Read-only and contribution Agent OpenAPI profiles import with breaking diff gate | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/MCP/Event/Migration（如适用）

## 7. 实施步骤

- [ ] 裁剪 aihub-agent-v1.yaml；生成 profile 脚本；breaking diff 与导入测试。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-005.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-005.md -CheckChangedPaths
npx @redocly/cli lint contracts/openapi/aihub-agent-v1.yaml
npx @redocly/cli diff contracts/openapi/aihub-agent-v1.yaml contracts/openapi/aihub-agent-v1.yaml --fail-on-breaking
```

## 9. 停止条件

- validate-task-card 或 task-specific 命令失败
- AC 证据未达到 requiredEvidenceLevel

## 10. 完成报告

```text
Completed:
Changed files:
Contract/database changes:
Validation run and results:
Validation not run and reasons:
Remaining risks or follow-ups:
```
