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
| P4 Agent 接入 | PARTIAL / IMPLEMENTED_UNVERIFIED | MCP + REST `/agent/*` 只读/贡献写、幂等、限流、onboarding E4 脚本已落地；Compose verify.sh 26/28 PASS（V05 JWT/V20 因持久化 DB 凭据漂移 FAIL）；行为级 E4 旅程仍 `notProven` |
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
| V29 | `asset_relation`（血缘 lineage） | **D** |
| V30 | version/artifact 唯一约束 | **D** |
| V31 | _跳过_（`principal_id` 已由 V20 添加） | **D** |
| V32 | `owner_team_id` NOT NULL 回填 | **D** |
| V33 | `iam_token.jti_digest` | **D** |
| V34 | `iam_token` PAT 类型 + name | **F** |
| V35+ | 预留给后续差距闭合 | 下一可用 |

> 历史 TASK-P1-002 曾预留 `V27__asset_backfill.sql`；该编号已被 P3 占用。Wave D 占用 V29–V33，Wave F 占用 V34。

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
| W7 | `811390f` / `4642e33` | evidence 卫生：P0BR REQ 修复、verify.sh V23–V28、EVD PARTIAL 诚实更新、CheckCompletion 尝试 |
| A | _pending_ | 授权断链修复：EffectiveScopeResolver 合并角色绑定 scopes 进 JWT，管理员登录后菜单可见 |
| B | _pending_ | 架构分层 Wave B：Preview/VersionQuery/transition 入队/DvcStoragePort/DownloadStatsRepo/ArchUnit 强化 |
| C | `9526632` | 前端 Wave C：4 个 admin 页面 + 幂等只读 API、资产表单受控字典字段、讨论 moderation、导航/路由 |
| D | _this commit_ | 数据模型与安全 Wave D：asset_relation 血缘、版本约束、owner_team NOT NULL、JWT 轮换、jti digest、Swagger 门控、DVC STS/scoped 凭据 |
| E | _this commit_ | 通知 Wave E：VERSION_* fan-out、讨论订阅/DISCUSSION_REPLIED、配额/依赖告警、渠道接口桩 |
| F | _this commit_ | 业务 Wave F：PAT/agent-bundle、上传恢复、aih push/resume/verify、CreateAssetPage |
| G | _this commit_ | CI Wave G：verify.sh/verify-schema 入 CI、gitleaks、MCP 契约校验、Task Card 门禁、用例对照 runbook、perf-smoke 可选 |
| H | `cbdfe12` | Agent Wave H：REST 贡献写端点、主体级限流、P4 onboarding E4 脚本 |
| I | `4530361` | 验收 Wave I：Compose E4 尽力执行、EVD E2/E3 PASS 翻转、CheckCompletion 尝试、V25/outbox 验收修复 |

W7 将关键 EVD 从纯 DRAFT 推进至 `PARTIAL`（E2/E3 单元测试已证、E4 Compose 仍 `notProven`）。`validate-task-card -CheckCompletion` 预期仍失败直至人工验收与 Compose E4 补验。

### Wave H 摘要（H1–H3，PRD §11.2）

| 项 | 状态 | 说明 |
| --- | --- | --- |
| H1 REST 贡献写适配器 | IMPLEMENTED_UNVERIFIED | `POST .../versions/draft`、`.../upload-sessions`、`.../complete`、`GET .../upload-sessions/{id}`；JWT + 工具白名单 + 写工具门控 + 幂等；OpenAPI `implemented` |
| H2 Agent/MCP 限流 | IMPLEMENTED_UNVERIFIED | `RateLimiter` 令牌桶（`aihub.agent.rate-limit.*`）；`McpController` + Agent 写端点；429 `RATE_LIMIT_EXCEEDED` + 审计 |
| H3 Agent onboarding E4 | IMPLEMENTED_UNVERIFIED | `p4-onboarding.sh` 全链路断言 + 证据脱敏扫描；预期 ≤30 min |

### Wave G 摘要（G1–G6，PRD §15.10）

| 项 | 状态 | 说明 |
| --- | --- | --- |
| G1 verify.sh 入 CI | IMPLEMENTED_UNVERIFIED | `verify-schema` 强制（Postgres+Flyway+V04/V28 子集）；`compose-e2e` 全量 verify.sh（main 强制、PR 可选失败） |
| G2 Secret 扫描 | IMPLEMENTED_UNVERIFIED | gitleaks-action + `.gitleaks.toml` 排除 `.env.example` 占位 |
| G3 MCP schema diff | IMPLEMENTED_UNVERIFIED | `validate-tools.sh` + `McpToolCatalogContractTest` 显式 CI 作业 |
| G4 validate-task-card | IMPLEMENTED_UNVERIFIED | 变更 `TASK-*.md` 时 `-CheckChangedPaths` |
| G5 用例编号对照 | VERIFIED | `docs/runbooks/verify-case-mapping.md`（仓库 V01–V28 ↔ PRD §13.2） |
| G6 性能冒烟 | PARTIAL | `perf-smoke.sh`（100 并发 P95≤500ms）；`workflow_dispatch`/main 可选作业 |

### Wave F 摘要（F1–F5）

