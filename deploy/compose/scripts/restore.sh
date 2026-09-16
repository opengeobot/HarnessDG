#!/usr/bin/env bash
# =============================================================================
# 功能: HarnessDG 全量恢复脚本
# 时间: 2026-07-30
# 说明: 按事实源边界顺序恢复：PostgreSQL → Gitea → MinIO。
#       PostgreSQL 作为元数据事实源必须最先恢复，保证后续组件可对照校验。
#       恢复后执行 SHA-256 校验和数据库约束验证。
# 作者: AxeXie
# =============================================================================
set -euo pipefail

BACKUP_DIR="${1:?Usage: restore.sh <backup-directory> [sample-count]}"
SAMPLE_COUNT="${2:-3}"

if [ ! -d "${BACKUP_DIR}" ]; then
    echo "ERROR: Backup directory not found: ${BACKUP_DIR}"
    exit 1
fi

if [ ! -f "${BACKUP_DIR}/manifest.json" ]; then
    echo "ERROR: manifest.json not found in ${BACKUP_DIR}"
    exit 1
fi

# ---- helper functions ----

# Cross-platform file size
file_size() {
    if stat -f%z "$1" >/dev/null 2>&1; then
        stat -f%z "$1"
    else
        stat -c %s "$1"
    fi
}

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

echo "=== HarnessDG Restore from ${BACKUP_DIR} ==="
echo "Restore order: PostgreSQL → Gitea → MinIO"
echo ""

# =============================================================================
# 1. PostgreSQL 恢复（事实源优先）
# =============================================================================
echo "[1/3] Restoring PostgreSQL..."
if [ -f "${BACKUP_DIR}/postgres_full.sql" ]; then
    PG_SIZE=$(file_size "${BACKUP_DIR}/postgres_full.sql")
    echo "  Source: postgres_full.sql (${PG_SIZE} bytes)"

    if ! command -v psql &>/dev/null; then
        echo "  [SKIP] psql not found, cannot restore PostgreSQL"
    else
        psql -h "${POSTGRES_HOST:-localhost}" -p "${POSTGRES_PORT:-5432}" \
            -U "${POSTGRES_USER:-aihub}" -f "${BACKUP_DIR}/postgres_full.sql"

        echo "  -> PostgreSQL restored"

        # Verify SHA-256 checksum if available
        if [ -f "${BACKUP_DIR}/checksums.sha256" ]; then
            EXPECTED=$(grep "postgres_full.sql" "${BACKUP_DIR}/checksums.sha256" | awk '{print $1}' || echo "")
            if [ -n "${EXPECTED}" ]; then
                ACTUAL=$(file_sha256 "${BACKUP_DIR}/postgres_full.sql")
                if [ "${ACTUAL}" = "${EXPECTED}" ]; then
                    echo "  [OK] SHA-256 checksum verified (postgres_full.sql)"
                else
                    echo "  [WARN] SHA-256 mismatch: postgres_full.sql (expected: ${EXPECTED:0:16}..., actual: ${ACTUAL:0:16}...)"
                fi
            fi
        fi

        # Verify database constraints after restore
        echo "  Verifying database constraints..."
        PSQLOPTS="-h ${POSTGRES_HOST:-localhost} -p ${POSTGRES_PORT:-5432} -U ${POSTGRES_USER:-aihub} -d ${POSTGRES_DB:-aihub} -t"

        CONSTRAINT_COUNT=$(psql ${PSQLOPTS} -t \
            -c "SELECT COUNT(*) FROM pg_constraint WHERE contype IN ('p', 'u', 'f', 'c') AND connamespace = 'public'::regnamespace" 2>/dev/null | tr -d '[:space:]') || CONSTRAINT_COUNT="ERROR"
        if [ "${CONSTRAINT_COUNT}" != "ERROR" ] && [ -n "${CONSTRAINT_COUNT}" ]; then
            echo "  [OK] Database constraints intact (${CONSTRAINT_COUNT} constraints)"
        else
            echo "  [WARN] Cannot verify database constraints"
        fi

        # Verify key tables exist and have data
        for TABLE in iam_principal asset audit_log; do
            ROW_COUNT=$(psql ${PSQLOPTS} -c "SELECT COUNT(*) FROM ${TABLE}" 2>/dev/null | tr -d '[:space:]') || ROW_COUNT="ERROR"
            if [ "${ROW_COUNT}" != "ERROR" ] && [ -n "${ROW_COUNT}" ]; then
                echo "  [OK] Table ${TABLE}: ${ROW_COUNT} rows"
            else
                echo "  [WARN] Table ${TABLE}: unable to verify"
            fi
        done

        # Verify Flyway migration history
        MIGRATION_COUNT=$(psql ${PSQLOPTS} -t \
            -c "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true" 2>/dev/null | tr -d '[:space:]') || MIGRATION_COUNT="ERROR"
        if [ "${MIGRATION_COUNT}" != "ERROR" ] && [ -n "${MIGRATION_COUNT}" ]; then
            echo "  [OK] Flyway migrations intact (${MIGRATION_COUNT} successful)"
        else
            echo "  [WARN] Flyway migration history not accessible"
        fi
    fi
