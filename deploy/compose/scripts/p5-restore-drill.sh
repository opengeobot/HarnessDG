#!/usr/bin/env bash
# ============================================================================
# 功能: P5 E5 备份恢复隔离演练——PG pg_dump、Gitea 卷归档、MinIO mc mirror；
#       销毁 Volume 后全量恢复并 SHA-256 抽检一致性。
# 时间: 2026-07-11
# 作者: AxeXie
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "${ROOT}"

if ! docker info >/dev/null 2>&1; then
  echo "SKIP: Docker 不可用"
  exit 2
fi

COMPOSE_FILE="${COMPOSE_FILE:-deploy/compose/compose.yaml}"
if [ ! -f "${COMPOSE_FILE}" ] && [ -f deploy/compose/docker-compose.yml ]; then
  COMPOSE_FILE="deploy/compose/docker-compose.yml"
fi

if [ "${DRILL_CONFIRM:-}" != "yes" ]; then
  echo "ERROR: 本演练将执行 docker compose down -v 销毁全部 Volume。"
  echo "设置 DRILL_CONFIRM=yes 以继续。"
  exit 1
fi

DRILL_ID="BKR-$(date +%Y%m%d-%H%M%S)"
DRILL_DIR="${DRILL_DIR:-${ROOT}/deploy/backup/drill-${DRILL_ID}}"
mkdir -p "${DRILL_DIR}/minio"

COMPOSE=(docker compose -f "${COMPOSE_FILE}" --env-file deploy/compose/.env)

log() { echo "[${DRILL_ID}] $*"; }

wait_healthy() {
  local svc="$1" tries="${2:-60}"
  local i=0
  while [ "${i}" -lt "${tries}" ]; do
    local state
    state="$("${COMPOSE[@]}" ps --format '{{.Service}} {{.Health}}' "${svc}" 2>/dev/null || true)"
    if [[ "${state}" == *"healthy"* ]] || [[ "${state}" == *"${svc}"*" running" && "${svc}" != "backend" && "${svc}" != "worker" ]]; then
      if [ "${svc}" = "backend" ]; then
        if curl -sf -m 5 http://localhost:8080/actuator/health >/dev/null 2>&1; then
          return 0
        fi
      else
        return 0
      fi
    fi
    sleep 3
    i=$((i + 1))
  done
  log "WARN: ${svc} 未在时限内 healthy"
  return 1
}

minio_mc() {
  "${COMPOSE[@]}" run --rm -T --entrypoint /bin/sh minio-init -c "$1"
}

START_MS="$(date +%s%3N)"
log "=== E5 备份恢复演练开始 ==="
log "备份目录: ${DRILL_DIR}"

# ── 0. 确保栈运行 ──
"${COMPOSE[@]}" up -d
wait_healthy postgres 40
wait_healthy minio 40
wait_healthy gitea 60
wait_healthy backend 80 || true

# ── 1. 植入 MinIO 抽检文件并记录 SHA-256 ──
CANARY_KEY="e5-drill/canary.txt"
CANARY_BODY="harnessdg-e5-drill-canary-$(date +%s)"
echo -n "${CANARY_BODY}" | minio_mc \
  'mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1
   mc pipe "local/dvc-cache/'"${CANARY_KEY}"'" >/dev/null'

HASH_BEFORE="$(minio_mc \
  'mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1
   mc cat "local/dvc-cache/'"${CANARY_KEY}"'"' | sha256sum | awk '{print $1}')"
log "抽检文件 SHA-256 (before): ${HASH_BEFORE}"

# ── 2. PostgreSQL 备份 ──
log "[1/3] pg_dump PostgreSQL..."
"${COMPOSE[@]}" exec -T postgres \
  pg_dump -U aihub -Fc aihub > "${DRILL_DIR}/postgres.dump"
log "  -> ${DRILL_DIR}/postgres.dump ($(wc -c < "${DRILL_DIR}/postgres.dump") bytes)"

# ── 3. Gitea 数据卷归档 ──
log "[2/3] tar gitea-data volume..."
GITEA_VOL="$(docker volume ls -q -f name=gitea-data | head -1)"
if [ -z "${GITEA_VOL}" ]; then
  echo "FAIL: 未找到 gitea-data volume"
  exit 1
