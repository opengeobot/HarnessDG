---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-026
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-LOG-001
acceptanceScenarios:
  - AC-P0B-AUD-002
  - AC-P0B-AUD-004
  - AC-P0B-AUD-005
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR026-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-026.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-026：HTTP/MCP/Worker/URL/Payload 全边界 Secret 脱敏 E4

> 状态：`READY`

## 1. 目标

在 Header/Cookie/Body/URL/任务 Payload 植入测试 Secret，扫描所有容器日志和审计表，原文出现次数为 0；验证 request/trace 上下文跨 DB/依赖/后台任务关联。

## 2. 关联规格

```yaml
requirements: [REQ-LOG-001]
acceptanceScenarios: [AC-P0B-AUD-002,AC-P0B-AUD-004,AC-P0B-AUD-005]
decisions: [DEC-001]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| AC-P0B-AUD-002 | 植入 Secret 标记 | 扫描日志/审计 | 原文零出现 | E4 |
| AC-P0B-AUD-004 | 单请求触发多组件 | — | requestId/traceId/principalId 可关联 | E4 |
| AC-P0B-AUD-005 | 超大/敏感请求 | — | 日志正文受大小和字段规则限制 | E4 |
