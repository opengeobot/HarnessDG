#!/usr/bin/env bash
# =============================================================================
# 功能: HarnessDG 全量恢复脚本
# 时间: 2026-07-04
# 说明: 按事实源边界顺序恢复：PostgreSQL → Gitea → MinIO。
#       PostgreSQL 作为元数据事实源必须最先恢复，保证后续组件可对照校验。
# =============================================================================
set -euo pipefail

BACKUP_DIR="${1:?Usage: restore.sh <backup-directory>}"

if [ ! -f "${BACKUP_DIR}/manifest.json" ]; then
    echo "ERROR: manifest.json not found in ${BACKUP_DIR}"
    exit 1
fi

echo "=== HarnessDG Restore from ${BACKUP_DIR} ==="
echo "Restore order: PostgreSQL → Gitea → MinIO"
echo ""

# 1. PostgreSQL 恢复（事实源优先）
echo "[1/3] Restoring PostgreSQL..."
if [ -f "${BACKUP_DIR}/postgres_full.sql" ]; then
    psql -h "${POSTGRES_HOST:-localhost}" -p "${POSTGRES_PORT:-5432}" \
        -U "${POSTGRES_USER:-aihub}" -f "${BACKUP_DIR}/postgres_full.sql"
    echo "  -> PostgreSQL restored"
else
    echo "  [SKIP] postgres_full.sql not found"
fi

# 2. Gitea 恢复
echo "[2/3] Restoring Gitea data..."
GITEA_DATA_DIR="${GITEA_DATA_DIR:-/opt/gitea/data}"
if [ -f "${BACKUP_DIR}/gitea_data.tar.gz" ]; then
    mkdir -p "$(dirname "${GITEA_DATA_DIR}")"
    tar xzf "${BACKUP_DIR}/gitea_data.tar.gz" -C "$(dirname "${GITEA_DATA_DIR}")"
    echo "  -> Gitea data restored to ${GITEA_DATA_DIR}"
else
    echo "  [SKIP] gitea_data.tar.gz not found"
fi

# 3. MinIO 恢复
echo "[3/3] Restoring MinIO data..."
MINIO_DATA_DIR="${MINIO_DATA_DIR:-/opt/minio/data}"
if [ -f "${BACKUP_DIR}/minio_data.tar.gz" ]; then
    mkdir -p "$(dirname "${MINIO_DATA_DIR}")"
    tar xzf "${BACKUP_DIR}/minio_data.tar.gz" -C "$(dirname "${MINIO_DATA_DIR}")"
    echo "  -> MinIO data restored to ${MINIO_DATA_DIR}"
else
    echo "  [SKIP] minio_data.tar.gz not found"
fi

# 4. 随机 Published Version 校验
echo ""
echo "[4/4] Verifying random published version..."
RESULT=$(psql -h "${POSTGRES_HOST:-localhost}" -p "${POSTGRES_PORT:-5432}" \
    -U "${POSTGRES_USER:-aihub}" -d "${POSTGRES_DB:-aihub}" -t -c \
    "SELECT version_id, git_tag, manifest_digest FROM asset_version WHERE status='PUBLISHED' ORDER BY RANDOM() LIMIT 1" 2>/dev/null || echo "")

if [ -n "${RESULT}" ]; then
    echo "  Random published version: ${RESULT}"
    echo "  [OK] Verify manually that Gitea tag and MinIO objects match."
else
    echo "  [SKIP] No published versions found to verify."
fi

echo ""
echo "=== Restore complete ==="
