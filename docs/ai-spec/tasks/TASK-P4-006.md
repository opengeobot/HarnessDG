---
schemaVersion: harnessdg.task/v1
taskId: TASK-P4-006
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
  - REQ-AGT-002
acceptanceScenarios:
  - AC-P4-SKL-001
decisions:
  - DEC-008
  - DEC-010
scenarioEvidencePlan:
  - AC-P4-SKL-001|E4|OpenClaw and QwenPaw Skill package with compatibility.yaml and locked client versions
crossCuttingPlan:
  - AUTHN|Skill documents Client Credentials to short-lived JWT exchange
  - AUTHZ|Skill rules enforce search-before-download and no version guessing
  - DB_FILTER|N/A - client-side Skill package only
  - STATE|N/A - Skill documentation
  - IDEMPOTENCY|N/A - Skill guidance only
  - CONSISTENCY|Skill references same Tool names as tools.yaml
  - ERRORS|Skill documents stable exit codes and retryable policy
  - AUDIT|N/A - Skill package documentation
  - NOTIFICATION|N/A - Skill package documentation
  - TAXONOMY_I18N|N/A - Skill uses stable field names
  - CONFIG|Secret Store references only; no real secrets in Skill files
  - OBSERVABILITY|N/A - Skill package documentation
  - SECRETS|Token/credential/URL must not enter chat, workspace or command history
allowedPaths:
  - aihub-agent-integration
  - docs/ai-spec/tasks/evidence/EVD-P4006-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P4006-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-006.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-006.md -CheckChangedPaths
  - test -f aihub-agent-integration/compatibility.yaml
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P4-006：OpenClaw/QwenPaw Skill 接入包

> 状态：`READY`

## 1. 目标

交付 REQ-AGT-002 aihub-agent-integration Skill 包，含 OpenClaw/QwenPaw SKILL.md 与 compatibility.yaml。

## 2. 关联规格

```yaml
requirements: ['REQ-AGT-002']
acceptanceScenarios: ['AC-P4-SKL-001']
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
| `AC-P4-SKL-001` | Operator | OpenClaw and QwenPaw Skill package with compatibility.yaml and locked client versions | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/MCP/Event/Migration（如适用）

## 7. 实施步骤

- [ ] 创建 Skill 目录结构；编写 SECURITY.md 与示例；记录锁定客户端版本。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-006.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P4-006.md -CheckChangedPaths
test -f aihub-agent-integration/compatibility.yaml
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
