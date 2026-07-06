# AI 可执行工作分解与依赖

> 状态：`ACTIVE`
> 注意：这是任务候选目录，不是实施授权。只有关联需求 READY、决策 ACCEPTED 后才能生成正式 Task Card。
> 当前：`DEC-001` 至 `DEC-004`、`DEC-006` 至 `DEC-019` 为生效决策；`DEC-005` 已被 `DEC-008` 替代。
> 所有决策问题（Q-001~Q-305）已闭合。规格包已批准（`manifest.yaml` status=APPROVED, 2026-07-06），DEC-007 代码冻结已解除。
> 后续实施按 DEC-009/DEC-010 的 Task Card 门禁执行，P0-B → P1 → P2 → P3 → P4 → P5 连续推进。

## 1. 切分规则

每个工作包必须：

- 产生一个可观察、可单独验证的结果；
- 同时完成受影响的契约、数据、后端、前端和测试，而非按技术层长期分批；
- 引用明确 Requirement/Invariant/Acceptance/Page/Journey ID；
- 声明允许修改路径和非目标；
- 将 E1-E4/E5 证据分别列出；
- 不依赖“后续任务会补安全、审计、失败路径”；
- 不在阶段出口任务中临时补功能。

建议单任务规模：

- 1 个主要用户动作；
- 1-3 条核心 Requirement；
- 5-12 个 Acceptance Scenario；
- 1 个可回滚的数据库/契约变更集合；
- 能在一次 AI 会话中读完上下文并验证。

## 2. 总体依赖

```text
GOV（决策与基线）
  → P0B-AUDIT（真实性重验与修复）
    → P1（资产目录）
      → P2（版本与数据面）
        → P3（发布治理）
          → P4（Agent 接入）
            → P5（质量与运维）
```

规格编写可前移，实现不能绕过箭头。

## 3. Wave G：规格治理与基线

| Task 候选 | 可观察结果 | 关键依赖 | 最低证据 |
| --- | --- | --- | --- |
| `TASK-GOV-001` | 第一轮范围/策略决策形成 DEC 记录 | Q-001..006 | 决策者确认 |
| `TASK-GOV-002` | 组织/Team/Owner/角色语义形成权限矩阵 | Q-101..106 | 规格一致性检查 |
| `TASK-GOV-003` | 资产/版本/审批/保留策略形成 DEC | Q-201..206 | 规格一致性检查 |
| `TASK-GOV-004` | UI、容量、部署、E2E 工具形成 DEC | Q-301..305 + RPO/RTO | 规格一致性检查 |
| `TASK-GOV-005` | PRD/ADR/AI Spec/AGENTS 的阶段与事实源一致 | GOV-001..004 | E1 文档/链接/状态检查 |
| `TASK-GOV-006` | 每条正式 Requirement 和 AC 进入机器索引 | GOV-005 | Schema/ID/引用 lint |
| `TASK-GOV-007` | AI IDE Task Card 的状态、base Commit、allowed paths、REQ/AC/DEC 和阶段门禁可机器拒绝 | DEC-009 | task-card validator 正反例 |
| `TASK-GOV-008` | AI Spec/Task/范围/完成证据及契约 breaking 门禁接入受保护 CI | TASK-GOV-007、AC-AI-IDE-008 | PR 正反例与 required check 证据 |

## 4. Wave P0B-R：P0-B 重验与确定性修复

### 4.1 契约和身份标识

| Task 候选 | 结果 | 主要领取 | 依赖 |
| --- | --- | --- | --- |
| `TASK-P0BR-000A` | 安装并验证 `pwsh`（PowerShell Core）在 Linux 开发环境和 CI 中可用 | 共识 8、AGENTS.md | 无 |
| `TASK-P0BR-000B` | P0-B 管理页面 MIPS 文档（7 页交互元素+API+权限+i18n 前缀）+ 全局前端规范 | 共识 5、PAGE-ADM-* | 无 |
| `TASK-P0BR-001` | OpenAPI 实现状态与真实证据同步，breaking diff 强制阻断；补契约一致性集成测试（springdoc vs YAML）；每个端点添加 `x-error-codes` 扩展（DEC-019） | AUD-003、AC-P0B-ENG-002/003、共识 6 | GOV-005 |
| `TASK-P0BR-002` | `principalId=prn_`、userId/agentId 语义在 JWT/API/DB/审计统一 | TERM-001、INV-COM-001 | GOV-005 |
| `TASK-P0BR-003` | Permission Catalog、Java 常量、V4+ Seed、OpenAPI 和 UI 无漂移 | TERM-003、AUD-016 | GOV-002 |
| `TASK-P0BR-004` | 内置角色按确认 Persona 最小权限重建：拆分 READER 为 READER+OBSERVER（DEC-018） | AUD-017、personas matrix、DEC-018 | P0BR-003 |

