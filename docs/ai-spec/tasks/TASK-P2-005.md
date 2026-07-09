---
schemaVersion: harnessdg.task/v1
taskId: TASK-P2-005
status: READY
implementationAuthorized: true
phase: P2
baseCommit: 26c3985c5092d1c64def2c33e8db1e5d6ac4a256
stageGatePassed: true
stageGateEvidence: P1 asset catalog gap closure committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-UPL-003
acceptanceScenarios:
  - AC-P2-UPL-003
decisions:
  - DEC-010
scenarioEvidencePlan:
  - AC-P2-UPL-003|E4|Materialization Worker validates streams to DVC/Git with fault injection recovery
crossCuttingPlan:
  - AUTHN|Worker runs with system principal scoped to session asset
  - AUTHZ|Handler verifies session owner and asset:upload before materialization
  - DB_FILTER|N/A - worker processes authorized session only
  - STATE|Session transitions to PROCESSING then COMPLETED or FAILED per state machine
  - IDEMPOTENCY|UPLOAD_MATERIALIZE handler idempotent by sessionId
  - CONSISTENCY|Upload Saga U4-U9 with outbox and staging cleanup Job
  - ERRORS|Retryable vs non-retryable classification; DVC_OBJECT_MISSING on verification failure
  - AUDIT|UPLOAD_MATERIALIZED and UPLOAD_MATERIALIZE_FAILED events without sensitive samples
  - NOTIFICATION|N/A - status via session/job polling
  - TAXONOMY_I18N|N/A - worker path normalization only
  - CONFIG|DVC CLI path and work directory from secure config
  - OBSERVABILITY|Worker stage metrics and trace spans per saga step
  - SECRETS|Work directory isolated; no object bytes or credentials in logs
allowedPaths:
  - backend/src/main/java/com/aihub/transfer/infrastructure
  - backend/src/main/java/com/aihub/transfer/application
  - backend/src/main/java/com/aihub/version/application
  - backend/src/main/java/com/aihub/integration/gitea
  - backend/src/main/java/com/aihub/integration/minio
  - backend/src/main/java/com/aihub/job
  - backend/src/test/java/com/aihub/transfer
  - docs/ai-spec/tasks/evidence/EVD-P2005-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P2005-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-005.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-005.md -CheckChangedPaths
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P2-005：Materialization Worker 校验→DVC→Git→投影 Saga

> 状态：`READY`

## 1. 目标

实现 UPLOAD_MATERIALIZE Worker：流式校验、DVC add/push、Git commit/push 与 Artifact 投影，含故障注入 E4。

## 2. 关联规格

```yaml
requirements: ['REQ-UPL-003']
acceptanceScenarios: ['AC-P2-UPL-003']
decisions: ['DEC-010']
```

## 3. 范围

### 允许修改

- UploadMaterializeJobHandler、Version manifest binding、集成测试与故障注入。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P2-UPL-003` | Worker | Materialization Worker validates streams to DVC/Git with fault injection recovery | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- 依赖 TASK-P2-003 complete 入队路径

## 7. 实施步骤

- [ ] 完善 UploadMaterializeJobHandler 流式校验与 DVC/Git saga；故障注入测试。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-005.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-005.md -CheckChangedPaths
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