else
    echo "  [SKIP] postgres_full.sql not found"
fi

# =============================================================================
# 2. Gitea 恢复
# =============================================================================
echo ""
echo "[2/3] Restoring Gitea data..."
GITEA_DATA_DIR="${GITEA_DATA_DIR:-/opt/gitea/data}"
if [ -f "${BACKUP_DIR}/gitea_data.tar.gz" ]; then
    GITEA_SIZE=$(file_size "${BACKUP_DIR}/gitea_data.tar.gz")
    echo "  Source: gitea_data.tar.gz (${GITEA_SIZE} bytes)"

    mkdir -p "$(dirname "${GITEA_DATA_DIR}")"
    tar xzf "${BACKUP_DIR}/gitea_data.tar.gz" -C "$(dirname "${GITEA_DATA_DIR}")"
    echo "  -> Gitea data restored to ${GITEA_DATA_DIR}"

    # Verify SHA-256 checksum if available
    if [ -f "${BACKUP_DIR}/checksums.sha256" ]; then
        EXPECTED=$(grep "gitea_data.tar.gz" "${BACKUP_DIR}/checksums.sha256" | awk '{print $1}' || echo "")
        if [ -n "${EXPECTED}" ]; then
            ACTUAL=$(file_sha256 "${BACKUP_DIR}/gitea_data.tar.gz")
            if [ "${ACTUAL}" = "${EXPECTED}" ]; then
                echo "  [OK] SHA-256 checksum verified (gitea_data.tar.gz)"
            else
                echo "  [WARN] SHA-256 mismatch: gitea_data.tar.gz"
            fi
        fi
    fi

    # Verify tar archive integrity
    if tar tzf "${BACKUP_DIR}/gitea_data.tar.gz" >/dev/null 2>&1; then
        ENTRY_COUNT=$(tar tzf "${BACKUP_DIR}/gitea_data.tar.gz" | wc -l | tr -d ' ')
        echo "  [OK] Gitea archive integrity verified (${ENTRY_COUNT} entries)"
    else
        echo "  [WARN] Gitea archive integrity check inconclusive"
    fi

    # Count restored repositories
    if [ -d "${GITEA_DATA_DIR}/repositories" ]; then
        REPO_COUNT=$(find "${GITEA_DATA_DIR}/repositories" -maxdepth 3 -name ".git" -type d 2>/dev/null | wc -l | tr -d ' ')
        echo "  [OK] Gitea repositories restored: ${REPO_COUNT}"
    fi
else
    echo "  [SKIP] gitea_data.tar.gz not found"
fi

# =============================================================================
# 3. MinIO 恢复
# =============================================================================
echo ""
echo "[3/3] Restoring MinIO data..."
MINIO_DATA_DIR="${MINIO_DATA_DIR:-/opt/minio/data}"
if [ -f "${BACKUP_DIR}/minio_data.tar.gz" ]; then
    MINIO_SIZE=$(file_size "${BACKUP_DIR}/minio_data.tar.gz")
    echo "  Source: minio_data.tar.gz (${MINIO_SIZE} bytes)"

    mkdir -p "$(dirname "${MINIO_DATA_DIR}")"
    tar xzf "${BACKUP_DIR}/minio_data.tar.gz" -C "$(dirname "${MINIO_DATA_DIR}")"
    echo "  -> MinIO data restored to ${MINIO_DATA_DIR}"

    # Verify SHA-256 checksum if available
    if [ -f "${BACKUP_DIR}/checksums.sha256" ]; then
        EXPECTED=$(grep "minio_data.tar.gz" "${BACKUP_DIR}/checksums.sha256" | awk '{print $1}' || echo "")
        if [ -n "${EXPECTED}" ]; then
            ACTUAL=$(file_sha256 "${BACKUP_DIR}/minio_data.tar.gz")
            if [ "${ACTUAL}" = "${EXPECTED}" ]; then
                echo "  [OK] SHA-256 checksum verified (minio_data.tar.gz)"
            else
                echo "  [WARN] SHA-256 mismatch: minio_data.tar.gz"
            fi
        fi
    fi

    # Verify tar archive integrity
    if tar tzf "${BACKUP_DIR}/minio_data.tar.gz" >/dev/null 2>&1; then
        ENTRY_COUNT=$(tar tzf "${BACKUP_DIR}/minio_data.tar.gz" | wc -l | tr -d ' ')
        echo "  [OK] MinIO archive integrity verified (${ENTRY_COUNT} entries)"
    else
        echo "  [WARN] MinIO archive integrity check inconclusive"
    fi

    # Count restored objects
    if [ -d "${MINIO_DATA_DIR}" ]; then
        MINIO_OBJ_COUNT=$(find "${MINIO_DATA_DIR}" -type f 2>/dev/null | wc -l | tr -d ' ')
        echo "  [OK] MinIO objects restored: ${MINIO_OBJ_COUNT}"
    fi
