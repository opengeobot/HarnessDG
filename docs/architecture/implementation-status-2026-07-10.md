# 实现状态快照（2026-07-10）——P1–P5 部分落地

> 本文记录在 `implementation-status-2026-07-02.md`（P0-B VERIFIED）之后，P1–P5 业务波次的部分落地状态。
> 不回写更早快照。作者：AxeXie
> 基线 Commit：`2a8069252224c829c8cbed86070f38d1be07de6f`（及后续 gap-closure 提交）

## 1. 阶段总览

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| P0-A | VERIFIED | 工程与部署骨架 |
| P0-B | VERIFIED | 公共平台底座；Compose V01–V21 PASS |
| P1 资产目录 | PARTIAL / IMPLEMENTED_UNVERIFIED | CRUD/搜索/Facet/Discussion/坐标/归档守卫已落地；Owner 字符串列表、资产 ACL 页、rename Saga、E4 证据未闭环 |
| P2 版本与数据面 | PARTIAL / IMPLEMENTED_UNVERIFIED | Version/Upload/Download/Preview API 与 complete→UPLOAD_MATERIALIZE 已接线；真实 multipart、DVC/Git 物化、Manifest 写 Gitea、STS 凭据未闭环 |
| P3 发布治理 | PARTIAL / IMPLEMENTED_UNVERIFIED | 四眼审批、冻结 Commit、Gitea Tag（enabled 时）已落地；多审批人策略、Diff UI、Saga 故障注入 E4 未闭环 |
| P4 Agent 接入 | PARTIAL / IMPLEMENTED_UNVERIFIED | MCP initialize/list/call、catalog 同步、写工具门控已落地；REST `/agent/*`、写幂等、30 分钟接入 E4 未闭环 |
| P5 质量与运维 | PARTIAL / IMPLEMENTED_UNVERIFIED | 对账增强、下载统计 API、runbook/备份脚本已落地；OpenAPI stats、in-app DEAD_JOB、E5 演练未闭环 |

**不得将 PARTIAL / IMPLEMENTED_UNVERIFIED 表述为阶段 VERIFIED。** 关键 P1–P5 Evidence Manifest 已更新为 `PARTIAL`（E2/E3 单元测试已证；Compose E4 仍 `notProven`，需人工验收后才能 `-CheckCompletion`）。

## 2. Flyway 迁移映射（V12–V28）

| 版本 | 文件 | 波次语义 |
| --- | --- | --- |
| V12–V14 | asset governance / remediation | P1 基础整改 |
| V15 | discussion（`asset_discussion` / `asset_comment`） | P1 Discussion |
| V16 | version / transfer / preview | P2 Schema |
| V17 | release governance | P3 Schema |
| V18 | webhook_inbox | P5 Inbox |
| V19 | team | P1 Owner/Team |
| V20 | permission seed + idempotency | P0-B/P1 |
| V21 | alerts | P5 |
| V22 | asset P1 remediation（owner_team_id 等） | P1 |
| V23–V26 | observer / deprecation / publish changes | P1/P3 |
| V27 | `publish_request_frozen_commit` | **P3**（非 P1 回填） |
| V28 | `reconciliation_checkpoint` | P5 |
| V29+ | 预留给 P1 Owner/tag 回填与后续差距闭合 | 下一可用 |

> 历史 TASK-P1-002 曾预留 `V27__asset_backfill.sql`；该编号已被 P3 占用，回填下限改为 **V29+**。

## 3. 已关闭的关键差距（相对 2026-07-02）

- `aih://{namespace}/{type}/{name}` 坐标字段（AssetView/Summary/OpenAPI）
- 搜索 `matchedFields`；归档前 PUBLISHED 版本守卫；Quick Use 面板
- Gitea 仓库存在性对账 + 重建仓入队
- Upload complete → PROCESSING → `UPLOAD_MATERIALIZE` 入队；Manifest-v1 结构；GIT_DVC 票据 enrichment；Preview generate API
- Publish Saga 真实 Gitea Tag（`aihub.gitea.enabled=true`）；四眼与 Commit 漂移拒绝
- MCP catalog↔tools.yaml 同步测试；Agent allowlist；写工具默认隐藏
- 下载统计 API（`/assets/{id}/stats`、`/system/metrics/downloads`）；对账/Webhook 硬化；ops/backup/security runbook

## 4. 仍开放的差距（见 gap-closure 波次 1–7）

详见计划：文档/契约卫生、P1 Owner/ACL/rename/Facet UX/mention、P2 multipart/DVC/Git/STS/Parquet/CLI、P3 Diff/多审批/Saga E4、P4 REST Agent/幂等/onboarding、P5 DEAD_JOB/E5、Evidence PASS。

## 5. 验证快照（本文件日期）

| 检查 | 结果 |
| --- | --- |
| 定向后端回归（Asset/Upload/Download/Publish/Mcp/Reconciler 等） | 121 PASS（2026-07-10 会话） |
| 前端 `src/features/assets/` | 19 PASS |
| 全量 `./mvnw verify` / Compose `verify.sh` 行为级 P1–P5 | verify.sh 已扩至 V23–V28（默认拒绝 + DEPRECATED 降权源码断言）；全栈 E4 旅程仍需 `docker compose up` 后补验 |
| Task Card `-CheckCompletion` | 未通过（Evidence `PARTIAL`、E4 `notProven`、Task baseCommit 冻结于起草时 HEAD） |

因此：**构建与定向测试通过 ≠ P1–P5 阶段验收完成。**

## 6. W1–W7 gap closure progress

| 波次 | Commit | 摘要 |
| --- | --- | --- |
| W1 | `46a3837` | docs+contract：刷新实现状态、修复 V29 backfill 槽位、启用 Gitea |
| W2 | `057c63c` | P1：Owner/Team 治理、资产 ACL、rename Saga、Facet UX、mention、noop gate |
| W3 | `68560da` | P2：真实 MinIO multipart、上传持久化、DVC/Git 物化、manifest fixtures、幂等、scoped DVC creds、parquet preview、aih CLI |
| W4 | `72de8a4` | P3：发布治理 reconciler、quorum、review UI 深化 |
| W5 | `9e08b3a` | P4：Agent REST 适配器、MCP 幂等、batch latestPublished、auth alias、onboarding |
| W6 | `2482eb1` | P5：DEAD_JOB 告警、preview limits、i18n badge、E5 drill stubs |
| W7 | `811390f` | evidence 卫生：P0BR REQ 修复、verify.sh V23–V28、EVD PARTIAL 诚实更新、CheckCompletion 尝试 |

W7 将关键 EVD 从纯 DRAFT 推进至 `PARTIAL`（E2/E3 单元测试已证、E4 Compose 仍 `notProven`）。`validate-task-card -CheckCompletion` 预期仍失败直至人工验收与 Compose E4 补验。
