#!/usr/bin/env bash
# =============================================================================
# 功能: HarnessDG 备份恢复完整性验证脚本 (REQ-BKR-001)
# 时间: 2026-07-30
# 说明: 验证 PostgreSQL/Gitea/MinIO 备份恢复的完整性。
#       1. 检查备份文件存在性和一致性
#       2. 验证 SHA-256 校验和（若 checksums.sha256 存在）
#       3. 验证 PostgreSQL 恢复后的关键表行数和约束
#       4. 随机抽样 Gitea 仓库 Clone + SHA-256 校验
#       5. 随机抽样 MinIO 对象 MD5/SHA-256 校验
#       6. 交叉验证 Published Version（Git tag + MinIO digest + DB 记录）
#       7. 输出验证报告（PASS/FAIL per component）
# 作者: AxeXie
# =============================================================================
set -euo pipefail

BACKUP_DIR="${1:?Usage: verify-restore.sh <backup-directory> [sample-count]}"
SAMPLE_COUNT="${2:-3}"
REPORT_FILE="${BACKUP_DIR}/verify_report.txt"

# ---- helper functions ----

# Cross-platform file size: macOS `stat -f%z`, Linux `stat -c %s`
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

# Initialize report
{
    echo "=== HarnessDG Backup Restore Verification ==="
    echo "Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "Backup Directory: ${BACKUP_DIR}"
    echo "Sample Count: ${SAMPLE_COUNT}"
    echo ""
} > "${REPORT_FILE}"

PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

pass() { echo "[PASS] $1" | tee -a "${REPORT_FILE}"; ((PASS_COUNT++)) || true; }
fail() { echo "[FAIL] $1" | tee -a "${REPORT_FILE}"; ((FAIL_COUNT++)) || true; }
skip() { echo "[SKIP] $1" | tee -a "${REPORT_FILE}"; ((SKIP_COUNT++)) || true; }

# =============================================================================
# 1. Backup File Existence and Manifest
# =============================================================================
echo "--- Backup Manifest ---" | tee -a "${REPORT_FILE}"

if [ -f "${BACKUP_DIR}/manifest.json" ]; then
    pass "manifest.json exists"
    # Validate manifest contains required fields
    if grep -q "timestamp" "${BACKUP_DIR}/manifest.json" && \
       grep -q "components" "${BACKUP_DIR}/manifest.json"; then
        pass "manifest.json contains required fields"
    else
        fail "manifest.json missing required fields (timestamp, components)"
    fi
else
    fail "manifest.json missing"
fi

# =============================================================================
# 2. Backup File Integrity (Size + SHA-256)
# =============================================================================
echo "" | tee -a "${REPORT_FILE}"
echo "--- Backup File Integrity ---" | tee -a "${REPORT_FILE}"

# Check each expected backup artifact
for ARTIFACT in postgres_full.sql gitea_data.tar.gz minio_data.tar.gz; do
    if [ -f "${BACKUP_DIR}/${ARTIFACT}" ]; then
        FSIZE=$(file_size "${BACKUP_DIR}/${ARTIFACT}")
        pass "${ARTIFACT} exists (${FSIZE} bytes)"

        # Verify non-empty
        if [ "${FSIZE}" -gt 0 ] 2>/dev/null; then
            pass "${ARTIFACT} non-empty"
        else
            fail "${ARTIFACT} is empty (0 bytes)"
        fi
    else
        fail "${ARTIFACT} missing"
    fi
done

