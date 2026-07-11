#!/usr/bin/env bash
# ============================================================================
# 功能: AIHub 搜索接口性能冒烟测试。发起 N 次并发 GET /api/v1/assets 请求，
#       统计 P95 延迟并与 NFR 阈值（默认 500ms）比较。
#       需后端已启动且 BASE_URL 可达；无有效 Token 时以匿名 401 仍计延迟。
#       用于手动 runbook 或可选 CI perf-smoke 作业。
# 时间: 2026-07-11
# 作者: AxeXie
# ============================================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
CONCURRENCY="${CONCURRENCY:-100}"
THRESHOLD_MS="${THRESHOLD_MS:-500}"
SEARCH_PATH="${SEARCH_PATH:-/api/v1/assets?limit=20}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

if ! curl -sf -m 5 "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
  echo "FAIL: 后端不可达 (${BASE_URL}/actuator/health)"
  exit 1
fi

echo "性能冒烟: ${CONCURRENCY} 并发 GET ${SEARCH_PATH}，P95 阈值 ${THRESHOLD_MS}ms"

one_request() {
  local idx="$1"
  local start end elapsed_ms code
  start="$(date +%s%3N)"
  if [ -n "${ACCESS_TOKEN}" ]; then
    code="$(curl -s -o /dev/null -w '%{http_code}' -m 15 \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      "${BASE_URL}${SEARCH_PATH}" 2>/dev/null || echo 000)"
  else
    code="$(curl -s -o /dev/null -w '%{http_code}' -m 15 \
      "${BASE_URL}${SEARCH_PATH}" 2>/dev/null || echo 000)"
  fi
  end="$(date +%s%3N)"
  elapsed_ms=$((end - start))
  echo "${elapsed_ms}" > "${TMP_DIR}/${idx}.ms"
  echo "${code}" > "${TMP_DIR}/${idx}.code"
}

export BASE_URL SEARCH_PATH ACCESS_TOKEN TMP_DIR
export -f one_request

seq 1 "${CONCURRENCY}" | xargs -P "${CONCURRENCY}" -I {} bash -c 'one_request "$@"' _ {}

# 汇总延迟
sort -n "${TMP_DIR}"/*.ms | awk -v n="${CONCURRENCY}" '
  { a[NR]=$1 }
  END {
    if (NR == 0) { print "FAIL: 无采样"; exit 1 }
    p95_idx = int(0.95 * NR + 0.999999)
    if (p95_idx < 1) p95_idx = 1
    if (p95_idx > NR) p95_idx = NR
    sum = 0
    for (i = 1; i <= NR; i++) sum += a[i]
    printf "samples=%d min=%d max=%d avg=%.0f p95=%d\n", NR, a[1], a[NR], sum/NR, a[p95_idx]
  }
' | tee "${TMP_DIR}/summary.txt"

P95="$(awk '/p95=/ { sub(/.*p95=/, ""); print }' "${TMP_DIR}/summary.txt")"
ERRORS="$(grep -vc '^401$\|^200$\|^403$' "${TMP_DIR}"/*.code 2>/dev/null || true)"

echo "HTTP 非预期状态码请求数: ${ERRORS:-0}"

if [ "${P95}" -gt "${THRESHOLD_MS}" ]; then
  echo "FAIL: P95=${P95}ms 超过阈值 ${THRESHOLD_MS}ms"
  exit 1
fi

echo "PASS: P95=${P95}ms <= ${THRESHOLD_MS}ms"
exit 0
