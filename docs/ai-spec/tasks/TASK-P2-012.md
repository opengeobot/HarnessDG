---
schemaVersion: harnessdg.task/v1
taskId: TASK-P2-012
status: READY
implementationAuthorized: false
phase: P2
baseCommit: 71a03efcbaf9408b7ed07df5a7015c4ee110f8c3
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED；P2 上传/物化与 P1 血缘已部分落地；本任务为上传 Manifest 真实化与血缘删除差距闭合
stageGateEvidenceRefs:
  - DEC-008
  - DEC-010
requirements:
  - REQ-UPL-001
  - REQ-UPL-003
  - REQ-MNF-001
  - REQ-AST-006
acceptanceScenarios:
  - AC-P2-UPL-001
  - AC-P2-UPL-003
  - AC-P2-MNF-001
  - AC-P1-AST-010
decisions:
  - DEC-008
  - DEC-010
  - DEC-011
scenarioEvidencePlan:
  - AC-P2-UPL-001|E4|backend test: 上传 complete 在 files 为空时拒绝（不再生成 upload-N.bin 占位），返回明确错误码
  - AC-P2-UPL-003|E4|backend integration test: Materialization Worker 校验真实 Manifest/DVC Digest，占位文件不进入物化
  - AC-P2-MNF-001|E4|backend test: Manifest 摘要算法对真实文件清单生效；摘要与文件 SHA-256 一致
  - AC-P1-AST-010|E4|backend test: 血缘关系删除 API 按权限/审计移除关系，软删除保留审计历史
crossCuttingPlan:
  - AUTHN|上传 complete 与血缘删除复用 JWT PrincipalContext；匿名拒绝
  - AUTHZ|血缘删除需 asset:manage 或 asset:write；上传 complete 需 asset:upload；按资产 ACL 过滤
  - DB_FILTER|N/A - 写路径，按 assetId 主键定位；血缘删除校验关系归属 assetId
  - STATE|上传 complete 仅在 session 有效且 files 非空时受理；血缘删除在资产非 ARCHIVED 时受理
  - IDEMPOTENCY|上传 complete 复用 Idempotency-Key；血缘删除使用 rowVersion 乐观锁
  - CONSISTENCY|Manifest 物化走 Saga/Outbox；血缘删除与审计同事务
  - ERRORS|UPLOAD_NO_FILES/UPLOAD_SESSION_EXPIRED/ASSET_RELATION_NOT_FOUND/CONCURRENT_MODIFICATION 按目录映射
  - AUDIT|UPLOAD_COMPLETED/UPLOAD_REJECTED/ASSET_RELATION_DELETED 审计，含 Principal/资源/幂等键摘要/Trace
  - NOTIFICATION|N/A - 上传/血缘删除无订阅通知（VERSION_* fan-out 由发布流程触发）
  - TAXONOMY_I18N|N/A - 不涉及字典/标签变更
  - CONFIG|上传空文件拒绝阈值与 session 过期走 aihub.upload.* 配置
  - OBSERVABILITY|上传/物化/血缘删除指标与 trace
  - SECRETS|DVC/MinIO 凭据走 STS/scoped，不进入日志/审计/响应
allowedPaths:
  - backend/src/main/java/com/aihub/transfer/application/UploadApplicationService.java
  - backend/src/main/java/com/aihub/transfer/infrastructure/UploadMaterializeJobHandler.java
  - backend/src/main/java/com/aihub/transfer/domain
  - backend/src/main/java/com/aihub/asset/application/AssetRelationApplicationService.java
  - backend/src/main/java/com/aihub/asset/api/AssetController.java
  - backend/src/main/java/com/aihub/asset/domain/AssetRelation.java
  - backend/src/test/java/com/aihub/transfer/application
  - backend/src/test/java/com/aihub/asset/application
  - contracts/openapi/aihub-v1.yaml
  - docs/ai-spec/tasks/evidence/EVD-P2012-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P2012-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-012.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-012.md -CheckChangedPaths
  - ./mvnw verify
  - npx @redocly/cli lint contracts/openapi/aihub-v1.yaml
  - npx @redocly/cli diff contracts/openapi/aihub-v1.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking
