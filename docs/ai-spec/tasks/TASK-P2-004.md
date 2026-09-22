---
schemaVersion: harnessdg.task/v1
taskId: TASK-P2-004
status: READY
implementationAuthorized: true
phase: P2
baseCommit: 26c3985c5092d1c64def2c33e8db1e5d6ac4a256
stageGatePassed: true
stageGateEvidence: P1 asset catalog gap closure committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-UPL-004
  - REQ-UPL-002
acceptanceScenarios:
  - AC-P2-UPL-004
decisions:
  - DEC-010
scenarioEvidencePlan:
  - AC-P2-UPL-004|E4|Browser multipart refresh, pause/resume and failed-part retry without storing secrets
crossCuttingPlan:
  - AUTHN|Browser JWT in memory only for upload pages
  - AUTHZ|Upload UI actions gated by asset:upload permission from backend
  - DB_FILTER|N/A - frontend consumes authorized session APIs only
  - STATE|Upload UI reflects UploadSessionStatus and Worker job stages
  - IDEMPOTENCY|Complete retry uses server-side idempotency; UI does not duplicate complete
  - CONSISTENCY|Refresh reconciles completed parts from server not LocalStorage
  - ERRORS|Display UPLOAD_* and AUTH_* errors with retry for transient failures
  - AUDIT|N/A - frontend does not emit audit events
  - NOTIFICATION|N/A - inline progress only
  - TAXONOMY_I18N|zh/en strings for upload states, quotas and CLI fallback guidance
  - CONFIG|Display 20 GiB threshold and quota from API not hardcoded
  - OBSERVABILITY|Frontend error reporting excludes presigned URLs
  - SECRETS|No Token or presigned URL in LocalStorage, sessionStorage or analytics
allowedPaths:
  - frontend/src/features/upload
  - frontend/src/features/version
  - frontend/src/shared/i18n/en.json
  - frontend/src/shared/i18n/zh.json
  - docs/ai-spec/tasks/evidence/EVD-P2004-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P2004-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-004.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-004.md -CheckChangedPaths
  - cd frontend && pnpm lint
  - cd frontend && pnpm typecheck
  - cd frontend && pnpm test
  - cd frontend && pnpm build
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P2-004：浏览器 Multipart 暂停/刷新/失败 Part 恢复

> 状态：`READY`

## 1. 目标

实现 PAGE-UPL-001/002 上传前端：Part 级进度、刷新恢复、失败重试与 Worker 阶段展示。

## 2. 关联规格

```yaml
requirements: ['REQ-UPL-004', 'REQ-UPL-002']
acceptanceScenarios: ['AC-P2-UPL-004']
decisions: ['DEC-010']
```

## 3. 范围

### 允许修改

- UploadPage、Version 上传入口、i18n 与组件/E2E 测试。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P2-UPL-004` | Web user | Browser multipart refresh, pause/resume and failed-part retry without storing secrets | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- 依赖 TASK-P2-003 后端 Session API

## 7. 实施步骤

- [ ] 实现 UploadPage 多 Part 进度、刷新对账、失败 Part 重签与 complete 后轮询。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-004.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-004.md -CheckChangedPaths
cd frontend && pnpm lint
cd frontend && pnpm typecheck
cd frontend && pnpm test
cd frontend && pnpm build
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
