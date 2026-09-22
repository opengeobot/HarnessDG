---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-014
status: READY
implementationAuthorized: true
phase: P1
baseCommit: 6cdf200472e47062462a1b2d736e54d58c189446
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED；P1-P5 已部分落地但 E4 notProven；本任务为 Compose E4 旅程闭合差距
stageGateEvidenceRefs:
  - DEC-008
  - DEC-010
requirements:
  - REQ-AST-001
acceptanceScenarios:
  - AC-P1-AST-014
  - AC-P2-EXIT-001
  - AC-P3-EXIT-001
decisions:
  - DEC-008
  - DEC-010
scenarioEvidencePlan:
  - AC-P1-AST-014|E4|Compose verify.sh + verify-journey.sh: V05 JWT 生命周期、V20 对账 Worker PASS；资产创建/发布旅程不再 SKIP（REPOSITORY_PROVISION job 修复）
  - AC-P2-EXIT-001|E4|verify-journey.sh: CLI/Web 上传下载校验旅程 PASS；E2E Playwright dataset/publish-flow 本地或 CI 执行
  - AC-P3-EXIT-001|E4|verify-journey.sh: 发布不可变完整 Compose 出口旅程 PASS
crossCuttingPlan:
  - AUTHN|V05 JWT 生命周期修复复用双密码回退与 must_change_password 门处理；不引入空主体或 allow-all
  - AUTHZ|对账 Worker 与旅程断言按两主体权限差异执行；不削弱权限下推
  - DB_FILTER|N/A - 旅程脚本断言 API 行为，不改查询权限
  - STATE|REPOSITORY_PROVISION job 修复使资产创建进入 COMPLETED；旅程覆盖 DRAFT→PUBLISHED 状态机
  - IDEMPOTENCY|旅程脚本复用既有 Idempotency-Key 断言；不引入新幂等机制
  - CONSISTENCY|对账 Worker 修复保持 Gitea/MinIO/PG 对账一致；不双向同步
  - ERRORS|旅程脚本的 http_status/http_body 断言合并为单次请求；SKIP-safe；失败可定位
  - AUDIT|旅程断言关键审计事件存在；不记录敏感正文
  - NOTIFICATION|N/A - 旅程不新增通知类型
  - TAXONOMY_I18N|N/A - 旅程不涉及字典/标签变更
  - CONFIG|Compose 配置（application.yml/docker-compose.yml）修复凭据漂移；不硬编码 secret
  - OBSERVABILITY|旅程产出可读证据；perf-smoke 可选
  - SECRETS|旅程脚本与证据输出脱敏；不泄露 JWT/预签名 URL/DVC 凭据
allowedPaths:
  - deploy/compose/scripts/verify-journey.sh
  - deploy/compose/scripts/verify.sh
  - deploy/compose/docker-compose.yml
  - backend/src/main/resources/application.yml
  - backend/src/test/java/com/aihub/agent/AgentRestIT.java
  - backend/src/test/java/com/aihub/mcp/McpEndToEndIT.java
  - backend/src/test/java/com/aihub/transfer/UploadTransferIT.java
  - backend/src/test/java/com/aihub/version/VersionPublishIT.java
  - frontend/e2e/dataset.spec.ts
  - frontend/e2e/publish-flow.spec.ts
  - frontend/e2e/mcp-integration.spec.ts
  - docs/ai-spec/tasks/TASK-P1-014.md
  - docs/ai-spec/tasks/evidence/EVD-P1014-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1014-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-014.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-014.md -CheckChangedPaths
  - ./mvnw verify
  - docker compose config --quiet
  - ./deploy/compose/scripts/verify.sh
  - pnpm lint
  - pnpm typecheck
  - pnpm test
  - pnpm build
approvedBy: User (plan execution authorization 2026-07-12)
approvedAt: 2026-07-12T05:45:00Z
---
# TASK-P1-014：Compose E4 旅程闭合（Wave W 差距闭合）

> 状态：`READY`（待用户授权；`implementationAuthorized: false`，AI 不自行授权）

## 1. 目标

闭合 Compose E4 验收旅程：修复 verify.sh V05 JWT 生命周期与 V20 对账 Worker FAIL，修复 verify-journey.sh 资产创建/发布 SKIP（REPOSITORY_PROVISION job 500），并执行 E2E（Playwright）dataset/publish-flow/mcp-integration 旅程。

## 2. 关联规格

```yaml
requirements: ['REQ-AST-001']
acceptanceScenarios: ['AC-P1-AST-014', 'AC-P2-EXIT-001', 'AC-P3-EXIT-001']
decisions: ['DEC-008', 'DEC-010']
adrs: ['ADR-0001', 'ADR-0002']
openapiOperations: ['login', 'refreshToken', 'createAsset', 'submitPublishRequest', 'issueDownloadTicket']
flywayBaseline: V36
```

## 3. 范围

### 允许修改

- Compose：verify-journey.sh、verify.sh、docker-compose.yml。
- 后端：application.yml（凭据漂移修复）、AgentRestIT/McpEndToEndIT/UploadTransferIT/VersionPublishIT 集成测试。
- 前端：e2e dataset/publish-flow/mcp-integration spec。

### 明确不在范围

- 产品功能实现（属 TASK-P1-013/P2-011/P2-012/P3-009）；本任务仅闭合 E4 验收与配置漂移。

### 禁止变化

- 不引入空主体/allow-all/全 Scope；不削弱权限/校验；不修改 V1/V2 Migration。
- 不谎报 E4 PASS；Docker 不可用时保持 notProven。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P1-AST-014` | 两主体 | Compose up | verify.sh + verify-journey.sh | V05 JWT 生命周期、V20 对账 PASS；资产创建/发布旅程不再 SKIP | `E4` |
| `AC-P2-EXIT-001` | Uploader/Downloader | Compose up | CLI/Web 上传下载旅程 | 校验 PASS；E2E Playwright 执行 | `E4` |
| `AC-P3-EXIT-001` | Approver | Compose up | 发布不可变旅程 | 四眼 + 受保护 Tag + 三元组 PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 无新增 OpenAPI/Migration；仅配置与测试修复。
- application.yml 凭据漂移修复不硬编码 secret，走 env 注入。

## 7. 实施步骤

- [ ] verify.sh V05 JWT 双密码回退 + must_change_password 门处理对齐 verify-journey.sh
- [ ] V20 对账 Worker 依赖 V05 token 修复
- [ ] REPOSITORY_PROVISION job 修复（资产创建进入 COMPLETED）
- [ ] verify-journey.sh 资产创建/发布旅程闭合
- [ ] E2E Playwright dataset/publish-flow/mcp-integration 执行
- [ ] Evidence Manifest（E4 proven，附 verify 输出与脱敏扫描）

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-014.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-014.md -CheckChangedPaths
./mvnw verify
docker compose config --quiet
./deploy/compose/scripts/verify.sh
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

## 9. 停止条件

- 本机 Docker 不可用：E4 旅程保持 notProven，不谎报；记录 SKIP 原因。
- 需要空主体/allow-all 绕过 JWT 生命周期时停止。

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
