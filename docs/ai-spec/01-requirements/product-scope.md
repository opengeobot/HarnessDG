# 产品范围与阶段出口

> 状态：`PROPOSED`
> 依据：设计文档第 0、1、2、5、13、16 章及 ADR-0001/0002。
> 已确认：`DEC-001`、`DEC-002`、`DEC-003`、`DEC-005`、`DEC-007`。
> 当前焦点：P0 尚未完成，先保持产品代码冻结并重新验收；P1-P5 规格只作后续顺序规划。

## 1. 产品使命

HarnessDG 是公司内部模型和数据集的资产控制平面。它必须让人类与 Agent 能够：

1. 发现自己有权访问的资产；
2. 确认资产由谁负责、适用什么 License 和敏感等级；
3. 将一个发布版本精确解析到 Git Tag、Commit SHA 和 Manifest/DVC Digest；
4. 通过受控路径上传、审批、发布、下载和审计资产；
5. 在依赖故障、Webhook 丢失或 Worker 重启后恢复到一致状态。

它不是简单的 CRUD 后台，也不是 Gitea、DVC、MinIO 三个界面的拼接。产品价值只有在完整用户旅程
可运行且版本事实可验证时才成立。

## 2. 核心可观察结果

| ID | 结果 | 产品层验收 |
| --- | --- | --- |
| `OUT-001` | 已认证用户只看见有权资产 | 两个权限不同的主体得到不同且正确的数据库查询结果 |
| `OUT-002` | 每个资产都有稳定责任主体和治理元数据 | Owner、字典项、标签均为有效受控引用 |
| `OUT-003` | 发布版本不可歧义、不可覆盖 | Tag、Commit、Digest 同时存在且覆盖 Tag 被拒绝并审计 |
| `OUT-004` | 大文件不经过业务后端中转 | 数据流直接发生在 Client/DVC 与 MinIO 之间 |
| `OUT-005` | REST、MCP、Worker 行为一致 | 同一用例使用同一 Application Service、授权、状态机和审计 |
| `OUT-006` | 可靠工作可恢复 | 进程重启、临时依赖失败和重复事件不会丢任务或重复副作用 |
| `OUT-007` | 管理操作可治理、可追责 | 用户、权限、标签、配置和高风险操作均有不可变审计 |
| `OUT-008` | Compose 可复现完整验收 | 从空环境启动后，权威 E2E 集合全部 PASS 且无强制场景 SKIP |

## 3. 范围内能力

### 3.1 产品控制面

- 本地用户、Agent、Service、API Client 和 Worker 身份；
- 组织、项目、Team（待确认）和角色/ACL；
- MODEL、DATASET 资产登记、卡片、治理字段和搜索；
- 版本、上传下载、审批发布、弃用归档和血缘；
- MCP、REST/OpenAPI 和 Agent Skill 接入；
- 字典、受控标签、配置、任务、审计、通知和观测管理端。

### 3.2 版本与数据面

- Gitea 保存仓库、卡片、配置、Commit 和 Tag；
- DVC 保存大对象内容版本；
- MinIO 承载 DVC Remote、暂存和预览；
- PostgreSQL 保存授权、流程、可靠任务、审计和查询投影。

这些边界由 ADR-0001 固定，任何变化均需新 ADR。

## 4. 明确非目标

- GPU 训练调度和实验跟踪；
- 在线推理、模型网关和弹性伸缩；
- Notebook 和交互开发环境；
- 数据标注平台；
- 通用数据湖/湖仓；
- 公网社区运营；
- Compose 高可用或跨地域多活；
- 完整复刻 Hugging Face/ModelScope SDK；
- 在 P0 使用平台 JWT 冒充 OAuth/OIDC Authorization Server。

AI 不得因为“有助于未来”而在当前任务预埋上述业务能力。

## 5. 阶段与出口

| 阶段 | 对用户可观察的交付 | 必须通过的出口 | 当前证据状态 |
| --- | --- | --- | --- |
| P0-A | 可构建、可启动的工程与契约骨架 | 构建、Flyway、ArchUnit、Compose 配置 | 历史声明完成；非本轮重验重点 |
| P0-B | 安全可复用的平台公共底座及管理端 | 身份、授权、治理、可靠性、审计、通知、观测和真实管理端 E4 | `INCOMPLETE / REVALIDATION_REQUIRED`（`DEC-003`） |
| P1 | 可治理的 MODEL/DATASET 目录 | 创建、详情、搜索、修改、归档；Owner/权限/字典/标签/Gitea 一致性 E4 | `PARTIAL/FROZEN` |
| P2 | 可复现的数据上传与下载 | DVC 往返、Multipart 恢复、短期下载票据、持久化 Worker E4 | `NOT_STARTED/PARTIAL_SKELETON` |
| P3 | 不可变版本与发布治理 | 校验、提交、审批、Tag、Digest、Saga/Outbox/补偿 E4 | `NOT_STARTED/SKELETON` |
| P4 | Agent 标准接入 | MCP 协议、Tool 双控、只读消费、越权审计、Skill 兼容 E4 | `NOT_STARTED/CONTRACT_DRAFT` |
| P5 | 质量与运维闭环 | 预览、对账、备份恢复、性能和安全目标 E4/E5 | `NOT_STARTED` |

“代码目录存在”“页面可访问”“Build 通过”均不能作为阶段出口。

## 6. 阶段推进规则

1. 上游阶段所有 MUST 需求为 `VERIFIED` 后才能解除下一阶段准入门；
2. 可并行编写后续规格，但不得并行实现依赖未验证底座的业务；
3. 发现上游缺口时，建立修复任务，不在下游复制临时实现；
4. 每个阶段至少有一个从浏览器/客户端入口到事实源的纵向 E4 旅程；
5. 每次阶段评审必须绑定 Commit SHA 和 Evidence Manifest；
6. 阶段结论更新到 `AGENTS.md` 前必须先同步契约、Runbook 和追踪矩阵。

## 7. “达到设计要求”的判定

软件达到本设计要求，至少意味着：

- 所有当前发布范围内的 MUST 需求为 `VERIFIED`；
- 所有 OUT-001 至 OUT-008 都有匹配范围的证据；
- 设计、ADR、机器契约和运行行为不存在未决冲突；
- P0-P5 各自的阶段出口按依赖顺序通过；
- 不存在把占位、Noop、Mock、拒绝路径或静态构建包装成业务完成的情况；
- 用户确认的首要旅程能由非开发人员按 Runbook 在干净环境复现。