### 4.2 身份与授权纵向旅程

| Task 候选 | 结果 | 主要领取 | 依赖 |
| --- | --- | --- | --- |
| `TASK-P0BR-010` | Bootstrap→首登→改密完整 E4 | AC-P0B-IAM-001..003 | P0BR-002/003 |
| `TASK-P0BR-011` | 登录失败、锁定、解锁/启用状态机 E4 | AC-P0B-IAM-004/005/012 | GOV-002 |
| `TASK-P0BR-012` | 刷新轮换、并发、重放、登出 E4 | AC-P0B-IAM-006/007/009/011 | P0BR-010 |
| `TASK-P0BR-013` | 禁用用户/Agent 后旧 access/refresh/credential 实时失效 | AC-P0B-IAM-008、AGT-003 | P0BR-012 |
| `TASK-P0BR-014` | Agent 注册/凭据/受控 Scope/Tool Catalog E4 | AC-P0B-AGT-001..005 | P0BR-003 |
| `TASK-P0BR-015` | 授权从 Controller 布尔值收敛到可复用 Application Use Case | AUD-010、NFR-MNT-002 | P0BR-003 |
| `TASK-P0BR-016` | 组织/项目/Team 成员和角色绑定成功/越权 E4 | AC-P0B-AUTH-001/002/004/008/009 | GOV-002、P0BR-015 |
| `TASK-P0BR-017` | Scope+RBAC+ACL+资源策略组合及防枚举 SQL 下推 E4 | AC-P0B-AUTH-003/005..007 | P0BR-016 |

### 4.3 治理和运行平台

| Task 候选 | 结果 | 主要领取 | 依赖 |
| --- | --- | --- | --- |
| `TASK-P0BR-020` | 字典/标签启停、作用域和两语言历史回显 E4 | AC-P0B-TAX-001..006 | P0BR-017 |
| `TASK-P0BR-021` | 类型化配置、Secret 拒绝、并发冲突和生效提示 E4 | AC-P0B-CFG-001..003 | P0BR-017 |
| `TASK-P0BR-022` | IdempotencyService 接入首个真实写用例并证明重放/冲突/并发 | AC-P0B-IDM-001..003、AUD-006 | P0BR-010 |
| `TASK-P0BR-023` | 可靠任务用真实幂等 Handler 证明多 Worker/崩溃/恢复 | AC-P0B-JOB-001..003 | P0BR-022 |
| `TASK-P0BR-024` | DEAD、人工重试/取消、通知和审计 E4 | AC-P0B-JOB-004/005 | P0BR-023 |
| `TASK-P0BR-025` | 必审计成功/失败/拒绝覆盖和不可篡改 E4 | AC-P0B-AUD-001/003 | P0BR-015 |
| `TASK-P0BR-026` | HTTP/MCP/Worker/URL/Payload 全边界 Secret 脱敏 E4 | AC-P0B-AUD-002/004/005 | P0BR-025 |
| `TASK-P0BR-027` | 站内通知+Outbox 原子性和读取 E4；含 IdPrefix.EVENT("evt") 修复、publishOutboxEvent() 接入业务流程（DEC-020） | AC-P0B-NOT-001/002、DEC-020 | P0BR-023 |
| `TASK-P0BR-028` | 签名 Webhook SSRF、重试、验签、去重 E4 | AC-P0B-NOT-003..005 | P0BR-027 |
| `TASK-P0BR-029` | REST→Job 完整 Trace、Prometheus、健康和诊断 E4 | AC-P0B-OBS-001..004 | GOV-004、P0BR-023 |
| `TASK-P0BR-030` | P0-B 必需告警在故障注入下触发/恢复 | AC-P0B-OBS-005 | P0BR-024/028/029 |

### 4.4 管理端与出口

| Task 候选 | 结果 | 主要领取 | 依赖 |
| --- | --- | --- | --- |
| `TASK-P0BR-040` | 前端 Auth、Route Catalog、缓存清理和 Token 存储 E4 | AC-P0B-UI-001/002 | P0BR-013/017 |
| `TASK-P0BR-041` | 运行时 zh/en i18n 和统一错误/页面状态 E4；含后端 `messages_zh/en.properties`、前端 `zh.json`/`en.json` 中全部 56 个 error.* key + 2 个前端额外 key（DEC-019）；Error Catalog 文档同步验证 | AC-P0B-UI-003/005、DEC-019 | P0BR-020/040 |
| `TASK-P0BR-042` | 管理表单移除自由 Scope/Tool/Principal/Permission 输入 | AC-P0B-UI-004 | P0BR-014/016/017 |
| `TASK-P0BR-043` | P0-B 管理页面组件测试+浏览器 E2E+视觉证据 | AC-P0B-UI-006、PAGE-ADM-* | P0BR-041/042 |
| `TASK-P0BR-044` | Runbook、V 编号和 AC 覆盖映射同步 | AUD-002/007 | 所有 P0BR 功能任务 |
| `TASK-P0BR-045` | 干净环境执行 70 条 P0-B MUST 场景并生成 Evidence Manifest | p0b-exit-catalog 全部 | P0BR-044 |
| `TASK-P0BR-046` | 仅在无缺口时更新阶段状态并解除 P1 门禁 | source/status policy | P0BR-045 |

