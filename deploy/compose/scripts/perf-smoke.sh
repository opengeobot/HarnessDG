#!/usr/bin/env bash
# ============================================================================
# 功能: AIHub 性能冒烟测试——覆盖 PRD §13.5 多维度场景。
#       场景 1: 资产搜索（GET /api/v1/assets）
#       场景 2: MCP 搜索（POST /mcp tools/call asset_search）
#       场景 3: 预签名 URL 签发（GET /api/v1/versions/:id/download）
#       场景 4: Multipart 上传会话创建（POST /api/v1/assets/:id/versions/:id/upload-sessions）
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
# 产生过有效测量（PASS 或 FAIL 判定）的场景数。全部 SKIP（如无 token 匿名运行）
# 时不得以 PASS 退出——零测量不构成"通过"，防止虚假绿灯。
MEASURED_SCENARIOS=0

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

  # 写入请求函数到临时文件。
  # 延迟取自 curl 进程内 time_total（单调时钟，含网络+服务端处理），不用
  # shell date 前后差值：Windows Git Bash 下子进程 fork 开销 200-400ms 会
  # 整体抬高读数造成假 FAIL（2026-09-23 实测：shell 侧 P95=617ms vs
  # curl 侧同场景 P95=17ms），CI Linux 下同样消除测量噪声。
  cat > "${scenario_dir}/request.sh" << REQEOF
#!/usr/bin/env bash
hit() {
  local out
  out="\$(curl -s -o /dev/null -w "%{http_code} %{time_total}" -m 15 "\$@" 2>/dev/null || echo "000 0")"
  code="\${out%% *}"
  latency="\${out##* }"
}
one_request() {
  local idx="\$1"
  local code latency
  ${func_body}
  # 单行结果 "<code> <延迟秒>"。延迟样本仅取业务成功(2xx)：鉴权拒绝(401/403)
  # 与限流命中(429)都是快速拒绝，计入会污染 P95；全部被拒 → 样本 0 → SKIP，
  # 强制带鉴权运行才能得到有效测量（收紧而非放宽门禁）。
  echo "\${code} \${latency:-0}" > "${scenario_dir}/\${idx}.res"
}
REQEOF
  chmod +x "${scenario_dir}/request.sh"

  export BASE_URL ACCESS_TOKEN
  seq 1 "${concurrency}" | xargs -P "${concurrency}" -I {} \
    bash -c "source '${scenario_dir}/request.sh' && one_request {}" 2>/dev/null

  # 汇总 P50/P95/P99：从 *.res 提取 2xx 样本的延迟（秒 → 毫秒）
  local samples_file="${scenario_dir}/samples.ms"
  cat "${scenario_dir}"/*.res 2>/dev/null \
    | awk '$1 ~ /^2/ { printf "%d\n", $2 * 1000 }' | sort -n > "${samples_file}" || true
  if [ ! -s "${samples_file}" ]; then
    echo "  SKIP: 无 2xx 采样（需 ACCESS_TOKEN 且请求被服务端受理）"
    return
  fi
  MEASURED_SCENARIOS=$((MEASURED_SCENARIOS + 1))

  # 超阈值时 awk exit 1：在 set -e + pipefail 下裸管道会中止整个脚本、
  # 短路其余场景与汇总；显式 || 捕获让所有场景跑完后由 TOTAL_FAIL 统一失败。
  awk -v n="${concurrency}" -v name="${name}" -v threshold="${threshold_ms}" '
    { a[NR]=$1 }
    END {
      p50_idx = int(0.50 * NR + 0.999999); if (p50_idx < 1) p50_idx = 1; if (p50_idx > NR) p50_idx = NR
      p95_idx = int(0.95 * NR + 0.999999); if (p95_idx < 1) p95_idx = 1; if (p95_idx > NR) p95_idx = NR
      p99_idx = int(0.99 * NR + 0.999999); if (p99_idx < 1) p99_idx = 1; if (p99_idx > NR) p99_idx = NR
      sum = 0
      for (i = 1; i <= NR; i++) sum += a[i]
      printf "  %s: samples=%d/%d P50=%d P95=%d P99=%d avg=%.0f\n", name, NR, n, a[p50_idx], a[p95_idx], a[p99_idx], sum/NR
      if (a[p95_idx] > threshold) { printf "  FAIL: P95=%dms > %dms\n", a[p95_idx], threshold; exit 1 }
      printf "  PASS: P95=%dms <= %dms\n", a[p95_idx], threshold
    }
  ' "${samples_file}" || TOTAL_FAIL=$((TOTAL_FAIL + 1))

  # 错误率（先汇总所有采样再计数；grep 对多文件会逐文件输出 file:count）
  local total_codes error_codes
  total_codes="$(ls "${scenario_dir}"/*.res 2>/dev/null | wc -l)"
  error_codes="$(cat "${scenario_dir}"/*.res 2>/dev/null | awk '{print $1}' | grep -vcE '^(200|201|401|403)$' || true)"
  echo "  错误率: ${error_codes}/${total_codes}"
}

# ---- 场景 1: 资产搜索 ----
run_scenario "asset-search" 100 500 \
  'if [ -n "${ACCESS_TOKEN}" ]; then
    hit -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      "${BASE_URL}/api/v1/assets?limit=20"
  else
    hit "${BASE_URL}/api/v1/assets?limit=20"
  fi'

# ---- 场景 2: MCP 搜索 ----
run_scenario "mcp-search" 20 1000 \
  'mcp_body="{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"asset_search\",\"arguments\":{\"keyword\":\"\",\"limit\":5}}}"
  if [ -n "${ACCESS_TOKEN}" ]; then
    hit -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "${mcp_body}" \
      "${BASE_URL}/mcp"
  else
    hit -H "Content-Type: application/json" \
      -d "${mcp_body}" \
      "${BASE_URL}/mcp"
  fi'

# ---- 场景 3: 预签名 URL 签发（需要 PUBLISHED 版本）----
# 发现逻辑说明（2026-09-23 修正）：
#   * 资产 status 是生命周期态（ACTIVE/DEPRECATED/ARCHIVED），PUBLISHED 是版本状态，
#     旧写法 "assets?status=PUBLISHED" 永远查不到，且首资产可能零版本。
#   * versions 端点不支持 status 查询参数，必须遍历资产后在客户端按版本状态过滤。
#   * VersionView JSON 键序为 versionId,assetId,version,status（record 声明序），可安全正则匹配。
find_version_by_status() {
  local want_status="$1"
  local asset_ids aid ver_resp hit
  asset_ids="$(curl -s -m 10 -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    "${BASE_URL}/api/v1/assets?limit=100" 2>/dev/null \
    | grep -oE '"assetId":"[^"]+"' | sed -E 's/"assetId":"([^"]+)"/\1/' | sort -u | head -n 60)"
  for aid in ${asset_ids}; do
    ver_resp="$(curl -s -m 10 -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      "${BASE_URL}/api/v1/assets/${aid}/versions?limit=50" 2>/dev/null || true)"
    hit="$(echo "${ver_resp}" \
      | grep -oE "\{\"versionId\":\"[^\"]*\",\"assetId\":\"[^\"]*\",\"version\":\"[^\"]*\",\"status\":\"${want_status}\"" \
      | head -n1)"
    if [ -n "${hit}" ]; then
      echo "${aid}|$(echo "${hit}" | sed -nE 's/\{"versionId":"([^"]+)".*/\1/p')"
      return 0
    fi
  done
  return 1
}

