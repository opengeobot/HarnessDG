---
schemaVersion: harnessdg.task/v1
taskId: TASK-P4-008
status: READY
implementationAuthorized: true
phase: P4
baseCommit: 59f40d28dab5a9fb5fdeb5cb29080219b10ee4a6
stageGatePassed: true
stageGateEvidence: P3 release governance gap closure committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-008
  - DEC-010
requirements:
  - REQ-DST-AI-001
acceptanceScenarios:
  - AC-DST-AI-001
  - AC-DST-AI-002
  - AC-DST-AI-003
  - AC-DST-AI-004
  - AC-DST-AI-005
decisions:
  - DEC-008
  - DEC-010
scenarioEvidencePlan:
  - AC-DST-AI-001|E4|Read-only Agent asset_search returns governed DATASET candidates with matchedFields
  - AC-DST-AI-002|E4|Agent does not guess version when multiple candidates or no published version
  - AC-DST-AI-003|E4|Agent selects exact version and trusted local pull without URL in conversation
  - AC-DST-AI-004|E4|Missing Tool Scope Permission or hidden Tool dual deny with audit
  - AC-DST-AI-005|E4|Download verify failure handle expiry and retryable errors handled correctly
crossCuttingPlan:
  - AUTHN|Platform-issued Bearer JWT establishes same PrincipalContext as REST
  - AUTHZ|mcp:invoke plus Scope, Permission, allowlist and resource policy enforced
  - DB_FILTER|Queries scoped by authorization predicate; no cross-org leakage
  - STATE|Version/upload/job state preconditions enforced before Tool call
  - IDEMPOTENCY|Write Tools require Idempotency-Key; read Tools are safe to retry
  - CONSISTENCY|Tool calls reuse Application Service; no direct Mapper/SDK access
  - ERRORS|MCP_TOOL_NOT_ALLOWED and business errors map to stable Error Catalog
  - AUDIT|Tool calls, denials and download authorization audited without secrets
  - NOTIFICATION|N/A - MCP responses return job/request IDs for client polling
  - TAXONOMY_I18N|Governed fields use dictionary itemCode; no free-form tags
  - CONFIG|mcp.writeTools.enabled and mcp.maxResultItems from configuration service
  - OBSERVABILITY|MCP requestId/traceparent/locale aligned with REST observability
  - SECRETS|No Authorization, presigned URL or credential in logs/responses
allowedPaths:
  - backend/src/main/java/com/aihub/mcp/application
  - backend/src/main/java/com/aihub/asset/application
  - backend/src/test/java/com/aihub/mcp
  - deploy/compose/scripts/p4-dst-ai
  - docs/ai-spec/tasks/evidence/EVD-P4008-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P4008-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-008.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-008.md -CheckChangedPaths
  - cd backend && ./mvnw -o verify
  - ./deploy/compose/scripts/verify.sh
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P4-008：AI 搜索分类 Facet 与 downloadHandle 数据通道

> 状态：`READY`

## 1. 目标

实现 REQ-DST-AI-001 AI 搜索、精确选择与 downloadHandle→可信 CLI 数据通道，满足 AC-DST-AI-*。

## 2. 关联规格

```yaml
requirements: ['REQ-DST-AI-001']
acceptanceScenarios: ['AC-DST-AI-001', 'AC-DST-AI-002', 'AC-DST-AI-003', 'AC-DST-AI-004', 'AC-DST-AI-005']
decisions: ['DEC-008', 'DEC-010']
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
| `AC-DST-AI-001` | Operator | Read-only Agent asset_search returns governed DATASET candidates with matchedFields | Execute | PASS | `E4` |
| `AC-DST-AI-002` | Operator | Agent does not guess version when multiple candidates or no published version | Execute | PASS | `E4` |
| `AC-DST-AI-003` | Operator | Agent selects exact version and trusted local pull without URL in conversation | Execute | PASS | `E4` |
| `AC-DST-AI-004` | Operator | Missing Tool Scope Permission or hidden Tool dual deny with audit | Execute | PASS | `E4` |
| `AC-DST-AI-005` | Operator | Download verify failure handle expiry and retryable errors handled correctly | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/MCP/Event/Migration（如适用）

## 7. 实施步骤

- [ ] 扩展 asset_search DATASET facet/matchedFields；downloadHandle 兑换由 aih CLI 完成；AC-DST-AI-* 测试。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-008.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-008.md -CheckChangedPaths
cd backend && ./mvnw -o verify
./deploy/compose/scripts/verify.sh
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
