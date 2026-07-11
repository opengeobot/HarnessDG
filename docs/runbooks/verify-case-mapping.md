# verify.sh 用例编号与 PRD §13.2 对照

> 功能：映射仓库 `deploy/compose/scripts/verify.sh`（及 `verify-schema.sh` 子集）的 V01–V28 编号与
> `prd/DEEP_RESEARCH_内部AI资产管理平台设计.md` §13.2 验收用例，标明语义差异与 CI 覆盖状态。
> 时间：2026-07-11
> 作者：AxeXie

## 背景

仓库 `verify.sh` 在 P0-B 闭环节点后按**平台底座 + 渐进 Schema/行为断言**组织用例；PRD §13.2 按**端到端业务旅程**
（创建模型、DVC 往返、发布、MCP 消费等）组织用例。**编号相同但语义不同**——不得将仓库 V05 等同于 PRD V05。

权威 CI 门禁以仓库脚本为准；PRD §13.2 为产品级 E4/E5 目标清单，全量对齐需 Compose 全栈 + 人工旅程补验。

## 对照总表

| 仓库编号 | 仓库 verify.sh 语义 | PRD §13.2 编号 | PRD 语义 | 对齐状态 |
| --- | --- | --- | --- | --- |
| V01 | Compose 配置合法 (`docker compose config --quiet`) | V01 | Compose 配置 | **一致** |
| V02 | 核心服务健康（postgres/minio/gitea/backend 容器） | V02 | 服务健康 + Health API | **部分**：仓库查容器状态，PRD 含 Health API 全链路 |
| V03 | MinIO 四 Bucket 存在 | V03 | Bucket 初始化且非匿名 | **部分**：仓库未断言匿名策略 |
| V04 | Flyway 迁移完整性 + 关键表存在 | V04 | Gitea 初始化（组织/服务账号/Webhook） | **编号冲突**：仓库 V04=DB，PRD V04=Gitea |
| V05 | JWT 生命周期（登录/me/匿名 401） | V05 | 创建模型 + Gitea 仓库 | **编号冲突**：仓库 V05=JWT，PRD V05=资产创建 |
| V06 | 权限过滤（审计/指标默认拒绝） | V06 | DVC 往返 push/pull | **编号冲突** |
| V07 | 字典/标签受控访问（默认拒绝） | V07 | Webhook 索引（Push 后 10s 可查版本） | **编号冲突** |
| V08 | 审计脱敏 + 登录审计事件 | V08 | 版本发布（validate/submit/approve） | **编号冲突** |
| V09 | 持久化任务端点默认拒绝 | V09 | 不可变性（覆盖 Tag 被拒） | **编号冲突** |
| V10 | 通知端点默认拒绝 | V10 | 搜索与权限（多主体可见性） | **编号冲突** |
| V11 | 观测（actuator/dependencies 默认拒绝） | V11 | 下载票据预签名 URL | **编号冲突** |
| V12 | 资产表 + Gitea 仓库一致性 Schema/断言 | V12 | MCP 只读 initialize/list/call | **编号冲突** |
| V13 | 幂等键/ job_id 唯一约束 Schema | V13 | MCP 越权发布 403 | **编号冲突** |
| V14 | 多组织 ACL 字段 + 匿名搜索 401 | V14 | 幂等写入重放 | **编号冲突**（主题相近，编号与深度不同） |
| V15 | 数据集 Facet visibility + 匿名 401 | V15 | Worker 故障恢复 | **编号冲突** |
| V16 | 版本/上传表 Schema | V16 | 对账修复（丢失 Webhook） | **编号冲突** |
| V17 | 发布审批表 Schema | V17 | Principal 与权限（用户/Agent/服务 Token） | **编号冲突** |
| V18 | Webhook Inbox 表 + 端点探测 | V18 | 字典与配置运营变更 | **编号冲突** |
| V19 | MCP `/api/v1/mcp` initialize 可用 | V19 | 可靠通知渠道恢复 | **编号冲突** |
| V20 | 对账 Worker job_task 类型/端点 | V20 | Trace 贯通 REST/MCP | **编号冲突** |
| V21 | 告警表 Schema + 端点默认拒绝 | V21 | AI Coding 门禁（契约/ArchUnit/CI） | **编号冲突**（PRD V21 由 Wave G CI 承接） |
| V22 | Outbox 事件表 Schema | V22 | 本地登录与 JWT 全生命周期 | **编号冲突**（与仓库 V05 部分重叠） |
| V23 | 下载统计 API 默认拒绝 | V23 | 字典与受控标签治理写路径 | **编号冲突** |
| V24 | 预览生成 API 默认拒绝 | V24 | 结构化日志与审计 100% | **编号冲突** |
| V25 | Agent 令牌端点存在性 | V25 | 数据集详情 Card/Preview/Files 同版本 | **编号冲突** |
| V26 | MCP `/mcp` 根路径默认拒绝 | V26 | 数据集 CLI search/pull/verify | **编号冲突** |
| V27 | 资产 ACL API 默认拒绝 | V27 | AI 数据消费（MCP + CLI 下载） | **编号冲突** |
| V28 | DEPRECATED 搜索降权（AssetSearchDao 源码断言） | V28 | AI 数据贡献（Agent 草稿上传 + 发布闸门） | **编号冲突** |