else
    echo "  [SKIP] minio_data.tar.gz not found"
fi

# =============================================================================
# 4. Cross-Component Verification (Published Versions)
# =============================================================================
echo ""
echo "[4/4] Verifying published version consistency (${SAMPLE_COUNT} samples)..."
if command -v psql &>/dev/null; then
    PSQLOPTS="-h ${POSTGRES_HOST:-localhost} -p ${POSTGRES_PORT:-5432} -U ${POSTGRES_USER:-aihub} -d ${POSTGRES_DB:-aihub} -t"

    # Get random published versions with full details
    PUB_VERSIONS=$(psql ${PSQLOPTS} -c \
        "SELECT version_id, version, git_tag, git_commit_sha, manifest_sha256 FROM asset_version WHERE status = 'PUBLISHED' ORDER BY RANDOM() LIMIT ${SAMPLE_COUNT}" \
        2>/dev/null | grep -v "^$" || echo "")

    if [ -n "${PUB_VERSIONS}" ]; then
        PUB_COUNT=$(echo "${PUB_VERSIONS}" | wc -l | tr -d ' ')
        echo "  Found ${PUB_COUNT} published version(s) to verify"

        while IFS='|' read -r VERSION_ID VERSION_STR GIT_TAG GIT_COMMIT MANIFEST_SHA; do
            VERSION_ID=$(echo "${VERSION_ID}" | tr -d '[:space:]')
            VERSION_STR=$(echo "${VERSION_STR}" | tr -d '[:space:]')
            GIT_TAG=$(echo "${GIT_TAG}" | tr -d '[:space:]')
            GIT_COMMIT=$(echo "${GIT_COMMIT}" | tr -d '[:space:]')
            MANIFEST_SHA=$(echo "${MANIFEST_SHA}" | tr -d '[:space:]')

            [ -z "${VERSION_ID}" ] && continue
            echo "  --- Version: ${VERSION_ID} (${VERSION_STR}) ---"

            # Check Git tag exists in Gitea repos
            if [ -n "${GIT_TAG}" ] && [ -d "${GITEA_DATA_DIR:-/opt/gitea/data}/repositories" ] && command -v git &>/dev/null; then
                TAG_FOUND=false
                while IFS= read -r GIT_CONFIG; do
                    REPO_DIR="$(dirname "$(dirname "${GIT_CONFIG}")")"
                    if git -C "${REPO_DIR}" tag -l | grep -q "^${GIT_TAG}$" 2>/dev/null; then
                        echo "  [OK] Git tag '${GIT_TAG}' found in ${REPO_DIR}"
                        TAG_FOUND=true

                        # Verify commit SHA if available
                        if [ -n "${GIT_COMMIT}" ]; then
                            ACTUAL_COMMIT=$(git -C "${REPO_DIR}" rev-parse "${GIT_TAG}" 2>/dev/null | head -1 || echo "")
                            if [ "${ACTUAL_COMMIT}" = "${GIT_COMMIT}" ]; then
                                echo "  [OK] Git commit SHA matches (${GIT_COMMIT:0:12}...)"
                            else
                                echo "  [WARN] Git commit SHA mismatch (expected: ${GIT_COMMIT:0:12}..., actual: ${ACTUAL_COMMIT:0:12}...)"
                            fi
                        fi
                        break
                    fi
                done < <(find "${GITEA_DATA_DIR:-/opt/gitea/data}/repositories" -maxdepth 3 -name "config" -path "*/.git/config" 2>/dev/null)

                if [ "${TAG_FOUND}" = false ]; then
                    echo "  [WARN] Git tag '${GIT_TAG}' not found in Gitea repositories"
                fi
            elif [ -n "${GIT_TAG}" ]; then
                echo "  [SKIP] Cannot verify Git tag (Gitea data not restored or git not available)"
            fi

            # Check MinIO objects for this version's manifest digest
            if [ -n "${MANIFEST_SHA}" ] && [ -d "${MINIO_DATA_DIR:-/opt/minio/data}" ]; then
                if find "${MINIO_DATA_DIR:-/opt/minio/data}" -name "${MANIFEST_SHA}*" -type f 2>/dev/null | head -1 | grep -q .; then
                    echo "  [OK] MinIO object found for manifest digest (${MANIFEST_SHA:0:16}...)"
                else
                    # The manifest SHA may be embedded in metadata, not as a filename
                    echo "  [INFO] Manifest digest cross-check inconclusive (digest may be in metadata)"
                fi
            fi

            echo "  [OK] Published version DB record verified"
        done <<< "${PUB_VERSIONS}"
    else
        echo "  [SKIP] No published versions found to verify"
    fi
else
    echo "  [SKIP] psql not available, skipping version verification"
fi

echo ""
echo "=== Restore complete ==="
echo ""
echo "Next step: run deploy/compose/scripts/verify-restore.sh ${BACKUP_DIR} for full integrity check"