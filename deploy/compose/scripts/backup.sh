#!/usr/bin/env bash
# =============================================================================
# 功能: HarnessDG 全量备份脚本
# 时间: 2026-07-04
# 说明: 备份 PostgreSQL、Gitea、MinIO 数据到指定目录。
#       恢复顺序必须遵守事实源边界：PostgreSQL → Gitea → MinIO。
# =============================================================================
set -euo pipefail

BACKUP_ROOT="${BACKUP_ROOT:-/opt/aihub/backups}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="${BACKUP_ROOT}/${TIMESTAMP}"

mkdir -p "${BACKUP_DIR}"

echo "=== HarnessDG Backup ${TIMESTAMP} ==="

# 1. PostgreSQL 备份（事实源优先）
echo "[1/3] Backing up PostgreSQL..."
if command -v pg_dumpall &>/dev/null; then
    pg_dumpall -h "${POSTGRES_HOST:-localhost}" -p "${POSTGRES_PORT:-5432}" \
        -U "${POSTGRES_USER:-aihub}" --clean --if-exists \
        > "${BACKUP_DIR}/postgres_full.sql"
    echo "  -> ${BACKUP_DIR}/postgres_full.sql"
else
    echo "  [SKIP] pg_dumpall not found"
fi

# 2. Gitea 数据备份
echo "[2/3] Backing up Gitea data..."
GITEA_DATA_DIR="${GITEA_DATA_DIR:-/opt/gitea/data}"
if [ -d "${GITEA_DATA_DIR}" ]; then
    tar czf "${BACKUP_DIR}/gitea_data.tar.gz" -C "$(dirname "${GITEA_DATA_DIR}")" "$(basename "${GITEA_DATA_DIR}")"
    echo "  -> ${BACKUP_DIR}/gitea_data.tar.gz"
else
    echo "  [SKIP] Gitea data directory not found: ${GITEA_DATA_DIR}"
fi

# 3. MinIO 数据备份
echo "[3/3] Backing up MinIO data..."
MINIO_DATA_DIR="${MINIO_DATA_DIR:-/opt/minio/data}"
if [ -d "${MINIO_DATA_DIR}" ]; then
    tar czf "${BACKUP_DIR}/minio_data.tar.gz" -C "$(dirname "${MINIO_DATA_DIR}")" "$(basename "${MINIO_DATA_DIR}")"
    echo "  -> ${BACKUP_DIR}/minio_data.tar.gz"
else
    echo "  [SKIP] MinIO data directory not found: ${MINIO_DATA_DIR}"
fi

# 备份元数据
cat > "${BACKUP_DIR}/manifest.json" <<EOF
{
  "timestamp": "${TIMESTAMP}",
  "components": ["postgres", "gitea", "minio"],
  "restore_order": ["postgres", "gitea", "minio"]
}
EOF

echo ""
echo "=== Backup complete: ${BACKUP_DIR} ==="
echo "Restore order: PostgreSQL → Gitea → MinIO"
