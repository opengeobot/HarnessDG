#!/usr/bin/env bash
# ============================================================================
# 功能: P5002 预览 Worker E5 压力演练——cgroup 内存隔离（受限副本失败不外溢）、
#       内容级 PREVIEW_LIMIT_EXCEEDED 失败语义（3 次尝试 → DEAD）、PII 金丝雀零泄漏。
#       非侵入：不修改 compose.yaml；受限副本经 docker run 运行时施加 --memory/--cpus，
#       资源参数从运行中的主 worker 容器派生（inspect），演练结束即清理。
# 时间: 2026-09-23
# 作者: AxeXie
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${COMPOSE_DIR}"

PASS=0
FAIL=0
SKIP=0

record() {
  local status="$1"; shift
  case "${status}" in
    PASS) PASS=$((PASS + 1)); echo "[PASS] $*" ;;
    FAIL) FAIL=$((FAIL + 1)); echo "[FAIL] $*" ;;
    SKIP) SKIP=$((SKIP + 1)); echo "[SKIP] $*" ;;
  esac
}

BASE_URL="${BASE_URL:-http://localhost:8080}"
OOM_MEM_LIMIT="${OOM_MEM_LIMIT:-64m}"      # 远小于 JVM 冷启动所需，必触发限制
REPLICA_MEM_LIMIT="${REPLICA_MEM_LIMIT:-512m}"
REPLICA_MAX_BYTES="${REPLICA_MAX_BYTES:-65536}"
CANARY="PIICANARY-$(date +%s)-7f3a9c2e"
OOM_CONTAINER=aihub-p5002-oom
REPLICA_CONTAINER=aihub-p5002-replica
WORKER_ENV_FILE="$(mktemp -u /tmp/p5002_worker_env.XXXXXX)"
JOBS_DIR="$(mktemp -d /tmp/p5002_jobs.XXXXXX)"