PUBLISHED_HIT=""
if [ -n "${ACCESS_TOKEN}" ]; then
  PUBLISHED_HIT="$(find_version_by_status PUBLISHED || true)"
fi
PUBLISHED_ASSET_ID=""; PUBLISHED_VERSION_ID=""
if [ -n "${PUBLISHED_HIT}" ]; then
  PUBLISHED_ASSET_ID="${PUBLISHED_HIT%%|*}"
  PUBLISHED_VERSION_ID="${PUBLISHED_HIT##*|}"
fi

if [ -n "${PUBLISHED_VERSION_ID}" ]; then
  # PRD §13.5 MVP 目标：预签名 URL 签发 P95 ≤ 300ms
  run_scenario "presigned-url" 10 300 \
    "hit -H \"Authorization: Bearer \${ACCESS_TOKEN}\" \
      \"${BASE_URL}/api/v1/versions/${PUBLISHED_VERSION_ID}/download\""
else
  echo ""
  echo "=== presigned-url: SKIP (未提供 ACCESS_TOKEN 或未发现 PUBLISHED 版本) ==="
fi

# ---- 场景 4: Multipart 上传会话创建 ----
# 真实端点为 POST /api/v1/assets/{assetId}/versions/{versionId}/upload-sessions
# （旧写法 /api/v1/uploads/sessions 不存在，兜底 500；假 ID 触发外键违反同样 500）。
# 要求版本处于 DRAFT 态且调用方有 ASSET_MANAGE 权限；每次运行会在对象存储
# 创建开放 multipart 会话（TTL 2 小时后自过期），共享 dev 数据可接受。
DRAFT_HIT=""
if [ -n "${ACCESS_TOKEN}" ]; then
  DRAFT_HIT="$(find_version_by_status DRAFT || true)"
fi
DRAFT_ASSET_ID=""; DRAFT_VERSION_ID=""
if [ -n "${DRAFT_HIT}" ]; then
  DRAFT_ASSET_ID="${DRAFT_HIT%%|*}"
  DRAFT_VERSION_ID="${DRAFT_HIT##*|}"
fi

if [ -n "${DRAFT_VERSION_ID}" ]; then
  run_scenario "multipart-upload" 5 1000 \
    "hit -H \"Authorization: Bearer \${ACCESS_TOKEN}\" \
      -H \"Content-Type: application/json\" \
      -d '{\"totalBytes\":1048576,\"fileCount\":1}' \
      \"${BASE_URL}/api/v1/assets/${DRAFT_ASSET_ID}/versions/${DRAFT_VERSION_ID}/upload-sessions\""
else
  echo ""
  echo "=== multipart-upload: SKIP (未提供 ACCESS_TOKEN 或未发现 DRAFT 版本) ==="
fi

# ---- 汇总 ----
echo ""
echo "=============================="
if [ "${MEASURED_SCENARIOS}" -eq 0 ]; then
  echo "FAIL: 所有场景均无有效测量（零采样不构成通过；请提供 ACCESS_TOKEN 并确认夹具存在）"
  exit 1
fi
if [ "${TOTAL_FAIL}" -gt 0 ]; then
  echo "FAIL: ${TOTAL_FAIL} 场景超过阈值"
  exit 1
fi
echo "PASS: ${MEASURED_SCENARIOS}/4 场景完成测量且 P95 均在阈值内（SKIP 场景已如实报告）"
exit 0
