---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-015
status: READY
implementationAuthorized: false
phase: P1
baseCommit: b15a30918f53172087b80306d91cf9181494fac0
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED；P1 旅程脚本已部分落地；本任务重定义 verify 断言以反映 admin 合法权限
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-AST-001
acceptanceScenarios:
  - AC-P1-AST-014
decisions:
  - DEC-010
scenarioEvidencePlan:
  - AC-P1-AST-014|E4|Compose verify.sh V06/V07/V09/V10/V11/V21 PASS（anon=401 + admin=200 on system 端点；可选 reader=403）
crossCuttingPlan:
  - AUTHN|verify 复用 V05 取得 admin JWT；新增 ensure_reader_jwt 登录 rol_reader 用户
  - AUTHZ|不降 admin 权限；重定义断言为 admin 合法访问 system 端点=200；reader 用户真默认拒绝=403
  - DB_FILTER|N/A - 脚本不改查询权限
  - STATE|N/A
  - IDEMPOTENCY|N/A
  - CONSISTENCY|N/A
  - ERRORS|断言失败给出清晰期望/实际
  - AUDIT|N/A
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|N/A
  - CONFIG|N/A
  - OBSERVABILITY|N/A
  - SECRETS|reader 凭据不进入脚本输出/日志
allowedPaths:
  - deploy/compose/scripts/verify.sh
  - deploy/compose/scripts/verify.ps1
  - docs/ai-spec/tasks/TASK-P1-015.md
  - docs/ai-spec/tasks/evidence/EVD-P1015-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1015-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-015.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-015.md -CheckChangedPaths
  - docker compose config --quiet
  - ./deploy/compose/scripts/verify.sh
approvedBy: pending-verification-authority
approvedAt: 2026-07-12T00:00:00Z
---
# TASK-P1-015：重定义 verify 断言（admin=200，不降权）（Wave Y）

> 状态：`READY`（待用户授权）

## 1. 目标
重定义 verify.sh/verify.ps1 的 `assert_default_deny` 为 anon=401 + admin=200（admin 经 rol_admin 合法访问 system 端点）；可选新增 reader 用户 403 真默认拒绝证明。不降 admin 权限。

## 2. 关联规格
```yaml
requirements: ['REQ-AST-001']
acceptanceScenarios: ['AC-P1-AST-014']
decisions: ['DEC-010']
```

## 3. 范围
- `verify.sh`/`verify.ps1` 断言重定义；V06/V07/V09/V10/V11/V21 调用更新；可选 `ensure_reader_jwt` + `assert_unprivileged_denied`。
- 不改后端授权模型。

## 4. 行为切片
| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P1-AST-014` | anon+admin | Compose up | verify.sh V06-V21 | anon=401、admin=200（system 端点）；reader=403（可选） | `E4` |

## 5-10. 见模板（横切/契约/步骤/验证/停止/报告）
## 8. 验证命令
```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-015.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-015.md -CheckChangedPaths
docker compose config --quiet
./deploy/compose/scripts/verify.sh
```
