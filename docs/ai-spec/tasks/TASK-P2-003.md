---
schemaVersion: harnessdg.task/v1
taskId: TASK-P2-003
status: READY
implementationAuthorized: true
phase: P2
baseCommit: 26c3985c5092d1c64def2c33e8db1e5d6ac4a256
stageGatePassed: true
stageGateEvidence: P1 asset catalog gap closure committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-UPL-001
  - REQ-UPL-002
acceptanceScenarios:
  - AC-P2-UPL-001
  - AC-P2-UPL-002
decisions:
  - DEC-010
scenarioEvidencePlan:
  - AC-P2-UPL-001|E4|Upload Session created with quota/path checks and idempotent replay
  - AC-P2-UPL-002|E4|Part presign, parallel upload and complete with Materialization Job idempotency
crossCuttingPlan:
  - AUTHN|JWT PrincipalContext required for session create and part sign
  - AUTHZ|asset:upload and session owner checks before presign and complete
  - DB_FILTER|Session bound to Principal and target Version; cross-subject access denied
  - STATE|UploadSessionStatus transitions per state-machines.md
  - IDEMPOTENCY|Session create and complete idempotency with conflict detection
  - CONSISTENCY|PG Session and MinIO prefix created atomically; cleanup Job on expiry
  - ERRORS|UPLOAD_NOT_FOUND, UPLOAD_LIMIT_EXCEEDED, UPLOAD_PATH_INVALID, UPLOAD_QUOTA_EXCEEDED
  - AUDIT|Session create/cancel/complete audited without presigned URLs
  - NOTIFICATION|N/A - client polls session status
  - TAXONOMY_I18N|N/A - upload path normalization only
  - CONFIG|Upload limits and presign TTL from configuration service
  - OBSERVABILITY|Session lifecycle metrics and presign latency traces
  - SECRETS|Presigned URLs short TTL; no permanent MinIO credentials in responses
allowedPaths:
  - backend/src/main/java/com/aihub/transfer/application
  - backend/src/main/java/com/aihub/transfer/domain
  - backend/src/main/java/com/aihub/transfer/api
  - backend/src/main/java/com/aihub/transfer/infrastructure
  - backend/src/test/java/com/aihub/transfer
  - contracts/openapi/aihub-v1.yaml
  - docs/ai-spec/tasks/evidence/EVD-P2003-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P2003-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-003.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-003.md -CheckChangedPaths
  - npx @redocly/cli lint contracts/openapi/aihub-v1.yaml
  - npx @redocly/cli diff contracts/openapi/aihub-v1.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P2-003：Upload Session、Part 签名与 complete

> 状态：`READY`

## 1. 目标

实现 REQ-UPL-001/002 的 Session 创建、Part 预签名、complete 与 Materialization Job 入队。

## 2. 关联规格

```yaml
requirements: ['REQ-UPL-001', 'REQ-UPL-002']
acceptanceScenarios: ['AC-P2-UPL-001', 'AC-P2-UPL-002']
decisions: ['DEC-010']
```

## 3. 范围

### 允许修改

- UploadApplicationService、UploadController、StoragePort 适配与集成测试。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P2-UPL-001` | Uploader | Upload Session created with quota/path checks and idempotent replay | Execute | PASS | `E4` |
| `AC-P2-UPL-002` | Uploader | Part presign, parallel upload and complete with Materialization Job idempotency | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- V16+ 为既有 upload 下限；按需新增 forward Migration

## 7. 实施步骤

- [ ] Session 创建/Part 签名/complete 全流程；限额与路径校验；幂等与审计。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-003.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-003.md -CheckChangedPaths
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
