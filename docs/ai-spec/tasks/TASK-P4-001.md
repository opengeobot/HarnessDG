---
schemaVersion: harnessdg.task/v1
taskId: TASK-P4-001
status: READY
implementationAuthorized: true
phase: P4
baseCommit: 59f40d28dab5a9fb5fdeb5cb29080219b10ee4a6
stageGatePassed: true
stageGateEvidence: P3 release governance gap closure committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-MCP-002
acceptanceScenarios:
  - AC-P4-SCH-001
decisions:
  - DEC-010
scenarioEvidencePlan:
  - AC-P4-SCH-001|E3|tools.yaml and McpToolCatalog sync with schema, write flags and error mapping
crossCuttingPlan:
  - AUTHN|N/A - contract and catalog documentation only
  - AUTHZ|Document requiredScopes/requiredPermissions per Tool in tools.yaml
  - DB_FILTER|N/A - no database change in contract task
  - STATE|Document Tool read/write and enabledByDefault invariants
  - IDEMPOTENCY|Document write Tool idempotency requirements in schema
  - CONSISTENCY|N/A - contract-only task
  - ERRORS|Add MCP_TOOL_NOT_ALLOWED and pagination limit errors to error-catalog
  - AUDIT|Document AGENT_ACCESS_DENIED audit events for denied Tool calls
  - NOTIFICATION|N/A - contract metadata only
  - TAXONOMY_I18N|N/A - Tool schemas use stable codes only
  - CONFIG|Document mcp.writeTools.enabled and mcp.maxResultItems flags
  - OBSERVABILITY|N/A - no runtime service in contract task
  - SECRETS|N/A - no secret handling in contract task
allowedPaths:
  - contracts/mcp/tools.yaml
  - contracts/openapi/aihub-agent-v1.yaml
  - docs/ai-spec/02-domain/error-catalog.md
  - backend/src/main/java/com/aihub/mcp/application/McpToolCatalog.java
  - backend/src/test/java/com/aihub/mcp/application/McpToolCatalogTest.java
  - docs/ai-spec/tasks/evidence/EVD-P4001-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E3
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P4001-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-001.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-001.md -CheckChangedPaths
  - npx @redocly/cli lint contracts/openapi/aihub-agent-v1.yaml
  - npx @redocly/cli diff contracts/openapi/aihub-agent-v1.yaml contracts/openapi/aihub-agent-v1.yaml --fail-on-breaking
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P4-001：MCP Tool/Resource Schema 与错误分页契约

> 状态：`READY`

## 1. 目标

将 REQ-MCP-002 固化为 tools.yaml、Agent OpenAPI 片段与 McpToolCatalog 同步契约，修复阶段/write 标记。

## 2. 关联规格

```yaml
requirements: ['REQ-MCP-002']
acceptanceScenarios: ['AC-P4-SCH-001']
decisions: ['DEC-010']
```

## 3. 范围

### 允许修改

- 见 Front Matter `allowedPaths`。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P4-SCH-001` | Operator | tools.yaml and McpToolCatalog sync with schema, write flags and error mapping | Execute | PASS | `E3` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/MCP/Event/Migration（如适用）

## 7. 实施步骤

- [ ] 更新 tools.yaml write 标记与 Schema；同步 McpToolCatalog；补充错误映射文档。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-001.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-001.md -CheckChangedPaths
npx @redocly/cli lint contracts/openapi/aihub-agent-v1.yaml
cd backend && ./mvnw -o -Dtest=McpToolCatalogTest test
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
