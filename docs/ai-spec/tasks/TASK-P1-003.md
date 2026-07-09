---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-003
status: READY
implementationAuthorized: true
phase: P1
baseCommit: 38fd44d2dcbc1a611cfdfd923337e5599c3b3feb
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED; DEC-010 continuous implementation; P0BR-046 gate lifted
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-AST-002
  - REQ-AST-008
acceptanceScenarios:
  - AC-P1-AST-002
  - AC-P1-AST-003
  - AC-P1-AST-004
  - AC-P1-AST-005
decisions:
  - DEC-010
  - DEC-011
scenarioEvidencePlan:
  - AC-P1-AST-002|E4|CreateAsset normal path completes Provision Saga with Gitea repo and initial commit
  - AC-P1-AST-003|E4|Idempotency-Key replay returns first response without duplicate repo
  - AC-P1-AST-004|E4|Gitea failure injection leaves retriable intent and recovers on retry
  - AC-P1-AST-005|E4|Unauthorized and cross-scope create rejected with no side effects
crossCuttingPlan:
  - AUTHN|JWT PrincipalContext required for create intent
  - AUTHZ|asset:create permission and scope check before saga start
  - DB_FILTER|N/A - write path creates new asset in caller scope
  - STATE|ProvisioningStatus lifecycle PENDING->COMPLETED/FAILED
  - IDEMPOTENCY|Principal+path+digest idempotency for create
  - CONSISTENCY|Asset Provision Saga A1-A6 with persistent Worker job
  - ERRORS|ASSET_ALREADY_EXISTS, IDEMPOTENCY_KEY_CONFLICT, ASSET_REPOSITORY_PROVISION_FAILED
  - AUDIT|ASSET_CREATE_REQUESTED, REPOSITORY_PROVISIONED, ASSET_CREATED, ASSET_CREATE_FAILED
  - NOTIFICATION|N/A - provisioning status via API polling
  - TAXONOMY_I18N|Reject free-form Owner/tag/governance values on create
  - CONFIG|Gitea connection from secure config, not hardcoded
  - OBSERVABILITY|Trace spans for saga steps and Gitea calls
  - SECRETS|No Gitea token in logs, audit or API responses
allowedPaths:
  - backend/src/main/java/com/aihub/asset/application
  - backend/src/main/java/com/aihub/asset/domain
  - backend/src/main/java/com/aihub/integration/gitea
  - backend/src/test/java/com/aihub/asset/application
  - backend/src/test/java/com/aihub/integration/gitea
  - contracts/events/events-v1.yaml
  - docs/ai-spec/tasks/evidence/EVD-P1003-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1003-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-003.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-003.md -CheckChangedPaths
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P1-003：资产创建意图与 Gitea 建仓 Saga

> 状态：`READY`

## 1. 目标

实现 CreateAsset 意图持久化、Gitea 建仓 Saga、幂等重放和故障恢复。

## 2. 关联规格

```yaml
requirements: ['REQ-AST-002', 'REQ-AST-008']
acceptanceScenarios: ['AC-P1-AST-002', 'AC-P1-AST-003', 'AC-P1-AST-004', 'AC-P1-AST-005']
decisions: ['DEC-010', 'DEC-011']
```

## 3. 范围

### 允许修改

- AssetApplicationService、Gitea Provisioner、Worker handler、事件 schema。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P1-AST-002` | Maintainer | CreateAsset normal path completes Provision Saga with Gitea repo and initial commit | Execute | PASS | `E4` |
| `AC-P1-AST-003` | Maintainer | Idempotency-Key replay returns first response without duplicate repo | Execute | PASS | `E4` |
| `AC-P1-AST-004` | Maintainer | Gitea failure injection leaves retriable intent and recovers on retry | Execute | PASS | `E4` |
| `AC-P1-AST-005` | Maintainer | Unauthorized and cross-scope create rejected with no side effects | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- V27+ 为 P1-002 回填下限；其他任务按需使用 V27+ 新 Migration

## 7. 实施步骤

- [ ] 实现 Saga 状态机；Worker 建仓；幂等存储；失败补偿与重试。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-003.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-003.md -CheckChangedPaths
cd backend && ./mvnw -o verify
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
