#!/usr/bin/env bash
# ============================================================================
# 功能: AIHub 性能冒烟测试——覆盖 PRD §13.5 多维度场景。
#       场景 1: 资产搜索（GET /api/v1/assets）
#       场景 2: MCP 搜索（POST /mcp tools/call asset_search）
#       场景 3: 预签名 URL 签发（GET /api/v1/versions/:id/download）
#       场景 4: Multipart 上传会话创建（POST /api/v1/uploads/sessions）
#       每个场景记录 P50/P95/P99 和错误率。
#       需后端已启动且 BASE_URL 可达。
# 时间: 2026-07-11
# 作者: AxeXie
# ============================================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

if ! curl -sf -m 5 "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
  echo "FAIL: 后端不可达 (${BASE_URL}/actuator/health)"
  exit 1
fi

TOTAL_FAIL=0

# ---- 通用并发压测函数 ----
# run_scenario <name> <concurrency> <threshold_ms> <request_func_body>
run_scenario() {
  local name="$1" concurrency="$2" threshold_ms="$3"
  shift 3
  local func_body="$*"

  echo ""
  echo "=== ${name}: ${concurrency} 并发, P95 阈值 ${threshold_ms}ms ==="

  local scenario_dir="${TMP_DIR}/${name}"
  mkdir -p "${scenario_dir}"

  # 写入请求函数到临时文件
  cat > "${scenario_dir}/request.sh" << REQEOF
#!/usr/bin/env bash
one_request() {
  local idx="\$1"
  local start end elapsed_ms code
  start="\$(date +%s%3N)"
  ${func_body}
  end="\$(date +%s%3N)"
  elapsed_ms=\$((end - start))
  echo "\${elapsed_ms}" > "${scenario_dir}/\${idx}.ms"
  echo "\${code}" > "${scenario_dir}/\${idx}.code"
}
REQEOF
  chmod +x "${scenario_dir}/request.sh"

  export BASE_URL ACCESS_TOKEN
  seq 1 "${concurrency}" | xargs -P "${concurrency}" -I {} \
    bash -c "source '${scenario_dir}/request.sh' && one_request {}" 2>/dev/null

  # 汇总 P50/P95/P99
  local ms_files
  ms_files="$(ls "${scenario_dir}"/*.ms 2>/dev/null | wc -l)"
  if [ "${ms_files}" -eq 0 ]; then
    echo "  SKIP: 无采样"
    return
  fi

  # 超阈值时 awk exit 1：在 set -e + pipefail 下裸管道会中止整个脚本、
  # 短路其余场景与汇总；显式 || 捕获让所有场景跑完后由 TOTAL_FAIL 统一失败。
  sort -n "${scenario_dir}"/*.ms | awk -v n="${concurrency}" -v name="${name}" -v threshold="${threshold_ms}" '
    { a[NR]=$1 }
    END {
      if (NR == 0) { printf "  %s: FAIL 无采样\n", name; exit 1 }
      p50_idx = int(0.50 * NR + 0.999999); if (p50_idx < 1) p50_idx = 1; if (p50_idx > NR) p50_idx = NR
      p95_idx = int(0.95 * NR + 0.999999); if (p95_idx < 1) p95_idx = 1; if (p95_idx > NR) p95_idx = NR
      p99_idx = int(0.99 * NR + 0.999999); if (p99_idx < 1) p99_idx = 1; if (p99_idx > NR) p99_idx = NR
      sum = 0
      for (i = 1; i <= NR; i++) sum += a[i]
      printf "  %s: samples=%d P50=%d P95=%d P99=%d avg=%.0f\n", name, NR, a[p50_idx], a[p95_idx], a[p99_idx], sum/NR
      if (a[p95_idx] > threshold) { printf "  FAIL: P95=%dms > %dms\n", a[p95_idx], threshold; exit 1 }
      printf "  PASS: P95=%dms <= %dms\n", a[p95_idx], threshold
    }
  ' || TOTAL_FAIL=$((TOTAL_FAIL + 1))

  # 错误率（先汇总所有采样再计数；grep 对多文件会逐文件输出 file:count）
  local total_codes error_codes
  total_codes="$(ls "${scenario_dir}"/*.code 2>/dev/null | wc -l)"
  error_codes="$(cat "${scenario_dir}"/*.code 2>/dev/null | grep -vcE '^(200|201|401|403)$' || true)"
  echo "  错误率: ${error_codes}/${total_codes}"
}

# ---- 场景 1: 资产搜索 ----
run_scenario "asset-search" 100 500 \
  'if [ -n "${ACCESS_TOKEN}" ]; then
    code="$(curl -s -o /dev/null -w "%{http_code}" -m 15 \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      "${BASE_URL}/api/v1/assets?limit=20" 2>/dev/null || echo 000)"
  else
    code="$(curl -s -o /dev/null -w "%{http_code}" -m 15 \
      "${BASE_URL}/api/v1/assets?limit=20" 2>/dev/null || echo 000)"
  fi'

# ---- 场景 2: MCP 搜索 ----
run_scenario "mcp-search" 20 1000 \
  'local mcp_body="{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"asset_search\",\"arguments\":{\"keyword\":\"\",\"limit\":5}}}"
  if [ -n "${ACCESS_TOKEN}" ]; then
    code="$(curl -s -o /dev/null -w "%{http_code}" -m 15 \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "${mcp_body}" \
      "${BASE_URL}/mcp" 2>/dev/null || echo 000)"
  else
    code="$(curl -s -o /dev/null -w "%{http_code}" -m 15 \
      -H "Content-Type: application/json" \
      -d "${mcp_body}" \
      "${BASE_URL}/mcp" 2>/dev/null || echo 000)"
  fi'

# ---- 场景 3: 预签名 URL 签发（需要 PUBLISHED 版本）----
# 从数据库或 API 获取一个已发布版本的 version_id
PUBLISHED_VERSION_ID=""
if [ -n "${ACCESS_TOKEN}" ]; then
  SEARCH_RESP="$(curl -s -m 10 -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    "${BASE_URL}/api/v1/assets?limit=1&status=PUBLISHED" 2>/dev/null || true)"
  FIRST_ASSET_ID="$(echo "${SEARCH_RESP}" | sed -nE 's/.*"assetId"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1)"
  if [ -n "${FIRST_ASSET_ID}" ]; then
    VER_RESP="$(curl -s -m 10 -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      "${BASE_URL}/api/v1/assets/${FIRST_ASSET_ID}/versions?status=PUBLISHED&limit=1" 2>/dev/null || true)"
    PUBLISHED_VERSION_ID="$(echo "${VER_RESP}" | sed -nE 's/.*"versionId"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1)"
  fi
fi

if [ -n "${PUBLISHED_VERSION_ID}" ]; then
  run_scenario "presigned-url" 10 500 \
    "if [ -n \"\${ACCESS_TOKEN}\" ]; then
      code=\"\$(curl -s -o /dev/null -w '%{http_code}' -m 15 \
        -H \"Authorization: Bearer \${ACCESS_TOKEN}\" \
        \"${BASE_URL}/api/v1/versions/${PUBLISHED_VERSION_ID}/download\" 2>/dev/null || echo 000)\"
    else
      code=\"\$(curl -s -o /dev/null -w '%{http_code}' -m 15 \
        \"${BASE_URL}/api/v1/versions/${PUBLISHED_VERSION_ID}/download\" 2>/dev/null || echo 000)\"
    fi"
else
  echo ""
  echo "=== presigned-url: SKIP (无 PUBLISHED 版本) ==="
fi

# ---- 场景 4: Multipart 上传会话创建 ----
run_scenario "multipart-upload" 5 1000 \
  'local upload_body="{\"assetId\":\"ast_perf_test\",\"versionId\":\"ver_perf_test\",\"fileCount\":1,\"totalBytes\":1024}"
  if [ -n "${ACCESS_TOKEN}" ]; then
    code="$(curl -s -o /dev/null -w "%{http_code}" -m 15 \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "${upload_body}" \
      "${BASE_URL}/api/v1/uploads/sessions" 2>/dev/null || echo 000)"
  else
    code="$(curl -s -o /dev/null -w "%{http_code}" -m 15 \
      -H "Content-Type: application/json" \
      -d "${upload_body}" \
      "${BASE_URL}/api/v1/uploads/sessions" 2>/dev/null || echo 000)"
  fi'

# ---- 汇总 ----
echo ""
echo "=============================="
if [ "${TOTAL_FAIL}" -gt 0 ]; then
  echo "FAIL: ${TOTAL_FAIL} 场景超过阈值"
  exit 1
fi
echo "PASS: 所有场景 P95 均在阈值内"
exit 0
