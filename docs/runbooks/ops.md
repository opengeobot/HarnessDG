# Operations Runbook

> 功能: P5 运维 Dashboard、告警与故障演练指针（TASK-P5-006）。
> 时间: 2026-07-10
> 作者: AxeXie

## 验证入口

P1-P5 功能验收以 Compose 全栈为基线:

```bash
cd deploy/compose
docker compose config --quiet
docker compose up -d
./deploy/compose/scripts/verify.sh
```

| 阶段 | 关键用例 | 说明 |
| --- | --- | --- |
| P0-B | V04-V11, V21 | 底座、审计脱敏、告警端点 |
| P1-P4 | V12-V20 | 资产、版本、Webhook、MCP、对账 Worker |
| P5 | V21 + 对账/备份 | DEAD Job、Webhook 积压、一致性修复 |

完整 Compose 指南见 [compose.md](./compose.md)。备份恢复见 [backup-restore.md](./backup-restore.md)。

## Dashboard 与指标

| 端点 | 权限 | 用途 |
| --- | --- | --- |
| `GET /api/v1/system/metrics/summary` | `system:observe` | JVM、任务状态（含 **dead**）、依赖健康 |
| `GET /api/v1/system/metrics/downloads` | `system:observe` | 下载热度排行（DEC-016） |
| `GET /api/v1/system/alerts` | `system:observe` | 平台告警历史 |
| `GET /api/v1/system/alerts/firing` | `system:observe` | 当前 FIRING 告警 |
| `GET /api/v1/system/dependencies` | `system:observe` | 依赖探针 |

Grafana 告警规则: `deploy/observability/alerting-rules.yaml`（与 Compose Grafana 配置同步）。

## 告警类型与响应

| 告警 | 来源 | 严重度 | 响应步骤 |
| --- | --- | --- | --- |
| **JobDead** | Prometheus `aihub_job_dead_count > 0` | critical | 查 `/system/jobs?status=DEAD`；分析 `error_code`；人工重试或修复根因 |
| **DEAD_JOB** | `AlertService` job_task DEAD 计数 | critical | 同上；确认 `system_alert` FIRING→RESOLVED 随 DEAD 清零 |
| **DEAD_DELIVERY** | `AlertService` Webhook 投递 | warning | 查 `webhook_delivery` DEAD 记录；检查目标 URL 与签名 |
| **OUTBOX_BACKLOG** | `AlertService` Outbox 积压 | warning | 查 pending outbox；确认 Worker 运行 |
| **WebhookInboxBacklog** | Prometheus inbox pending | warning | 确认 `WEBHOOK_PROCESS` Job 运行；查 `webhook_inbox` PENDING |
| **ReconciliationDiscrepancy** | 对账 Worker | warning | 查 `reconciliation_checkpoint`；按分类 MANUAL_REVIEW / SECURITY_INCIDENT 处理 |

### DEAD Job演练步骤

1. 确认指标: `GET /api/v1/system/metrics/summary` → `jobs.dead` 计数
2. 列出 DEAD 任务: `GET /api/v1/system/jobs?status=DEAD`（需 `job:read`）
3. 检查 `error_code` 与最近 `job_attempt` 日志
4. 修复根因后 `POST /api/v1/system/jobs/{jobId}/retry`（需 `job:manage`）
5. 确认 `system_alert` 中 `DEAD_JOB` 类型告警已 RESOLVED（如已配置）

### Webhook故障演练

1. 停止 Worker 容器模拟积压
2. 向 Gitea 推送触发 Webhook
3. 观察 `webhook_inbox` PENDING 增长
4. 重启 Worker，确认 `WEBHOOK_PROCESS` 消费且 status→COMPLETED
5. Tag 删除事件应触发 CRITICAL 指标 `aihub_webhook_critical_events_total`

### 一致性/备份故障演练

见 [backup-restore.md](./backup-restore.md) 隔离恢复流程；恢复后运行三类 Reconciler 确认无 SECURITY_INCIDENT。

## 告警静默与升级

静默层级：

1. **应用内去重（自动）**：`AlertService.hasFiringAlert` 对同 `alert_type` 已 FIRING 的告警不重复落行；恢复条件达成后自动 `RESOLVED`。演练注入不得绕过该去重语义。
2. **Grafana 静默（人工）**：`Alerting → Silence`，维护窗口内静默指定规则组，**必须**在静默创建时填写原因并绑定到期时间；禁止无到期时间的长期静默。
3. **Prometheus 规则**：`alerting-rules.yaml` 各规则 `labels.silence` 统一指向本节；`annotations.runbook` 指向对应故障演练步骤。

