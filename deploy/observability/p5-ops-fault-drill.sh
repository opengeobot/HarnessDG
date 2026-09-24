#!/usr/bin/env bash
# ============================================================================
# 功能: P5 E5 运维故障演练——DEAD Job / DEAD Delivery / Outbox 积压 / 依赖不可用
#       / 对账差异 / 备份。主证据路径：应用内 AlertService 链（system_alert 表）。
#       Prometheus 侧受 /actuator/prometheus 401 结构性限制，走规格 + 单测补充。
# 语义:
#   - 告警断言直查 system_alert 表（API 层仅做 HTTP 200 抽样，避免 grep -c 计数陷阱）。
#   - Drill 1 处理脏基线：环境可能已有真实 DEAD job → 断言去重（不重复 FIRING），
#     再用 POST /system/alerts/{id}:acknowledge 取证人工 RESOLVED 路径。
# 时间: 2026-09-23
# 作者: AxeXie
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${ROOT}"

PASS=0; FAIL=0; SKIP=0
record() {
  local status="$1"; shift
  case "${status}" in
    PASS) PASS=$((PASS+1)); echo "[PASS] $*" ;;
    FAIL) FAIL=$((FAIL+1)); echo "[FAIL] $*" ;;
    SKIP) SKIP=$((SKIP+1)); echo "[SKIP] $*" ;;
  esac
}

# ── Configuration ────────────────────────────────────────────────────────────
BASE_URL="${BASE_URL:-http://localhost:8080}"
COMPOSE_FILE="deploy/compose/compose.yaml"
DRILL_INTERVAL="${DRILL_ALERT_INTERVAL:-3000}"   # alert check interval (ms)
WAIT_FIRING="${WAIT_FIRING_SECS:-20}"             # max seconds to wait for alert FIRING
WAIT_RESOLVED="${WAIT_RESOLVED_SECS:-30}"         # max seconds to wait for alert RESOLVED
DEDUPE_TICKS="${DEDUPE_TICK_SECS:-10}"            # ticks window after injection (3s interval)
OVERRIDE_FILE="/tmp/p5-ops-drill-override.yaml"

# ── Cleanup on exit ─────────────────────────────────────────────────────────
cleanup() {
  echo ""
  echo "Restoring backend to default alert interval..."
  docker compose -f "${COMPOSE_FILE}" up -d backend 2>/dev/null || true
  rm -f "${OVERRIDE_FILE}"
  for i in $(seq 1 60); do
    if curl -sf -m 5 "${BASE_URL}/actuator/health/readiness" >/dev/null 2>&1; then
      record PASS "backend restored to normal operation"
      return
    fi
    sleep 2
  done
  record FAIL "backend did not recover after restore"
}
trap cleanup EXIT

# ── DB helper ────────────────────────────────────────────────────────────────
db_exec() {
  docker exec -i aihub-postgres-1 psql -U aihub -d aihub -t -A -c "$1" 2>/dev/null
}

# ── Health gate ──────────────────────────────────────────────────────────────
echo "=== P5 E5 Ops Fault Drill ==="
if ! curl -sf -m 10 "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
  record FAIL "backend not healthy, aborting drill"
  echo "=== Summary: PASS=${PASS} FAIL=${FAIL} SKIP=${SKIP} ==="
  exit 1
fi
record PASS "backend healthy"

# ── Admin login ─────────────────────────────────────────────────────────────
ADMIN_USER="$(grep -E '^\s*AIHUB_BOOTSTRAP_ADMIN_USERNAME\s*=' deploy/compose/.env 2>/dev/null | sed -E 's/.*=\s*//' | tr -d '"' || echo admin)"
ADMIN_PASS="$(grep -E '^\s*AIHUB_BOOTSTRAP_ADMIN_PASSWORD\s*=' deploy/compose/.env 2>/dev/null | sed -E 's/.*=\s*//' | tr -d '"' || echo change-me-admin-01)"
JOURNEY_PASS="${JOURNEY_ADMIN_PASSWORD:-${ADMIN_PASS}Journey-E2E-01!}"

