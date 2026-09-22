#!/usr/bin/env bash
# ============================================================================
# 功能: P4 Agent 30 分钟接入演练脚本——注册 Agent、凭据交换、MCP/REST 端到端验证；
#       证据文件脱敏扫描（禁止 JWT/凭据落盘）；失败非零退出。
# 时间: 2026-07-11
# 作者: AxeXie
# 预期运行时间: ≤ 30 分钟（含 Compose 冷启动；热栈通常 2–5 分钟）
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(dirname "${SCRIPT_DIR}")"
EVIDENCE_DIR="${SCRIPT_DIR}/../evidence/p4-onboarding"
BASE_URL="${AIHUB_BASE_URL:-http://localhost:8080}"
API="${BASE_URL}/api/v1"
RUN_ID="$(date +%Y%m%d-%H%M%S)"
EVIDENCE_FILE="${EVIDENCE_DIR}/onboarding-${RUN_ID}.log"

mkdir -p "${EVIDENCE_DIR}"

FAILED=0
ADMIN_TOKEN=""
AGENT_TOKEN=""
AGENT_SUBJECT=""
AGENT_CREDENTIAL=""
AGENT_ID=""
VERSION_ID=""

log() { printf '%s\n' "$*"; }
fail() { log "FAIL: $*"; FAILED=1; }
pass() { log "PASS: $*"; }

redact() { sed -E 's/(accessToken|refreshToken|credential|clientSecret|Bearer )[^", ]+/REDACTED/g'; }

get_env() {
  local key="$1"; local def="$2"; local val=""
  for f in "${COMPOSE_DIR}/.env" "${COMPOSE_DIR}/.env.example"; do
    if [ -f "$f" ]; then
      val="$(grep -E "^\s*${key}\s*=" "$f" 2>/dev/null | head -n1 | sed -E "s/^\s*${key}\s*=\s*//")"
      if [ -n "$val" ]; then echo "$val"; return; fi
    fi
  done
  echo "$def"
}

ADMIN_USER="$(get_env AIHUB_BOOTSTRAP_ADMIN_USERNAME admin)"
ADMIN_PASS="$(get_env AIHUB_BOOTSTRAP_ADMIN_PASSWORD change-me-admin-01)"

json_field() {
  local json="$1"; local field="$2"
  printf '%s' "${json}" | sed -nE "s/.*\"${field}\"[[:space:]]*:[[:space:]]*\"([^\"]+)\".*/\1/p" | head -n1
}

http_json() {
  local method="$1"; local path="$2"; local auth="${3:-}"; local body="${4:-}"
  local args=(-sS -m 30 -X "${method}" "${BASE_URL}${path}")
  [ -n "${auth}" ] && args+=(-H "Authorization: Bearer ${auth}")
  [ -n "${body}" ] && args+=(-H 'Content-Type: application/json' -d "${body}")
  curl "${args[@]}" 2>/dev/null
}

assert_http() {
  local method="$1"; local path="$2"; local auth="${3:-}"; local body="${4:-}"; local expected="$5"
  local code
  local args=(-sS -o /dev/null -w '%{http_code}' -m 30 -X "${method}" "${BASE_URL}${path}")
  [ -n "${auth}" ] && args+=(-H "Authorization: Bearer ${auth}")
  [ -n "${body}" ] && args+=(-H 'Content-Type: application/json' -d "${body}")
  code="$(curl "${args[@]}" 2>/dev/null || echo 000)"
  if [ "${code}" != "${expected}" ]; then
    fail "${method} ${path} returned HTTP ${code}, expected ${expected}"
    return 1
  fi
  return 0
}

record_step() {
  local label="$1"; local payload="$2"
  {
    log "--- ${label} ---"
    printf '%s\n' "${payload}" | redact
  } >> "${EVIDENCE_FILE}"
}

log "=== P4 Agent Onboarding E4 (expected runtime ≤ 30 min) ==="
log "Evidence: ${EVIDENCE_FILE}"
log ""