升级路径：

- **CRITICAL**：oncall-platform 15 分钟内确认（POST `/api/v1/system/alerts/{alertId}:acknowledge`，需 `system:observe`），1 小时内缓解或升级 tech-lead。
- **WARNING**：1 个工作日内处置；同一告警 7 天内复发 2 次以上，升级为 CRITICAL 跟进根因。
- **DEAD job 批量出现（≥ 同一 type 周期 2 个）**：视为上游依赖故障征兆，先按对应 Reconciler/Handler 诊断，再批量 `:retry`。

## E5 故障演练记录

| Commit | 时间 | 结果 | 说明 |
| --- | --- | --- | --- |
| 44bbc67 | 2026-09-24 | `deploy/observability/p5-ops-fault-drill.sh` exit 0：PASS=18 FAIL=0 SKIP=3（预期） | 证据详见 `docs/ai-spec/tasks/evidence/EVD-P5006-001.yaml` |

演练链（3s 加速检查间隔，compose override 注入 `AIHUB_ALERT_CHECK_INTERVAL`）：

- DEAD_JOB/JOB_DEAD：脏基线下去重不重复落行 + `POST :acknowledge` 人工 RESOLVED 双路径取证。
- DEAD_DELIVERY：5 条 DEAD 注入 → FIRING（WARNING）→ 清理 → 自动 RESOLVED。
- OUTBOX_BACKLOG：150 条 pending 注入 → FIRING（WARNING）→ 清理 → 自动 RESOLVED。
- 依赖不可用：`SystemDependencyServiceTest`（gitea DOWN → CRITICAL + fan-out）；live 停库演练自举不可行（告警链依赖 postgres 写库），结构性 SKIP。
- 对账审计：`webhook_inbox` 行插入/计数/清理 PASS；Prometheus live 规则受 `/actuator/prometheus` 401 结构性限制，SKIP 并登记 followUp。
- 备份：容器内 `pg_dumpall -U aihub` 25577 行 PASS；完整 backup.sh 链（manifest+checksums+restore）需 host 级三数据目录执行，按 [backup-restore.md](./backup-restore.md) 走。

**运维发现（脏基线）**：演练时 `job_task` 存在 40–46 条真实 DEAD 行且持续增长（类型 `REPOSITORY_PROVISION`/`ASSET_REPO_RECONCILE`/`MINIO_STORAGE_RECONCILE`/`VERSION_VALIDATE`/`UPLOAD_MATERIALIZE`/`PREVIEW_GENERATE`，error_code 几乎全 `INTERNAL_ERROR`），DEAD_JOB（CRITICAL）与 JOB_DEAD（WARNING）自 03:09 起持续 FIRING。处置：逐条 `GET /api/v1/system/jobs?status=DEAD` 定位 → 根因分析 → `POST /api/v1/system/jobs/{jobId}:retry`（需 `job:manage`，人工高风险动作，走审计）；清理后 `system_alert` 自动 RESOLVED。**禁止直接删库清 DEAD 行**（会丢失根因证据）。

## 对账 Worker 类型

| Job 类型 | 外部系统 | 说明 |
| --- | --- | --- |
| `PUBLISHED_VERSION_RECONCILE` | Gitea Tag API | PG↔Gitea Tag/Commit |
| `ASSET_REPO_RECONCILE` | Gitea Repo API | PG↔Gitea 仓库存在性 |
| `MINIO_STORAGE_RECONCILE` | MinIO statObject | PG artifact↔对象 |
| `WEBHOOK_PROCESS` | — | Inbox 异步解析（含 PENDING 扫描） |

周期任务由 `RecurringJobBootstrapper` 在启动时幂等注册。

## 安全与脱敏

日志/审计脱敏验证: `deploy/security/run-redaction-canary.sh`

详见 [security.md](./security.md)。

## 预览 Worker 资源隔离

| 层级 | 机制 | 配置 |
| --- | --- | --- |
| 内容上限 | `PreviewJobHandler` 解析前检查 | `aihub.preview.max-bytes` / `max-rows` / `max-cols` |
| 失败指标 | Micrometer Counter | `aihub_preview_failures_total{reason=...}` |
| 进程隔离 | Compose Worker 容器 cgroup / JVM 堆 | `deploy/compose/docker-compose.yml` worker 服务 `mem_limit`（生产建议 512Mi–1Gi） |

超限失败稳定错误码：`PREVIEW_LIMIT_EXCEEDED`（Job attempt `error_code` 为 `INTERNAL_ERROR`，message 含前缀）。
格式不支持：`PREVIEW_UNSUPPORTED_FORMAT`。
