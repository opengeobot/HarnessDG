---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-029
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-OBS-001
acceptanceScenarios:
  - AC-P0B-OBS-001
  - AC-P0B-OBS-002
  - AC-P0B-OBS-003
  - AC-P0B-OBS-004
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR029-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-029.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-029：REST->Job 完整 Trace、Prometheus、健康和诊断 E4

> 状态：`READY`

## 1. 目标

验证 REST 请求到 Job 全链路 Trace 贯通；Prometheus 抓取 Target UP 且指标覆盖设计清单；liveness/readiness 分层；诊断端点权限控制。

## 2. 关联规格

```yaml
requirements: [REQ-OBS-001]
acceptanceScenarios: [AC-P0B-OBS-001,AC-P0B-OBS-002,AC-P0B-OBS-003,AC-P0B-OBS-004]
decisions: [DEC-001]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| AC-P0B-OBS-001 | REST 请求创建后台任务 | — | Nginx->REST->DB->Job 全链关联，Worker Span Link | E4 |
| AC-P0B-OBS-002 | — | Prometheus 抓取 | Target UP，API/MCP/任务/依赖/JVM 指标存在 | E4 |
| AC-P0B-OBS-003 | 停 PostgreSQL | — | readiness DOWN 但 liveness UP | E4 |
| AC-P0B-OBS-004 | 无权/有权 | 访问诊断 | 无权拒绝；有权无凭据无拓扑 | E4 |
