---
schemaVersion: harnessdg.task/v1
taskId: TASK-P4-010
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
  - REQ-MCP-005
acceptanceScenarios:
  - AC-DST-AIW-003
  - AC-DST-AIW-004
  - AC-DST-AIW-005
  - AC-DST-AIW-006
  - AC-P4-EXIT-001
decisions:
  - DEC-008
  - DEC-010
scenarioEvidencePlan:
  - AC-DST-AIW-003|E4|Write Agent creates Session CLI uploads and Worker materializes without MCP binary URL
  - AC-DST-AIW-004|E4|Create complete timeout replay concurrency and same-key conflict handled
  - AC-DST-AIW-005|E4|Quota illegal path disabled tag content security Worker failure stable errors
  - AC-DST-AIW-006|E4|Write Agent publish delete expand permission configure always denied before human gate
  - AC-P4-EXIT-001|E4|Full P4 Compose vertical exit JRN-P4-001 through JRN-P4-005
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
  - backend/src/main/java/com/aihub/transfer
  - deploy/compose/scripts/p4-journey
  - deploy/compose/fixtures/p4
  - docs/ai-spec/tasks/evidence/EVD-P4010-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P4010-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-010.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-010.md -CheckChangedPaths
  - cd backend && ./mvnw -o verify
  - docker compose -f deploy/compose/docker-compose.yml config --quiet
  - ./deploy/compose/scripts/verify.sh
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P4-010：AI 创建→上传→Worker→状态完整旅程

> 状态：`READY`

## 1. 目标

完成 JRN-P4-005：写 Agent 创建草稿→上传→Worker 物化→状态查询→人工审核，满足 AC-DST-AIW-* 与 P4 出口。

## 2. 关联规格

```yaml
requirements: ['REQ-MCP-005']
acceptanceScenarios: ['AC-DST-AIW-003', 'AC-DST-AIW-004', 'AC-DST-AIW-005', 'AC-DST-AIW-006', 'AC-P4-EXIT-001']
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
| `AC-DST-AIW-003` | Operator | Write Agent creates Session CLI uploads and Worker materializes without MCP binary URL | Execute | PASS | `E4` |
| `AC-DST-AIW-004` | Operator | Create complete timeout replay concurrency and same-key conflict handled | Execute | PASS | `E4` |
| `AC-DST-AIW-005` | Operator | Quota illegal path disabled tag content security Worker failure stable errors | Execute | PASS | `E4` |
| `AC-DST-AIW-006` | Operator | Write Agent publish delete expand permission configure always denied before human gate | Execute | PASS | `E4` |
| `AC-P4-EXIT-001` | Operator | Full P4 Compose vertical exit JRN-P4-001 through JRN-P4-005 | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/MCP/Event/Migration（如适用）

## 7. 实施步骤

- [ ] 实现写 Tool 端到端；p4-journey 脚本；AC-DST-AIW-003..006 与 AC-P4-EXIT-001 证据。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-010.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-010.md -CheckChangedPaths
cd backend && ./mvnw -o verify
docker compose -f deploy/compose/docker-compose.yml config --quiet
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
