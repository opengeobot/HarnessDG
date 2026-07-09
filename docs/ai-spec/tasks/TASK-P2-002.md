---
schemaVersion: harnessdg.task/v1
taskId: TASK-P2-002
status: READY
implementationAuthorized: true
phase: P2
baseCommit: 26c3985c5092d1c64def2c33e8db1e5d6ac4a256
stageGatePassed: true
stageGateEvidence: P1 asset catalog gap closure committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-VER-001
  - REQ-DVC-001
acceptanceScenarios:
  - AC-P2-VER-001
  - AC-P2-DVC-001
decisions:
  - DEC-010
scenarioEvidencePlan:
  - AC-P2-VER-001|E4|Authorized maintainer creates DRAFT Version with idempotent replay and conflict handling
  - AC-P2-DVC-001|E4|Git+DVC push/pull roundtrip with short-lived credentials and SHA-256 verification
crossCuttingPlan:
  - AUTHN|JWT PrincipalContext required for draft version and DVC credential endpoints
  - AUTHZ|asset:update for draft create; asset:upload for DVC remote configuration
  - DB_FILTER|Version queries scoped by asset authorization predicate
  - STATE|VersionStatus DRAFT lifecycle enforced on create
  - IDEMPOTENCY|Principal-scoped idempotency for draft version create
  - CONSISTENCY|DVC remote config aligned with Gitea repo and MinIO bucket isolation
  - ERRORS|ASSET_VERSION_CONFLICT, AUTH_PERMISSION_DENIED, DVC_OBJECT_MISSING
  - AUDIT|VERSION_DRAFT_CREATED and DVC credential issuance audited without secrets
  - NOTIFICATION|N/A - synchronous create and credential endpoints
  - TAXONOMY_I18N|N/A - version string normalization only
  - CONFIG|MinIO bucket and DVC remote from secure Compose/config profiles
  - OBSERVABILITY|Trace spans for version create and DVC credential issuance
  - SECRETS|Short-lived S3 credentials only; no permanent keys in Git or logs
allowedPaths:
  - backend/src/main/java/com/aihub/version/application
  - backend/src/main/java/com/aihub/version/domain
  - backend/src/main/java/com/aihub/version/api
  - backend/src/main/java/com/aihub/integration/minio
  - backend/src/main/java/com/aihub/integration/gitea
  - backend/src/test/java/com/aihub/version
  - deploy/compose/fixtures/p2
  - docs/ai-spec/tasks/evidence/EVD-P2002-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P2002-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-002.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-002.md -CheckChangedPaths
  - docker compose -f deploy/compose/docker-compose.yml config --quiet
  - ./deploy/compose/scripts/verify.sh
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P2-002：DVC/MinIO 配置与草稿版本、CLI 往返

> 状态：`READY`

## 1. 目标

实现 DRAFT Version 创建与 DVC/MinIO 最小凭据配置，完成 JRN-P2-001 CLI 往返 E4 证据。

## 2. 关联规格

```yaml
requirements: ['REQ-VER-001', 'REQ-DVC-001']
acceptanceScenarios: ['AC-P2-VER-001', 'AC-P2-DVC-001']
decisions: ['DEC-010']
```

## 3. 范围

### 允许修改

- VersionApplicationService draft create、DVC remote 配置端口、MinIO 集成与 P2 fixtures。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P2-VER-001` | Maintainer | Authorized maintainer creates DRAFT Version with idempotent replay and conflict handling | Execute | PASS | `E4` |
| `AC-P2-DVC-001` | Developer | Git+DVC push/pull roundtrip with short-lived credentials and SHA-256 verification | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- V16+ 为既有 version/transfer 下限；其他任务按需使用 V27+ 新 Migration

## 7. 实施步骤

- [ ] 完善 createDraftVersion 幂等与冲突；DVC remote 与短期凭据签发；Compose fixture 与测试。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-002.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-002.md -CheckChangedPaths
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
