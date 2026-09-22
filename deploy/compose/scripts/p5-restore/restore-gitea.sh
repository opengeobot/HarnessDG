#!/usr/bin/env bash
# 功能: Gitea 隔离恢复脚本（gitea dump zip）。
# 时间: 2026-07-10
# 作者: AxeXie
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <gitea-backup.zip>" >&2
  exit 1
fi

BACKUP="$1"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/compose/docker-compose.yml}"

echo "==> Restoring Gitea from $BACKUP (isolated drill — confirm target environment)"
docker compose -f "$COMPOSE_FILE" cp "$BACKUP" gitea:/tmp/gitea-restore.zip
docker compose -f "$COMPOSE_FILE" exec -T gitea \
  gitea restore --from /tmp/gitea-restore.zip
echo "==> Gitea restore complete"