## PRD §13.2 用例在仓库中的近似覆盖

| PRD 编号 | PRD 语义 | 仓库中的近似证据 | 缺口 |
| --- | --- | --- | --- |
| V04 | Gitea 初始化 | V02 健康 + V12 Gitea API/仓库名 | 无组织/服务账号/Webhook 专项断言 |
| V05 | 创建模型 | 后端 Asset IT、V12 Schema | 无 verify.sh 行为级 POST /assets |
| V06 | DVC 往返 | P2 单测/IT、`docs/runbooks` DVC 手册 | 无 CI DVC 端到端 |
| V07 | Webhook 索引 | V18 Inbox Schema | 无 Push 后 10s 轮询 |
| V08 | 版本发布 | V17 Schema、Publish 单测 | 无四眼 E2E |
| V09 | 不可变性 | Publish 单测 | 无 Tag 覆盖拒绝 E2E |
| V10 | 搜索权限 | V14 匿名 401、授权单测 | 无多主体可见性 E2E |
| V11 | 下载票据 | Download 单测 | 无预签名过期 E2E |
| V12 | MCP 只读 | V19 端点、McpToolCatalogContractTest | 无 list/call 旅程 |
| V13 | MCP 越权 | McpControllerTest | 无审计 E2E |
| V14 | 幂等写入 | V13 Schema、Idempotency 单测 | 无重放 E2E |
| V15 | Worker 恢复 | job 单测 | 无故障注入 |
| V16 | 对账修复 | V20、Reconciler 单测 | 无 Webhook 丢失模拟 |
| V17 | Principal/权限 | V05/V06 JWT+默认拒绝 | 无 Agent/服务 Token 矩阵 |
| V18 | 字典配置运营 | V07 默认拒绝、admin 页面 | 无变更审计 E2E |
| V19 | 可靠通知 | Notification 单测 | 无渠道故障恢复 E2E |
| V20 | Trace 贯通 | Observability 配置 | 无跨 REST/MCP Trace 断言 |
| V21 | AI Coding 门禁 | **Wave G CI**（本对照表 + ci.yml 门禁） | ArchUnit/依赖漏洞仍为局部 |
| V22 | JWT 全生命周期 | V05 子集 | 无刷新轮换/禁用/重放全套 |
| V23 | 标签治理写 | V07、资产表单受控字段 | 无越权维护 E2E |
| V24 | 日志审计 100% | V08 脱敏子集 | 无全事件覆盖抽样 |
| V25 | 数据集详情 | 前端 assets 测试 | 无 Card/Preview 同版本 E2E |
| V26 | 数据集 CLI | `scripts/aih/aih` | 无 CI CLI 旅程 |
| V27 | AI 数据消费 | MCP 单测 | 无可信 CLI 下载 E2E |
| V28 | AI 数据贡献 | Upload/PAT 部分落地 | 无 Agent 草稿+发布拒绝 E2E |

## CI 覆盖（Wave G）

| 作业 | 覆盖仓库用例 | 说明 |
| --- | --- | --- |
| `compose`（既有） | V01 | `docker compose config --quiet` |
| `verify-schema`（G1） | V04、V16、V17、V21、V22、V28 | Postgres 服务容器 + Flyway 迁移后执行 `verify-schema.sh` |
| `compose-e2e`（G1，可选） | V01–V28 全量 | `docker compose up -d --build --wait` + `verify.sh`；PR 上 `continue-on-error` |
| `journey-e2e`（M1） | **PRD §13.2** V05–V28 行为旅程子集 | `needs: compose-e2e` 后执行 `verify-journey.sh`；main push 强制、PR `continue-on-error` |
| `backend` mvnw verify | McpToolCatalogContractTest 等 | 单元/集成，非 Compose 行为级 |
| `mcp-contract`（G3） | V19 契约侧 | `validate-tools.sh` + 显式契约测试 |
| `secret-scan`（G2） | PRD §15.10 Secret 扫描 | gitleaks |
| `task-card-gate`（G4） | PRD §15.10 Task Card | 变更 TASK-*.md 时 validate-task-card |
| `perf-smoke`（G6，可选） | NFR 搜索 P95 | 100 并发，500ms 阈值 |

## 建议

