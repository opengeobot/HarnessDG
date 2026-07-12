---
schemaVersion: harnessdg.task/v1
taskId: TASK-P3-010
status: READY
implementationAuthorized: true
phase: P3
baseCommit: 34f5cb16bcb128757707d3dd3cb7137b25b2acff
stageGatePassed: true
stageGateEvidence: P3 发布治理已部分落地；本任务修复 job 子系统阻断 bug（资产创建 500 根因），属 P3 发布链路依赖
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-AST-002
acceptanceScenarios:
  - AC-P1-AST-002
decisions:
  - DEC-010
  - DEC-011
scenarioEvidencePlan:
  - AC-P1-AST-002|E4|backend integration test + Compose verify-journey: POST /api/v1/assets 不再 500，REPOSITORY_PROVISION 入队成功，资产 provisioningStatus 进入 COMPLETED
crossCuttingPlan:
  - AUTHN|复用现有 JWT PrincipalContext；createAsset 需 asset:create
  - AUTHZ|createAsset 权限校验已实现（403 而非 500）；本任务仅修 job 入队 SQL，不动授权
  - DB_FILTER|N/A - 写路径按主键；job_task insert 列/占位符对齐
  - STATE|REPOSITORY_PROVISION 入队后 Worker 领取建仓；JobWorker.claimNext 须可领取（排查 FOR UPDATE SKIP LOCKED）
  - IDEMPOTENCY|job 入队复用既有 idempotency；本任务不改幂等机制
  - CONSISTENCY|createAsset @Transactional；job insert 失败回滚资产（当前 500 根因）；修复后资产+job 同事务提交
  - ERRORS|修复后 DataIntegrityViolationException 消失；GlobalExceptionHandler 不再收到该 500
  - AUDIT|ASSET_CREATED 审计已实现；job 入队成功后审计完整
  - NOTIFICATION|N/A - job 入队无通知
  - TAXONOMY_I18N|N/A - 不涉及字典/标签
  - CONFIG|N/A - 无新配置
  - OBSERVABILITY|job 入队/领取指标；RecurringJobBootstrapper 启动不再报错
  - SECRETS|N/A - job payload 仅含 assetId，无凭据
allowedPaths:
  - backend/src/main/java/com/aihub/job/infrastructure/JdbcJobRepository.java
  - backend/src/main/java/com/aihub/job/infrastructure
  - backend/src/test/java/com/aihub/job
  - docs/ai-spec/tasks/TASK-P3-010.md
  - docs/ai-spec/tasks/evidence/EVD-P3010-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P3010-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P3-010.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P3-010.md -CheckChangedPaths
  - ./mvnw verify
approvedBy: User (plan execution authorization 2026-07-12)
approvedAt: 2026-07-12T08:35:00Z
---
# TASK-P3-010：修复 JdbcJobRepository.insert 占位符 bug（Wave X 关键 bug）

> 状态：`READY`（待用户授权；`implementationAuthorized: false`，AI 不自行授权）

## 1. 目标

修复 `JdbcJobRepository.insert` SQL 16 列但 15 个 `?` 占位符的 bug，恢复 job 子系统与资产创建（POST /api/v1/assets 不再 500）。

## 2. 关联规格

```yaml
requirements: ['REQ-AST-002']
acceptanceScenarios: ['AC-P1-AST-002']
decisions: ['DEC-010', 'DEC-011']
openapiOperations: ['createAsset']
flywayBaseline: V36
```

## 3. 范围

### 允许修改
- `JdbcJobRepository.java` insert SQL（补第 16 个 `?`）+ `JobWorker.claimNext` 排查（若同源）。
- `backend/src/test/java/com/aihub/job/` 新增 insert/claim 测试。

### 明确不在范围
- 前端、OpenAPI、verify 脚本、Compose 重建（属 Z/B7/Y/E4）。

### 禁止变化
- 不修改 V1/V2 Migration；不削弱授权/校验。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P1-AST-002` | Maintainer | job_task 表存在 | POST /api/v1/assets | 资产创建成功（201），REPOSITORY_PROVISION 入队，无 500；Worker 可领取 | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行
- 无契约/Migration 变更；仅 SQL 占位符修正。

## 7. 实施步骤
- [ ] 修复 JdbcJobRepository.insert 占位符
- [ ] 排查 JobWorker.claimNext FOR UPDATE SKIP LOCKED
- [ ] 新增 JdbcJobRepositoryInsertTest（Testcontainers disabledWithoutDocker + 单元层 SQL 占位符断言）
- [ ] ./mvnw verify
- [ ] Evidence Manifest

## 8. 验证命令
```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P3-010.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P3-010.md -CheckChangedPaths
./mvnw verify
```

## 9. 停止条件
- 修复后仍 500；或需削弱授权/校验。

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
