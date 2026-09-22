---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-018
status: READY
implementationAuthorized: true
phase: P1
baseCommit: 1593e3b3a5dd9cceaf1d68d3219d3aa9c79212ac
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED；阶段二 T-W + 阶段三 X/Y/Z 落地后，本任务闭合 Compose E4 全栈验收
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
  - AC-P1-AST-014|E4|Compose verify.sh V01-V28 全 PASS + verify-journey 资产创建/发布旅程不再 SKIP
  - AC-P2-EXIT-001|E4|verify-journey CLI/Web 上传下载旅程 PASS + E2E Playwright dataset/publish-flow/mcp-integration/version-management
  - AC-P3-EXIT-001|E4|verify-journey 发布不可变旅程 PASS（四眼+受保护 Tag+三元组）
crossCuttingPlan:
  - AUTHN|复用 verify.sh V05 admin JWT + reader JWT（Wave Y）
  - AUTHZ|verify 断言经 Wave Y 重定义（admin=200，reader=403）
  - DB_FILTER|N/A
  - STATE|资产创建经 Wave X 修复后 REPOSITORY_PROVISION 入队成功
  - IDEMPOTENCY|旅程复用既有 Idempotency-Key 断言
  - CONSISTENCY|对账 Worker V20 经 V05 token 修复
  - ERRORS|旅程脚本的 http_status/http_body 断言合并为单次请求
  - AUDIT|旅程断言关键审计事件存在；不记录敏感正文
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|N/A
  - CONFIG|Compose 配置（application.yml/compose.yaml）凭据漂移修复；不硬编码 secret
  - OBSERVABILITY|旅程产出可读证据
  - SECRETS|旅程脚本与 EVD 输出脱敏；不泄露 JWT/预签名 URL/DVC 凭据
allowedPaths:
  - deploy/compose/scripts/verify.sh
  - deploy/compose/scripts/verify-journey.sh
  - deploy/compose/compose.yaml
  - backend/src/main/resources/application.yml
  - frontend/e2e/dataset.spec.ts
  - frontend/e2e/publish-flow.spec.ts
  - frontend/e2e/mcp-integration.spec.ts
  - frontend/e2e/version-management.spec.ts
  - docs/ai-spec/tasks/evidence/EVD-P1018-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1018-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-018.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-018.md -CheckChangedPaths
  - ./mvnw verify
  - docker compose config --quiet
  - ./deploy/compose/scripts/verify.sh
  - pnpm lint
  - pnpm typecheck
  - pnpm test
  - pnpm build
approvedBy: User (plan execution authorization 2026-07-12)
approvedAt: 2026-07-12T10:05:00Z
---
# TASK-P1-018：Compose E4 全栈验收闭合（Wave E4）

> 状态：`READY`（待用户授权；依赖 Wave X/Y/Z）

## 1. 目标
重建 backend/worker/frontend（含 Wave X 修复）+ verify.sh + verify-journey + E2E Playwright；将阶段二/三 EVD 翻 PASS（review.accepted 由用户作为验证责任方接受）。

## 2. 关联规格
```yaml
requirements: ['REQ-AST-001']
acceptanceScenarios: ['AC-P1-AST-014', 'AC-P2-EXIT-001', 'AC-P3-EXIT-001']
decisions: ['DEC-008', 'DEC-010']
```

## 3. 范围
- 重建镜像 + Compose 全栈验收 + E2E + EVD 翻 PASS。
- 依赖 Wave X（job bug）/Y（断言）/Z（前端）落地。

## 4. 行为切片
| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P1-AST-014` | 两主体 | Compose up（含 X 修复） | verify.sh + verify-journey | V01-V28 PASS；资产创建/发布旅程不再 SKIP | `E4` |
| `AC-P2-EXIT-001` | Uploader/Downloader | Compose up | CLI/Web + E2E | 上传下载旅程 PASS；Playwright 执行 | `E4` |
| `AC-P3-EXIT-001` | Approver | Compose up | 发布旅程 | 四眼+受保护 Tag+三元组 PASS | `E4` |

## 8. 验证命令
```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-018.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-018.md -CheckChangedPaths
./mvnw verify
docker compose config --quiet
./deploy/compose/scripts/verify.sh
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

## 9. 停止条件
- Docker 不可用或重建超时：E4 保持 notProven，不谎报。
- 需空主体/allow-all 绕过 JWT 时停止。