1. **短期**：以仓库编号为 CI/Runbook 权威；引用 PRD 时注明「PRD Vxx」避免与仓库 Vxx 混淆。
2. ~~**中期**：为 PRD 高价值旅程增加 `verify-journey.sh`~~ → **Wave M 已落地**（见下节）。
3. **长期**：ADR 或 TASK 决定是否将 verify.sh 编号向 PRD §13.2 收敛；收敛前本对照表保持更新。

## Wave M：`verify-journey.sh`（PRD §13.2 语义）

> 编号 **PRD-Vxx** 表示 `prd/DEEP_RESEARCH_内部AI资产管理平台设计.md` §13.2 用例；**不得**与仓库 `verify.sh` 的 Vxx 混淆。

| PRD 编号 | PRD 语义 | `verify-journey.sh` 函数 | 通过标准 | 仓库 verify.sh 近似 |
| --- | --- | --- | --- | --- |
| V05 | 创建模型 | `journey_v05_create_model` | POST `/api/v1/assets` 返回 assetId + provisioningStatus | 仓库 V05=JWT（无行为级创建） |
| V06 | DVC 往返 | `journey_v06_dvc_roundtrip` | dvc add/push/pull SHA-256 一致；无 CLI/仓库则 SKIP | 无 |
| V07 | Webhook 索引 | `journey_v07_webhook_index` | `webhook_inbox`/`webhook_delivery` 存在且有投递记录 | 仓库 V18=Inbox Schema |
| V08 | 版本发布 | `journey_v08_publish_triplet` | submit/approve 后 gitTag+sourceCommit+manifestDigest 非空 | 仓库 V17=Schema |
| V09 | 不可变性 | `journey_v09_tag_immutable` | 覆盖 Tag 被拒 | 无 E2E |
| V10 | 搜索权限 | `journey_v10_two_principal_search` | 两主体可见性不同；仅 admin 则 SKIP | 仓库 V14=匿名 401 |
| V11 | 下载票据 | `journey_v11_download_ttl` | `expiresAt` 在未来 | 仓库 V23=默认拒绝 |
| V12 | MCP 只读 | `journey_v12_mcp_list_call` | `/mcp` initialize + tools/list + asset_search | 仓库 V19=端点探测 |
| V13 | MCP 越权 | `journey_v13_mcp_publish_deny` | `asset_publish_version` 默认拒绝 | 仓库 V26=匿名 401 |
| V14 | 幂等写入 | `journey_v14_idempotency_replay` | 同 Idempotency-Key 重放同 assetId | 仓库 V13=Schema |
| V15 | Worker 恢复 | `journey_v15_worker_recovery` | REPOSITORY_PROVISION + UPLOAD_MATERIALIZE handler 源码注册（轻量） | 仓库 V09/V20 |
| V16 | 对账修复 | `journey_v16_webhook_reconcile` | `reconciliation_checkpoint` 表存在 | 仓库 V20 |
| V17 | Principal/权限 | `journey_v17_token_matrix` | 用户 JWT `/me`；Agent 端点存在性 | 仓库 V05/V25 |
| V19 | 可靠通知 | `journey_v19_notification_recovery` | `notification` 或 outbox 有处理行 | 仓库 V10/V22 |
| V25 | 数据集详情 | `journey_v25_dataset_detail` | DATASET 创建 + GET 详情含 profile | 无 E2E |
| V26 | 数据集 CLI | `journey_v26_cli_search` | `aih dataset search --output json` | 无 |
| V27 | AI 数据消费 | `journey_v27_ai_consume` | MCP DATASET asset_search | 无 |
| V28 | AI 数据贡献 | `journey_v28_ai_contribute` | 写工具默认拒绝 | 无 |

**运行方式**

```bash
cd deploy/compose
docker compose up -d --wait   # 若栈未启动
./scripts/verify-journey.sh
```

PowerShell 轻量子集（V05/V08/V12/V14）：`./scripts/verify-journey.ps1`

**CI**：`.github/workflows/ci.yml` 作业 `journey-e2e`（`needs: compose-e2e`）。

## 相关文件

- `deploy/compose/scripts/verify.sh` — 全量 Compose 验收（仓库 V01–V28）
- `deploy/compose/scripts/verify-journey.sh` — PRD §13.2 行为旅程（PRD-V05..V28）
- `deploy/compose/scripts/verify-journey.ps1` — 行为旅程 PowerShell 子集
- `deploy/compose/scripts/verify-schema.sh` — CI 轻量 Schema 子集
- `deploy/compose/scripts/perf-smoke.sh` — 搜索 P95 冒烟（G6）
- `.github/workflows/ci.yml` — Wave G 门禁作业
- `prd/DEEP_RESEARCH_内部AI资产管理平台设计.md` §13.2、§15.10