cleanup() {
  docker rm -f "${OOM_CONTAINER}" "${REPLICA_CONTAINER}" >/dev/null 2>&1 || true
  rm -f "${WORKER_ENV_FILE}" || true
  rm -rf "${JOBS_DIR}" || true
  # 主栈必须恢复运行（场景 B 会短暂 stop backend 和 worker）
  if ! docker inspect -f '{{.State.Running}}' aihub-worker-1 2>/dev/null | grep -q true; then
    docker compose start worker >/dev/null 2>&1 || true
  fi
  if ! docker inspect -f '{{.State.Running}}' aihub-backend-1 2>/dev/null | grep -q true; then
    docker compose start backend >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

psql_q() {
  local PGU PGD
  PGU="$(grep -E '^\s*POSTGRES_USER\s*=' .env | sed -E 's/.*=\s*//' | tr -d '"')"
  PGD="$(grep -E '^\s*POSTGRES_DB\s*=' .env | sed -E 's/.*=\s*//' | tr -d '"')"
  docker compose exec -T postgres psql -U "${PGU}" -d "${PGD}" -tAc "$1" 2>/dev/null
}

main_stack_alive() {
  curl -sf -m 5 "${BASE_URL}/actuator/health" >/dev/null 2>&1 || return 1
  docker inspect -f '{{.State.Status}}' aihub-worker-1 2>/dev/null | grep -q running
}

echo "=== P5002 Preview Worker Pressure Drill ==="

# ── 前置条件 ──
if ! curl -sf -m 5 "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
  echo "backend not reachable at ${BASE_URL}; run compose verify first"; exit 2
fi
if ! main_stack_alive; then
  echo "main stack (backend+worker) not healthy; abort"; exit 2
fi
record PASS "precondition: compose stack green (backend health + worker running)"

# 副本资源参数派生自主 worker（不改编排文件）
WORKER_IMAGE="$(docker inspect -f '{{.Config.Image}}' aihub-worker-1 2>/dev/null)"
WORKER_NETWORK="$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{break}}{{end}}' aihub-worker-1 2>/dev/null)"
if [ -z "${WORKER_IMAGE}" ] || [ -z "${WORKER_NETWORK}" ]; then
  echo "cannot derive worker image/network from aihub-worker-1"; exit 2
fi
umask 077
# 只复制业务环境变量（剔除 Docker 内建），含口令，用后即删、全程不回显
docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' aihub-worker-1 \
  | grep -vE '^(PATH|HOME|HOSTNAME|LANG|RUST)=|=[[:space:]]*$' > "${WORKER_ENV_FILE}"
if [ ! -s "${WORKER_ENV_FILE}" ]; then
  echo "worker env extraction failed"; exit 2
fi
echo "derived: image=${WORKER_IMAGE##*/} network=${WORKER_NETWORK} env_keys=$(wc -l < "${WORKER_ENV_FILE}")"

# ── 场景 A：cgroup 内存隔离——受限副本自身失败、主栈全程存活 ──
echo "--- Scenario A: cgroup memory isolation (${OOM_MEM_LIMIT}) ---"
HEALTH_PROBE_A=0
HEALTH_FAIL_A=0
docker run -d --name "${OOM_CONTAINER}" --memory "${OOM_MEM_LIMIT}" --cpus 0.5 \
  --network "${WORKER_NETWORK}" --env-file "${WORKER_ENV_FILE}" \
  "${WORKER_IMAGE}" >/dev/null 2>&1
if [ $? -ne 0 ]; then
  record FAIL "scenario A: docker run restricted replica failed to start"
else
  A_EXIT=""
  for _ in $(seq 1 60); do
    STATE="$(docker inspect -f '{{.State.Status}}|{{.State.ExitCode}}|{{.State.OOMKilled}}' "${OOM_CONTAINER}" 2>/dev/null)"
    S_STATUS="${STATE%%|*}"; rest="${STATE#*|}"; S_EXIT="${rest%%|*}"; S_OOM="${rest##*|}"
    if [ "${S_STATUS}" != "running" ] && [ "${S_STATUS}" != "created" ]; then
      A_EXIT="${S_EXIT}"; A_OOM="${S_OOM}"; break
    fi
    # 副本受压期间持续探测主栈
    HEALTH_PROBE_A=$((HEALTH_PROBE_A + 1))
    main_stack_alive || HEALTH_FAIL_A=$((HEALTH_FAIL_A + 1))
    sleep 2
  done
  if [ -z "${A_EXIT:-}" ]; then
    # 未自然退出：若健康检查一直不可达也视为被限制阻止；记录后再不强杀判定
    REPLICA_HEALTHY="$(docker exec "${OOM_CONTAINER}" sh -lc 'wget -q -O - -T 2 http://localhost:8080/actuator/health 2>/dev/null | head -c 40' 2>/dev/null || true)"
    docker kill "${OOM_CONTAINER}" >/dev/null 2>&1 || true
    if [ -n "${REPLICA_HEALTHY}" ]; then
      record FAIL "scenario A: replica survived ${OOM_MEM_LIMIT} cap and served traffic (${REPLICA_HEALTHY})"
    else
      record FAIL "scenario A: replica neither OOM-killed nor health-failing under ${OOM_MEM_LIMIT} (inconclusive)"
    fi
  else
    OOM_LOGS="$(docker logs "${OOM_CONTAINER}" 2>&1 | grep -ciE 'OutOfMemoryError|Killed|SIGKILL' || true)"
    if { [ "${A_OOM}" = "true" ] || [ "${A_EXIT}" = "137" ] || [ "${OOM_LOGS}" -gt 0 ] \
         || [ "${A_EXIT}" != "0" ]; } && [ "${HEALTH_FAIL_A}" -eq 0 ] && [ "${HEALTH_PROBE_A}" -ge 3 ]; then
      record PASS "scenario A: replica terminated under ${OOM_MEM_LIMIT} cgroup cap (exit=${A_EXIT} oomKilled=${A_OOM}); main stack probes=${HEALTH_PROBE_A}/fail=0"
    else
      record FAIL "scenario A: exit=${A_EXIT} oomKilled=${A_OOM} main_failures=${HEALTH_FAIL_A} probes=${HEALTH_PROBE_A}"
    fi
  fi
fi
docker rm -f "${OOM_CONTAINER}" >/dev/null 2>&1 || true

# ── 场景 B：内容级超限失败语义 + PII 金丝雀零泄漏 ──
echo "--- Scenario B: content limit exceeded (maxBytes=${REPLICA_MAX_BYTES}) + PII canary ---"
ADMIN_USER="$(grep -E '^\s*AIHUB_BOOTSTRAP_ADMIN_USERNAME\s*=' .env | sed -E 's/.*=\s*//' | tr -d '"')"
ADMIN_PASS="$(grep -E '^\s*AIHUB_BOOTSTRAP_ADMIN_PASSWORD\s*=' .env | sed -E 's/.*=\s*//' | tr -d '"')"
JOURNEY_PASS="${JOURNEY_ADMIN_PASSWORD:-${ADMIN_PASS}Journey-E2E-01!}"
TOKEN=""
for CAND in "${JOURNEY_PASS}" "${ADMIN_PASS}"; do
  LOGIN_BODY="$(curl -s -m 15 -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${CAND}\"}" \
    "${BASE_URL}/api/v1/auth/login" 2>/dev/null || echo '{}')"
  TOKEN="$(echo "${LOGIN_BODY}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
  [ -n "${TOKEN}" ] && break