`TASK-P0BR-045/046` 不允许新增功能；若发现缺口，退回对应任务。

## 5. Wave P1：可治理资产目录

| Task 候选 | 可观察结果 | 关键依赖/证据 |
| --- | --- | --- |
| `TASK-P1-001` | MODEL/DATASET 字段、Owner、坐标、别名和可见性需求/契约 READY | GOV-002/003、P0BR-046 |
| `TASK-P1-002` | V23+ 资产数据回填：自由标签→受控标签映射、字符串 Owner→Team ID 回填、UNMAPPED 标记与管理端整改页（V22 已用于 P1 Schema 补充，下一可用版本 V23） | E3 空库+升级、共识 7 |
| `TASK-P1-003` | 资产创建意图+Gitea 建仓 Saga，重放和远程不确定结果可恢复 | JRN-P1-001、E4 故障注入 |
| `TASK-P1-004` | 权限过滤的资产搜索/Cursor/防枚举 | JRN-P1-002、10 万数据性能准备 |
| `TASK-P1-005` | 资产详情展示 Card、治理、Owner、来源 Commit、历史停用项和"快速使用"代码片段面板（DEC-014） | PAGE-AST-003、E4 |
| `TASK-P1-006` | 乐观锁更新、重命名/别名和 Gitea/投影一致 | JRN-P1-003、E4 |
| `TASK-P1-007` | 弃用/归档/恢复与引用检查 | JRN-P1-004、E4 |
| `TASK-P1-008` | 资产创建/发现/详情/设置/访问 UI 完整状态与两语言 | PAGE-AST-*、E2/E4 |
| `TASK-P1-009` | 干净 Compose 完成两主体权限差异的资产纵向旅程 | JRN-P1-001..004、E4 |
| `TASK-P1-010` | DATASET 多值分类、受控标签、权限过滤 Facet 和 matchedFields | REQ-DST-TAX-001、AC-DST-TAX-* |
| `TASK-P1-011` | Dataset Card 详情外壳和精确版本导航，不展示后续 Placeholder | REQ-DST-DETAIL-001、AC-DST-DETAIL-* |
| `TASK-P1-012` | Asset Discussion/Comment/Revision/Moderation/Notification 纵向闭环 | REQ-DST-DISC-001、JRN-P1-005、E4 |

## 6. Wave P2：版本与数据面

| Task 候选 | 可观察结果 | 关键证据 |
| --- | --- | --- |
| `TASK-P2-001` | Version/Artifact/Manifest/Upload 状态和摘要算法契约 READY | GOV-003、Domain Specs |
| `TASK-P2-002` | DVC/MinIO Bucket/最小凭据配置与 CLI 往返 | JRN-P2-001、E4 |
| `TASK-P2-003` | Upload Session/Part/签名/限额/过期 | E3/E4 |
| `TASK-P2-004` | 浏览器 Multipart 暂停/刷新/失败 Part 恢复 | JRN-P2-002、PAGE-UPL-* |
| `TASK-P2-005` | Materialization Worker 完成校验→DVC→Git→投影 Saga | 故障注入 E4 |
| `TASK-P2-006` | 下载票据按权限/状态/敏感度签发，过期和日志脱敏；audit_log 中下载授权事件必须可聚合查询（DEC-016 预留 P5 统计数据源） | JRN-P2-003、E4 |
| `TASK-P2-007` | CLI/Web 上传和下载校验的干净 Compose 出口 | P2 全旅程 E4 |
| `TASK-P2-008` | CSV/JSONL/Parquet 最小安全预览 Job、API 和页面；Subset 下拉+Split Tab 选择器（首版单 Subset）+ 统计卡片（DEC-015） | REQ-PRE-001、AC-DST-PRE-* |
| `TASK-P2-009` | `aih dataset search/inspect/pull/create/push/status` 薄 CLI 与稳定退出码 | REQ-DST-CLI-001、AC-DST-CLI-* |
| `TASK-P2-010` | Dataset 详情的 Version/Files/Preview(Subset/Split/Stats)/CLI 纵向旅程 | JRN-P2-004、E4 |

## 7. Wave P3：发布治理

