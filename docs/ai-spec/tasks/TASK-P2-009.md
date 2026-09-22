---
schemaVersion: harnessdg.task/v1
taskId: TASK-P2-009
status: READY
implementationAuthorized: true
phase: P2
baseCommit: 26c3985c5092d1c64def2c33e8db1e5d6ac4a256
stageGatePassed: true
stageGateEvidence: P1 asset catalog gap closure committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-008
  - DEC-010
requirements:
  - REQ-CLI-001
  - REQ-DST-CLI-001
acceptanceScenarios:
  - AC-DST-CLI-001
  - AC-DST-CLI-002
  - AC-DST-CLI-003
  - AC-DST-CLI-004
  - AC-DST-CLI-005
  - AC-DST-CLI-006
decisions:
  - DEC-008
  - DEC-010
scenarioEvidencePlan:
  - AC-DST-CLI-001|E4|aih dataset search --output json matches REST authorization and fields
  - AC-DST-CLI-002|E4|pull exact version to empty dir with verify exits 0 and SHA-256 match
  - AC-DST-CLI-003|E4|Interrupted download resumes with include/exclude without full re-download
  - AC-DST-CLI-004|E4|Expired credential or revoked permission returns stable exit code without leakage
  - AC-DST-CLI-005|E4|create and push small multipart fixture tracks session and worker status
  - AC-DST-CLI-006|E4|create/push timeout replay and same-key conflict behave deterministically
crossCuttingPlan:
  - AUTHN|CLI reads JWT from Secret Store or protected env reference only
  - AUTHZ|CLI reuses REST authorization; no local permission bypass
  - DB_FILTER|N/A - CLI is REST adapter
  - STATE|CLI status command reflects upload session and job states from API
  - IDEMPOTENCY|CLI forwards Idempotency-Key headers for create/push/complete
  - CONSISTENCY|CLI and REST return equivalent business results for same Principal
  - ERRORS|Stable exit codes mapped from API error codes per CLI contract
  - AUDIT|CLI operations produce same audit trail as equivalent REST calls
  - NOTIFICATION|N/A - CLI polls status only
  - TAXONOMY_I18N|CLI JSON output uses stable codes not locale labels
  - CONFIG|CLI base URL from config file or env not hardcoded secrets
  - OBSERVABILITY|CLI verbose mode excludes tokens and presigned URLs
  - SECRETS|No Token/URL in argv, stdout, stderr or shell history files
allowedPaths:
  - scripts/aih
  - backend/src/test/java/com/aihub/cli
  - docs/ai-spec/tasks/evidence/EVD-P2009-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P2009-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-009.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-009.md -CheckChangedPaths
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P2-009：`aih dataset` 薄 CLI 与稳定退出码

> 状态：`READY`

## 1. 目标

实现 REQ-CLI-001 / REQ-DST-CLI-001：`aih dataset search/inspect/pull/create/push/status` 薄 CLI 与 AC-DST-CLI-* E4。

## 2. 关联规格

```yaml
requirements: ['REQ-CLI-001', 'REQ-DST-CLI-001']
acceptanceScenarios: ['AC-DST-CLI-001', 'AC-DST-CLI-002', 'AC-DST-CLI-003', 'AC-DST-CLI-004', 'AC-DST-CLI-005', 'AC-DST-CLI-006']
decisions: ['DEC-008', 'DEC-010']
```

## 3. 范围

### 允许修改

- scripts/aih CLI、CLI 集成测试、P2 fixtures。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-DST-CLI-001` | Developer | aih dataset search --output json matches REST authorization and fields | Execute | PASS | `E4` |
| `AC-DST-CLI-002` | Developer | pull exact version to empty dir with verify exits 0 and SHA-256 match | Execute | PASS | `E4` |
| `AC-DST-CLI-003` | Developer | Interrupted download resumes with include/exclude without full re-download | Execute | PASS | `E4` |
| `AC-DST-CLI-004` | Unauthorized | Expired credential or revoked permission returns stable exit code without leakage | Execute | PASS | `E4` |
| `AC-DST-CLI-005` | Developer | create and push small multipart fixture tracks session and worker status | Execute | PASS | `E4` |
| `AC-DST-CLI-006` | Developer | create/push timeout replay and same-key conflict behave deterministically | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- CLI 是 REST/数据面 Adapter，不复制业务规则
- AC 定义见 dataset-agent-exit-catalog.md

## 7. 实施步骤

- [ ] 实现 dataset 子命令；稳定退出码；Secret Store 认证；AC-DST-CLI-* 测试。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-009.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-009.md -CheckChangedPaths
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
