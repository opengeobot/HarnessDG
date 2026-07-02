# Trae 规格适配规则

> 状态：`DRAFT`
> 方向：`DEC-006` 已确认 Trae 为主要 IDE；本适配层待规格包整体批准后生效。

## 1. 定位

```text
docs/ai-spec/                 产品与工程语义事实
└─ REQ / INV / AC / PAGE / JRN / TASK / Evidence

.trae/specs/<task-slug>/      Trae 会话适配
├─ spec.md                    只链接事实并给出会话摘要
├─ tasks.md                   只列当前 Task Card 的执行步骤
└─ checklist.md               只映射 AC 与 Evidence
```

Trae 目录不得重新定义产品规则。若 `.trae` 与 `docs/ai-spec` 冲突，以已批准的 AI Spec/ADR/机器契约为准，
并登记冲突，而不是静默选择。

现有 `.trae/specs/bootstrap-p0-baseline` 与 `establish-p0b-platform-foundation` 是历史执行材料。它们的
勾选不构成当前完成证据，尤其是 P0-B tasks 全勾选而 checklist 仍有 51 项未勾选的状态。

## 2. 创建 Trae Task 的前置

1. 正式 Task Card 状态为 READY；
2. 关联 Requirement 均为 READY；
3. 相关 Decision 均 ACCEPTED；
4. base Commit 已冻结；
5. 允许路径、Migration 下界和证据等级明确；
6. 工作树重叠变更已确认；
7. `.trae/specs/<slug>` 不存在或明确为同一 Task。

## 3. spec.md 格式

```markdown
# <Task ID> <标题>

> Source Task: docs/ai-spec/tasks/TASK-....md
> Base Commit: ...
> Required Evidence: E...

## Outcome

引用 Task 的一句话结果，不复制整份需求。

## Required Context

- REQ-...：相对链接
- AC-...：相对链接
- INV-...：相对链接
- DEC-...：相对链接
- ADR/OpenAPI/MCP/Event/Flyway：相对链接

## Scope

列出 allowed paths 和 explicit non-goals。

## Stop Conditions

引用通用执行协议和 Task 专用停止条件。
```

## 4. tasks.md 格式

一个 checkbox 对应一个可观察产物：

```markdown
- [ ] T1 契约：<operation/schema> 已更新并通过 <contract check>
  - Evidence: evidence/EVD-...yaml
- [ ] T2 数据：<migration> 空库和升级测试通过
  - Evidence: evidence/EVD-...yaml
- [ ] T3 行为：AC-... 的 Application/Domain 实现与 E2
  - Evidence: evidence/EVD-...yaml
- [ ] T4 集成：AC-... 的 E3
  - Evidence: evidence/EVD-...yaml
- [ ] T5 用户旅程：AC-... 的 E4
  - Evidence: evidence/EVD-...yaml
- [ ] T6 同步：Traceability/Runbook/Status
  - Evidence: evidence/EVD-...yaml
```

禁止：

- 用“实现模块”“完善前端”作为一个 checkbox；
- 只因文件存在就勾选；
- 在 Evidence 仍 DRAFT/FAIL/SKIP 时勾选；
- 父 Task 全勾选而 checklist 未清零；
- 在 tasks.md 中加入未在 Task Card 授权的新功能。

## 5. checklist.md 格式

checklist 直接映射 Acceptance：

```markdown
| Done | AC ID | Kind | Evidence | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| [ ] | AC-... | NORMAL | evidence/EVD-...yaml | — | |
| [ ] | AC-... | BOUNDARY | evidence/EVD-...yaml | — | |
| [ ] | AC-... | FAILURE | evidence/EVD-...yaml | — | |
| [ ] | AC-... | UNAUTHORIZED | evidence/EVD-...yaml | — | |
| [ ] | AC-... | RECOVERY | evidence/EVD-...yaml | — | |
```

只有 Evidence `result: PASS`、达到场景最低等级、Commit 与 Task 一致且没有否定 limitation 时可勾选。

## 6. Evidence 目录

建议：

```text
.trae/specs/<slug>/
├─ evidence/
│  ├─ EVD-...yaml
│  ├─ reports/
│  ├─ screenshots/
│  └─ traces/
```

- 大日志不复制进 Markdown，保存报告路径/摘要；
- Secret 扫描后才能保留 Artifact；
- 临时预签名 URL、JWT、credential 永不作为 Evidence；
- Evidence 的权威归档位置最终由 CI/制品系统决定，`.trae` 可只存索引。

## 7. Trae 会话行为

Trae 开始时：

1. 读取根 AGENTS；
2. 读取 AI Spec manifest/通用协议；
3. 读取当前 `.trae/spec` 引用；
4. 检查 READY/ACCEPTED/base Commit；
5. 输出 AC→实现→证据计划；
6. 才开始编辑。

Trae 结束时：

1. 逐项更新真实 Evidence；
2. 未达到 E4 的需求保持 IMPLEMENTED_UNVERIFIED；
3. 不自动把阶段标为完成；
4. 使用仓库完成报告格式；
5. 报告 FAIL/SKIP/未运行；
6. 保留下一任务依赖，不顺手扩展。

## 8. 迁移现有 Trae Specs

用户确认规格包后，按以下方式处理旧目录：

1. 标记为 `HISTORICAL`，不删除历史；
2. 在顶部链接当前 AI Spec 审计；
3. 不把旧 `[x]` 导入新状态；
4. 从 P0-B Exit Catalog 重新生成未验证项；
5. 每个新 Task 使用新 slug 和 base Commit；
6. 待 P0-B 真实出口后，旧 Spec 只作档案。