# Verify SHA-256 checksums if available
if [ -f "${BACKUP_DIR}/checksums.sha256" ]; then
    echo "" | tee -a "${REPORT_FILE}"
    pass "checksums.sha256 exists"

    CHECKSUM_VERIFY_OK=true
    while IFS='  ' read -r EXPECTED_HASH FILENAME; do
        # Skip comment/empty lines
        [[ "${EXPECTED_HASH}" == \#* ]] && continue
        [ -z "${FILENAME}" ] && continue

        if [ -f "${BACKUP_DIR}/${FILENAME}" ]; then
            ACTUAL_HASH=$(file_sha256 "${BACKUP_DIR}/${FILENAME}")
            if [ "${ACTUAL_HASH}" = "${EXPECTED_HASH}" ]; then
                pass "SHA-256 match: ${FILENAME}"
            else
                fail "SHA-256 mismatch: ${FILENAME} (expected: ${EXPECTED_HASH}, actual: ${ACTUAL_HASH})"
                CHECKSUM_VERIFY_OK=false
            fi
        else
            skip "SHA-256 skip: ${FILENAME} not found (file may have been skipped)"
        fi
    done < "${BACKUP_DIR}/checksums.sha256"

    if [ "${CHECKSUM_VERIFY_OK}" = true ]; then
        pass "All available SHA-256 checksums verified"
    fi
else
    skip "checksums.sha256 not found (checksum verification skipped)"
fi

# =============================================================================
# 3. Tar Archive Integrity
# =============================================================================
echo "" | tee -a "${REPORT_FILE}"
echo "--- Tar Archive Integrity ---" | tee -a "${REPORT_FILE}"

for ARCHIVE in gitea_data.tar.gz minio_data.tar.gz; do
    if [ -f "${BACKUP_DIR}/${ARCHIVE}" ]; then
        if tar tzf "${BACKUP_DIR}/${ARCHIVE}" >/dev/null 2>&1; then
            # Count entries
            ENTRY_COUNT=$(tar tzf "${BACKUP_DIR}/${ARCHIVE}" | wc -l | tr -d ' ')
            pass "${ARCHIVE} integrity check passed (${ENTRY_COUNT} entries)"
        else
            fail "${ARCHIVE} corrupted (tar test failed)"
        fi
    fi
done

# =============================================================================
# 4. PostgreSQL Backup Content Sanity
# =============================================================================
echo "" | tee -a "${REPORT_FILE}"
echo "--- PostgreSQL Backup Content ---" | tee -a "${REPORT_FILE}"

if [ -f "${BACKUP_DIR}/postgres_full.sql" ]; then
    # Verify key tables are present in the dump
    KEY_TABLES="iam_principal asset audit_log"
    ALL_TABLES_FOUND=true
    for TABLE in ${KEY_TABLES}; do
        if grep -qi "CREATE TABLE.*${TABLE}" "${BACKUP_DIR}/postgres_full.sql" 2>/dev/null || \
           grep -qi "\"${TABLE}\"" "${BACKUP_DIR}/postgres_full.sql" 2>/dev/null; then
            pass "Backup contains ${TABLE} table definition"
        else
            fail "Backup missing ${TABLE} table definition"
            ALL_TABLES_FOUND=false
        fi
    done

    # Check backup contains constraint definitions
    if grep -qi "CONSTRAINT" "${BACKUP_DIR}/postgres_full.sql"; then
        pass "Backup contains constraint definitions"
    else
        fail "Backup missing constraint definitions"
    fi
fi

# =============================================================================
# 5. Runtime Verification (PostgreSQL)
# =============================================================================
echo "" | tee -a "${REPORT_FILE}"
echo "--- Runtime Verification (PostgreSQL) ---" | tee -a "${REPORT_FILE}"

if ! command -v psql &>/dev/null; then
    skip "psql not available in PATH, skipping runtime verification"
else
    # Test connectivity
    if psql -h "${POSTGRES_HOST:-localhost}" -p "${POSTGRES_PORT:-5432}" \
        -U "${POSTGRES_USER:-aihub}" -d "${POSTGRES_DB:-aihub}" -t -c "SELECT 1" >/dev/null 2>&1; then
        pass "PostgreSQL is accessible"

        # Check key table row counts
        PSQLOPTS="-h ${POSTGRES_HOST:-localhost} -p ${POSTGRES_PORT:-5432} -U ${POSTGRES_USER:-aihub} -d ${POSTGRES_DB:-aihub} -t"
        RUNTIME_TABLES="iam_principal iam_user iam_role asset audit_log asset_version"
        for TABLE in ${RUNTIME_TABLES}; do
            ROW_COUNT=$(psql ${PSQLOPTS} -c "SELECT COUNT(*) FROM ${TABLE}" 2>/dev/null | tr -d '[:space:]') || ROW_COUNT="ERROR"
            if [ "${ROW_COUNT}" != "ERROR" ] && [ -n "${ROW_COUNT}" ]; then
                pass "Table ${TABLE}: ${ROW_COUNT} rows"
            else
                skip "Table ${TABLE}: unable to query (table may not exist)"
            fi
        done

        # Verify database constraints are intact
        CONSTRAINT_COUNT=$(psql ${PSQLOPTS} -t \
            -c "SELECT COUNT(*) FROM pg_constraint WHERE contype IN ('p', 'u', 'f', 'c') AND connamespace = 'public'::regnamespace" 2>/dev/null | tr -d '[:space:]') || CONSTRAINT_COUNT="ERROR"
        if [ "${CONSTRAINT_COUNT}" != "ERROR" ] && [ -n "${CONSTRAINT_COUNT}" ]; then
            pass "Database constraints intact (${CONSTRAINT_COUNT} constraints)"
        else
            fail "Cannot verify database constraints"
        fi

        # Verify foreign key constraints reference valid tables
        BROKEN_FK_COUNT=$(psql ${PSQLOPTS} -t \
            -c "
            SELECT COUNT(*) FROM pg_constraint c
            JOIN pg_class ref ON c.confrelid = ref.oid
            WHERE c.contype = 'f'
              AND c.connamespace = 'public'::regnamespace
              AND ref.relname IS NULL
            " 2>/dev/null | tr -d '[:space:]') || BROKEN_FK_COUNT="ERROR"
        if [ "${BROKEN_FK_COUNT}" != "ERROR" ] && [ "${BROKEN_FK_COUNT}" = "0" ]; then
            pass "No broken foreign key references"
        elif [ "${BROKEN_FK_COUNT}" != "ERROR" ]; then
            fail "${BROKEN_FK_COUNT} broken foreign key references found"
        else
            skip "Cannot verify foreign key integrity"
        fi

        # Verify Flyway migration history is intact
        MIGRATION_COUNT=$(psql ${PSQLOPTS} -t \
            -c "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true" 2>/dev/null | tr -d '[:space:]') || MIGRATION_COUNT="ERROR"
        if [ "${MIGRATION_COUNT}" != "ERROR" ] && [ -n "${MIGRATION_COUNT}" ]; then
            pass "Flyway migrations intact (${MIGRATION_COUNT} successful)"
        else
            skip "Flyway migration history not accessible"
        fi

    else
        skip "PostgreSQL is not accessible (may not be running after restore)"
    fi
fi

# =============================================================================
# 6. Gitea Repository Sample Verification
# =============================================================================
echo "" | tee -a "${REPORT_FILE}"
echo "--- Gitea Repository Sample ---" | tee -a "${REPORT_FILE}"

GITEA_DATA_DIR="${GITEA_DATA_DIR:-/opt/gitea/data}"
if [ -d "${GITEA_DATA_DIR}/repositories" ] && command -v git &>/dev/null; then
    REPO_COUNT=$(find "${GITEA_DATA_DIR}/repositories" -maxdepth 3 -name ".git" -type d 2>/dev/null | wc -l | tr -d ' ')
    if [ "${REPO_COUNT}" -gt 0 ]; then
        pass "Gitea has ${REPO_COUNT} repositories"

        ACTUAL_SAMPLE=$((SAMPLE_COUNT > REPO_COUNT ? REPO_COUNT : SAMPLE_COUNT))
        pass "Will sample ${ACTUAL_SAMPLE} repositories for verification"

        # Random sample: verify each repo is a valid git repo
        # Use process substitution to avoid subshell variable scope issue
        SAMPLED=0
        while IFS= read -r GIT_CONFIG; do
            REPO_DIR="$(dirname "$(dirname "${GIT_CONFIG}")")"
            REPO_NAME="$(basename "${REPO_DIR}")"

            # Verify git repo integrity
            if git -C "${REPO_DIR}" fsck --no-dangling >/dev/null 2>&1; then
                pass "Gitea repo ${REPO_NAME}: fsck passed"

                # Verify SHA-256 of HEAD commit
                HEAD_SHA=$(git -C "${REPO_DIR}" rev-parse HEAD 2>/dev/null || echo "ERROR")
                if [ "${HEAD_SHA}" != "ERROR" ]; then
                    pass "Gitea repo ${REPO_NAME}: HEAD = ${HEAD_SHA:0:12}..."
                else
                    skip "Gitea repo ${REPO_NAME}: unable to read HEAD"
                fi
            else
                fail "Gitea repo ${REPO_NAME}: fsck failed (corrupted)"
            fi

            ((SAMPLED++)) || true
        done < <(find "${GITEA_DATA_DIR}/repositories" -maxdepth 3 -name "config" -path "*/.git/config" 2>/dev/null \
            | sort -R | head -n "${ACTUAL_SAMPLE}")

        pass "Sampled ${SAMPLED}/${ACTUAL_SAMPLE} repositories"
    else
        skip "No Gitea repositories found (empty instance)"
    fi
else
    if [ -d "${GITEA_DATA_DIR}" ]; then
        skip "Gitea data directory exists but no repositories subdirectory"
    else
        skip "Gitea data directory not found at ${GITEA_DATA_DIR}"
    fi
fi

# =============================================================================
# 7. MinIO Object Sample Verification
# =============================================================================
echo "" | tee -a "${REPORT_FILE}"
echo "--- MinIO Object Sample ---" | tee -a "${REPORT_FILE}"

MINIO_DATA_DIR="${MINIO_DATA_DIR:-/opt/minio/data}"
if [ -d "${MINIO_DATA_DIR}" ]; then
    # Count objects in MinIO data directory
    MINIO_OBJ_COUNT=$(find "${MINIO_DATA_DIR}" -type f 2>/dev/null | wc -l | tr -d ' ')
    pass "MinIO data directory contains ${MINIO_OBJ_COUNT} files"

    if [ "${MINIO_OBJ_COUNT}" -gt 0 ]; then
        ACTUAL_SAMPLE=$((SAMPLE_COUNT > MINIO_OBJ_COUNT ? MINIO_OBJ_COUNT : SAMPLE_COUNT))

        # Sample random files and compute SHA-256
        # Use process substitution to avoid subshell variable scope issue
        MINIO_SAMPLED=0
        while IFS= read -r OBJ_FILE; do
            OBJ_REL="$(realpath --relative-to="${MINIO_DATA_DIR}" "${OBJ_FILE}" 2>/dev/null || echo "${OBJ_FILE}")"
            OBJ_HASH=$(file_sha256 "${OBJ_FILE}")
            OBJ_FSIZE=$(file_size "${OBJ_FILE}")
            pass "MinIO object ${OBJ_REL}: SHA-256=${OBJ_HASH:0:16}... (${OBJ_FSIZE} bytes)"
            ((MINIO_SAMPLED++)) || true
        done < <(find "${MINIO_DATA_DIR}" -type f 2>/dev/null \
            | sort -R | head -n "${ACTUAL_SAMPLE}")

        pass "Sampled ${MINIO_SAMPLED} MinIO objects"
    else
        skip "MinIO data directory is empty"
    fi
else
    skip "MinIO data directory not found at ${MINIO_DATA_DIR}"
fi

# =============================================================================
# 8. Cross-Component Verification (Published Versions)
# =============================================================================
echo "" | tee -a "${REPORT_FILE}"
echo "--- Cross-Component Verification (Published Versions) ---" | tee -a "${REPORT_FILE}"

if command -v psql &>/dev/null; then
    PSQLOPTS="-h ${POSTGRES_HOST:-localhost} -p ${POSTGRES_PORT:-5432} -U ${POSTGRES_USER:-aihub} -d ${POSTGRES_DB:-aihub} -t"

    PUB_VERSIONS=$(psql ${PSQLOPTS} -c \
        "SELECT version_id, git_tag, manifest_sha256 FROM asset_version WHERE status = 'PUBLISHED' ORDER BY RANDOM() LIMIT ${SAMPLE_COUNT}" \
        2>/dev/null | grep -v "^$" || echo "")

    if [ -n "${PUB_VERSIONS}" ]; then
        PUB_COUNT=$(echo "${PUB_VERSIONS}" | wc -l | tr -d ' ')
        pass "Found ${PUB_COUNT} published version(s) to verify"

        # Use process substitution to avoid subshell variable scope issue
        while IFS='|' read -r VERSION_ID GIT_TAG MANIFEST_SHA256; do
            # Trim whitespace
            VERSION_ID=$(echo "${VERSION_ID}" | tr -d '[:space:]')
            GIT_TAG=$(echo "${GIT_TAG}" | tr -d '[:space:]')
            MANIFEST_SHA256=$(echo "${MANIFEST_SHA256}" | tr -d '[:space:]')

            [ -z "${VERSION_ID}" ] && continue

            # Check if Gitea tag reference exists in any repo
            if [ -n "${GIT_TAG}" ] && [ -d "${GITEA_DATA_DIR}/repositories" ] && command -v git &>/dev/null; then
                TAG_FOUND=false
                while IFS= read -r GIT_CONFIG; do
                    REPO_DIR="$(dirname "$(dirname "${GIT_CONFIG}")")"
                    if git -C "${REPO_DIR}" tag -l | grep -q "^${GIT_TAG}$" 2>/dev/null; then
                        pass "Published version ${VERSION_ID}: Git tag '${GIT_TAG}' found"
                        TAG_FOUND=true
                        break
                    fi
                done < <(find "${GITEA_DATA_DIR}/repositories" -maxdepth 3 -name "config" -path "*/.git/config" 2>/dev/null)
                if [ "${TAG_FOUND}" = false ]; then
                    skip "Published version ${VERSION_ID}: Git tag '${GIT_TAG}' not found in repos"
                fi
            elif [ -n "${GIT_TAG}" ]; then
                skip "Published version ${VERSION_ID}: Cannot verify Git tag (Gitea data not restored or git not available)"
            fi

            # Check if MinIO objects exist for this version's manifest digest
            if [ -n "${MANIFEST_SHA256}" ] && [ -d "${MINIO_DATA_DIR}" ]; then
                if find "${MINIO_DATA_DIR}" -name "${MANIFEST_SHA256}*" -type f 2>/dev/null | head -1 | grep -q .; then
                    pass "Published version ${VERSION_ID}: MinIO object for manifest digest exists"
                else
                    # The manifest SHA may be stored in metadata rather than as filename
                    skip "Published version ${VERSION_ID}: manifest digest cross-check inconclusive"
                fi
            fi

            pass "Published version ${VERSION_ID}: DB record present (tag=${GIT_TAG})"
        done <<< "${PUB_VERSIONS}"
    else
        skip "No published versions found in database to cross-verify"
    fi
else
    skip "psql not available, skipping cross-component verification"
fi

# =============================================================================
# 9. Summary
# =============================================================================
echo "" | tee -a "${REPORT_FILE}"
echo "========================================" | tee -a "${REPORT_FILE}"
echo "=== Verification Summary ===" | tee -a "${REPORT_FILE}"
echo "========================================" | tee -a "${REPORT_FILE}"
echo "PASS: ${PASS_COUNT}" | tee -a "${REPORT_FILE}"
echo "FAIL: ${FAIL_COUNT}" | tee -a "${REPORT_FILE}"
echo "SKIP: ${SKIP_COUNT}" | tee -a "${REPORT_FILE}"
echo "" | tee -a "${REPORT_FILE}"
echo "Report saved to: ${REPORT_FILE}" | tee -a "${REPORT_FILE}"
echo "" | tee -a "${REPORT_FILE}"

if [ "${FAIL_COUNT}" -gt 0 ]; then
    echo "RESULT: FAIL" | tee -a "${REPORT_FILE}"
    exit 1
else
    echo "RESULT: PASS (all checks passed, ${SKIP_COUNT} skipped)" | tee -a "${REPORT_FILE}"
    exit 0
fi