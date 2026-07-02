# 当前状态证据审计（2026-07-02）

> 状态：`DRAFT`
> 结论范围：当前工作树的文档、契约、源代码和已有验证材料。
> 审计基线：分支 `ai`，Commit `e490857bf889bf04976f5a012585dc8ecbd2600a` 加当前未提交规格文档。
> 本文不判定产品最终取舍，只判断现有证据能否支持已有完成声明。

## 1. 总结

当前证据不能证明 P0-B 已满足设计和 ADR-0002 的全部出口条件。仓库确实包含大量 P0-B 代码和测试，
但“有实现”与“需求已验证”之间仍存在结构性缺口。现阶段更准确的结论是：

```text
P0-B = IMPLEMENTED_UNVERIFIED（且若干能力仅为 PARTIAL）
P1 资产目录 = PARTIAL / FROZEN
P2-P5 = 以骨架、占位或目标契约为主
```

在冲突解决和逐需求重验之前，不应把 P0-B 标成 `VERIFIED`，也不应据此解除 P1+ 准入门。

## 2. 关键冲突与证据缺口

| ID | 发现 | 当前证据 | 影响 | 处理要求 |
| --- | --- | --- | --- | --- |
| `AUD-001` | 当前阶段互相矛盾 | `AGENTS.md` 指定当前仍为 P0-B 并引用 6 月 30 日快照；7 月 2 日快照称遗留闭环 | AI 无法判断是否允许进入 P1 | 由 `Q-003` 决策；在此之前保持 P0-B 门禁 |
| `AUD-002` | Compose Runbook 已过期 | Runbook 仍写“本地账号与 JWT 尚未实现”、V04 仍写 V1-V12；当前已有 V13 和身份代码 | 运维和 AI 会按错误现状工作 | 状态确认后更新 Runbook，不回写历史快照 |
| `AUD-003` | OpenAPI 实现状态与代码/报告冲突 | P0-B 几乎所有操作仍为 `x-implementation-status: planned-p0-b`，资产为 `partial-p1-frozen` | 契约无法作为可靠实施状态源 | 状态必须由证据清单生成或同步更新 |
| `AUD-004` | Trae 执行材料不在可审计仓库中 | `.gitignore` 排除 `.trae/`，当前检出没有 `.trae`；原适配文档却把不存在的目录描述为“现有” | 不能从仓库复核 Trae 历史任务、清单或 Evidence | IDE 私有材料仅作薄适配；正式 Task/Evidence 进入 `docs/ai-spec/tasks` 或受控 CI 制品 |
| `AUD-005` | 可观测范围冲突 | ADR-0002 和 P0-B Task 12 要求 OpenTelemetry Trace 贯通；7 月 2 日快照称完整 OTel 导出在 P0-B 之外 | P0-B 出口标准被事后缩小 | 若缩小范围需新 ADR；否则补齐 E4 Trace 证据 |
| `AUD-006` | 幂等能力存在但未接入业务写路径 | `IdempotencyService` 在主代码中没有调用方；前端部分写请求只发送随机 `Idempotency-Key` | 重放仍可能产生重复副作用 | 为每个需幂等的写用例声明策略并在应用服务/统一入口接入 |
| `AUD-007` | Compose P0-B 验收覆盖过窄 | V05 主要验证登录与 `/me`；V06-V11 多为 401/403 默认拒绝；未覆盖完整刷新轮换、旧 Token 重放、用户禁用、任务租约恢复、通知失败恢复、授权成功路径 | V01-V11 PASS 不能证明追踪矩阵中的广泛要求 | 将出口拆为独立场景 ID，正常/失败/恢复均跑 E4 |
| `AUD-008` | 前端“已实现”缺乏行为证据 | `frontend` 没有 `*.test.*`/`*.spec.*`；页面大量硬编码中文；无运行时翻译调用；Agent Scope 和 Tool 使用 `mode="tags"` 自由输入 | 不满足前端测试、受控值和 zh-CN/en-US 要求 | 建立页面规格、受控选择 API、Vitest 和浏览器 E2E |
| `AUD-009` | Owner 的 Team 模型缺失 | PRD/ADR 要求 Owner 引用 Principal/Team ID；公共表、管理 API 和页面没有完整 Team 资源 | 资产责任与授权语义无法实施 | 由 `Q-103`/`Q-104` 决策并补领域规格 |
| `AUD-010` | 权限边界不利于多适配器复用 | 多个 Controller 调 `AuthorizationService`；组织应用服务接收 `platformAdmin` 布尔值；设计要求 Application Service 负责权限且 REST/MCP/Worker 共用 | MCP/Worker 可能绕过或复制授权规则 | 权限前置条件进入统一用例服务，Adapter 只做协议认证/转换 |
| `AUD-011` | MCP 目标契约内部不一致 | `asset_publish_version` 标为 `write: false`，同时是高风险发布动作；契约状态为 `target-p0-b`，路线却把 MCP 实现在 P4 | AI 可能错误开放写工具或提前实现 P4 | 修正阶段归属和 Schema，发布工具必须 `write: true` |
| `AUD-012` | 过期占位注释污染当前事实 | 已接入持久化审计的 Port 仍写“TODO Task 10/不写持久化”；多个已实现包仍称“骨架占位” | AI 可能重复实现或错误删除有效代码 | 在证据确认后做限定范围的文档债清理 |
| `AUD-013` | P1+ 页面明确仍是占位 | Version、Upload、Access、Integrations 页面使用 `PlaceholderPage`；version/transfer/mcp/minio 多个模块只有 package-info 骨架 | 完整产品离设计目标仍很远 | 属阶段事实，不得被 P0-B 构建成功掩盖 |
| `AUD-014` | Gitea 创建仍未达到跨系统一致性设计 | 当前存在 Noop Provisioner，真实创建说明中把 Saga/Outbox/对账留到后续；资产创建应用服务仍直接编排 Provisioner | 失败时可能出现数据库/Gitea 不一致 | P1 规格需明确建仓 Saga、补偿、幂等和对账 |
| `AUD-015` | Principal ID 语义不一致 | V3 明确 `principalId=prn_`、`userId=usr_`、`agentId=agt_`；`PrincipalContext` 注释和部分测试却把 `usr_` 当 principalId | JWT subject、ACL、角色绑定和审计可能引用错误 ID 类型 | 接受 `TERM-001` 后统一契约、代码和数据 |
| `AUD-016` | 权限常量和数据库 Seed 不一致 | `Permissions.java` 定义并使用 `asset:manage`，V4 的 `iam_permission` Seed 没有该权限 | 资产创建/更新/删除可能在真实 RBAC 下永远被拒绝 | 决定该权限是删除还是正式新增；用契约/迁移测试阻止漂移 |
| `AUD-017` | 内置 READER 权限超出普通用户 Persona | V4 给 READER 授予 user/authorization/audit/job/system 读取权限 | 普通只读用户可能看见管理与诊断数据 | 先确认 Persona 权限矩阵，再通过 V14+ 前向修正 Seed/绑定 |
| `AUD-018` | AI IDE 门禁尚未接入服务端 CI | `.github/workflows/ci.yml` 未调用 `validate-spec.ps1`、Task 范围或完成 Evidence 校验 | 遵循规则的 IDE 可被指导，忽略规则的 IDE/提交仍可绕过 | 完成 `TASK-GOV-008`，把门禁设为受保护分支 required checks |
| `AUD-019` | 当前 CI 与已声明验收规则冲突 | OpenAPI breaking diff 使用 `continue-on-error: true`；前端没有 `test` script/CI 测试；Compose CI 只跑 config | 契约破坏、前端行为错误和 E4 缺口不会阻断合并 | 评审后修改 CI、补前端测试基线和 Compose E4 required checks |
| `AUD-020` | 本地批准字段不能证明真实人工授权 | Task validator 可拒绝明显 AI 名称，但本地文本字段无法提供签名或分支保护身份 | 恶意/不合规客户端可伪造 approvedBy/stageGatePassed | 由受控 PR Review/签名审批生成不可伪造的 approval Evidence |
| `AUD-021` | 数据集目标规格 READY，但产品与机器契约未实现 | `dataset-experience.md`/AC 已覆盖分类、详情、预览、文件、讨论、CLI、AI 读写；OpenAPI `/datasets` 仍为 `partial-p1-frozen`，MCP/CLI/页面未形成 E4 | 只能称“设计闭环”，不能称“ModelScope 类能力已交付” | P0-B VERIFIED 后按 P1→P4 Task/契约/Migration/实现/测试顺序交付 |