| Task 候选 | 可观察结果 | 关键证据 |
| --- | --- | --- |
| `TASK-P3-001` | 审批角色、策略、版本规则和状态机 READY | Q-203..205 |
| `TASK-P3-002` | validate Job 冻结 Commit 并生成结构化校验报告 | E3/E4 |
| `TASK-P3-003` | submit/reject/resubmit 的四眼与内容漂移保护 | JRN-P3-001 |
| `TASK-P3-004` | publish Saga 创建受保护 Tag 并形成三元组 | NFR-REL-001/002 |
| `TASK-P3-005` | 每个发布步骤的失败/超时/进程退出/补偿 | JRN-P3-002、故障注入 E4 |
| `TASK-P3-006` | 审批中心、版本详情和 Diff UI | PAGE-VER/REV-* |
| `TASK-P3-007` | 弃用/归档/下载/搜索降权 | JRN-P3-003 |
| `TASK-P3-008` | 发布不可变完整 Compose 出口 | E4 |

## 8. Wave P4：Agent 接入

| Task 候选 | 可观察结果 | 关键证据 |
| --- | --- | --- |
| `TASK-P4-001` | MCP Tool/Resource Schema 和错误/分页契约 READY，修复阶段/`write` 标记；tools.yaml 与 McpToolCatalog 同步验证 | AUD-011 |
| `TASK-P4-002` | Streamable HTTP initialize/list/call 与 JWT | 协议 E3/E4 |
| `TASK-P4-003` | 只读搜索→精确版本→下载票据复用 REST Application Service | JRN-P4-001/002 |
| `TASK-P4-004` | Tool Allowlist+Scope+Permission+资源策略拒绝 | JRN-P4-003 |
| `TASK-P4-005` | 裁剪 OpenAPI Agent API 复用同一内核 | JRN-P4-004 |
| `TASK-P4-006` | OpenClaw/QwenPaw Skill、Secret Store、版本兼容探测 | 锁定客户端 E4 |
| `TASK-P4-007` | 30 分钟接入和无凭据泄漏出口 | NFR-PERF-004、E4 |
| `TASK-P4-008` | asset_search 分类/Facet/matchedFields 与 downloadHandle→可信 CLI 数据通道 | REQ-DST-AI-001、AC-DST-AI-* |
| `TASK-P4-009` | 写 Tool Catalog、contribution OpenAPI Profile 和显式授权 Agent | REQ-DST-AIW-001、REQ-MCP-005 |
| `TASK-P4-010` | AI 创建草稿→上传→Worker→状态→人工审核的完整旅程 | JRN-P4-005、AC-DST-AIW-*、E4 |

## 9. Wave P5：质量与运维

| Task 候选 | 可观察结果 | 关键证据 |
| --- | --- | --- |
| `TASK-P5-001` | Gitea Webhook Inbox 和所有 Reconciler | JRN-P5-001 |
| `TASK-P5-002` | 在 P2 最小预览上扩展格式、资源隔离、压力和安全 E5 | REQ-PRE-001、AC-DST-PRE-* |
| `TASK-P5-003` | PostgreSQL/Gitea/MinIO/Secret 备份恢复 | JRN-P5-002、E5 |
| `TASK-P5-004` | 目标规模性能基线和回归阈值 | JRN-P5-003、NFR-PERF-* |
| `TASK-P5-005` | 上传/SSRF/凭据/Prompt 注入安全测试 | JRN-P5-004、E5 |
| `TASK-P5-006` | 告警、Dashboard、容量与运维 Runbook | E5 |
| `TASK-P5-007` | P0-P5 全量追踪无缺口的发布候选审计 | Completion Audit |
| `TASK-P5-008` | 下载量/热度统计面板：基于 audit_log 下载授权事件聚合查询，模型/数据集卡片展示下载量（DEC-016） | REQ-OBS-001、E5 |

## 10. 正式 Task Card 生成规则

从候选任务生成 `docs/ai-spec/tasks/TASK-*.md` 时：

1. 冻结 base Commit；
2. 将所有“主要领取”展开为精确 REQ/AC/INV/PAGE ID；
3. 填满横切要求，N/A 必须有理由；
4. 列出具体允许路径，不使用整个仓库作为默认范围；
5. 指定 Migration 文件名下界，不预先占用已存在版本；
6. 指定每条验证命令、工作目录和预期报告；
7. 生成初始 Evidence Manifest；
8. 由产品/架构责任人把 Task 从 DRAFT 变为 READY；
9. AI 才能开始产品代码修改。
10. 运行 `tools/validate-task-card.ps1`（需 `pwsh`）；任何失败都使 Task 保持 DRAFT/不可实施。
11. Task Card 的 `taskId` 必须在本文件的任务候选表中注册；未注册的 ID 被 validator 拒绝。
