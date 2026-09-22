# P5 性能基线：100k 资产规模

> 功能: TASK-P5-004 性能门禁方法说明（E5 演练占位，不预加载 100k 数据）。
> 时间: 2026-07-10
> 作者: AxeXie

## 目标

在隔离环境中验证 **100k 资产** 规模下搜索、下载票据、Webhook 投递的 SLI 门禁，记录 RPO 无关的只读/写入混合负载指标。

## 前置条件

- Compose 全栈已启动（`deploy/compose/scripts/verify.sh` PASS）。
- 独立性能数据库 Volume，禁止覆盖开发数据。
- Prometheus/Grafana 已抓取 `aihub_*` 指标。

## 数据准备（E5 演练时执行）

1. **合成资产批量插入**（推荐 SQL 批处理或专用 seed Job，非本仓库默认路径）：
   - 目标：`asset` + `asset_search_projection` 各 100k 行。
   - 分布：80% ACTIVE / 15% DEPRECATED / 5% ARCHIVED；MODEL/DATASET 各半。
2. **抽样校验**：`SELECT status, COUNT(*) FROM asset GROUP BY status;` 合计 100k。
3. **索引预热**：执行 3 轮代表性搜索后再开始计时。

## 门禁场景

| 场景 | 操作 | 阈值（初稿） | 指标 |
| --- | --- | --- | --- |
| 目录搜索 | `GET /api/v1/assets?q=test&limit=50` × 100 并发 1min | p95 < 800ms | `http.server.requests` |
| 下载统计 | `GET /api/v1/assets/{id}/stats` × 50 RPS 30s | p95 < 400ms | 同上 |
| Webhook 积压 | 停止 Worker 5min 后恢复 | inbox pending < 500 恢复后 10min 清零 | `aihub_webhook_inbox_pending` |
| DEAD Job 告警 | 人工注入失败 Job | `DEAD_JOB` FIRING ≤ 5min 内可见 | `system_alert` + `aihub_job_dead_count` |

## 执行命令（占位）

```bash
# 1. 基线冒烟（当前仓库规模，无需 100k）
cd deploy/compose && ./scripts/verify.sh

# 2. 搜索压测（需安装 hey 或 k6，E5 时替换 TARGET）
# hey -n 1000 -c 50 -H "Authorization: Bearer $TOKEN" \
#   "http://localhost:8080/api/v1/assets?q=model&limit=50"

# 3. 导出 Prometheus 快照供 Evidence
# curl -s localhost:9090/api/v1/query?query=histogram_quantile(0.95,...)
```

## Evidence 关联

- `docs/ai-spec/tasks/evidence/EVD-P5004-001.yaml`（TASK-P5-004）
- 本文件为 **方法说明**；完整 E5 需在隔离环境填充 execution 字段并附 metrics 截图/导出。

## 限制

- 本地开发默认资产数 ≪ 100k；不在 CI 中自动加载 100k，避免 Testcontainers/Compose 超时。
- 100k seed 脚本可在 E5 演练 Issue 中单独授权路径后追加至 `deploy/perf/`。
