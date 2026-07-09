---
schemaVersion: harnessdg.task/v1
taskId: TASK-P2-007
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
  - REQ-DL-001
  - REQ-DVC-001
acceptanceScenarios:
  - AC-P2-EXIT-001
decisions:
  - DEC-010
scenarioEvidencePlan:
  - AC-P2-EXIT-001|E4|Clean Compose JRN-P2-001..003 upload download journey with E4 evidence manifest
crossCuttingPlan:
  - AUTHN|Two real principals with distinct JWT sessions in journey script
  - AUTHZ|Journey proves authorized vs unauthorized upload and download paths
  - DB_FILTER|Journey asserts no cross-org session or download leakage
  - STATE|Full upload session and version lifecycle visible in journey assertions
  - IDEMPOTENCY|Journey includes upload complete idempotency replay step
  - CONSISTENCY|Gitea/DVC/MinIO state matches DB projection at journey checkpoints
  - ERRORS|Journey captures stable UPLOAD_* and download error codes for deny paths
  - AUDIT|Journey verifies upload and download authorization audit events
  - NOTIFICATION|N/A - unless journey includes notification assertion
  - TAXONOMY_I18N|Journey uses governed asset and version fixtures only
  - CONFIG|Compose fixtures use secure MinIO and DVC defaults
  - OBSERVABILITY|Journey captures trace IDs for upload and download correlation
  - SECRETS|Journey scripts redact tokens and presigned URLs in logs and evidence
allowedPaths:
  - deploy/compose/scripts/verify.sh
  - deploy/compose/scripts/p2-journey
  - deploy/compose/fixtures/p2
  - docs/ai-spec/tasks/evidence/EVD-P2007-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P2007-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-007.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-007.md -CheckChangedPaths
  - docker compose -f deploy/compose/docker-compose.yml config --quiet
  - ./deploy/compose/scripts/verify.sh
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P2-007：CLI/Web 上传下载 Compose 纵向出口

> 状态：`READY`

## 1. 目标

在干净 Compose 环境完成 JRN-P2-001..003 上传/物化/下载纵向旅程 E4 出口。

## 2. 关联规格

```yaml
requirements: ['REQ-UPL-001', 'REQ-DL-001', 'REQ-DVC-001']
acceptanceScenarios: ['AC-P2-EXIT-001']
decisions: ['DEC-010']
```

## 3. 范围

### 允许修改

- verify.sh P2 旅程脚本、fixtures、Evidence Manifest。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P2-EXIT-001` | Maintainer | Clean Compose JRN-P2-001..003 upload download journey with E4 evidence manifest | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 依赖 TASK-P2-002..006 后端与 TASK-P2-004 前端能力

## 7. 实施步骤

- [ ] 编写 p2-journey 脚本；fixtures；绑定 AC-P2-EXIT-001 Evidence。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-007.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-007.md -CheckChangedPaths
docker compose -f deploy/compose/docker-compose.yml config --quiet
./deploy/compose/scripts/verify.sh
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
