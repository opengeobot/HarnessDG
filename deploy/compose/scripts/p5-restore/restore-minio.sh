#!/usr/bin/env bash
# 功能: MinIO 隔离恢复脚本（mc mirror 回灌）。
# 时间: 2026-07-10
# 作者: AxeXie
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <backup-dir>" >&2
  exit 1
fi

BACKUP_DIR="$1"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/compose/docker-compose.yml}"

echo "==> Restoring MinIO from $BACKUP_DIR (isolated drill — confirm target environment)"
for bucket in gitea-storage dvc-cache asset-staging asset-preview; do
  if [ -d "$BACKUP_DIR/$bucket" ]; then
    echo "  -> mirror $bucket"
    docker compose -f "$COMPOSE_FILE" run --rm minio-init \
      mc mirror "$BACKUP_DIR/$bucket" "local/$bucket"
  fi
done
echo "==> MinIO restore complete"