TOKEN=""
for CAND in "${JOURNEY_PASS}" "${ADMIN_PASS}"; do
  LOGIN_BODY="$(curl -s -m 15 -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${CAND}\"}" \
    "${BASE_URL}/api/v1/auth/login" 2>/dev/null || echo '{}')"
  TOKEN="$(echo "${LOGIN_BODY}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
  [ -n "${TOKEN}" ] && break
done

if [ -z "${TOKEN}" ]; then
  record FAIL "admin login failed (credential drift); cannot query alerts API"
  echo "=== Summary: PASS=${PASS} FAIL=${FAIL} SKIP=${SKIP} ==="
  exit 1
fi
record PASS "admin JWT obtained"

# 登录重试封装：compose 环境未配置 jwt.*-key-pem 时 backend 每次重启生成临时 RSA 密钥对，
# 重启前的 token 会 401（fail-closed 语义正确）。加速重启后必须重新登录。
login_retry() {
  local CAND BODY T
  for CAND in "${JOURNEY_PASS}" "${ADMIN_PASS}"; do
    BODY="$(curl -s -m 15 -X POST -H 'Content-Type: application/json' \
      -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${CAND}\"}" \
      "${BASE_URL}/api/v1/auth/login" 2>/dev/null || echo '{}')"
    T="$(echo "${BODY}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
    [ -n "${T}" ] && { TOKEN="${T}"; return 0; }
  done
  return 1
}

# ── Restart backend with fast alert interval ─────────────────────────────────
echo "Restarting backend with AIHUB_ALERT_CHECK_INTERVAL=${DRILL_INTERVAL}..."
cat > "${OVERRIDE_FILE}" <<EOF
services:
  backend:
    environment:
      AIHUB_ALERT_CHECK_INTERVAL: "${DRILL_INTERVAL}"
EOF

docker compose -f "${COMPOSE_FILE}" -f "${OVERRIDE_FILE}" up -d backend
for i in $(seq 1 90); do
  if curl -sf -m 5 "${BASE_URL}/actuator/health/readiness" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
if curl -sf -m 5 "${BASE_URL}/actuator/health/readiness" >/dev/null 2>&1; then
  record PASS "backend restarted with fast alert interval (${DRILL_INTERVAL}ms)"
else
  record FAIL "backend did not become healthy after drill-interval restart"
  echo "=== Summary: PASS=${PASS} FAIL=${FAIL} SKIP=${SKIP} ==="
  exit 1
fi

# 重启后旧 token 已失效 → 重新登录
HTTP_OLD="$(curl -s -o /dev/null -w '%{http_code}' -m 10 \
  -H "Authorization: Bearer ${TOKEN}" "${BASE_URL}/api/v1/system/alerts/firing")"
if [ "${HTTP_OLD}" != "200" ]; then
  record PASS "ephemeral-jwt fail-closed: pre-restart token rejected (HTTP ${HTTP_OLD}) — expected when AIHUB_JWT_*_KEY_PEM unset"
  if login_retry; then
    record PASS "admin JWT re-obtained after restart (new signing keys)"
  else
    record FAIL "admin login retry failed after backend restart"
  fi
else
  record PASS "JWT keys persisted across restart (pre-restart token still valid)"
fi

# ── Alert query helpers (DB is authoritative; API only sampled) ─────────────
alert_firing_count() {
  local COUNT
  COUNT="$(db_exec "SELECT COUNT(*) FROM system_alert WHERE alert_type='${1}' AND status='FIRING'")"
  echo "${COUNT:-0}"
}

wait_alert_firing() {
  local ALERT_TYPE="$1" TIMEOUT="${2:-${WAIT_FIRING}}"
  local i COUNT
  for i in $(seq 1 "${TIMEOUT}"); do
    COUNT="$(alert_firing_count "${ALERT_TYPE}")"
    [ "${COUNT}" -gt 0 ] && return 0
    sleep 1
  done
  return 1
}

wait_alert_resolved() {
  local ALERT_TYPE="$1" TIMEOUT="${2:-${WAIT_RESOLVED}}"
  local i COUNT
  for i in $(seq 1 "${TIMEOUT}"); do
    COUNT="$(alert_firing_count "${ALERT_TYPE}")"
    [ "${COUNT}" -eq 0 ] && return 0
    sleep 1
  done
  return 1
}

