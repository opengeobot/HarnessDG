# Backup & Restore Runbook

> 功能: PostgreSQL / Gitea / MinIO 备份恢复演练步骤（TASK-P5-003 E1/E3）。
> 时间: 2026-07-10
> 作者: AxeXie

本文档描述 HarnessDG 平台三大数据平面的备份与隔离恢复流程。完整 E5 演练需在独立环境中执行并记录 RPO/RTO 证据。

## 范围与 RPO/RTO 目标

| 组件 | 数据源 | 默认 RPO | 默认 RTO | 验证点 |
| --- | --- | --- | --- | --- |
| PostgreSQL | 工作流、授权、审计、任务 | 24h | 30min | Flyway 迁移完整、随机 Published Version 可读 |
| Gitea | Git 仓库、Tag、Commit | 24h | 60min | 仓库与 Tag 与 PG `asset_version` 一致 |
| MinIO | DVC 对象、暂存、预览 | 24h | 60min | 随机 artifact SHA-256 与 PG 记录一致 |

阈值可通过 `system_config` 覆盖；演练报告须脱敏（禁止记录 Secret、预签名 URL、JWT）。

## 前置条件

- 已阅读 [compose.md](./compose.md) 并完成全栈启动。
- 备份脚本位于 `deploy/backup/`（见下方命令）。
- 恢复脚本位于 `deploy/compose/scripts/p5-restore/`。
- 演练在**隔离网络/独立 Volume** 中执行，禁止覆盖生产数据。

## 1. PostgreSQL 备份

```bash
# 从运行中的 postgres 容器导出（逻辑备份）
docker compose -f deploy/compose/docker-compose.yml exec -T postgres \
  pg_dump -U aihub -Fc aihub > deploy/backup/aihub-$(date +%Y%m%d).dump

# 校验备份可读
pg_restore -l deploy/backup/aihub-$(date +%Y%m%d).dump | head
```

**恢复（隔离环境）:**

```bash
./deploy/compose/scripts/p5-restore/restore-postgres.sh deploy/backup/aihub-YYYYMMDD.dump
```

恢复后验证:

```bash
psql -U aihub -d aihub -c "SELECT COUNT(*) FROM audit_log WHERE event_type='DOWNLOAD_TICKET_ISSUED';"
psql -U aihub -d aihub -c "SELECT version_id, git_tag, source_commit FROM asset_version WHERE status='PUBLISHED' ORDER BY random() LIMIT 3;"
```

## 2. Gitea 备份

```bash
# Gitea 内置 dump（含仓库与配置）
docker compose -f deploy/compose/docker-compose.yml exec -T gitea \
  gitea dump -c /data/gitea/conf/app.ini -f /tmp/gitea-backup.zip

docker compose -f deploy/compose/docker-compose.yml cp gitea:/tmp/gitea-backup.zip \
  deploy/backup/gitea-$(date +%Y%m%d).zip
```

**恢复:**

```bash
./deploy/compose/scripts/p5-restore/restore-gitea.sh deploy/backup/gitea-YYYYMMDD.zip
```

验证: 随机选取 PG 中 `PUBLISHED` 版本的 `git_tag`，在 Gitea UI 或 API 确认 Tag 与 `source_commit` 一致。

## 3. MinIO 备份

```bash
# 使用 mc mirror 同步四个 Bucket
docker compose -f deploy/compose/docker-compose.yml run --rm minio-init \
  mc mirror local/dvc-cache deploy/backup/minio/dvc-cache/
```

涉及 Bucket: `gitea-storage`, `dvc-cache`, `asset-staging`, `asset-preview`。

**恢复:**

```bash
./deploy/compose/scripts/p5-restore/restore-minio.sh deploy/backup/minio/
```

验证: 对随机 `version_artifact` 记录，确认 MinIO 对象存在且 `sha256` 匹配。

## 4. 恢复后一致性检查

1. 启动全栈: `docker compose up -d`
2. 运行 `./deploy/compose/scripts/verify.sh`（P0-B + P1-P5 指针用例）
3. 触发对账 Job（自动周期或手动）:
   - `PUBLISHED_VERSION_RECONCILE`
   - `ASSET_REPO_RECONCILE`
   - `MINIO_STORAGE_RECONCILE`
4. 检查 `reconciliation_checkpoint` 表与 `system_alert` 无未解决 `SECURITY_INCIDENT`

## 5. 演练记录模板

```text
Drill ID: BKR-YYYYMMDD-001
Operator:
Started:
Completed:
RPO achieved: yes/no (max data loss: ___ min)
RTO achieved: yes/no (downtime: ___ min)
Random Published versions verified: N/M
DVC/Manifest digest match: yes/no
Reconciler discrepancies: 0 / list
Secrets redacted in report: confirmed
```

## 关联 Runbook

- [compose.md](./compose.md) — 日常部署与 V01-V21 验收
- [ops.md](./ops.md) — 告警、Dashboard 与故障演练
- [security.md](./security.md) — 脱敏与安全验证