| 项 | 状态 | 说明 |
| --- | --- | --- |
| F1 血缘页面 | VERIFIED (D1) | `AssetLineagePage` + `/assets/:id/lineage` + 方向切换 + 详情页链接已由 Wave D 完成，本波次跳过 |
| F2 PAT + agent-bundle | IMPLEMENTED_UNVERIFIED | V34 + `POST/GET/DELETE /tokens` + `GET /integrations/agent-bundle` + AccessPage 实装 |
| F3 上传会话恢复 | IMPLEMENTED_UNVERIFIED | `GET /assets/{id}/upload-sessions/{sessionId}` 含 parts + UploadPage `?sessionId=` 恢复 |
| F4 aih CLI push/resume/verify | IMPLEMENTED_UNVERIFIED | `scripts/aih/aih` 三命令对齐 OpenAPI `/api/v1` |
| F5 新建资产页 | IMPLEMENTED_UNVERIFIED | `CreateAssetPage` `/assets/new` + 导航 + 弹窗保留 |

### Wave E 摘要（E1–E4）

| 项 | 状态 | 说明 |
| --- | --- | --- |
| E1 VERSION_* in-app fan-out | IMPLEMENTED_UNVERIFIED | 发布/版本服务同步创建 notification 行；评审人/提交人/Owner/订阅者 |
| E2 DISCUSSION_REPLIED + subscription | IMPLEMENTED_UNVERIFIED | `asset_subscription` JDBC 仓储 + subscribe API + 订阅者 fan-out |
| E3 STORAGE_QUOTA / DEPENDENCY_UNHEALTHY | IMPLEMENTED_UNVERIFIED | MinIO reconciler 配额检测 + SystemDependencyService 定时 DOWN 监测 |
| E4 Email/IM/Webhook 渠道 | PARTIAL | `NotificationChannel` 接口 + Email/IM 桩 + Webhook 适配器 + runbook |

### Wave D 摘要（D1–D8）

| 项 | 状态 | 说明 |
| --- | --- | --- |
| D1 asset_relation + lineage API/UI | IMPLEMENTED_UNVERIFIED | V29 + BFS 查询 + `/assets/:id/lineage` |
| D2 version constraints | IMPLEMENTED_UNVERIFIED | V30 partial index + artifact path unique |
| D3 idempotency principal_id | VERIFIED (V20) | 无 V31；JdbcIdempotencyStore 已使用 |
| D4 owner/tag governance | IMPLEMENTED_UNVERIFIED | V32 NOT NULL；更新拒绝 free-form owners/tags |
| D5 JWT key rotation | IMPLEMENTED_UNVERIFIED | `previous-public-key-pem` + runbook |
| D6 jti digest | IMPLEMENTED_UNVERIFIED | V33 + 新 token 存 digest，jti 回退查询 |
| D7 Swagger gating | IMPLEMENTED_UNVERIFIED | compose/prod 需认证 |
| D8 DVC STS | PARTIAL | STS 尝试 + scoped 回退 + runbook；Compose MinIO IAM 未配 |

## 7. Validation snapshot (I) — 2026-07-11

| 检查 | 结果 |
| --- | --- |
| `validate-spec.ps1` | **PASS**（123 manifest docs, 695 stable IDs） |
| `validate-task-card -TaskPath TASK-P1-001.md` | **FAIL**（baseCommit 不匹配 HEAD `4530361`） |
| `validate-task-card -CheckCompletion` | **FAIL**（420 issues：allowedPaths 外变更、Evidence PARTIAL/E4 notProven、review.accepted 未人工验收） |
| 定向后端回归 `*Asset*,*Auth*,*Mcp*,*Agent*,*Publish*,*Notification*` | **191 PASS / 1 FAIL / 23 SKIP**（`AuthenticationApplicationServiceTest.refreshReplayRecordsDeniedEvent` mock 断言） |
| 前端 `pnpm test` | **137 PASS / 14 FAIL**（`AssetDetailPage` 缺 `PermissionProvider` 包裹） |
| `verify-schema.sh` | **PASS**（V04/V16/V17/V21/V22/V28 全通过；33 迁移） |
| Compose `verify.sh` 全量 E4 | **26/28 PASS**；**V05 JWT 生命周期 FAIL**（持久化 DB admin 口令≠`.env` 默认）；**V20 对账 Worker FAIL**（依赖 V05 token）；V18/V22 经 Wave I 修复后 PASS |
| Compose E4 行为旅程（P1–P5 AC） | **notProven** — 无 JWT 的 authenticated 端到端旅程未闭环 |

### Wave I 摘要（I1–I4）

| 项 | 状态 | 说明 |
| --- | --- | --- |
| I1 Compose E4 | PARTIAL | Docker 可用；`docker compose up -d --build` 成功；V25 迁移列名修复后 backend healthy；verify.sh 26/28 |
| I2 EVD PASS 翻转 | PARTIAL | 10 个关键 EVD 更新 E3 proven + E4 notProven；`commitSha=4530361`；`review.accepted=false` |
| I3 CheckCompletion | FAIL（预期） | spec PASS；task baseCommit 不匹配；completion 420 issues |
| I4 状态快照 | DONE | 本文件 Wave H/I 行 + Validation snapshot (I) |

### 剩余 E4 差距（诚实）

- P1–P5 全量 authenticated Compose 旅程（创建→上传→发布→Agent 调用）需重置 admin 凭据或刷新 DB 后重跑 V05
- 前端 `AssetDetailPage` 测试需 `PermissionProvider` 测试包裹
- `AuthenticationApplicationServiceTest.refreshReplayRecordsDeniedEvent` 后端回归 1 FAIL
- P4 `p4-onboarding.sh` 全链路 E4 未在本会话执行（依赖有效 admin JWT）
- E5 SAST/dependency/image 扫描仍 `notProven`
