# HarnessDG AI 可执行规格包

> 状态：`DRAFT`
> 建立日期：2026-07-02
> 用途：把产品意图转换为 AI 编程 IDE 可逐项实施、可自动验收、可追溯的工程规格。

## 1. 本目录解决什么问题

`prd/DEEP_RESEARCH_内部AI资产管理平台设计.md` 描述了完整愿景、架构和阶段路线，但一份长设计文档
不能单独承担以下职责：

- 消除产品行为歧义；
- 区分目标能力、当前实现和验证证据；
- 约束单次 AI 任务的输入、输出和允许变更范围；
- 让需求、契约、迁移、代码、页面、测试和运行证据形成闭环；
- 阻止 AI 用“类/表/页面已存在”替代“用户旅程已经满足”。

本目录将成为上述闭环的入口。它不会复制 PRD，而是引用 PRD，并把其中的要求拆成带稳定 ID 的
可执行条目。

## 2. 当前使用限制

本规格包仍处于需求澄清阶段：

- 标记为 `OPEN` 的决策不得被 AI 自行假设；
- `DEC-001` 至 `DEC-007` 已确认当前先完成并重验 P0、保留代码整改、单公司多组织/项目、
  主要适配 Trae、首批不含 Agent/MCP，且规格批准前只修改文档；
- 本目录中的审计结论用于发现证据缺口，不等同于批准重写产品代码；
- 在范围、阶段和组织模型得到确认前，不得据此启动 P1+ 功能开发；
- 现有代码、任务勾选或历史状态报告都不是产品需求的权威来源。

## 3. 文档地图

| 文档 | 作用 |
| --- | --- |
| `manifest.yaml` | 机器可读入口、文档状态、未决决策和执行门禁 |
| `00-governance/source-and-status-policy.md` | 规定事实优先级、冲突处理、状态和证据等级 |
| `00-governance/open-questions.md` | 记录待用户确认的问题、建议默认值和最终决策 |
| `00-governance/decision-log.md` | 保存用户确认后的不可变决策及其影响范围 |
| `00-governance/current-state-audit-2026-07-02.md` | 记录当前文档、契约、代码与验证之间的冲突 |
| `01-requirements/requirement-schema.md` | 规定每条可执行需求必须包含的字段 |
| `01-requirements/product-scope.md` | 把产品目标、非目标和阶段出口改写为可判定边界 |
| `01-requirements/glossary.md` | 固定核心术语，并登记当前命名冲突 |
| `01-requirements/personas-and-scope.md` | 区分用户角色、Principal 类型、授权作用域和职责 |
| `01-requirements/non-functional-requirements.md` | 把性能、安全、可靠性、兼容和 UI 质量改写为可量化门禁 |
| `01-requirements/p0b-identity-authorization.md` | 身份、JWT、Agent、组织、Team、RBAC/ACL 的逐条可执行需求 |
| `01-requirements/p0b-platform-services.md` | 字典、标签、配置、幂等、任务、日志审计、通知和观测需求 |
| `01-requirements/p1-asset-catalog.md` | MODEL/DATASET 登记、搜索、详情、更新、归档和 Gitea 一致性需求 |
| `01-requirements/p2-version-transfer.md` | 版本草稿、Manifest/DVC、Web 上传和下载票据需求 |
| `01-requirements/p3-release-governance.md` | 校验、审批、受保护 Tag、不可变发布和弃用归档需求 |
| `01-requirements/p4-agent-integration.md` | MCP、Agent OpenAPI、Tool 双控、Skill 和客户端兼容需求 |
| `01-requirements/p5-quality-operations.md` | Inbox/对账、预览、备份恢复、性能、安全和运维需求 |
| `02-domain/entity-invariants.md` | 固定身份、授权、治理、资产、版本和可靠性数据的不变量 |
| `02-domain/state-machines.md` | 固定用户、Token、资产版本、上传、任务等允许状态转换 |
| `02-domain/consistency-and-compensation.md` | 定义 PostgreSQL/Gitea/DVC/MinIO 的 Saga、Inbox、Outbox 和对账 |
| `02-delivery/traceability-matrix.md` | 从能力追踪到契约、数据、代码、UI、测试和运行证据 |
| `02-delivery/work-breakdown.md` | 把 P0-B 重验至 P5 拆成有依赖和证据出口的 AI 工作包 |
| `03-use-cases/user-journey-catalog.md` | 以端到端用户结果划分 P0-B 至 P5 的实施顺序 |
| `04-ui/information-architecture.md` | 固定导航、路由、全局页面状态和权限体验 |
| `04-ui/page-catalog.md` | 登记每个页面的目的、权限、数据、动作与完成证据 |
| `04-ui/page-spec-template.md` | 单页面详细交互规格模板 |
| `05-acceptance/p0b-exit-catalog.md` | 把 P0-B 出口拆为不可用窄断言冒充的验收场景 |
| `06-ide/generic-execution-protocol.md` | 规定任意 AI 编程 IDE 的会话输入、执行顺序和停止条件 |
| `06-ide/trae-adapter.md` | 将正式规格映射到 Trae spec/tasks/checklist，避免复制产品语义 |
| `templates/task-card.md` | 交给 AI 编程 IDE 的原子任务模板 |
| `templates/evidence-manifest.yaml` | 每次验证必须填写的机器可读证据模板 |
| `tools/validate-spec.ps1` | 校验 manifest 路径、稳定 ID 重复和悬空引用 |

需求确认后还需补齐：

```text
docs/ai-spec/
├─ 01-requirements/
│  ├─ product-scope.md
│  ├─ glossary.md
│  └─ permission-matrix.md
├─ 02-domain/
│  └─ ...
├─ 03-use-cases/
│  └─ UC-xxxx-*.md
├─ 04-ui/
│  ├─ information-architecture.md
│  └─ PAGE-xxxx-*.md
├─ 05-acceptance/
│  ├─ acceptance-catalog.md
│  └─ evidence-manifest.md
└─ 06-ide/
   └─ ...
```

## 4. AI 执行入口

AI 开始任何实现任务前必须按顺序：

1. 读取仓库根 `AGENTS.md`；
2. 读取本文件和 `source-and-status-policy.md`；
3. 读取任务卡引用的需求、ADR、OpenAPI/MCP/Event Schema 和 Flyway；
4. 确认任务引用的所有决策均为 `ACCEPTED`，需求均为 `READY`；
5. 先更新机器契约，再实现代码；
6. 生成任务要求的测试与运行证据；
7. 只在证据达到需求声明的最低等级后更新状态。

若任一规范事实冲突，AI 必须停止受影响部分并登记冲突；不得选择最容易实现的一份继续开发。

规格包静态校验：

```powershell
./docs/ai-spec/tools/validate-spec.ps1
```

## 5. 完成判定

“完成”是一个证据结论，不是开发者或 AI 的主观声明。单个需求只有在以下条件全部成立时才能标为
`VERIFIED`：

- 需求无未决产品问题；
- 机器契约、迁移、实现和文档一致；
- 正常、边界、失败、越权和恢复路径均有对应测试；
- 验收证据覆盖需求所声明的真实范围；
- 所有强制命令已实际运行且没有 SKIP 被当成 PASS；
- 追踪矩阵不存在缺失列；
- 没有通过占位、Noop、宽松校验或仅拒绝路径冒充功能完成。