# ---- Step 0: backend health ----
HTTP_CODE="$(curl -sS -o /dev/null -w '%{http_code}' "${BASE_URL}/actuator/health" 2>/dev/null || echo 000)"
if [ "${HTTP_CODE}" != "200" ]; then
  fail "backend not healthy at ${BASE_URL} (HTTP ${HTTP_CODE})"
  log "Ensure: docker compose up -d (from ${COMPOSE_DIR})"
  exit 1
fi
pass "backend healthy"

# ---- Step 1: admin login ----
LOGIN_BODY="$(http_json POST /api/v1/auth/login "" "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}")"
ADMIN_TOKEN="$(json_field "${LOGIN_BODY}" accessToken)"
if [ -z "${ADMIN_TOKEN}" ]; then
  fail "admin login did not return accessToken"
  exit 1
fi
pass "admin login"
record_step "admin-login" "{\"http\":200}"

# ---- Step 2: register agent ----
REGISTER_BODY="$(http_json POST /api/v1/system/agents "${ADMIN_TOKEN}" \
  "{\"displayName\":\"E4 Onboarding Agent\",\"agentType\":\"mcp-client\",\"vendor\":\"internal\",\"maxSensitivityLevel\":1,\"scopes\":[\"mcp:invoke\",\"asset:read\",\"asset:manage\"]}")"
AGENT_ID="$(json_field "${REGISTER_BODY}" agentId)"
AGENT_SUBJECT="$(json_field "${REGISTER_BODY}" principalId)"
AGENT_CREDENTIAL="$(json_field "${REGISTER_BODY}" credential)"
if [ -z "${AGENT_ID}" ] || [ -z "${AGENT_SUBJECT}" ] || [ -z "${AGENT_CREDENTIAL}" ]; then
  fail "agent registration missing agentId/principalId/credential"
  record_step "register-agent" "${REGISTER_BODY}"
  exit 1
fi
pass "agent registered (${AGENT_ID})"
record_step "register-agent" "{\"agentId\":\"${AGENT_ID}\",\"principalId\":\"${AGENT_SUBJECT}\"}"

# ---- Step 3: tool allowlist ----
ALLOWLIST_BODY='{"tools":["asset_search","asset_get","asset_get_version","asset_list_versions","asset_request_download"]}'
ALLOW_HTTP="$(curl -sS -o /dev/null -w '%{http_code}' -m 30 -X PUT \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: e4-allowlist-'"${RUN_ID}" \
  -d "${ALLOWLIST_BODY}" \
  "${API}/system/agents/${AGENT_ID}/tool-allowlist" 2>/dev/null || echo 000)"
if [ "${ALLOW_HTTP}" != "200" ]; then
  fail "tool allowlist update HTTP ${ALLOW_HTTP}"
else
  pass "tool allowlist configured"
fi
record_step "tool-allowlist" "{\"http\":${ALLOW_HTTP}}"

# ---- Step 4: exchange credential ----
TOKEN_BODY="$(http_json POST /api/v1/auth/agent/token "" \
  "{\"grantType\":\"client_credentials\",\"subjectId\":\"${AGENT_SUBJECT}\",\"credential\":\"${AGENT_CREDENTIAL}\"}")"
AGENT_TOKEN="$(json_field "${TOKEN_BODY}" accessToken)"
if [ -z "${AGENT_TOKEN}" ]; then
  fail "agent credential exchange failed"
  record_step "agent-token" "${TOKEN_BODY}"
  exit 1
fi
pass "agent credential exchanged"
record_step "agent-token" "{\"http\":200}"

# ---- Step 5: MCP initialize ----
INIT_RESP="$(http_json POST /mcp "${AGENT_TOKEN}" '{"jsonrpc":"2.0","id":1,"method":"initialize"}')"
if ! printf '%s' "${INIT_RESP}" | grep -q 'protocolVersion'; then
  fail "MCP initialize missing protocolVersion"