approvedBy: pending-verification-authority
approvedAt: 2026-07-12T00:00:00Z
---
# TASK-P2-012：上传 Manifest 真实化与血缘删除 API（Wave U 差距闭合）

> 状态：`READY`（待用户授权；`implementationAuthorized: false`，AI 不自行授权）

## 1. 目标

移除 `UploadApplicationService` 在 `files` 为空时生成 `upload-N.bin` 占位文件的逻辑（违反 PRD §6.5 Manifest 为机器事实），并在 `AssetRelationApplicationService` 增加血缘关系删除 API（软删除保留审计）。

## 2. 关联规格

```yaml
requirements: ['REQ-UPL-001', 'REQ-UPL-003', 'REQ-MNF-001', 'REQ-AST-006']
acceptanceScenarios: ['AC-P2-UPL-001', 'AC-P2-UPL-003', 'AC-P2-MNF-001', 'AC-P1-AST-010']
decisions: ['DEC-008', 'DEC-010', 'DEC-011']
adrs: ['ADR-0001']
openapiOperations: ['completeUploadSession', 'deleteAssetRelation']
flywayBaseline: V36
```

## 3. 范围

### 允许修改

- 后端：UploadApplicationService、UploadMaterializeJobHandler、transfer domain；AssetRelationApplicationService、AssetController、AssetRelation domain；对应测试。
- 契约：aihub-v1.yaml completeUploadSession 错误码 + deleteAssetRelation 操作。

### 明确不在范围

- 前端上传/血缘 UI（属 TASK-P1-013 / TASK-P3-009）。
- 预览自动取数（属 TASK-P2-011）。

### 禁止变化

- 不削弱上传校验/Manifest 摘要；不物理删除血缘审计历史；不修改 V1/V2 Migration。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P2-UPL-001` | Uploader | 创建/complete 上传会话 | files 为空 | 拒绝并返回 UPLOAD_NO_FILES；不再生成占位文件 | `E4` |
| `AC-P2-UPL-003` | Worker | 物化 | Manifest/DVC Digest | 校验真实文件清单与 SHA-256；占位文件不进入物化 | `E4` |
| `AC-P2-MNF-001` | Uploader | complete | 生成 Manifest | 摘要算法对真实文件生效；与文件 SHA-256 一致 | `E4` |
| `AC-P1-AST-010` | Owner/Manager | 资产有血缘关系 | 删除关系 | 按 asset:manage 授权；软删除保留审计；越权拒绝 | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

1. OpenAPI：`completeUploadSession` 增加 `UPLOAD_NO_FILES` 错误；新增 `deleteAssetRelation` 操作。
2. 无新增 Migration；血缘软删除复用现有 `asset_relation` 表（如需 deleted_at 列，新增 V37+ 前向 Migration）。
3. 错误码：`UPLOAD_NO_FILES/UPLOAD_SESSION_EXPIRED/ASSET_RELATION_NOT_FOUND/CONCURRENT_MODIFICATION`。

## 7. 实施步骤

- [ ] OpenAPI 契约更新 + lint + breaking diff
- [ ] UploadApplicationService 移除占位逻辑 + 拒绝空 files
- [ ] AssetRelationApplicationService 删除 API + 软删除 + 审计
- [ ] 单元/集成/契约/授权/失败路径测试
- [ ] Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-012.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-012.md -CheckChangedPaths
./mvnw verify
npx @redocly/cli lint contracts/openapi/aihub-v1.yaml
npx @redocly/cli diff contracts/openapi/aihub-v1.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking
```

## 9. 停止条件

- 需要物理删除血缘审计历史时停止。
- 需要削弱 Manifest 摘要校验时停止。

## 10. 完成报告

```text
Completed:
Changed files:
Requirements and scenarios satisfied:
Contract/database changes:
Validation run and results:
Evidence artifacts:
Validation not run and reasons:
Remaining risks or follow-ups:
```
