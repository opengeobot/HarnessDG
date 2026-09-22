---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-012
status: READY
implementationAuthorized: true
phase: P1
baseCommit: 38fd44d2dcbc1a611cfdfd923337e5599c3b3feb
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED; DEC-010 continuous implementation; P0BR-046 gate lifted
stageGateEvidenceRefs:
  - DEC-008
  - DEC-010
requirements:
  - REQ-DST-DISC-001
acceptanceScenarios:
  - AC-DST-DISC-001
  - AC-DST-DISC-002
  - AC-DST-DISC-003
  - AC-DST-DISC-004
  - AC-DST-DISC-005
decisions:
  - DEC-008
  - DEC-010
scenarioEvidencePlan:
  - AC-DST-DISC-001|E4|Thread create/reply with mention produces notification and cursor read
  - AC-DST-DISC-002|E4|Unauthorized discuss/read rejected with anti-enumeration
  - AC-DST-DISC-003|E4|Idempotent reply replay and rowVersion conflict produce single comment
  - AC-DST-DISC-004|E4|Author revise/withdraw and moderator hide/restore/lock with tombstone
  - AC-DST-DISC-005|E4|Untrusted HTML/script content safely rendered without execution
crossCuttingPlan:
  - AUTHN|JWT required for discussion
  - AUTHZ|asset:discuss and asset:read permissions enforced
  - DB_FILTER|Thread list scoped to authorized assets
  - STATE|CommentStatus lifecycle with revision history
  - IDEMPOTENCY|Idempotency-Key on reply/create
  - CONSISTENCY|Notification/Outbox atomic with comment write
  - ERRORS|AUTH_PERMISSION_DENIED, CONCURRENT_MODIFICATION
  - AUDIT|COMMENT_CREATED, COMMENT_REVISED, COMMENT_MODERATED events
  - NOTIFICATION|Mention triggers notification via Outbox
  - TAXONOMY_I18N|Moderation labels use i18nKey
  - CONFIG|N/A - no config change
  - OBSERVABILITY|Discussion write traced
  - SECRETS|N/A - no secrets in comment content storage
allowedPaths:
  - backend/src/main/java/com/aihub/asset/discussion
  - backend/src/test/java/com/aihub/asset/discussion
  - frontend/src/features/assets/DiscussionPanel.tsx
  - frontend/src/features/assets/DiscussionPanel.test.tsx
  - docs/ai-spec/tasks/evidence/EVD-P1012-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1012-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-012.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-012.md -CheckChangedPaths
  - cd frontend && pnpm lint
  - cd frontend && pnpm typecheck
  - cd frontend && pnpm test
  - cd frontend && pnpm build
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P1-012：Asset Discussion 纵向闭环

> 状态：`READY`

## 1. 目标

实现 Asset Discussion/Comment/Revision/Moderation/Notification 纵向闭环。

## 2. 关联规格

```yaml
requirements: ['REQ-DST-DISC-001']
acceptanceScenarios: ['AC-DST-DISC-001', 'AC-DST-DISC-002', 'AC-DST-DISC-003', 'AC-DST-DISC-004', 'AC-DST-DISC-005']
decisions: ['DEC-008', 'DEC-010']
```

## 3. 范围

### 允许修改

- discussion 模块后端与 DiscussionPanel 前端。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-DST-DISC-001` | Maintainer | Thread create/reply with mention produces notification and cursor read | Execute | PASS | `E4` |
| `AC-DST-DISC-002` | Maintainer | Unauthorized discuss/read rejected with anti-enumeration | Execute | PASS | `E4` |
| `AC-DST-DISC-003` | Maintainer | Idempotent reply replay and rowVersion conflict produce single comment | Execute | PASS | `E4` |
| `AC-DST-DISC-004` | Maintainer | Author revise/withdraw and moderator hide/restore/lock with tombstone | Execute | PASS | `E4` |
| `AC-DST-DISC-005` | Maintainer | Untrusted HTML/script content safely rendered without execution | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- V29+ 为 P1-002 回填下限（V27 已用于 P3 frozen commit）；其他任务按需使用 V29+ 新 Migration

## 7. 实施步骤

- [ ] Thread/Comment CRUD；修订/撤回/Moderation；通知 Outbox；安全渲染。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-012.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-012.md -CheckChangedPaths
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