done

# 副本注入低 maxBytes（内存放宽到可正常启动）。backend 进程内也跑 JobWorker
# （aihub.job.worker.enabled matchIfMissing=true），故必须同时 stop backend+worker，
# 入队改走副本自身 REST（副本共享同一 postgres，可独立登录/签发 JWT），保证唯一领取者。
docker run -d --name "${REPLICA_CONTAINER}" --memory "${REPLICA_MEM_LIMIT}" --cpus 0.5 \
  --network "${WORKER_NETWORK}" --env-file "${WORKER_ENV_FILE}" \
  -e "AIHUB_PREVIEW_MAX_BYTES=${REPLICA_MAX_BYTES}" -e "AIHUB_PREVIEW_MAX_ROWS=100000" \
  "${WORKER_IMAGE}" >/dev/null 2>&1 \
&& docker compose stop backend worker >/dev/null 2>&1
REPLICA_READY=0
for _ in $(seq 1 60); do
  if docker exec "${REPLICA_CONTAINER}" sh -lc 'wget -q -O - -T 2 http://localhost:8080/actuator/health/readiness 2>/dev/null' 2>/dev/null | grep -q 'UP'; then
    REPLICA_READY=1; break
  fi
  sleep 2
done

if [ "${REPLICA_READY}" -ne 1 ]; then
  record FAIL "scenario B setup: replica_ready=${REPLICA_READY} (see prerequisites)"
  docker compose start backend worker >/dev/null 2>&1 || true
else
  record PASS "scenario B setup: rate-limited replica ready, backend+worker paused (deterministic claim)"

  # 登录与入队都通过 docker exec 打到副本自身 8080（8080 宿主端口经 nginx 指向已停的 backend）
  LOGIN_RESP="$(docker exec "${REPLICA_CONTAINER}" sh -lc \
    "wget -q -O - --header='Content-Type: application/json' --post-data='{\"username\":\"${ADMIN_USER}\",\"password\":\"${JOURNEY_PASS}\"}' http://localhost:8080/api/v1/auth/login 2>/dev/null || wget -q -O - --header='Content-Type: application/json' --post-data='{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}' http://localhost:8080/api/v1/auth/login 2>/dev/null || true")"
  TOKEN="$(echo "${LOGIN_RESP}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"

  # ~120KB CSV：> 副本 maxBytes(64KB)；首行嵌金丝雀。在副本容器内生成并直接 POST。
  docker exec -i "${REPLICA_CONTAINER}" python3 - "${CANARY}" <<'PYEOF'
import csv, io, json, sys, uuid
canary = sys.argv[1]
buf = io.StringIO()
w = csv.writer(buf)
w.writerow(["row_id", "note", "payload"])
w.writerow([0, canary, "x" * 200])
i = 1
while buf.tell() < 120_000:
    w.writerow([i, uuid.uuid4().hex, "x" * 200])
    i += 1
json.dump({"contentType": "text/csv", "content": buf.getvalue()},
          open("/tmp/p5002_payload.json", "w", encoding="utf-8"))
