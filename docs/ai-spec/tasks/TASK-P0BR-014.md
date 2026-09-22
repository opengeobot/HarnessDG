---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-014
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-AGT-001
acceptanceScenarios:
  - AC-P0B-AGT-001
  - AC-P0B-AGT-002
  - AC-P0B-AGT-003
  - AC-P0B-AGT-004
  - AC-P0B-AGT-005
decisions:
  - DEC-001
scenarioEvidencePlan:
  - AC-P0B-AGT-001|E4|Agent registered, credential shown once, hash in DB, minimal scope/tool
  - AC-P0B-AGT-002|E4|Credential exchange: correct gets scoped JWT, wrong unified reject
  - AC-P0B-AGT-003|E4|Covered by TASK-P0BR-013
  - AC-P0B-AGT-004|E4|High-risk scope denied by default or requires explicit approval
  - AC-P0B-AGT-005|E4|Unknown MCP tool name rejected by controlled Tool Catalog
crossCuttingPlan:
  - AUTHN|Agent credential exchange issues scoped JWT
  - AUTHZ|agent:register, agent:authorize permissions; scope delegation
  - DB_FILTER|N/A
  - STATE|AgentStatus: ACTIVE->DISABLED
  - IDEMPOTENCY|Agent register idempotent by displayName+agentType
  - CONSISTENCY|Principal + Agent + credential atomic
  - ERRORS|AGENT_NOT_FOUND, AUTH_INVALID_CREDENTIALS, COMMON_INVALID_ARGUMENT
  - AUDIT|AGENT_REGISTERED, CLIENT_TOKEN_ISSUED/REJECTED, AGENT_TOOL_ALLOWLIST_UPDATED
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|N/A
  - CONFIG|N/A
  - OBSERVABILITY|Agent metrics
  - SECRETS|Credential shown once, hash in DB, never in logs
allowedPaths:
  - backend/src/main/java/com/aihub/identity
  - backend/src/test/java/com/aihub/identity
  - contracts/mcp/tools.yaml
  - deploy/compose
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V3__identity.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR014-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-014.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-014：Agent 注册/凭据/受控 Scope/Tool Catalog E4

> 状态：`READY`

## 1. 目标

验证 Agent 注册（凭据仅显示一次、DB 存哈希）、凭据交换签发受限 JWT、高风险 Scope 默认拒绝、未知 MCP Tool 被 Tool Catalog 拒绝。覆盖 AC-P0B-AGT-001~005。

## 2. 关联规格

```yaml
requirements: [REQ-AGT-001]
acceptanceScenarios: [AC-P0B-AGT-001, AC-P0B-AGT-002, AC-P0B-AGT-003, AC-P0B-AGT-004, AC-P0B-AGT-005]
decisions: [DEC-001]
```

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-AGT-001` | 管理员 | 授权 | 注册 Agent | 凭据显示一次，DB 存哈希，最小 Scope/Tool | `E4` |
| `AC-P0B-AGT-002` | Agent | 注册完成 | 凭据交换 | 正确凭据得受限 JWT，错误统一拒绝 | `E4` |
| `AC-P0B-AGT-004` | 管理员 | — | 授予高风险 Scope | 默认拒绝或人工审批 | `E4` |
| `AC-P0B-AGT-005` | 管理员 | — | 提交未知 Tool 名 | Tool Catalog 校验拒绝 | `E3` |
