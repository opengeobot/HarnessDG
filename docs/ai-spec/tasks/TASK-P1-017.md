---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-017
status: READY
implementationAuthorized: false
phase: P1
baseCommit: b15a30918f53172087b80306d91cf9181494fac0
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED；契约已落地但 overstated；本任务据实校正 x-implementation-status
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-AST-001
acceptanceScenarios:
  - AC-P1-AST-001
decisions:
  - DEC-010
  - DEC-011
scenarioEvidencePlan:
  - AC-P1-AST-001|E3|OpenAPI aihub-v1.yaml/aihub-agent-v1.yaml x-implementation-status 与项目 IMPLEMENTED_UNVERIFIED 一致；redocly lint PASS
crossCuttingPlan:
  - AUTHN|N/A - 契约元数据
  - AUTHZ|N/A
  - DB_FILTER|N/A
  - STATE|N/A
  - IDEMPOTENCY|N/A
  - CONSISTENCY|N/A
  - ERRORS|N/A
  - AUDIT|N/A
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|N/A
  - CONFIG|N/A
  - OBSERVABILITY|N/A
  - SECRETS|N/A - 契约无凭据
allowedPaths:
  - contracts/openapi/aihub-v1.yaml
  - contracts/openapi/aihub-agent-v1.yaml
  - docs/ai-spec/tasks/TASK-P1-017.md
  - docs/ai-spec/tasks/evidence/EVD-P1017-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E3
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1017-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-017.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-017.md -CheckChangedPaths
  - npx @redocly/cli lint --config contracts/openapi/redocly.yaml contracts/openapi/aihub-v1.yaml
  - npx @redocly/cli diff /tmp/aihub-base.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking
approvedBy: pending-verification-authority
approvedAt: 2026-07-12T00:00:00Z
---
# TASK-P1-017：OpenAPI x-implementation-status 据实校正（Wave B7）

> 状态：`READY`（待用户授权）

## 1. 目标
将 `aihub-v1.yaml`/`aihub-agent-v1.yaml` 的 `x-implementation-status: implemented` 据实校正为与项目 `IMPLEMENTED_UNVERIFIED` 一致（如 `implemented-unverified` 或 `info.x-verification-status` 顶层声明），不削弱契约语义。

## 2. 关联规格
```yaml
requirements: ['REQ-AST-001']
acceptanceScenarios: ['AC-P1-AST-001']
decisions: ['DEC-010', 'DEC-011']
```

## 3. 范围
- 仅契约元数据校正；不改端点 schema/行为。

## 4. 行为切片
| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P1-AST-001` | Maintainer | OpenAPI | 校正 | x-implementation-status 与项目状态一致；redocly lint PASS | `E3` |

## 8. 验证命令
```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-017.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-017.md -CheckChangedPaths
npx @redocly/cli lint --config contracts/openapi/redocly.yaml contracts/openapi/aihub-v1.yaml
```