## 3. 已有证据能够支持什么

| 能力 | 可支持的结论 | 不能支持的结论 |
| --- | --- | --- |
| 后端单元/Web 测试 | 多个隔离规则和 Controller 行为有 E2 证据 | P0-B 全部运行时旅程完成 |
| 7 个 Testcontainers IT | 部分真实 PostgreSQL 迁移和模块行为可达 E3（仅在未跳过时） | 所有公共模块都有集成覆盖 |
| Compose V01-V11 历史 PASS | 指定 Commit/环境若可复现，则证明脚本实际断言范围 | 脚本没有断言的 JWT/任务/通知/Trace 完整语义 |
| 前端 lint/typecheck/build | 前端代码可静态构建 | 页面业务正确、i18n、权限和交互满足设计 |
| OpenAPI lint | YAML 语法和规则集通过 | 实现与契约一致，或契约完整 |
| AI Task 本地 validator | 合规 IDE 可在编辑前拒绝错误状态/范围/引用，并在交接时拒绝不完整 Evidence | 非合规 IDE 无法绕过服务端检查；当前 CI 尚未接入 |

## 4. 数据集目标对照结论

ModelScope 官方数据集页可观察到 Dataset Card、Preview、Files and versions；官方 Dataset Download
文档支持 SDK、Git、CLI、精确 revision、单/多文件、include/exclude 和 local directory。本规格没有
照搬其公网社区或 Token 用法，而是用平台 JWT、受控 download handle、DVC/MinIO 和审计要求实现
内部等价目标。

