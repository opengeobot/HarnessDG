---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-028
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-WHK-001
acceptanceScenarios:
  - AC-P0B-NOT-003
  - AC-P0B-NOT-004
  - AC-P0B-NOT-005
decisions:
  - DEC-001
scenarioEvidencePlan: []
crossCuttingPlan:
  - AUTHN|See task description
  - AUTHZ|See task description
  - DB_FILTER|N/A
  - STATE|See task description
  - IDEMPOTENCY|See task description
  - CONSISTENCY|N/A
  - ERRORS|See task description
  - AUDIT|See task description
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|N/A
  - CONFIG|N/A
  - OBSERVABILITY|N/A
  - SECRETS|See task description
allowedPaths:
  - backend/src/main/java/com/aihub
  - backend/src/test/java/com/aihub
  - deploy/compose
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR028-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-028.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-028：签名 Webhook SSRF、重试、验签、去重 E4

> 状态：`READY`

## 1. 目标

验证 Webhook 5xx 后重试恢复 2xx 最终 DELIVERED；SSRF 防护拒绝 loopback/私网/保留地址；接收方可验签、时间戳校验和 Delivery ID 去重。

## 2. 关联规格

```yaml
requirements: [REQ-WHK-001]
acceptanceScenarios: [AC-P0B-NOT-003,AC-P0B-NOT-004,AC-P0B-NOT-005]
decisions: [DEC-001]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| AC-P0B-NOT-003 | Webhook 目标 5xx | 恢复 2xx | 核心事务不回滚，Delivery 最终 DELIVERED | E4 |
| AC-P0B-NOT-004 | 目标 loopback/私网 | 创建/投递 | WEBHOOK_TARGET_FORBIDDEN | E4 |
| AC-P0B-NOT-005 | 接收方 | 验签 | 合法可验签，篡改失败，重复 ID 去重 | E4 |