PYEOF
  PAYLOAD_BYTES="$(docker exec "${REPLICA_CONTAINER}" sh -lc 'wc -c < /tmp/p5002_payload.json' 2>/dev/null | tr -d ' ' || echo 0)"
  AID="$(docker exec "${REPLICA_CONTAINER}" sh -lc \
    "wget -q -O - --header='Authorization: Bearer ${TOKEN}' http://localhost:8080/api/v1/assets?limit=1 2>/dev/null" \
    | sed -n 's/.*"assetId":"\([^"]*\)".*/\1/p' | head -1)"
  PREV_ROWS_BEFORE="$(psql_q "select count(*) from asset_preview where asset_id='${AID}'")"
  JOB_ID=""
  if [ -n "${AID}" ] && [ -n "${TOKEN}" ] && [ "${PAYLOAD_BYTES:-0}" -gt "${REPLICA_MAX_BYTES}" ]; then
    GEN_RESP="$(docker exec "${REPLICA_CONTAINER}" sh -lc \
      "wget -q -O - --header='Authorization: Bearer ${TOKEN}' --header='Content-Type: application/json' \
       --post-file=/tmp/p5002_payload.json \
       http://localhost:8080/api/v1/assets/${AID}/previews/generate 2>&1 || true")"
    JOB_ID="$(echo "${GEN_RESP}" | sed -n 's/.*"jobId":"\([^"]*\)".*/\1/p')"
  fi

  if [ -z "${JOB_ID}" ]; then
    record FAIL "scenario B: preview enqueue failed (asset=${AID:-none} payload_bytes=${PAYLOAD_BYTES} resp=${GEN_RESP:-n/a})"
  else
    DEAD=0
    for _ in $(seq 1 45); do
      ROW="$(psql_q "select status||'|'||attempts||'|'||coalesce(error_code,'') from job_task where job_id='${JOB_ID}'")"
      case "${ROW}" in DEAD*) DEAD=1; break ;; esac
      sleep 2
    done
    if [ "${DEAD}" -ne 1 ]; then
      record FAIL "scenario B: job ${JOB_ID} did not reach DEAD (last=${ROW:-none})"
    else
      ATTEMPTS="$(echo "${ROW}" | cut -d'|' -f2)"
      ERRCODE="$(echo "${ROW}" | cut -d'|' -f3)"
      # JobWorker.handleFailure：前两次 attempt 记 FAILED，第三次（终态）记 DEAD——都必须计入
      LIMIT_MSGS="$(psql_q "select count(*) from job_attempt where job_id='${JOB_ID}' and status in ('FAILED','DEAD') and error_message like '%PREVIEW_LIMIT_EXCEEDED%'")"
      if [ "${ATTEMPTS}" = "3" ] && [ "${ERRCODE}" = "INTERNAL_ERROR" ] && [ "${LIMIT_MSGS}" = "3" ]; then
        record PASS "scenario B: oversized preview job DEAD after ${ATTEMPTS} attempts, error_code=${ERRCODE}, 3x PREVIEW_LIMIT_EXCEEDED in job_attempt"
      else
        record FAIL "scenario B: attempts=${ATTEMPTS} error_code=${ERRCODE} limit_msgs=${LIMIT_MSGS} (expect 3/INTERNAL_ERROR/3)"
      fi

      # 无部分产物：该 job 不得为资产写入任何 asset_preview 行
      PREV_ROWS="$(psql_q "select count(*) from asset_preview where asset_id='${AID}'")"
      if [ -n "${PREV_ROWS}" ] && [ "${PREV_ROWS}" = "${PREV_ROWS_BEFORE}" ]; then
        record PASS "scenario B: no partial preview artifact written (asset_preview rows unchanged=${PREV_ROWS})"
      else
        record FAIL "scenario B: asset_preview rows changed after DEAD (${PREV_ROWS_BEFORE}->${PREV_ROWS:-query-failed})"
      fi

      # PII 金丝雀零泄漏：attempt 错误信息 / 副本日志 / 主 worker 日志 / backend 日志
      # （job_task.payload 为业务数据的合法存储位置，不在断言范围）
      CANARY_LEAK=0
      psql_q "select coalesce(string_agg(error_message,''),'') from job_attempt where job_id='${JOB_ID}'" \
        | grep -q "${CANARY}" && CANARY_LEAK=1
      docker logs "${REPLICA_CONTAINER}" 2>&1 | grep -q "${CANARY}" && CANARY_LEAK=1
      docker logs aihub-worker-1 2>&1 | tail -1000 | grep -q "${CANARY}" && CANARY_LEAK=1
      docker logs aihub-backend-1 2>&1 | tail -1000 | grep -q "${CANARY}" && CANARY_LEAK=1
      if [ "${CANARY_LEAK}" -eq 0 ]; then
        record PASS "scenario B: PII canary absent from attempt messages, replica/worker/backend logs"
      else
        record FAIL "scenario B: PII canary leaked into failure path (attempt messages or logs)"
      fi
    fi
  fi

  # 恢复 backend + worker，验证租约/队列恢复后可继续消费
  docker rm -f "${REPLICA_CONTAINER}" >/dev/null 2>&1 || true
  docker compose start backend worker >/dev/null 2>&1
  RECOVERED=0
  for _ in $(seq 1 45); do
    main_stack_alive && { RECOVERED=1; break; }
    sleep 2
  done
  if [ "${RECOVERED}" -eq 1 ]; then
    record PASS "recovery: backend+worker back up, main stack green after drill"
  else
    record FAIL "recovery: main stack not green after restoring primary worker"
  fi
fi

echo ""
echo "=== P5002 pressure drill summary: PASS=${PASS} FAIL=${FAIL} SKIP=${SKIP} ==="
[ "${FAIL}" -gt 0 ] && exit 1
exit 0