| 用户目标 | 规格闭环 | 当前实现/证据 | 判定 |
| --- | --- | --- | --- |
| 分类与受控标签 | REQ-DST-TAX-001、AC-DST-TAX-* | 现有 `/datasets` 仅 partial；无完整 Facet E4 | `READY / NOT_IMPLEMENTED` |
| 介绍、版本与文件 | REQ-DST-DETAIL-001、AC-DST-DETAIL-* | P1/P2 页面和聚合契约未交付 | `READY / NOT_IMPLEMENTED` |
| CSV/JSONL/Parquet 安全预览 | REQ-PRE-001、AC-DST-PRE-* | Preview 仍为后续 Job/API/Page | `READY / NOT_IMPLEMENTED` |
| 交流反馈 | REQ-DST-DISC-001、AC-DST-DISC-* | 无 Discussion 表/API/Page/E4 | `READY / NOT_IMPLEMENTED` |
| API/CLI 搜索与下载 | REQ-DST-CLI-001、AC-DST-CLI-* | 无 `aih` CLI、下载票据纵向证据 | `READY / NOT_IMPLEMENTED` |
| AI Tool 搜索发现并精确下载 | REQ-DST-AI-001、AC-DST-AI-* | MCP 只存在早期只读目标声明；无真实 Client E4 | `READY / NOT_IMPLEMENTED` |
| 人或获授权 AI 创建上传 | REQ-DST-AIW-001、AC-DST-AIW-* | 写 Tool/Upload Worker/人工发布闸门未交付 | `READY / NOT_IMPLEMENTED` |

参考基线：

- <https://www.modelscope.cn/docs/datasets/download>
- <https://modelscope.cn/datasets/Tsumugii24/better-synth-challenge-baseline>

## 5. AI 编程 IDE 可执行性结论

当前通用协议、Task 模板和 validator 已足以让 Trae/Codex/Cursor 等**遵循仓库规则的 IDE**按同一输入
执行：读取 READY Task，检查阶段/基线/范围，建立 AC→Evidence 计划，执行精确测试命令，并在范围
或完成证据不满足时停止。

但这还不是“完全强制”：`AUD-018` 至 `AUD-020` 仍未关闭。尤其是当前 CI 不执行 AI Spec 门禁，
本地批准也没有受控身份背书。因此准确状态为：

```text
AI IDE protocol/specification = READY
local preflight/scope/completion validator = ACTIVE
protected CI enforcement = NOT_IMPLEMENTED
end-to-end IDE compliance = IMPLEMENTED_UNVERIFIED
```

## 6. P0-B 重验前置

1. 回答 `Q-001` 至 `Q-006`；
2. 确认 P0-B 的最终出口范围，尤其是 OTel、通知恢复、管理端和资产安全整改；
3. 为每个出口条件建立稳定需求 ID 和场景 ID；
4. 把 OpenAPI 状态、追踪矩阵和 Runbook 同步到同一 Commit；
5. 为每条需求声明最低证据等级；
6. 运行重验并生成 Evidence Manifest；
7. 只有全部必需需求为 `VERIFIED` 后，才更新 `AGENTS.md` 的当前阶段。

## 7. 本次实际验证快照

| 命令/检查 | 实际结果 | 可证明范围 |
| --- | --- | --- |
| `validate-spec.ps1` | PASS；36 个 manifest 文档、632 个稳定 ID | AI Spec 路径、ID、引用和执行策略静态一致 |
| `validate-task-card.ps1` | PASS | TASK-GOV-007 的 READY/授权/基线/引用/计划/命令合法 |
| `validate-task-card.ps1 -CheckChangedPaths` | PASS | 当前实际变更均在批准范围 |
| `validate-task-card.ps1 -CheckCompletion` | 预期 FAIL；脏工作树、notProven、未独立评审等 5 项 | 阻止把当前规格工作误报成已完成 |
| DRAFT、遗漏计划、旧基线/OPEN REQ/未接受 DEC/过宽范围反例 | 均按预期非零退出 | 本地负向门禁有效 |
| `backend/mvnw.cmd -B verify`（JAVA_HOME 指向 JDK 21） | BUILD SUCCESS；Testcontainers 26 项全部 SKIP | 单元/静态构建可用；不能证明 PostgreSQL E3 |
| 前端 `lint/typecheck/build` | PASS | 静态质量与构建 |
| 前端 `test` | FAIL：不存在 `test` script | 前端行为测试能力未建立 |
| `docker compose --env-file .env.example config --quiet` | PASS | Compose 配置可解析 |
| `deploy/compose/scripts/verify.ps1` | V01 PASS，V02-V11 共 10 项 SKIP | 不能证明任何 E4 业务旅程 |
| Redocly OpenAPI lint | 描述文件有效、1 个 unused component warning；Node 25/Windows libuv assertion 使进程退出 1 | 本机不能给出 CI 等价 PASS；应在 Node 20 CI 重跑 |
| `git diff --check` | PASS | 无 whitespace error |
