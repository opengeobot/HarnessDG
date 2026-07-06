---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-016
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-ORG-001
  - REQ-ORG-002
  - REQ-AUTH-003
acceptanceScenarios:
  - AC-P0B-AUTH-001
  - AC-P0B-AUTH-002
  - AC-P0B-AUTH-004
  - AC-P0B-AUTH-008
  - AC-P0B-AUTH-009
decisions:
  - DEC-004
  - DEC-011
scenarioEvidencePlan:
  - AC-P0B-AUTH-001|E4|Platform admin creates org, org admin creates project/team
  - AC-P0B-AUTH-002|E4|User in two orgs with different roles, no cross-org leakage
  - AC-P0B-AUTH-004|E4|Authorized user succeeds, proving not all-denied
  - AC-P0B-AUTH-008|E4|Disable org/project/role removes access in real-time
  - AC-P0B-AUTH-009|E4|User leaves team, permissions revoked, asset ownership transferred
crossCuttingPlan:
  - AUTHN|PrincipalContext carries currentOrganizationId
  - AUTHZ|organization:manage, project:view/manage permissions
  - DB_FILTER|Organization/project queries scoped by membership
  - STATE|OrganizationStatus/ProjectStatus/TeamStatus lifecycle
  - IDEMPOTENCY|Create org/project/team idempotent by code
  - CONSISTENCY|N/A
  - ERRORS|ORGANIZATION_NOT_FOUND/ALREADY_EXISTS, PROJECT_*, MEMBER_*
  - AUDIT|ORGANIZATION_CREATED, membership changes, PROJECT_CREATED
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|N/A
  - CONFIG|N/A
  - OBSERVABILITY|N/A
  - SECRETS|N/A
allowedPaths:
  - backend/src/main/java/com/aihub/organization
  - backend/src/test/java/com/aihub/organization
  - deploy/compose
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V5__organization.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR016-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-016.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-016：组织/项目/Team 成员和角色绑定成功/越权 E4

> 状态：`READY`

## 1. 目标

验证组织/项目/Team 创建、成员管理、跨组织隔离、禁用实时失效和 Team 离开后资产责任转移的完整 E4 旅程。

## 2. 关联规格

```yaml
requirements: [REQ-ORG-001, REQ-ORG-002, REQ-AUTH-003]
acceptanceScenarios: [AC-P0B-AUTH-001, AC-P0B-AUTH-002, AC-P0B-AUTH-004, AC-P0B-AUTH-008, AC-P0B-AUTH-009]
decisions: [DEC-004, DEC-011]
```

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-AUTH-001` | 管理员 | — | 创建组织/项目/Team | 作用域、唯一约束、审计正确 | `E4` |
| `AC-P0B-AUTH-002` | 用户 | 两组织成员 | 切换组织访问 | 每个请求按目标组织计算权限 | `E4` |
| `AC-P0B-AUTH-004` | 有权限用户 | — | 执行操作 | 成功路径通过 | `E4` |
| `AC-P0B-AUTH-008` | 管理员 | — | 禁用组织 | 后续访问实时失效 | `E4` |
| `AC-P0B-AUTH-009` | 用户 | Team 成员 | 离开 Team | 权限撤销，资产责任转移 | `E4` |
