#!/usr/bin/env bash
# ============================================================================
# 功能: P4 Agent 30 分钟接入演练脚本——注册/登录 Agent、MCP 与 REST 端点 curl 示例、
#       证据文件脱敏扫描（禁止 JWT/凭据落盘）。
# 时间: 2026-07-10
# 作者: AxeXie
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EVIDENCE_DIR="${SCRIPT_DIR}/../evidence/p4-onboarding"
BASE_URL="${AIHUB_BASE_URL:-http://localhost:8080}"
API="${BASE_URL}/api/v1"

mkdir -p "${EVIDENCE_DIR}"

log() { printf '%s\n' "$*"; }
redact() { sed -E 's/(accessToken|refreshToken|credential|clientSecret|Bearer )[^", ]+/REDACTED/g'; }

log "=== P4 Agent Onboarding (documentation + safe curl examples) ==="
log ""
log "Prerequisites:"
log "  1. Compose stack running (backend :8080 healthy)"
log "  2. Platform admin JWT (browser login or bootstrap admin)"
log "  3. Agent registered via POST /api/v1/system/agents with tool allowlist"
log ""
log "Step 1 — Register Agent (admin, requires agent:register + authorization:manage)"
log "  curl -sS -X POST ${API}/system/agents \\"
log "    -H 'Authorization: Bearer <ADMIN_ACCESS_JWT>' \\"
log "    -H 'Idempotency-Key: agent-register-demo-001' \\"
log "    -H 'Content-Type: application/json' \\"
log "    -d '{\"displayName\":\"Demo Agent\",\"scopes\":[\"mcp:invoke\",\"asset:read\"],\"allowedTools\":[\"asset_search\",\"asset_get\",\"asset_get_version\",\"asset_request_download\"]}'"
log ""
log "Step 2 — Exchange Agent credential for access JWT (alias endpoint)"
log "  curl -sS -X POST ${API}/auth/agent/token \\"
log "    -H 'Content-Type: application/json' \\"
log "    -d '{\"grantType\":\"client_credentials\",\"subjectId\":\"<AGENT_SUBJECT_ID>\",\"credential\":\"<AGENT_SECRET>\"}'"
log "  # Equivalent: POST ${API}/auth/token"
log ""
log "Step 3 — MCP tools/list (Streamable HTTP JSON-RPC)"
log "  curl -sS -X POST ${BASE_URL}/mcp \\"
log "    -H 'Authorization: Bearer <AGENT_ACCESS_JWT>' \\"
log "    -H 'Content-Type: application/json' \\"
log "    -d '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}'"
log ""
log "Step 4 — MCP asset_search"
log "  curl -sS -X POST ${BASE_URL}/mcp \\"
log "    -H 'Authorization: Bearer <AGENT_ACCESS_JWT>' \\"
log "    -H 'Content-Type: application/json' \\"
log "    -d '{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"asset_search\",\"arguments\":{\"keyword\":\"demo\",\"limit\":5}}}'"
log ""
log "Step 5 — REST Agent API search (read-only profile)"
log "  curl -sS '${API}/agent/assets/search?keyword=demo&limit=5' \\"
log "    -H 'Authorization: Bearer <AGENT_ACCESS_JWT>'"
log ""
log "Step 6 — REST download handle (no presigned URL in response)"
log "  curl -sS -X POST '${API}/agent/versions/<VERSION_ID>/download' \\"
log "    -H 'Authorization: Bearer <AGENT_ACCESS_JWT>' \\"
log "    -H 'Content-Type: application/json' \\"
log "    -d '{\"artifactId\":null}'"
log ""
log "Step 7 — MCP write tool idempotency (write tools disabled by default)"
log "  # When mcp.writeTools.enabled=true, write tools require Idempotency-Key header or _idempotencyKey param:"
log "  curl -sS -X POST ${BASE_URL}/mcp \\"
log "    -H 'Authorization: Bearer <AGENT_ACCESS_JWT>' \\"
log "    -H 'Idempotency-Key: draft-create-001' \\"
log "    -H 'Content-Type: application/json' \\"
log "    -d '{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"asset_create_draft\",\"arguments\":{\"assetId\":\"<ASSET_ID>\",\"version\":\"0.1.0\"}}}'"
log ""

# Safe live probe when stack is reachable (no secrets committed or written).
PROBE_FILE="${EVIDENCE_DIR}/probe-$(date +%Y%m%d-%H%M%S).log"
HTTP_CODE="$(curl -sS -o /dev/null -w '%{http_code}' "${BASE_URL}/actuator/health" 2>/dev/null || echo 000)"
if [ "${HTTP_CODE}" = "200" ]; then
  {
    log "Live probe: backend healthy at ${BASE_URL}"
    curl -sS -X POST "${BASE_URL}/mcp" \
      -H 'Content-Type: application/json' \
      -d '{"jsonrpc":"2.0","id":0,"method":"initialize"}' | redact
    log "---"
    curl -sS -o /dev/null -w 'agent-search-unauth-http=%{http_code}\n' \
      "${API}/agent/assets/search?limit=1"
    curl -sS -o /dev/null -w 'auth-agent-token-missing-body-http=%{http_code}\n' \
      -X POST "${API}/auth/agent/token" -H 'Content-Type: application/json' -d '{}'
  } > "${PROBE_FILE}" 2>&1
  log "Probe evidence written: ${PROBE_FILE}"
else
  log "SKIP live probe: backend not reachable (HTTP ${HTTP_CODE})"
fi

# Secret scan: evidence files must not contain JWT-shaped or raw token strings.
SCAN_FAILED=0
if [ -d "${EVIDENCE_DIR}" ]; then
  if grep -rEn 'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}|"accessToken"\s*:\s*"[^R]|"credential"\s*:\s*"[^<]' "${EVIDENCE_DIR}" 2>/dev/null; then
    log "FAIL secret-scan: token-like value found in evidence files"
    SCAN_FAILED=1
  else
    log "PASS secret-scan: no token material in ${EVIDENCE_DIR}"
  fi
fi

log ""
log "Onboarding script complete. Never commit real credentials or JWTs to Git."
exit "${SCAN_FAILED}"
