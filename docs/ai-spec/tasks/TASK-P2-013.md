---
schemaVersion: harnessdg.task/v1
taskId: TASK-P2-013
status: READY
implementationAuthorized: false
phase: P2
baseCommit: b15a30918f53172087b80306d91cf9181494fac0
stageGatePassed: true
stageGateEvidence: P2 版本/传输已部分落地；Wave T 已对齐 taskCodes/modalityCodes/formatCodes；本任务补 languageCodes 多值
stageGateEvidenceRefs:
  - DEC-008
  - DEC-010
requirements:
  - REQ-DST-TAX-001
acceptanceScenarios:
  - AC-DST-TAX-001
  - AC-DST-TAX-002
decisions:
  - DEC-008
  - DEC-010
scenarioEvidencePlan:
  - AC-DST-TAX-001|E4|backend test + OpenAPI: /assets 与 /datasets 支持 languageCodes 多值过滤（同维度 OR）
  - AC-DST-TAX-002|E4|backend test: Facet 计数经权限/敏感度/状态过滤；languageCodes facet 正确
crossCuttingPlan:
  - AUTHN|复用 JWT PrincipalContext；匿名拒绝
  - AUTHZ|搜索权限下推 AssetSearchDao；不泄露无权资产
  - DB_FILTER|languageCodes 经 appendJsonArrayContains 下推；Facet 权限过滤
  - STATE|N/A
  - IDEMPOTENCY|N/A - 读路径
  - CONSISTENCY|N/A
  - ERRORS|COMMON_INVALID_ARGUMENT/DICTIONARY_VALUE_INVALID
  - AUDIT|搜索仅记脱敏访问指标
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|languageCodes 引用 dataset_language 字典 itemCode
  - CONFIG|N/A
  - OBSERVABILITY|N/A
  - SECRETS|N/A
allowedPaths:
  - backend/src/main/java/com/aihub/asset/application/AssetSearchQuery.java
  - backend/src/main/java/com/aihub/asset/api/AssetRequestMapper.java
  - backend/src/main/java/com/aihub/asset/api/AssetController.java
  - backend/src/main/java/com/aihub/asset/api/AssetCatalogController.java
  - backend/src/main/java/com/aihub/asset/application/AssetApplicationService.java
  - backend/src/test/java/com/aihub/asset/application
  - contracts/openapi/aihub-v1.yaml
  - docs/ai-spec/tasks/TASK-P2-013.md
  - docs/ai-spec/tasks/evidence/EVD-P2013-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P2013-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-013.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-013.md -CheckChangedPaths
  - ./mvnw verify
  - npx @redocly/cli lint --config contracts/openapi/redocly.yaml contracts/openapi/aihub-v1.yaml
  - npx @redocly/cli diff /tmp/aihub-base.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking
approvedBy: pending-verification-authority
approvedAt: 2026-07-12T00:00:00Z
---
# TASK-P2-013：languageCodes 多值接入应用层（Wave L）

> 状态：`READY`（待用户授权）

## 1. 目标
`AssetSearchQuery` 增 `languageCodes` 多值字段；`AssetRequestMapper`/`AssetController`/`AssetCatalogController`/`AssetApplicationService` 接入；OpenAPI `searchAssets`/`searchDatasets` 增 `languageCodes` 数组参数。保留 `language` 单值向后兼容。

## 2. 关联规格
```yaml
requirements: ['REQ-DST-TAX-001']
acceptanceScenarios: ['AC-DST-TAX-001', 'AC-DST-TAX-002']
decisions: ['DEC-008', 'DEC-010']
openapiOperations: ['searchAssets', 'searchDatasets']
flywayBaseline: V36
```

## 3. 范围
- 后端应用层 + 控制器 + OpenAPI；AssetSearchDao 已支持 languageCodes（Wave T 确认）。
- 不改前端（前端 Wave S 已支持 languageCodes 数组）。

## 4. 行为切片
| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-DST-TAX-001` | Reader | /assets 与 /datasets | languageCodes 多值 | 多值 OR 过滤；criteria 传播 | `E4` |
| `AC-DST-TAX-002` | Reader | facets | languageCodes facet | 权限下推后计数正确 | `E4` |

## 8. 验证命令
```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-013.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-013.md -CheckChangedPaths
./mvnw verify
npx @redocly/cli lint --config contracts/openapi/redocly.yaml contracts/openapi/aihub-v1.yaml
```
