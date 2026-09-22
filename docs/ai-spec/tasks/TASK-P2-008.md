---
schemaVersion: harnessdg.task/v1
taskId: TASK-P2-008
status: READY
implementationAuthorized: true
phase: P2
baseCommit: 26c3985c5092d1c64def2c33e8db1e5d6ac4a256
stageGatePassed: true
stageGateEvidence: P1 asset catalog gap closure committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-008
  - DEC-010
  - DEC-015
requirements:
  - REQ-PRE-001
acceptanceScenarios:
  - AC-DST-PRE-001
  - AC-DST-PRE-002
  - AC-DST-PRE-003
  - AC-DST-PRE-004
decisions:
  - DEC-008
  - DEC-010
  - DEC-015
scenarioEvidencePlan:
  - AC-DST-PRE-001|E4|Preview Job generates schema, split, redacted samples and stats for supported formats
  - AC-DST-PRE-002|E4|CSV/JSONL/Parquet limits and unsupported format return PREVIEW_UNSUPPORTED_FORMAT
  - AC-DST-PRE-003|E4|Unauthorized or revoked access rejects preview in real time
  - AC-DST-PRE-004|E4|PII/secret canary, zip bomb and malicious path fail safely without leakage
crossCuttingPlan:
  - AUTHN|Authenticated principals for preview request and page
  - AUTHZ|Preview respects asset:read, sensitivity and exact version binding
  - DB_FILTER|Preview queries scoped by authorization predicate
  - STATE|Preview Job lifecycle PENDING->COMPLETED/FAILED per job state machine
  - IDEMPOTENCY|Preview Job idempotent by versionId and format scope
  - CONSISTENCY|Preview artifact stored in isolated bucket with source digest metadata
  - ERRORS|PREVIEW_UNSUPPORTED_FORMAT, PREVIEW_LIMIT_EXCEEDED, PREVIEW_DEPENDENCY_UNAVAILABLE
  - AUDIT|Preview generation and deny events audited without sample content
  - NOTIFICATION|N/A - client polls preview job status
  - TAXONOMY_I18N|Subset/Split labels from dictionary itemCode with zh/en display
  - CONFIG|Preview row/size limits from configuration service
  - OBSERVABILITY|Preview job duration and failure metrics
  - SECRETS|Preview output redacts PII/secrets; bucket not public
allowedPaths:
  - backend/src/main/java/com/aihub/version/application
  - backend/src/main/java/com/aihub/version/infrastructure
  - backend/src/main/java/com/aihub/version/api
  - backend/src/test/java/com/aihub/version
  - frontend/src/features/assets/PreviewPanel.tsx
  - frontend/src/features/version
  - docs/ai-spec/tasks/evidence/EVD-P2008-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P2008-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-008.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-008.md -CheckChangedPaths
  - cd frontend && pnpm lint
  - cd frontend && pnpm typecheck
  - cd frontend && pnpm test
  - cd frontend && pnpm build
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P2-008：CSV/JSONL/Parquet 最小安全预览

> 状态：`READY`

## 1. 目标

实现 REQ-PRE-001 最小安全预览 Job、API 与页面；Subset 下拉、Split Tab 与统计卡片（DEC-015）。

## 2. 关联规格

```yaml
requirements: ['REQ-PRE-001']
acceptanceScenarios: ['AC-DST-PRE-001', 'AC-DST-PRE-002', 'AC-DST-PRE-003', 'AC-DST-PRE-004']
decisions: ['DEC-008', 'DEC-010', 'DEC-015']
```

## 3. 范围

### 允许修改

- PreviewJobHandler、Preview API、PreviewPanel 与 Subset/Split/Stats UI。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-DST-PRE-001` | Consumer | Preview Job generates schema, split, redacted samples and stats for supported formats | Execute | PASS | `E4` |
| `AC-DST-PRE-002` | Consumer | CSV/JSONL/Parquet limits and unsupported format return PREVIEW_UNSUPPORTED_FORMAT | Execute | PASS | `E4` |
| `AC-DST-PRE-003` | Unauthorized | Unauthorized or revoked access rejects preview in real time | Execute | PASS | `E4` |
| `AC-DST-PRE-004` | Security | PII/secret canary, zip bomb and malicious path fail safely without leakage | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- AC 定义见 dataset-agent-exit-catalog.md

## 7. 实施步骤

- [ ] Preview Job/API；PreviewPanel Subset/Split/Stats；AC-DST-PRE-* 测试。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-008.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-008.md -CheckChangedPaths
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