fi
docker run --rm \
  -v "${GITEA_VOL}:/data:ro" \
  -v "${DRILL_DIR}:/backup" \
  docker.m.daocloud.io/library/alpine:3.20 \
  tar czf /backup/gitea-data.tar.gz -C /data .
log "  -> ${DRILL_DIR}/gitea-data.tar.gz"

# ── 4. MinIO mc mirror（dvc-cache + asset-staging）──
log "[3/3] mc mirror MinIO buckets..."
mkdir -p "${DRILL_DIR}/minio"
"${COMPOSE[@]}" run --rm --entrypoint /bin/sh \
  -v "${DRILL_DIR}/minio:/backup" \
  minio-init -c '
    mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1
    for b in dvc-cache asset-staging; do
      mc mirror --overwrite "local/$b" "/backup/$b"
    done
  '
if [ ! -f "${DRILL_DIR}/minio/dvc-cache/${CANARY_KEY}" ]; then
  log "FAIL: 备份中缺少抽检文件 ${CANARY_KEY}"
  exit 1
fi
log "  -> ${DRILL_DIR}/minio/{dvc-cache,asset-staging}"

BACKUP_END_MS="$(date +%s%3N)"
log "备份完成，耗时 $(( BACKUP_END_MS - START_MS )) ms"

# ── 5. 销毁 Volume ──
log "docker compose down -v ..."
"${COMPOSE[@]}" down -v

# ── 6. 全新启动 ──
log "docker compose up -d (fresh volumes)..."
"${COMPOSE[@]}" up -d
wait_healthy postgres 40
wait_healthy minio 40
wait_healthy gitea 60
wait_healthy backend 80 || true

# ── 7. PostgreSQL 恢复 ──
log "pg_restore..."
"${COMPOSE[@]}" exec -T postgres \
  pg_restore -U aihub -d aihub --clean --if-exists --no-owner < "${DRILL_DIR}/postgres.dump" \
  || log "pg_restore 返回非零（部分对象可能已存在，继续校验）"

# ── 8. Gitea 卷恢复 ──
log "untar gitea-data..."
GITEA_VOL="$(docker volume ls -q -f name=gitea-data | head -1)"
docker run --rm \
  -v "${GITEA_VOL}:/data" \
  -v "${DRILL_DIR}:/backup:ro" \
  docker.m.daocloud.io/library/alpine:3.20 \
  sh -c 'find /data -mindepth 1 -maxdepth 1 -exec rm -rf {} +; tar xzf /backup/gitea-data.tar.gz -C /data'
"${COMPOSE[@]}" restart gitea
wait_healthy gitea 60 || true

# ── 9. MinIO 恢复 ──
log "mc mirror restore MinIO..."
"${COMPOSE[@]}" run --rm --entrypoint /bin/sh \
  -v "${DRILL_DIR}/minio:/backup:ro" \
  minio-init -c '
    mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1
    for b in dvc-cache asset-staging; do
      if [ -d "/backup/$b" ]; then
        mc mirror --overwrite "/backup/$b" "local/$b"
      fi
    done
  '

# ── 10. SHA-256 抽检 ──
HASH_AFTER="$(minio_mc \
  'mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1
   mc cat "local/dvc-cache/'"${CANARY_KEY}"'"' | sha256sum | awk '{print $1}')"
log "抽检文件 SHA-256 (after):  ${HASH_AFTER}"

END_MS="$(date +%s%3N)"
RTO_MS=$(( END_MS - START_MS ))

if [ "${HASH_BEFORE}" != "${HASH_AFTER}" ]; then
  log "FAIL: SHA-256 不一致 before=${HASH_BEFORE} after=${HASH_AFTER}"
  log "RTO: ${RTO_MS} ms"
  exit 1
fi

log "PASS: SHA-256 抽检一致"
log "RTO (备份→恢复→校验): ${RTO_MS} ms"
log "备份目录保留: ${DRILL_DIR}"
exit 0
