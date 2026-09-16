#!/usr/bin/env bash
# =============================================================================
# 功能: HarnessDG 全量备份脚本
# 时间: 2026-07-30
# 说明: 备份 PostgreSQL、Gitea、MinIO 数据到指定目录。
#       恢复顺序必须遵守事实源边界：PostgreSQL → Gitea → MinIO。
#       备份后生成 SHA-256 校验文件 (checksums.sha256) 用于恢复验证。
# 作者: AxeXie
# =============================================================================
set -euo pipefail

BACKUP_ROOT="${BACKUP_ROOT:-/opt/aihub/backups}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="${BACKUP_ROOT}/${TIMESTAMP}"

mkdir -p "${BACKUP_DIR}"

# Cross-platform SHA-256
file_sha256() {
    if command -v sha256sum &>/dev/null; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum &>/dev/null; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        echo "UNKNOWN"
    fi
}

echo "=== HarnessDG Backup ${TIMESTAMP} ==="

# Track files for checksum generation
CHECKSUM_FILES=""

# 1. PostgreSQL 备份（事实源优先）
echo "[1/3] Backing up PostgreSQL..."
if command -v pg_dumpall &>/dev/null; then
    pg_dumpall -h "${POSTGRES_HOST:-localhost}" -p "${POSTGRES_PORT:-5432}" \
        -U "${POSTGRES_USER:-aihub}" --clean --if-exists \
        > "${BACKUP_DIR}/postgres_full.sql"
    echo "  -> ${BACKUP_DIR}/postgres_full.sql"
    CHECKSUM_FILES="postgres_full.sql"
else
    echo "  [SKIP] pg_dumpall not found"
fi

# 2. Gitea 数据备份
echo "[2/3] Backing up Gitea data..."
GITEA_DATA_DIR="${GITEA_DATA_DIR:-/opt/gitea/data}"
if [ -d "${GITEA_DATA_DIR}" ]; then
    tar czf "${BACKUP_DIR}/gitea_data.tar.gz" -C "$(dirname "${GITEA_DATA_DIR}")" "$(basename "${GITEA_DATA_DIR}")"
    echo "  -> ${BACKUP_DIR}/gitea_data.tar.gz"
    CHECKSUM_FILES="${CHECKSUM_FILES} gitea_data.tar.gz"
else
    echo "  [SKIP] Gitea data directory not found: ${GITEA_DATA_DIR}"
fi

# 3. MinIO 数据备份
echo "[3/3] Backing up MinIO data..."
MINIO_DATA_DIR="${MINIO_DATA_DIR:-/opt/minio/data}"
if [ -d "${MINIO_DATA_DIR}" ]; then
    tar czf "${BACKUP_DIR}/minio_data.tar.gz" -C "$(dirname "${MINIO_DATA_DIR}")" "$(basename "${MINIO_DATA_DIR}")"
    echo "  -> ${BACKUP_DIR}/minio_data.tar.gz"
    CHECKSUM_FILES="${CHECKSUM_FILES} minio_data.tar.gz"
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

# 4. 生成 SHA-256 校验文件
echo ""
echo "[4/4] Generating SHA-256 checksums..."
if [ -n "${CHECKSUM_FILES}" ]; then
    : > "${BACKUP_DIR}/checksums.sha256"
    for FILENAME in ${CHECKSUM_FILES}; do
        if [ -f "${BACKUP_DIR}/${FILENAME}" ]; then
            HASH=$(file_sha256 "${BACKUP_DIR}/${FILENAME}")
            echo "${HASH}  ${FILENAME}" >> "${BACKUP_DIR}/checksums.sha256"
            echo "  ${FILENAME}: ${HASH:0:16}..."
        fi
    done
    echo "  -> ${BACKUP_DIR}/checksums.sha256"
fi

echo ""
echo "=== Backup complete: ${BACKUP_DIR} ==="
echo "Restore order: PostgreSQL → Gitea → MinIO"
echo "Next step: run deploy/compose/scripts/restore.sh ${BACKUP_DIR}"
echo "Verify: run deploy/compose/scripts/verify-restore.sh ${BACKUP_DIR}"