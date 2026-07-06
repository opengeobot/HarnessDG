---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-000B
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-UI-001
acceptanceScenarios: []
decisions:
  - DEC-013
scenarioEvidencePlan: []
crossCuttingPlan:
  - AUTHN|N/A - documentation and specification only
  - AUTHZ|N/A - documentation only
  - DB_FILTER|N/A - no database
  - STATE|N/A - no state machine
  - IDEMPOTENCY|N/A - no persistent side effect
  - CONSISTENCY|N/A - no transaction
  - ERRORS|N/A - documentation only
  - AUDIT|N/A - no business audit
  - NOTIFICATION|N/A - no notification
  - TAXONOMY_I18N|Document i18n prefix conventions for admin pages
  - CONFIG|N/A - no deployment config
  - OBSERVABILITY|N/A - no runtime service
  - SECRETS|N/A - no secret handling
allowedPaths:
  - docs/ai-spec/04-ui
  - docs/ai-spec/01-requirements
  - frontend/src/shared
  - frontend/src/features
preExistingDirtyPaths: []
forbiddenPaths:
  - backend
  - contracts
  - deploy
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E1
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR000B-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-000B.md
approvedBy: null
approvedAt: null
---

# TASK-P0BR-000B：P0-B 管理页面 MIPS 文档 + 全局前端规范

> 状态：`READY`

## 1. 目标

为 7 个 P0-B 管理页面（用户、Agent、组织、Team、角色/权限、字典、标签、配置、审计、任务、通知、依赖）编写交互元素+API+权限+i18n 前缀的 MIPS（Minimum Implementable Page Specification）文档，并建立全局前端编码规范。

## 2. 关联规格

```yaml
requirements: [REQ-UI-001]
acceptanceScenarios: []
decisions: [DEC-013]
adrs: [ADR-0002]
```

## 3. 范围

### 允许修改

- `docs/ai-spec/04-ui/`：页面规格文档
- `frontend/src/shared/`：全局前端规范（API client、permission provider、i18n setup）

### 明确不在范围

- 后端产品代码、契约、Migration
- 实际页面组件实现

### 禁止变化

- 不改变任何已有页面组件

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| — | AI IDE | 读取 MIPS 文档 | 实施管理页面 | 知道每个页面的 API、权限、i18n key 和交互元素 | `E1` |

## 5. 横切要求

横切要求仅在文档层面描述约定，不涉及代码实现。

## 6. 契约与数据先行

无。

## 7. 实施步骤

- [ ] 编写 7 个管理页面 MIPS 文档
- [ ] 建立全局前端规范（API client、Permission Provider、i18n 约定）
- [ ] 文档与现有 PAGE-ADM-* 规格一致

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
# 预期：PASS
```

## 9. 停止条件

无。

## 10. 完成报告

```text
Completed:
Changed files:
Validation run and results:
```
