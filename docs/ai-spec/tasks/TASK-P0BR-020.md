---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-020
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-TAX-001
  - REQ-TAG-001
acceptanceScenarios:
  - AC-P0B-TAX-001
  - AC-P0B-TAX-002
  - AC-P0B-TAX-003
  - AC-P0B-TAX-004
  - AC-P0B-TAX-005
  - AC-P0B-TAX-006
decisions:
  - DEC-001
scenarioEvidencePlan:
  - AC-P0B-TAX-001|E4|Admin create/update dict item: itemCode stable, version increment, i18n zh/en
  - AC-P0B-TAX-002|E4|Disable dict item referenced by asset: new write rejected, history locale display
  - AC-P0B-TAX-003|E4|Platform and two-org same-code tags: unique per scope
  - AC-P0B-TAX-004|E4|Free-form tag/unknown tagId: TAG_VALUE_INVALID rejected
  - AC-P0B-TAX-005|E4|Disable linked tag: history preserved, new link rejected
  - AC-P0B-TAX-006|E4|zh-CN/en-US switch: menus, buttons, dicts, errors, notifications localized
crossCuttingPlan:
  - AUTHN|PrincipalContext required
  - AUTHZ|dictionary:read/manage, tag:read/manage
  - DB_FILTER|N/A
  - STATE|DictItemStatus/TagStatus: ACTIVE<->DISABLED
  - IDEMPOTENCY|Write operations use expectedVersion
  - CONSISTENCY|N/A
  - ERRORS|DICTIONARY_ITEM_NOT_FOUND/VALUE_INVALID, TAG_NOT_FOUND/VALUE_INVALID
  - AUDIT|DICTIONARY_ITEM_CREATED/UPDATED/ENABLED/DISABLED, TAG_CREATED/UPDATED/ENABLED/DISABLED
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|Core: every dict item has zh-CN/en-US i18nKey
  - CONFIG|N/A
  - OBSERVABILITY|N/A
  - SECRETS|N/A
allowedPaths:
  - backend/src/main/java/com/aihub/taxonomy
  - backend/src/test/java/com/aihub/taxonomy
  - frontend/src/features/admin
  - deploy/compose
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V6__taxonomy_dictionary.sql
  - backend/src/main/resources/db/migration/V7__taxonomy_tag.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR020-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-020.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-020：字典/标签启停、作用域和两语言历史回显 E4

> 状态：`READY`

## 1. 目标

验证字典项/标签的创建、更新、启停、作用域唯一性、自由标签拒绝和 zh-CN/en-US 双语回显的完整 E4 旅程。

## 2. 关联规格

```yaml
requirements: [REQ-TAX-001, REQ-TAG-001]
acceptanceScenarios: [AC-P0B-TAX-001, AC-P0B-TAX-002, AC-P0B-TAX-003, AC-P0B-TAX-004, AC-P0B-TAX-005, AC-P0B-TAX-006]
decisions: [DEC-001]
```

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-TAX-001` | 管理员 | — | 创建/更新字典项 | itemCode 稳定，版本递增，i18nKey 双语 | `E4` |
| `AC-P0B-TAX-002` | 管理员 | 字典项被资产引用 | 停用 | 新写入拒绝，历史按 locale 回显 | `E4` |
| `AC-P0B-TAX-003` | 管理员 | — | 创建平台/组织标签 | 作用域唯一约束生效 | `E4` |
| `AC-P0B-TAX-004` | 用户 | — | 提交自由标签/未知 tagId | TAG_VALUE_INVALID | `E4` |
| `AC-P0B-TAX-005` | 管理员 | 标签已关联 | 停用 | 历史保留，新关联拒绝 | `E4` |
| `AC-P0B-TAX-006` | 用户 | — | 切换 zh-CN/en-US | 全部 UI 元素使用对应语言 | `E4` |