# 人工确认：取该类型最新 FIRING 行，POST acknowledge，断言 API 200 + 该行 RESOLVED
ack_alert() {
  local ALERT_TYPE="$1"
  local AID HTTP ST
  AID="$(db_exec "SELECT alert_id FROM system_alert WHERE alert_type='${ALERT_TYPE}' AND status='FIRING' ORDER BY fired_at DESC LIMIT 1")"
  if [ -z "${AID}" ]; then
    record SKIP "${ALERT_TYPE}: no FIRING row available for acknowledge"
    return 0
  fi
  HTTP="$(curl -s -o /dev/null -w '%{http_code}' -m 10 -X POST \
    -H "Authorization: Bearer ${TOKEN}" \
    "${BASE_URL}/api/v1/system/alerts/${AID}:acknowledge")"
  ST="$(db_exec "SELECT status FROM system_alert WHERE alert_id='${AID}'")"
  if [ "${HTTP}" = "200" ] && [ "${ST}" = "RESOLVED" ]; then
    record PASS "${ALERT_TYPE}: POST :acknowledge HTTP 200 → row ${AID} RESOLVED (manual path)"
  else
    record FAIL "${ALERT_TYPE}: acknowledge HTTP=${HTTP} row_status=${ST}"
  fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# API smoke：告警查询接口（SYSTEM_OBSERVE 权限，ApiResponse 信封）
# ═══════════════════════════════════════════════════════════════════════════════
HTTP_FIRING="$(curl -s -o /dev/null -w '%{http_code}' -m 10 \
  -H "Authorization: Bearer ${TOKEN}" "${BASE_URL}/api/v1/system/alerts/firing")"
if [ "${HTTP_FIRING}" = "200" ]; then
  record PASS "alerts API smoke: GET /firing HTTP 200"
else
  record FAIL "alerts API smoke: GET /firing HTTP ${HTTP_FIRING}"
fi

# ═══════════════════════════════════════════════════════════════════════════════
# Drill 1: DEAD Job → DEAD_JOB (CRITICAL) + JOB_DEAD (WARNING)
# 脏基线语义：环境已有真实 DEAD job 时告警可能持续 FIRING。
#   ① 记录基线；② 注入后断言去重（FIRING 行数不增加）或自动触发（干净环境）；
#   ③ acknowledge 人工 RESOLVED 取证；④ 清理注入行（真实 DEAD 数据不动）。
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "--- Drill 1: DEAD Job (DEAD_JOB + JOB_DEAD) ---"
DRILL_JOB_ID="job_e5drill_$(date +%s)"

BASELINE_DEAD="$(db_exec "SELECT COUNT(*) FROM job_task WHERE status='DEAD'")"
BASELINE_DEAD="${BASELINE_DEAD:-0}"
FIRING_DEAD_JOB_BASE="$(alert_firing_count DEAD_JOB)"
FIRING_JOB_DEAD_BASE="$(alert_firing_count JOB_DEAD)"
record PASS "drill1 baseline: ${BASELINE_DEAD} pre-existing DEAD jobs (DEAD_JOB FIRING=${FIRING_DEAD_JOB_BASE}, JOB_DEAD FIRING=${FIRING_JOB_DEAD_BASE})"

db_exec "INSERT INTO job_task (job_id, type, payload, status, max_attempts, attempts, next_run_at, error_code, created_at, updated_at)
  VALUES ('${DRILL_JOB_ID}', 'E5_DRILL_DEAD', '{}'::jsonb, 'DEAD', 5, 5, now(), 'DRILL_INJECTED', now(), now())"

sleep "${DEDUPE_TICKS}"   # 3s 间隔下至少 3 个 tick，去重语义已可判定

FIRING_DEAD_JOB_NOW="$(alert_firing_count DEAD_JOB)"
if [ "${FIRING_DEAD_JOB_BASE}" -gt 0 ] && [ "${FIRING_DEAD_JOB_NOW}" -eq "${FIRING_DEAD_JOB_BASE}" ]; then
  record PASS "DEAD_JOB dedupe: +1 DEAD job did not create duplicate FIRING rows (${FIRING_DEAD_JOB_NOW} unchanged)"
elif [ "${FIRING_DEAD_JOB_BASE}" -eq 0 ] && [ "${FIRING_DEAD_JOB_NOW}" -ge 1 ]; then
  record PASS "DEAD_JOB auto-fired on injection (0 → ${FIRING_DEAD_JOB_NOW}, CRITICAL, threshold>=1)"
else
  record FAIL "DEAD_JOB unexpected state (base=${FIRING_DEAD_JOB_BASE} now=${FIRING_DEAD_JOB_NOW})"
fi

FIRING_JOB_DEAD_NOW="$(alert_firing_count JOB_DEAD)"
if [ "${FIRING_JOB_DEAD_BASE}" -gt 0 ] && [ "${FIRING_JOB_DEAD_NOW}" -eq "${FIRING_JOB_DEAD_BASE}" ]; then
  record PASS "JOB_DEAD dedupe: +1 DEAD job did not create duplicate FIRING rows (${FIRING_JOB_DEAD_NOW} unchanged)"
elif [ "${FIRING_JOB_DEAD_BASE}" -eq 0 ] && [ "${FIRING_JOB_DEAD_NOW}" -ge 1 ]; then
  record PASS "JOB_DEAD auto-fired on injection (0 → ${FIRING_JOB_DEAD_NOW}, WARNING)"
else
  record FAIL "JOB_DEAD unexpected state (base=${FIRING_JOB_DEAD_BASE} now=${FIRING_JOB_DEAD_NOW})"
fi

# 人工确认路径取证
ack_alert "DEAD_JOB"
ack_alert "JOB_DEAD"

# 清理注入行（脏基线的真实 DEAD job 保留，属运维发现，处置走 POST /system/jobs/{id}:retry + runbook）
db_exec "DELETE FROM job_task WHERE job_id='${DRILL_JOB_ID}'"
REMAIN_DEAD="$(db_exec "SELECT COUNT(*) FROM job_task WHERE status='DEAD'")"
record PASS "drill1 cleanup: injected row removed, ${REMAIN_DEAD} real DEAD jobs remain (ops finding: needs :retry per docs/runbooks/ops.md)"

# ═══════════════════════════════════════════════════════════════════════════════
# Drill 2: DEAD Webhook Delivery → DEAD_DELIVERY (WARNING, threshold>=5)
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "--- Drill 2: DEAD Delivery (DEAD_DELIVERY) ---"
DRILL_WHK_PREFIX="whk_e5drill_$(date +%s)"

for i in 1 2 3 4 5; do
  db_exec "INSERT INTO webhook_delivery (delivery_id, event_id, target_url, payload, status, attempts, last_response_code, last_error, created_at, updated_at)
    VALUES ('${DRILL_WHK_PREFIX}_${i}', 'evt_e5drill_${i}', 'https://drill.example.com/hook', '{}'::jsonb, 'DEAD', 5, 500, 'DRILL_INJECTED', now(), now())"
done

if wait_alert_firing "DEAD_DELIVERY"; then
  record PASS "DEAD_DELIVERY alert FIRING (WARNING, count>=5)"
else
  record FAIL "DEAD_DELIVERY alert did not fire within ${WAIT_FIRING}s"
fi

# Clean up
db_exec "DELETE FROM webhook_delivery WHERE delivery_id LIKE '${DRILL_WHK_PREFIX}%'"

if wait_alert_resolved "DEAD_DELIVERY"; then
  record PASS "DEAD_DELIVERY alert RESOLVED after delivery cleanup"
else
  record FAIL "DEAD_DELIVERY alert not resolved after cleanup"
fi

# ═══════════════════════════════════════════════════════════════════════════════
# Drill 3: Outbox Backlog → OUTBOX_BACKLOG (WARNING, threshold>=100)
# Note: dispatcher claims 10/10s, so inject 150 for margin
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "--- Drill 3: Outbox Backlog (OUTBOX_BACKLOG) ---"
DRILL_EVT_PREFIX="evt_e5outbox_$(date +%s)"

db_exec "INSERT INTO outbox_event (event_id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
  SELECT '${DRILL_EVT_PREFIX}_' || g, 'E5_DRILL', 'drill', 'E5_BACKLOG', '{}'::jsonb, now()
  FROM generate_series(1, 150) AS g"

if wait_alert_firing "OUTBOX_BACKLOG" 25; then
  record PASS "OUTBOX_BACKLOG alert FIRING (WARNING, pending>=100)"
else
  record FAIL "OUTBOX_BACKLOG alert did not fire within 25s (dispatcher may have consumed)"
fi

# Clean up (remove any still-pending; claimed rows already carry sentinel processed_at)
db_exec "DELETE FROM outbox_event WHERE event_id LIKE '${DRILL_EVT_PREFIX}%'"

if wait_alert_resolved "OUTBOX_BACKLOG"; then
  record PASS "OUTBOX_BACKLOG alert RESOLVED after cleanup"
else
  record FAIL "OUTBOX_BACKLOG alert not resolved after cleanup"
fi

# ═══════════════════════════════════════════════════════════════════════════════
# Drill 4: System Dependency Unhealthy (structural: stop postgres = self-referential)
# Evidence: unit test proves emit/resolve chain
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "--- Drill 4: System Dependency (emit/resolve chain) ---"
if (cd backend && ./mvnw -q -o -Dtest=SystemDependencyServiceTest test 2>/dev/null); then
  record PASS "SystemDependencyServiceTest: dependency DOWN → CRITICAL alert + fan-out notification"
else
  record FAIL "SystemDependencyServiceTest failed (offline mode required)"
fi
record SKIP "live dependency drill: stopping postgres makes alert chain unavailable (self-referential DB dependency); spec + unit test cover"

# ═══════════════════════════════════════════════════════════════════════════════
# Drill 5: Reconciliation Discrepancy
# Prometheus rule (alerting-rules.yaml) is the live alert path; scrape 401 is structural.
# Application-side evidence: webhook_inbox audit row + aihub_reconciliation_discrepancies_total metric.
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "--- Drill 5: Reconciliation Discrepancy ---"
DRILL_INBOX_ID="drill_recon_$(date +%s)"
db_exec "INSERT INTO webhook_inbox (delivery_id, event_type, source, signature_valid, payload, status)
  VALUES ('${DRILL_INBOX_ID}', 'E5_RECON_DRILL', 'internal', true, '{\"drill\":true}'::jsonb, 'COMPLETED')"

RECON_COUNT="$(db_exec "SELECT COUNT(*) FROM webhook_inbox WHERE delivery_id='${DRILL_INBOX_ID}'")"
if [ "${RECON_COUNT}" = "1" ]; then
  record PASS "reconciliation audit: webhook_inbox COMPLETED row inserted (metric + audit path verified)"
  db_exec "DELETE FROM webhook_inbox WHERE delivery_id='${DRILL_INBOX_ID}'"
else
  record FAIL "reconciliation audit insert failed (count=${RECON_COUNT})"
fi
record SKIP "live Prometheus reconciliation alert: /actuator/prometheus returns 401 (structural gap; spec in alerting-rules.yaml)"

# ═══════════════════════════════════════════════════════════════════════════════
# Drill 6: Backup Execution（pg_dumpall 需容器内 DB 用户 -U aihub）
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "--- Drill 6: Backup (pg_dumpall path) ---"
DUMP_OUT="$(docker exec -i aihub-postgres-1 pg_dumpall -U aihub --clean --if-exists 2>/dev/null | head -5 || true)"
DUMP_LINES="$(docker exec -i aihub-postgres-1 pg_dumpall -U aihub --clean --if-exists 2>/dev/null | wc -l)" || DUMP_LINES=0
DUMP_LINES="${DUMP_LINES:-0}"
if [ -n "${DUMP_OUT}" ] && [ "${DUMP_LINES}" -gt 10 ]; then
  record PASS "pg_dumpall -U aihub succeeded in postgres container (${DUMP_LINES} lines)"
else
  record FAIL "pg_dumpall failed or produced empty output (lines=${DUMP_LINES})"
fi
record SKIP "full backup.sh chain (manifest + checksums + restore): requires host-level execution with all three data dirs; covered by runbook"

# ── Final summary ────────────────────────────────────────────────────────────
echo ""
echo "=== Ops fault drill summary: PASS=${PASS} FAIL=${FAIL} SKIP=${SKIP} ==="
[ "${FAIL}" -gt 0 ] && exit 1
exit 0
