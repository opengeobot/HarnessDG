#!/usr/bin/env bash
# 功能: PostgreSQL 隔离恢复脚本（逻辑备份 pg_restore）。
# 时间: 2026-07-10
# 作者: AxeXie
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <dump-file>" >&2
  exit 1
fi

DUMP="$1"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/compose/docker-compose.yml}"

echo "==> Restoring PostgreSQL from $DUMP (isolated drill — confirm target environment)"
docker compose -f "$COMPOSE_FILE" exec -T postgres \
  pg_restore -U aihub -d aihub --clean --if-exists < "$DUMP"
echo "==> PostgreSQL restore complete"
