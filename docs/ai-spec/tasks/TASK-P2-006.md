---
schemaVersion: harnessdg.task/v1
taskId: TASK-P2-006
status: READY
implementationAuthorized: true
phase: P2
baseCommit: 26c3985c5092d1c64def2c33e8db1e5d6ac4a256
stageGatePassed: true
stageGateEvidence: P1 asset catalog gap closure committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-010
  - DEC-016
requirements:
  - REQ-DL-001
acceptanceScenarios:
  - AC-P2-DL-001
decisions:
  - DEC-010
  - DEC-016
scenarioEvidencePlan:
  - AC-P2-DL-001|E4|Download ticket issued by permission/state/sensitivity with audit and URL redaction
crossCuttingPlan:
  - AUTHN|JWT PrincipalContext required for download ticket request
  - AUTHZ|asset:download with scope, ACL, visibility and sensitivity checks
  - DB_FILTER|Download queries apply same authorization predicate as search/detail
  - STATE|Draft download only for maintainers; consumer limited to PUBLISHED/DEPRECATED
  - IDEMPOTENCY|Optional short dedupe for repeated ticket requests per policy
  - CONSISTENCY|Ticket references exact version revision and manifestDigest
  - ERRORS|ASSET_NOT_FOUND anti-enumeration, VERSION_STATE_NOT_ALLOWED, DVC_OBJECT_MISSING
  - AUDIT|100% download authorization audit; events queryable for DEC-016 P5 aggregation
  - NOTIFICATION|N/A - synchronous ticket response
  - TAXONOMY_I18N|N/A - download path list uses normalized artifact paths
  - CONFIG|Presign TTL default 15 minutes from configuration
  - OBSERVABILITY|Download authorization metrics without URL in labels
  - SECRETS|Response, logs and audit redact full presigned URLs
allowedPaths:
  - backend/src/main/java/com/aihub/transfer/application
  - backend/src/main/java/com/aihub/transfer/api
  - backend/src/main/java/com/aihub/transfer/domain
  - backend/src/main/java/com/aihub/audit
  - backend/src/test/java/com/aihub/transfer
  - contracts/openapi/aihub-v1.yaml
  - docs/ai-spec/tasks/evidence/EVD-P2006-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P2006-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-006.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-006.md -CheckChangedPaths
  - npx @redocly/cli lint contracts/openapi/aihub-v1.yaml
  - npx @redocly/cli diff contracts/openapi/aihub-v1.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P2-006：授权下载票据、过期与审计脱敏

> 状态：`READY`

## 1. 目标

实现 REQ-DL-001 下载票据签发：权限/状态/敏感度检查、短期 URL、审计可聚合与日志脱敏。

## 2. 关联规格

```yaml
requirements: ['REQ-DL-001']
acceptanceScenarios: ['AC-P2-DL-001']
decisions: ['DEC-010', 'DEC-016']
```

## 3. 范围

### 允许修改

- DownloadApplicationService、DownloadController、audit 集成与授权测试。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P2-DL-001` | Consumer | Download ticket issued by permission/state/sensitivity with audit and URL redaction | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- DEC-016 预留 audit_log 下载授权事件可聚合查询

## 7. 实施步骤

- [ ] 完善 download ticket API；PRESIGNED_URL 与 GIT_DVC 方法；审计与脱敏测试。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-006.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-006.md -CheckChangedPaths
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