else
  pass "MCP initialize"
fi
record_step "mcp-initialize" "${INIT_RESP}"

# ---- Step 6: MCP tools/list ----
LIST_RESP="$(http_json POST /mcp "${AGENT_TOKEN}" '{"jsonrpc":"2.0","id":2,"method":"tools/list"}')"
if ! printf '%s' "${LIST_RESP}" | grep -q 'asset_search'; then
  fail "MCP tools/list missing asset_search"
else
  pass "MCP tools/list"
fi
record_step "mcp-tools-list" "${LIST_RESP}"

# ---- Step 7: MCP asset_search ----
SEARCH_RESP="$(http_json POST /mcp "${AGENT_TOKEN}" \
  '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"asset_search","arguments":{"keyword":"","limit":5}}}')"
if printf '%s' "${SEARCH_RESP}" | grep -q '"isError"[[:space:]]*:[[:space:]]*true'; then
  fail "MCP asset_search returned isError=true"
else
  pass "MCP asset_search"
fi
record_step "mcp-asset-search" "${SEARCH_RESP}"

# ---- Step 8: REST agent search ----
REST_SEARCH="$(http_json GET "/api/v1/agent/assets/search?limit=5" "${AGENT_TOKEN}")"
if ! printf '%s' "${REST_SEARCH}" | grep -q '"data"'; then
  fail "REST agent search missing data envelope"
else
  pass "REST agent search"
fi
record_step "rest-agent-search" "${REST_SEARCH}"

# ---- Step 9: resolve version for download ticket ----
# Use first asset from search if available; otherwise skip download with warning.
ASSET_ID="$(printf '%s' "${REST_SEARCH}" | sed -nE 's/.*"assetId"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1)"
if [ -n "${ASSET_ID}" ]; then
  VERSIONS_RESP="$(http_json GET "/api/v1/agent/assets/${ASSET_ID}/versions?limit=5" "${AGENT_TOKEN}")"
  VERSION_ID="$(printf '%s' "${VERSIONS_RESP}" | sed -nE 's/.*"versionId"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1)"
fi

if [ -n "${VERSION_ID}" ]; then
  DOWNLOAD_RESP="$(http_json POST "/api/v1/agent/versions/${VERSION_ID}/download" "${AGENT_TOKEN}" '{"artifactId":null}')"
  if printf '%s' "${DOWNLOAD_RESP}" | grep -q 'presignedUrl'; then
    fail "download handle leaked presignedUrl"
  elif ! printf '%s' "${DOWNLOAD_RESP}" | grep -q '"versionId"'; then
    fail "download handle missing versionId"
  else
    pass "download ticket (handle only, no presignedUrl)"
  fi
  record_step "download-ticket" "${DOWNLOAD_RESP}"
else
  log "SKIP download ticket: no versionId found (empty catalog is acceptable)"
  record_step "download-ticket" "{\"skipped\":true}"
fi

# ---- Step 10: verify unauthenticated denied ----
assert_http GET /api/v1/agent/assets/search "" "" "401" && pass "unauthenticated agent search denied"

# ---- Secret scan ----
SCAN_FAILED=0
if grep -rEn 'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}|"accessToken"\s*:\s*"[^R]|"credential"\s*:\s*"[^<]' \
    "${EVIDENCE_DIR}" 2>/dev/null; then
  fail "secret-scan: token-like value found in evidence files"
  SCAN_FAILED=1
else
  pass "secret-scan: no token material in ${EVIDENCE_DIR}"
fi

log ""
if [ "${FAILED}" -ne 0 ] || [ "${SCAN_FAILED}" -ne 0 ]; then
  log "Onboarding E4 FAILED. Review ${EVIDENCE_FILE}"
  exit 1
fi
log "Onboarding E4 PASSED. Evidence: ${EVIDENCE_FILE}"
exit 0
