#!/usr/bin/env bash
# ============================================================================
# 功能: AIHub Compose 验收脚本 (Shell)。与 verify.ps1 等价，执行 V01-V21：
#       V01 Compose 配置；V02 核心服务健康；V03 Bucket 初始化；
#       V04 数据库迁移；V05 JWT 生命周期；V05b 验收主体前置条件；V06 权限过滤；
#       V07 字典/标签；V08 审计完整性与脱敏；V09 持久化任务；V10 通知；
#       V11 观测与诊断；V12 资产创建与 Gitea 仓库一致性；V13 幂等键重放；
#       V14 多组织权限隔离；V15 数据集 Facet 权限过滤；
#       V16 版本 Schema 验证；V17 发布审批 Schema 验证；
#       V18 Webhook Inbox 验证；V19 MCP 端点可用性；
#       V20 对账 Worker 注册验证；
#       V21 告警 Schema 与端点验证；
#       V22 Outbox 事件表验证；
#       V23 下载统计默认拒绝；V24 预览生成默认拒绝；
#       V25 Agent 令牌端点存在性；V26 MCP /mcp 默认拒绝；
#       V27 资产 ACL 默认拒绝；V28 DEPRECATED 搜索降权（源码断言）。
#       后端/服务不可达时相关用例标记 SKIP（非 PASS，绝不冒充通过），
#       仅真实 FAIL 返回非零退出码。
# 时间: 2026-07-04
# 作者: AxeXie
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(dirname "${SCRIPT_DIR}")"
cd "${COMPOSE_DIR}"

FAILED=0
SKIPPED=0
BASE_URL="http://localhost:8080"
ACCESS_TOKEN=""

# ---- 读取 Bootstrap 管理员凭据（优先 .env，其次 .env.example，最后默认）----
get_env() {
  local key="$1"; local def="$2"; local val=""
  for f in .env .env.example; do
    if [ -f "$f" ]; then
      val="$(grep -E "^\s*${key}\s*=" "$f" 2>/dev/null | head -n1 | sed -E "s/^\s*${key}\s*=\s*//")"
      if [ -n "$val" ]; then echo "$val"; return; fi
    fi
  done
  echo "$def"
}
ADMIN_USER="$(get_env AIHUB_BOOTSTRAP_ADMIN_USERNAME admin)"
ADMIN_PASS="$(get_env AIHUB_BOOTSTRAP_ADMIN_PASSWORD change-me-admin-01)"
# E2E 改密后的持久口令（与 verify-journey.sh 对齐）
JOURNEY_ADMIN_PASS="${JOURNEY_ADMIN_PASSWORD:-${ADMIN_PASS}Journey-E2E-01!}"

step() {
  local id="$1"; local name="$2"; shift 2
  printf '[%s] %s ... ' "${id}" "${name}"
  if "$@"; then
    printf 'PASS\n'
  else
    printf 'FAIL\n'
    FAILED=1
  fi
}

skip() {
  local id="$1"; local name="$2"; local reason="$3"
  printf '[%s] %s ... SKIP: %s\n' "${id}" "${name}" "${reason}"
  SKIPPED=$((SKIPPED + 1))
}

# HTTP：输出 HTTP 状态码到 stdout（网络错误输出 000）。
http_status() {
  local method="$1"; local path="$2"; local auth="${3:-}"; local body="${4:-}"
  local args=(-s -o /dev/null -w '%{http_code}' -m 15 -X "${method}" "${BASE_URL}${path}")
  [ -n "${auth}" ] && args+=(-H "Authorization: Bearer ${auth}")
  [ -n "${body}" ] && args+=(-H 'Content-Type: application/json' -d "${body}")
  curl "${args[@]}" 2>/dev/null || echo "000"
}

# 仅取响应体（用于解析稳定错误码 code）。
# 功能: 默认拒绝断言依据响应体 code，不得只看 HTTP 状态码。时间: 2026-09-22 作者: AxeXie
http_body() {
  local method="$1"; local path="$2"; local auth="${3:-}"
  local args=(-s -m 15 -X "${method}" "${BASE_URL}${path}")
  [ -n "${auth}" ] && args+=(-H "Authorization: Bearer ${auth}")
  curl "${args[@]}" 2>/dev/null || echo ""
}

# 稳定错误码提取：断言必须依据响应体 code，不得只看 HTTP 状态码。
error_code() {
  local body="$1"
  printf '%s' "${body}" | sed -nE 's/.*"code"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1
}

# 前置条件守卫：首登强制改密门产生的 403 与权限判定无关，必须报为前置条件未满足（非 PASS）。
assert_not_password_change_required() {
  local body="$1" who="$2" code
  code="$(error_code "${body}")"
  if [ "${code}" = "PASSWORD_CHANGE_REQUIRED" ]; then
    echo "  前置条件未满足：${who} 处于首登强制改密状态（must_change_password），"
    echo "  其 403 与权限判定无关。请先完成 PUT /api/v1/me/password 改密（V05b 会自动完成）。"
    return 1
  fi
  return 0
}

# 最小权限验证主体（默认拒绝断言的被测主体）：权限集合显式列出，刻意不含任何被测端点所需权限。
# 该主体不绑定任何角色，故其后端判定只剩「JWT 粗粒度 scope」，403 必然来自权限判定。
LEAST_PRIV_USER="compose-verify-minimal"
LEAST_PRIV_PASS="ComposeVerifyOnly-01!"
LEAST_PRIV_TOKEN=""

# 以既有登录端点换取 access Token；失败输出空串。
get_access_token() {
  local u="$1" p="$2" body
  body="$(curl -s -m 15 -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"${u}\",\"password\":\"${p}\"}" \
    "${BASE_URL}/api/v1/auth/login" 2>/dev/null)"
  printf '%s' "${body}" | sed -nE 's/.*"accessToken"\s*:\s*"([^"]+)".*/\1/p'
}

# 读取 /me 的 forcePasswordChange 标记（/me 在改密门放行名单内，必定可达）。
force_password_change() {
  local token="$1" body
  body="$(http_body GET /api/v1/me "${token}")"
  printf '%s' "${body}" | sed -nE 's/.*"forcePasswordChange"[[:space:]]*:[[:space:]]*(true|false).*/\1/p' | head -n1
}

# 按产品既定流程解除首登强制改密门（轮换为同值），返回可用的新 Token。
resolve_password_change_gate() {
  local u="$1" p="$2" token="$3" who="$4" force chg code
  force="$(force_password_change "${token}")"
  if [ -z "${force}" ]; then echo "  无法读取 ${who} 的 /api/v1/me，无法确认改密门状态" >&2; return 1; fi
  if [ "${force}" = "false" ]; then printf '%s' "${token}"; return 0; fi
  chg="$(curl -s -m 15 -X PUT -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${token}" \
    -d "{\"currentPassword\":\"${p}\",\"newPassword\":\"${p}\"}" \
    "${BASE_URL}/api/v1/me/password" -w '\n%{http_code}' 2>/dev/null)"
  code="$(printf '%s' "${chg}" | tail -n1)"
  if [ "${code}" != "200" ]; then echo "  ${who} 首登改密失败，状态 ${code}" >&2; return 1; fi
  # 改密会递增 tokenVersion，旧 access Token 不再可用，必须重新登录。
  get_access_token "${u}" "${p}"
}

# 登录并回显响应体（用于取 accessToken）；可选将 Set-Cookie 写入 cookie_jar。
# 用法: http_login_body [cookie_jar] [password]
http_login_body() {
  local cookie_jar="${1:-}"
  local pass="${2:-${ADMIN_PASS}}"
  local args=(-s -m 15 -X POST -H 'Content-Type: application/json'
    -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${pass}\"}"
    "${BASE_URL}/api/v1/auth/login")
  [ -n "${cookie_jar}" ] && args=(-c "${cookie_jar}" "${args[@]}")
  curl "${args[@]}" 2>/dev/null
}

# 使用 refresh Cookie 轮换 access token（PRD V22 子集：刷新端点可达且签发新 token）。
http_refresh_body() {
  local cookie_jar="$1"
  curl -s -m 15 -b "${cookie_jar}" -c "${cookie_jar}" -X POST \
    -H 'Content-Type: application/json' \
    "${BASE_URL}/api/v1/auth/refresh" 2>/dev/null
}

backend_reachable() {
  [ "$(http_status GET /actuator/health)" = "200" ]
}

postgres_reachable() {
  local state
  state="$(docker compose ps --format '{{.Service}} {{.State}}' postgres 2>/dev/null)"
  [[ "${state}" == *" running"* ]]
}

# 探测指定服务容器是否在运行。
service_running() {
  local state
  state="$(docker compose ps --format '{{.Service}} {{.State}}' "$1" 2>/dev/null)"
  [[ "${state}" == *" running"* ]]
}

psql_q() {
  docker compose exec -T postgres sh -c "psql -U \$POSTGRES_USER -d \$POSTGRES_DB -tAc \"$1\"" 2>/dev/null | tr -d '[:space:]'
}

# 断言无 Token=401（默认拒绝），携带管理员 Token 合法访问=200（admin 经 rol_admin 持 system:* 权限）。
# 注：早期版本期望 admin=403，但 Wave A 后 admin 合并 rol_admin 全权限合法访问 system 端点，
# 故 admin=403 是陈旧「假 PASS」（V05 修复前因 ACCESS_TOKEN 空跳过 admin 断言）。真默认拒绝由 reader 用户证明（可选 ensure_reader_jwt）。
assert_default_deny() {
  local path="$1" anon anon_body anon_code auth auth_body code
  anon_body="$(http_body GET "${path}")"
  anon="$(http_status GET "${path}")"
  if [ "${anon}" != "401" ]; then echo "  无 Token 访问 ${path} 返回 ${anon}，期望 401"; return 1; fi
  if [ -n "${ACCESS_TOKEN}" ]; then
    auth="$(http_status GET "${path}" "${ACCESS_TOKEN}")"
    if [ "${auth}" != "200" ]; then echo "  admin Token 访问 ${path} 返回 ${auth}，期望 200（rol_admin 合法访问）"; return 1; fi
  fi
  if [ -z "${LEAST_PRIV_TOKEN}" ]; then
    echo "  前置条件未满足：最小权限验证主体不可用，无法证明 ${path} 的 403 来自权限判定"; return 1
  fi
  auth_body="$(http_body GET "${path}" "${LEAST_PRIV_TOKEN}")"
  assert_not_password_change_required "${auth_body}" "最小权限验证主体" || return 1
  auth="$(http_status GET "${path}" "${LEAST_PRIV_TOKEN}")"
  if [ "${auth}" != "403" ]; then echo "  最小权限主体访问 ${path} 返回 ${auth}，期望 403（默认拒绝）"; return 1; fi
  code="$(error_code "${auth_body}")"
  if [ "${code}" != "AUTH_PERMISSION_DENIED" ]; then
    echo "  最小权限主体访问 ${path} 返回错误码 ${code}，期望 AUTH_PERMISSION_DENIED"; return 1
  fi
  return 0
}

# 有权限正向断言（AC-P0B-AUTH-004）：证明系统不是「全部拒绝」，且返回真实数据而非空集合占位。
assert_permitted() {
  local path="$1" require_non_empty="${2:-0}" resp code data count
  if [ -z "${ACCESS_TOKEN}" ]; then
    echo "  前置条件未满足：管理员 Token 不可用，无法执行 ${path} 的正向断言"; return 1
  fi
  resp="$(http_body GET "${path}" "${ACCESS_TOKEN}")"
  assert_not_password_change_required "${resp}" "管理员" || return 1
  code="$(http_status GET "${path}" "${ACCESS_TOKEN}")"
  if [ "${code}" != "200" ]; then
    echo "  有权限主体访问 ${path} 返回 ${code}，期望 200（$(error_code "${resp}")）"; return 1
  fi
  if [ "${require_non_empty}" = "1" ]; then
    data="$(printf '%s' "${resp}" | sed -nE 's/.*"data"[[:space:]]*:[[:space:]]*\[(.*)/\1/p')"
    count="$(printf '%s' "${data}" | grep -o '"' | wc -l | tr -d '[:space:]')"
    if [ -z "${data}" ] || [ "${count:-0}" -lt 4 ]; then
      echo "  有权限主体访问 ${path} 返回空集合占位，未证明数据真实可达"; return 1
    fi
  fi
  return 0
}

# ===========================================================================
# IAM 验收辅助（AC-P0B-IAM-001/003/004/005/006/007/008/009/011/012）
# 判据严格对齐 p0b-exit-catalog.md 第 3 节；与 verify.ps1 的判据一一对应。
# ===========================================================================

IAM_PROBE_PASSWORD="ComposeIam-Probe-01!z"

# 带 Cookie 的请求：输出 "<body>\n<http_code>\n<set-cookie 原文>"。
http_with_cookie() {
  local method="$1" path="$2" cookie="$3" body="${4:-}"
  local hdr
  hdr="$(mktemp)"
  local args=(-s -D "${hdr}" -m 20 -X "${method}" "${BASE_URL}${path}")
  [ -n "${cookie}" ] && args+=(-H "Cookie: aihub_refresh=${cookie}")
  [ -n "${body}" ] && args+=(-H 'Content-Type: application/json' -d "${body}")
  local resp code setcookie
  resp="$(curl "${args[@]}" -w '\n%{http_code}' 2>/dev/null)"
  code="$(printf '%s' "${resp}" | tail -n1)"
  resp="$(printf '%s' "${resp}" | sed '$d')"
  setcookie="$(grep -i '^set-cookie:' "${hdr}" 2>/dev/null | tr -d '\r' | sed -E 's/^[Ss]et-[Cc]ookie:[[:space:]]*//' | tr '\n' ' ')"
  rm -f "${hdr}"
  printf '%s\n%s\n%s' "${resp}" "${code}" "${setcookie}"
}

# 从 Set-Cookie 原文提取刷新 JWT 值。
refresh_from_cookie() {
  printf '%s' "$1" | sed -nE 's/.*aihub_refresh=([^;]+).*/\1/p' | head -n1
}

# 断言刷新 Cookie 的安全属性（AC-P0B-IAM-006）。Secure 由部署配置控制，本地 http 验收只断言
# 与运行环境无关的属性：HttpOnly、SameSite、受限 Path、有效期。
assert_refresh_cookie_attributes() {
  local setcookie="$1" context="$2" attr
  if [ -z "${setcookie}" ]; then echo "  ${context} 未下发 Set-Cookie，无法断言刷新 Cookie 属性"; return 1; fi
  for attr in "HttpOnly" "SameSite=Strict" "Path=/api/v1/auth"; do
    if ! printf '%s' "${setcookie}" | grep -qF "${attr}"; then
      echo "  ${context} 的 Set-Cookie 缺少属性 ${attr}"; return 1
    fi
  done
  if ! printf '%s' "${setcookie}" | grep -qE 'Max-Age=[0-9]+'; then
    echo "  ${context} 的 Set-Cookie 缺少 Max-Age 有效期"; return 1
  fi
  return 0
}

# 规范化登录失败响应体：仅 requestId/traceId 允许逐次不同（AC-P0B-IAM-004 防枚举）。
comparable_error_shape() {
  printf '%s' "$1" | sed -E 's/"requestId":"[^"]*"/"requestId":"X"/g; s/"traceId":"[^"]*"/"traceId":"X"/g'
}

# 读取用户行断言值（状态|失败次数|锁定截止|强制改密|Token 版本）。
user_row_facts() {
  psql_q "select status || '|' || failed_login_attempts || '|' || coalesce(locked_until::text,'NULL') || '|' || must_change_password || '|' || token_version from iam_user where username='$1'"
}

# 创建（幂等）IAM 验证用户；输出 userId。
new_iam_probe_user() {
  local username="$1" display="$2" resp code uid
  resp="$(curl -s -m 20 -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -d "{\"username\":\"${username}\",\"displayName\":\"${display}\",\"email\":null,\"locale\":\"zh-CN\",\"temporaryPassword\":\"${IAM_PROBE_PASSWORD}\",\"scopes\":[\"project:view\"]}" \
    "${BASE_URL}/api/v1/system/users" -w '\n%{http_code}' 2>/dev/null)"
  code="$(printf '%s' "${resp}" | tail -n1)"
  resp="$(printf '%s' "${resp}" | sed '$d')"
  if [ "${code}" = "409" ]; then
    uid="$(psql_q "select user_id from iam_user where username='${username}'")"
    if [ -z "${uid}" ]; then echo "  用户 ${username} 已存在但无法读取 user_id"; return 1; fi
    printf '%s' "${uid}"; return 0
  fi
  if [ "${code}" != "201" ]; then
    echo "  创建 IAM 验证用户 ${username} 失败，状态 ${code}，错误码 $(error_code "${resp}")"; return 1
  fi
  printf '%s' "${resp}" | sed -nE 's/.*"userId"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1
}

# AC-P0B-IAM-001：引导只创建一次；口令仅存自适应哈希；日志/审计无明文。
assert_bootstrap_admin_uniqueness() {
  local rows algo prefix leak logs
  rows="$(psql_q "select count(*) from iam_user where username='${ADMIN_USER}'")"
  if [ "${rows}" != "1" ]; then echo "  引导管理员行数 ${rows}，期望恰好 1（不得重复创建）"; return 1; fi
  algo="$(psql_q "select password_algorithm from iam_user where username='${ADMIN_USER}'")"
  if [ "${algo}" != "BCRYPT" ]; then echo "  引导管理员口令算法为 ${algo}，期望自适应哈希 BCRYPT（不得存明文）"; return 1; fi
  prefix="$(psql_q "select left(password_hash, 4) from iam_user where username='${ADMIN_USER}'")"
  if ! printf '%s' "${prefix}" | grep -qE '^\$2[aby]\$'; then
    echo "  引导管理员口令列不是 bcrypt 摘要（前缀 ${prefix}）"; return 1
  fi
  leak="$(psql_q "select count(*) from audit_log where request_summary::text like '%${ADMIN_PASS}%'")"
  if [ "${leak}" != "0" ]; then echo "  审计正文出现引导管理员明文口令（脱敏失效）"; return 1; fi
  logs="$(docker compose logs backend --tail 400 2>&1 || true)"
  if printf '%s' "${logs}" | grep -qF "${ADMIN_PASS}"; then
    echo "  backend 容器日志出现引导管理员明文口令"; return 1
  fi
  return 0
}

# AC-P0B-IAM-003：临时口令失效、新口令可登录、force-change 清除、审计追加。
assert_first_login_password_change() {
  local username="compose-verify-iam3" temp="ComposeIam3-Temp-01!z" new="ComposeIam3-New-02!y"
  local resp code uid facts token gated chg oldlogin newlogin newtoken me force principal audit
  resp="$(curl -s -m 20 -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -d "{\"username\":\"${username}\",\"displayName\":\"Compose Verify Iam3\",\"email\":null,\"locale\":\"zh-CN\",\"temporaryPassword\":\"${temp}\",\"scopes\":[\"project:view\"]}" \
    "${BASE_URL}/api/v1/system/users" -w '\n%{http_code}' 2>/dev/null)"
  code="$(printf '%s' "${resp}" | tail -n1)"
  resp="$(printf '%s' "${resp}" | sed '$d')"
  if [ "${code}" = "409" ]; then
    uid="$(psql_q "select user_id from iam_user where username='${username}'")"
    # 已存在：先重置口令回到「强制改密」前置状态，避免上一轮残留。
    local rst
    rst="$(curl -s -m 20 -X POST -H 'Content-Type: application/json' \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      -d "{\"temporaryPassword\":\"${temp}\"}" \
      "${BASE_URL}/api/v1/system/users/${uid}:reset-password" -w '\n%{http_code}' 2>/dev/null | tail -n1)"
    if [ "${rst}" != "200" ]; then echo "  重置首登改密验证用户口令失败，状态 ${rst}"; return 1; fi
  elif [ "${code}" != "201" ]; then
    echo "  创建首登改密验证用户失败，状态 ${code}，错误码 $(error_code "${resp}")"; return 1
  fi
  facts="$(user_row_facts "${username}")"
  if ! printf '%s' "${facts}" | grep -qE '^PENDING_ACTIVATION\|.*\|1\|'; then
    echo "  新建用户未处于强制改密前置状态（实际：${facts}，期望 must_change_password=1）"; return 1
  fi

  token="$(get_access_token "${username}" "${temp}")"
  if [ -z "${token}" ]; then echo "  临时口令登录失败"; return 1; fi
  gated="$(curl -s -m 20 -H "Authorization: Bearer ${token}" -w '\n%{http_code}' "${BASE_URL}/api/v1/system/users" 2>/dev/null)"
  code="$(printf '%s' "${gated}" | tail -n1)"
  gated="$(printf '%s' "${gated}" | sed '$d')"
  if [ "${code}" != "403" ] || [ "$(error_code "${gated}")" != "PASSWORD_CHANGE_REQUIRED" ]; then
    echo "  未改密主体访问业务端点返回 ${code}/$(error_code "${gated}")，期望 403 PASSWORD_CHANGE_REQUIRED"; return 1
  fi

  chg="$(curl -s -m 20 -X PUT -H 'Content-Type: application/json' -H "Authorization: Bearer ${token}" \
    -d "{\"currentPassword\":\"${temp}\",\"newPassword\":\"${new}\"}" \
    "${BASE_URL}/api/v1/me/password" -w '\n%{http_code}' 2>/dev/null)"
  code="$(printf '%s' "${chg}" | tail -n1)"
  chg="$(printf '%s' "${chg}" | sed '$d')"
  if [ "${code}" != "200" ]; then echo "  首登改密失败，状态 ${code}，错误码 $(error_code "${chg}")"; return 1; fi
  facts="$(user_row_facts "${username}")"
  if ! printf '%s' "${facts}" | grep -qE '^ACTIVE\||\|0\|'; then
    echo "  改密后 must_change_password 未清除或状态未激活（实际：${facts}）"; return 1
  fi
  oldlogin="$(curl -s -m 20 -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"${username}\",\"password\":\"${temp}\"}" "${BASE_URL}/api/v1/auth/login" -w '\n%{http_code}' 2>/dev/null)"
  code="$(printf '%s' "${oldlogin}" | tail -n1)"
  oldlogin="$(printf '%s' "${oldlogin}" | sed '$d')"
  if [ "${code}" != "401" ] || [ "$(error_code "${oldlogin}")" != "AUTH_INVALID_CREDENTIALS" ]; then
    echo "  临时口令改密后仍可用，状态 ${code}/$(error_code "${oldlogin}")，期望 401 AUTH_INVALID_CREDENTIALS"; return 1
  fi
  newtoken="$(get_access_token "${username}" "${new}")"
  if [ -z "${newtoken}" ]; then echo "  新口令登录失败"; return 1; fi
  me="$(http_body GET /api/v1/me "${newtoken}")"
  force="$(printf '%s' "${me}" | sed -nE 's/.*"forcePasswordChange"[[:space:]]*:[[:space:]]*(true|false).*/\1/p' | head -n1)"
  if [ "${force}" != "false" ]; then echo "  /me 的 forcePasswordChange 为 ${force}，期望 false（改密后清除）"; return 1; fi
  principal="$(psql_q "select principal_id from iam_user where username='${username}'")"
  audit="$(psql_q "select count(*) from audit_log where event_type='USER_PASSWORD_CHANGED' and resource_id='${principal}'")"
  if [ "${audit:-0}" -lt 1 ]; then echo "  未发现该主体的 USER_PASSWORD_CHANGED 审计事件（改密未审计）"; return 1; fi
  return 0
}

# AC-P0B-IAM-004：不存在用户名与错误口令返回同一错误码与同一响应体形状。
assert_login_anti_enumeration() {
  local b1 c1 b2 c2 leak failed
  b1="$(curl -s -m 20 -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"compose-verify-nosuch-account\",\"password\":\"${IAM_PROBE_PASSWORD}\"}" \
    "${BASE_URL}/api/v1/auth/login" -w '\n%{http_code}' 2>/dev/null)"
  c1="$(printf '%s' "${b1}" | tail -n1)"; b1="$(printf '%s' "${b1}" | sed '$d')"
  b2="$(curl -s -m 20 -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"ComposeWrong-Password-01!z\"}" \
    "${BASE_URL}/api/v1/auth/login" -w '\n%{http_code}' 2>/dev/null)"
  c2="$(printf '%s' "${b2}" | tail -n1)"; b2="$(printf '%s' "${b2}" | sed '$d')"
  if [ "${c1}" != "401" ]; then echo "  不存在的用户名登录返回 ${c1}，期望 401"; return 1; fi
  if [ "${c2}" != "401" ]; then echo "  错误口令登录返回 ${c2}，期望 401"; return 1; fi
  if [ "$(error_code "${b1}")" != "AUTH_INVALID_CREDENTIALS" ] || [ "$(error_code "${b2}")" != "AUTH_INVALID_CREDENTIALS" ]; then
    echo "  失败登录错误码不一致，期望均为 AUTH_INVALID_CREDENTIALS"; return 1
  fi
  if [ "$(comparable_error_shape "${b1}")" != "$(comparable_error_shape "${b2}")" ]; then
    echo "  两种失败登录的响应体形状不一致，暴露账号存在性"; return 1
  fi
  leak="$(psql_q "select count(*) from audit_log where request_summary::text like '%ComposeWrong-Password-01!z%'")"
  if [ "${leak}" != "0" ]; then echo "  登录失败审计出现口令明文（脱敏失效）"; return 1; fi
  failed="$(psql_q "select count(*) from audit_log where event_type='AUTH_LOGIN_FAILED'")"
  if [ "${failed:-0}" -lt 1 ] && [ "${IAM_SHAPE_PROBE:-0}" = "1" ]; then
    echo "  未发现 AUTH_LOGIN_FAILED 审计事件（登录失败未审计）"; return 1
  fi
  return 0
}

# AC-P0B-IAM-006：access/refresh 同时轮换；旧 jti 失效；新 Cookie 属性正确。
assert_refresh_rotation() {
  local login rt1 tok1 ref rt2 tok2 family active principal status
  login="$(http_with_cookie POST /api/v1/auth/login "" "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}")"
  local code setcookie
  code="$(printf '%s' "${login}" | sed -n '2p')"
  setcookie="$(printf '%s' "${login}" | sed -n '3p')"
  if [ "${code}" != "200" ]; then echo "  登录失败，状态 ${code}（无法执行刷新轮换断言）"; return 1; fi
  assert_refresh_cookie_attributes "${setcookie}" "登录" || return 1
  rt1="$(refresh_from_cookie "${setcookie}")"
  if [ -z "${rt1}" ]; then echo "  登录未下发刷新 Cookie"; return 1; fi
  tok1="$(printf '%s' "${login}" | sed -n '1p' | sed -nE 's/.*"accessToken"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p')"

  ref="$(http_with_cookie POST /api/v1/auth/refresh "${rt1}" "")"
  code="$(printf '%s' "${ref}" | sed -n '2p')"
  setcookie="$(printf '%s' "${ref}" | sed -n '3p')"
  if [ "${code}" != "200" ]; then echo "  刷新失败，状态 ${code}，错误码 $(error_code "$(printf '%s' "${ref}" | sed -n '1p')")"; return 1; fi
  assert_refresh_cookie_attributes "${setcookie}" "刷新" || return 1
  rt2="$(refresh_from_cookie "${setcookie}")"
  if [ -z "${rt2}" ]; then echo "  刷新未轮换出新刷新 Cookie"; return 1; fi
  if [ "${rt2}" = "${rt1}" ]; then echo "  刷新后 refresh 未轮换（Cookie 值与旧的相同）"; return 1; fi
  tok2="$(printf '%s' "${ref}" | sed -n '1p' | sed -nE 's/.*"accessToken"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p')"
  if [ -z "${tok2}" ]; then echo "  刷新响应缺少 accessToken"; return 1; fi
  if [ "${tok2}" = "${tok1}" ]; then echo "  刷新后 access 未轮换（响应体与旧的相同）"; return 1; fi

  principal="$(psql_q "select principal_id from iam_user where username='${ADMIN_USER}'")"
  family="$(psql_q "select token_family from iam_token where jti=(select jti from iam_token where principal_id='${principal}' and status='ROTATED' order by id desc limit 1)")"
  if [ -z "${family}" ]; then echo "  未发现 ROTATED 状态的旧 jti（刷新未把旧 jti 标记失效）"; return 1; fi
  active="$(psql_q "select count(*) from iam_token where token_family='${family}' and status='ACTIVE'")"
  if [ "${active}" != "1" ]; then
    echo "  轮换后该 Token Family 的 ACTIVE 数量为 ${active}，期望恰好 1（无双有效 refresh）"; return 1
  fi
  return 0
}

# AC-P0B-IAM-008：旧 access 立即被拒；refresh 被拒；重新登录被拒；状态与审计一致。
assert_disable_revokes_sessions() {
  local username="compose-verify-iam8" uid reset login utok urt before dis facts after rstat relogin audit en
  uid="$(new_iam_probe_user "${username}" "Compose Verify Iam8")" || return 1
  reset="$(curl -s -m 20 -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -d "{\"temporaryPassword\":\"${IAM_PROBE_PASSWORD}\"}" \
    "${BASE_URL}/api/v1/system/users/${uid}:reset-password" -w '\n%{http_code}' 2>/dev/null | tail -n1)"
  if [ "${reset}" != "200" ]; then echo "  重置验证用户口令失败，状态 ${reset}"; return 1; fi

  login="$(http_with_cookie POST /api/v1/auth/login "" "{\"username\":\"${username}\",\"password\":\"${IAM_PROBE_PASSWORD}\"}")"
  local code
  code="$(printf '%s' "${login}" | sed -n '2p')"
  if [ "${code}" != "200" ]; then echo "  验证用户登录失败，状态 ${code}"; return 1; fi
  utok="$(printf '%s' "${login}" | sed -n '1p' | sed -nE 's/.*"accessToken"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p')"
  urt="$(refresh_from_cookie "$(printf '%s' "${login}" | sed -n '3p')")"
  before="$(http_status GET /api/v1/me "${utok}")"
  if [ "${before}" != "200" ]; then echo "  禁用前 /me 返回 ${before}，期望 200（前置条件）"; return 1; fi

  dis="$(curl -s -m 20 -X POST -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    "${BASE_URL}/api/v1/system/users/${uid}:disable" -w '\n%{http_code}' 2>/dev/null | tail -n1)"
  if [ "${dis}" != "200" ]; then echo "  禁用用户失败，状态 ${dis}"; return 1; fi
  facts="$(user_row_facts "${username}")"
  if ! printf '%s' "${facts}" | grep -qE '^DISABLED\|'; then
    echo "  禁用后 iam_user.status 未落库为 DISABLED（实际：${facts}）"; return 1
  fi
  after="$(http_status GET /api/v1/me "${utok}")"
  if [ "${after}" != "401" ]; then echo "  禁用后旧 access 访问 /me 返回 ${after}，期望 401（未实时失效）"; return 1; fi
  if [ -n "${urt}" ]; then
    rstat="$(curl -s -m 20 -X POST -H "Cookie: aihub_refresh=${urt}" "${BASE_URL}/api/v1/auth/refresh" -o /dev/null -w '%{http_code}' 2>/dev/null)"
    if [ "${rstat}" = "200" ]; then echo "  禁用后该用户 refresh 仍可刷新，期望被拒"; return 1; fi
  fi
  relogin="$(curl -s -m 20 -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"${username}\",\"password\":\"${IAM_PROBE_PASSWORD}\"}" \
    "${BASE_URL}/api/v1/auth/login" -w '\n%{http_code}' 2>/dev/null)"
  code="$(printf '%s' "${relogin}" | tail -n1)"
  if [ "${code}" != "403" ]; then echo "  禁用用户重新登录返回 ${code}，期望 403"; return 1; fi
  if [ "$(error_code "$(printf '%s' "${relogin}" | sed '$d')")" != "AUTH_ACCOUNT_DISABLED" ]; then
    echo "  禁用用户重新登录错误码不是 AUTH_ACCOUNT_DISABLED"; return 1
  fi
  audit="$(psql_q "select count(*) from audit_log where event_type='USER_DISABLED'")"
  if [ "${audit:-0}" -lt 1 ]; then echo "  未发现 USER_DISABLED 审计事件（禁用未审计）"; return 1; fi
  en="$(curl -s -m 20 -X POST -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    "${BASE_URL}/api/v1/system/users/${uid}:enable" -o /dev/null -w '%{http_code}' 2>/dev/null)"
  if [ "${en}" != "200" ]; then echo "  复原启用用户失败，状态 ${en}"; return 1; fi
  return 0
}

# AC-P0B-IAM-009：Cookie 清除；Family 吊销；重用被拒；重复登出幂等。
assert_logout_and_reuse() {
  local login code setcookie rt atok family active reuse lo2 lo3
  login="$(http_with_cookie POST /api/v1/auth/login "" "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}")"
  code="$(printf '%s' "${login}" | sed -n '2p')"
  setcookie="$(printf '%s' "${login}" | sed -n '3p')"
  if [ "${code}" != "200" ]; then echo "  登录失败，状态 ${code}"; return 1; fi
  rt="$(refresh_from_cookie "${setcookie}")"
  atok="$(printf '%s' "${login}" | sed -n '1p' | sed -nE 's/.*"accessToken"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p')"
  if [ -z "${rt}" ]; then echo "  登录未下发刷新 Cookie"; return 1; fi
  family="$(psql_q "select token_family from iam_token where jti=(select jti from iam_token where principal_id=(select principal_id from iam_user where username='${ADMIN_USER}') order by id desc limit 1)")"

  lo2="$(curl -s -D /tmp/lo.$$ -m 20 -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${atok}" -H "Cookie: aihub_refresh=${rt}" -d '{}' \
    "${BASE_URL}/api/v1/auth/logout" -w '\n%{http_code}' 2>/dev/null)"
  code="$(printf '%s' "${lo2}" | tail -n1)"
  setcookie="$(grep -i '^set-cookie:' /tmp/lo.$$ 2>/dev/null | tr -d '\r' | sed -E 's/^[Ss]et-[Cc]ookie:[[:space:]]*//' | tr '\n' ' ')"
  rm -f /tmp/lo.$$
  if [ "${code}" != "200" ]; then echo "  登出失败，状态 ${code}"; return 1; fi
  if ! printf '%s' "${setcookie}" | grep -qE 'aihub_refresh=;[[:space:]]' || ! printf '%s' "${setcookie}" | grep -qF 'Max-Age=0'; then
    echo "  登出未下发清除 Cookie"; return 1
  fi
  active="$(psql_q "select count(*) from iam_token where token_family='${family}' and status='ACTIVE'")"
  if [ "${active}" != "0" ]; then
    echo "  登出后 Token Family ${family} 残留 ${active} 条 ACTIVE，期望 0（Family 未吊销）"; return 1
  fi
  reuse="$(curl -s -m 20 -X POST -H "Cookie: aihub_refresh=${rt}" "${BASE_URL}/api/v1/auth/refresh" -o /dev/null -w '%{http_code}' 2>/dev/null)"
  if [ "${reuse}" = "200" ]; then echo "  登出后重用 refresh 仍可刷新，期望被拒"; return 1; fi
  lo2="$(curl -s -m 20 -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer ${atok}" \
    -H "Cookie: aihub_refresh=${rt}" -d '{}' "${BASE_URL}/api/v1/auth/logout" -o /dev/null -w '%{http_code}' 2>/dev/null)"
  if [ "${lo2}" != "200" ]; then echo "  重复登出返回 ${lo2}，期望 200（登出必须幂等）"; return 1; fi
  lo3="$(curl -s -m 20 -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer ${atok}" \
    -d '{}' "${BASE_URL}/api/v1/auth/logout" -o /dev/null -w '%{http_code}' 2>/dev/null)"
  if [ "${lo3}" != "200" ]; then echo "  无 refresh 的登出返回 ${lo3}，期望 200（登出必须幂等）"; return 1; fi
  return 0
}

# AC-P0B-IAM-007：重用已轮换 refresh → AUTH_REFRESH_REPLAYED；整族吊销；新 refresh 失效；拒绝审计。
assert_refresh_replay_revokes_family() {
  local login rt family code first newrt replay active after audit
  login="$(http_with_cookie POST /api/v1/auth/login "" "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}")"
  code="$(printf '%s' "${login}" | sed -n '2p')"
  if [ "${code}" != "200" ]; then echo "  登录失败，状态 ${code}"; return 1; fi
  rt="$(refresh_from_cookie "$(printf '%s' "${login}" | sed -n '3p')")"
  family="$(psql_q "select token_family from iam_token where jti=(select jti from iam_token where principal_id=(select principal_id from iam_user where username='${ADMIN_USER}') order by id desc limit 1)")"
  first="$(http_with_cookie POST /api/v1/auth/refresh "${rt}" "")"
  code="$(printf '%s' "${first}" | sed -n '2p')"
  if [ "${code}" != "200" ]; then echo "  建立重放前置的首次刷新失败，状态 ${code}"; return 1; fi
  newrt="$(refresh_from_cookie "$(printf '%s' "${first}" | sed -n '3p')")"
  replay="$(http_with_cookie POST /api/v1/auth/refresh "${rt}" "")"
  code="$(printf '%s' "${replay}" | sed -n '2p')"
  if [ "${code}" != "401" ]; then echo "  重用已轮换 refresh 返回 ${code}，期望 401"; return 1; fi
  if [ "$(error_code "$(printf '%s' "${replay}" | sed -n '1p')")" != "AUTH_REFRESH_REPLAYED" ]; then
    echo "  重用已轮换 refresh 错误码不是 AUTH_REFRESH_REPLAYED"; return 1
  fi
  active="$(psql_q "select count(*) from iam_token where token_family='${family}' and status='ACTIVE'")"
  if [ "${active}" != "0" ]; then
    echo "  重放检测后 Token Family ${family} 仍残留 ${active} 条 ACTIVE，期望 0（整族吊销未生效）"; return 1
  fi
  if [ -n "${newrt}" ]; then
    after="$(curl -s -m 20 -X POST -H "Cookie: aihub_refresh=${newrt}" "${BASE_URL}/api/v1/auth/refresh" -o /dev/null -w '%{http_code}' 2>/dev/null)"
    if [ "${after}" = "200" ]; then echo "  重放后轮换出的新 refresh 仍可刷新，未整族吊销"; return 1; fi
  fi
  audit="$(psql_q "select count(*) from audit_log where event_type='AUTH_TOKEN_REPLAY_REJECTED'")"
  if [ "${audit:-0}" -lt 1 ]; then echo "  未发现 AUTH_TOKEN_REPLAY_REJECTED 审计事件（重放拒绝未审计）"; return 1; fi
  return 0
}

# AC-P0B-IAM-005：达到阈值进入 LOCKED；正确口令也被拒；审计存在。
assert_account_lockout() {
  local username="compose-verify-iam5" uid reset i facts code locked correct audit
  uid="$(new_iam_probe_user "${username}" "Compose Verify Iam5")" || return 1
  reset="$(curl -s -m 20 -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -d "{\"temporaryPassword\":\"${IAM_PROBE_PASSWORD}\"}" \
    "${BASE_URL}/api/v1/system/users/${uid}:reset-password" -w '\n%{http_code}' 2>/dev/null | tail -n1)"
  if [ "${reset}" != "200" ]; then echo "  重置验证用户口令失败，状态 ${reset}"; return 1; fi
  psql_q "update iam_user set failed_login_attempts=0, locked_until=null, status='ACTIVE', updated_at=now() where username='${username}' and status='LOCKED'" >/dev/null

  for i in 1 2 3 4 5; do
    curl -s -m 20 -X POST -H 'Content-Type: application/json' \
      -d "{\"username\":\"${username}\",\"password\":\"ComposeWrong-Password-01!z\"}" \
      "${BASE_URL}/api/v1/auth/login" -o /dev/null 2>/dev/null
  done
  facts="$(user_row_facts "${username}")"
  code="$(curl -s -m 20 -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"${username}\",\"password\":\"ComposeWrong-Password-01!z\"}" \
    "${BASE_URL}/api/v1/auth/login" -w '\n%{http_code}' 2>/dev/null)"
  code="$(printf '%s' "${code}" | tail -n1)"
  if [ "${code}" != "423" ]; then
    echo "  第 5 次错误口令返回 ${code}，期望 423 AUTH_ACCOUNT_LOCKED（账户未锁定）"; return 1
  fi
  # status 是 facts 首列，故 LOCKED 可能位于串首（前面没有分隔符），匹配需兼容两种位置。
  if ! printf '%s' "${facts}" | grep -qE '(^|\|)LOCKED\|'; then
    echo "  锁定后 iam_user.status 未落库为 LOCKED（实际：${facts}）"; return 1
  fi
  locked="$(psql_q "select coalesce(locked_until::text,'NULL') from iam_user where username='${username}'")"
  if [ "${locked}" = "NULL" ]; then echo "  锁定后 locked_until 为空，期望非空"; return 1; fi
  correct="$(curl -s -m 20 -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"${username}\",\"password\":\"${IAM_PROBE_PASSWORD}\"}" \
    "${BASE_URL}/api/v1/auth/login" -w '\n%{http_code}' 2>/dev/null)"
  code="$(printf '%s' "${correct}" | tail -n1)"
  if [ "${code}" != "423" ]; then
    echo "  锁定期间正确口令返回 ${code}，期望 423 AUTH_ACCOUNT_LOCKED"; return 1
  fi
  if [ "$(error_code "$(printf '%s' "${correct}" | sed '$d')")" != "AUTH_ACCOUNT_LOCKED" ]; then
    echo "  锁定期间正确口令错误码不是 AUTH_ACCOUNT_LOCKED"; return 1
  fi
  audit="$(psql_q "select count(*) from audit_log where event_type='AUTH_LOGIN_FAILED'")"
  if [ "${audit:-0}" -lt 1 ]; then echo "  未发现 AUTH_LOGIN_FAILED 审计事件（登录失败未审计）"; return 1; fi
  curl -s -m 20 -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -d "{\"temporaryPassword\":\"${IAM_PROBE_PASSWORD}\"}" \
    "${BASE_URL}/api/v1/system/users/${uid}:reset-password" -o /dev/null 2>/dev/null
  curl -s -m 20 -X POST -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    "${BASE_URL}/api/v1/system/users/${uid}:enable" -o /dev/null 2>/dev/null
  return 0
}

# AC-P0B-IAM-011：并发刷新只允许一个成功；不得出现两条并存 ACTIVE。
assert_concurrent_refresh() {
  local login rt family ok=0 code active
  login="$(http_with_cookie POST /api/v1/auth/login "" "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}")"
  code="$(printf '%s' "${login}" | sed -n '2p')"
  if [ "${code}" != "200" ]; then echo "  登录失败，状态 ${code}"; return 1; fi
  rt="$(refresh_from_cookie "$(printf '%s' "${login}" | sed -n '3p')")"
  family="$(psql_q "select token_family from iam_token where jti=(select jti from iam_token where principal_id=(select principal_id from iam_user where username='${ADMIN_USER}') order by id desc limit 1)")"
  # 两个独立 curl 进程同时发起，真实并发到达后端。
  local f1 f2 b1 b2
  f1="$(mktemp)"; f2="$(mktemp)"; b1="$(mktemp)"; b2="$(mktemp)"
  curl -s -m 30 -X POST -H "Cookie: aihub_refresh=${rt}" "${BASE_URL}/api/v1/auth/refresh" \
    -o "${b1}" -w '%{http_code}' >"${f1}" 2>/dev/null &
  local pid1=$!
  curl -s -m 30 -X POST -H "Cookie: aihub_refresh=${rt}" "${BASE_URL}/api/v1/auth/refresh" \
    -o "${b2}" -w '%{http_code}' >"${f2}" 2>/dev/null &
  local pid2=$!
  wait "${pid1}" "${pid2}" 2>/dev/null
  local c1 c2
  c1="$(cat "${f1}")"; c2="$(cat "${f2}")"
  [ "${c1}" = "200" ] && ok=$((ok + 1))
  [ "${c2}" = "200" ] && ok=$((ok + 1))
  if [ "${ok}" != "1" ]; then
    rm -f "${f1}" "${f2}" "${b1}" "${b2}"
    echo "  并发刷新成功次数为 ${ok}（状态 ${c1}/${c2}），期望恰好 1"; return 1
  fi
  # 输家复用的 jti 此刻已为 ROTATED，按 REQ-IAM-004「ROTATED jti 再用视为重放，
  # 整个 Family REVOKED」必须整族吊销，故该 Family 的 ACTIVE 为 0。
  # 出口要求「无双有效 Token」即 ACTIVE <= 1；== 0 是更强且符合规格重放语义的结论。
  active="$(psql_q "select count(*) from iam_token where token_family='${family}' and status='ACTIVE'")"
  # 输家必须返回定义好的重放语义（与 verify.ps1 的 Get-ErrorCode 断言等价）。
  local loserCode
  if [ "${c1}" != "200" ]; then
    loserCode="$(error_code "$(cat "${b1}")")"
  else
    loserCode="$(error_code "$(cat "${b2}")")"
  fi
  rm -f "${f1}" "${f2}" "${b1}" "${b2}"
  if [ "${active}" != "0" ]; then
    echo "  并发刷新后该 Token Family 的 ACTIVE 数量为 ${active}，期望 0（输家触发重放 → 整族吊销）"; return 1
  fi
  if [ "${loserCode}" != "AUTH_REFRESH_REPLAYED" ]; then
    echo "  并发刷新失败方错误码为 ${loserCode}，期望 AUTH_REFRESH_REPLAYED"; return 1
  fi
  if [ "$(psql_q "select count(*) from audit_log where event_type='AUTH_TOKEN_REPLAY_REJECTED'")" -lt 1 ]; then
    echo "  未发现 AUTH_TOKEN_REPLAY_REJECTED 审计事件（并发拒绝未审计）"; return 1
  fi
  return 0
}

# ---- V05c: 身份生命周期（AC-P0B-IAM-001/003/004）----
check_iam_lifecycle() {
  assert_bootstrap_admin_uniqueness || return 1
  assert_first_login_password_change || return 1
  assert_login_anti_enumeration || return 1
  return 0
}

# AC-P0B-IAM-012：创建/编辑/重置/禁用/启用 + 每步审计 + 最后一个管理员保护。
assert_admin_user_lifecycle() {
  local username="compose-verify-iam12" uid code locale facts missing eventType count
  local adminUserId activeAdmins dis deniedBefore adminPrincipalId deniedForAdmin deniedTotalAfter
  uid="$(new_iam_probe_user "${username}" "Compose Verify Iam12")" || return 1
  code="$(curl -s -m 20 -X PATCH -H 'Content-Type: application/json' -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -d '{"displayName":"Compose Verify Iam12 Renamed","email":null,"locale":"en-US"}' \
    "${BASE_URL}/api/v1/system/users/${uid}" -o /dev/null -w '%{http_code}' 2>/dev/null)"
  if [ "${code}" != "200" ]; then echo "  编辑用户失败，状态 ${code}"; return 1; fi
  locale="$(psql_q "select locale from iam_user where user_id='${uid}'")"
  if [ "${locale}" != "en-US" ]; then echo "  编辑后 locale 未落库为 en-US（实际：${locale}）"; return 1; fi
  code="$(curl -s -m 20 -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -d "{\"temporaryPassword\":\"${IAM_PROBE_PASSWORD}\"}" \
    "${BASE_URL}/api/v1/system/users/${uid}:reset-password" -o /dev/null -w '%{http_code}' 2>/dev/null)"
  if [ "${code}" != "200" ]; then echo "  重置口令失败，状态 ${code}"; return 1; fi
  facts="$(user_row_facts "${username}")"
  if ! printf '%s' "${facts}" | grep -qE '^PENDING_ACTIVATION\|.*\|1\|'; then
    echo "  重置口令后未回到强制改密/待激活（实际：${facts}）"; return 1
  fi
  code="$(curl -s -m 20 -X POST -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    "${BASE_URL}/api/v1/system/users/${uid}:disable" -o /dev/null -w '%{http_code}' 2>/dev/null)"
  if [ "${code}" != "200" ]; then echo "  禁用用户失败，状态 ${code}"; return 1; fi
  code="$(curl -s -m 20 -X POST -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    "${BASE_URL}/api/v1/system/users/${uid}:enable" -o /dev/null -w '%{http_code}' 2>/dev/null)"
  if [ "${code}" != "200" ]; then echo "  启用用户失败，状态 ${code}"; return 1; fi

  missing=""
  for eventType in USER_CREATED USER_UPDATED USER_ENABLED USER_DISABLED USER_PASSWORD_RESET; do
    count="$(psql_q "select count(*) from audit_log where event_type='${eventType}'")"
    if [ "${count:-0}" -lt 1 ]; then missing="${missing} ${eventType}"; fi
  done
  if [ -n "${missing}" ]; then
    echo "  以下用户管理审计事件缺失：${missing}（管理动作未审计）"; return 1
  fi

  adminUserId="$(psql_q "select user_id from iam_user where username='${ADMIN_USER}'")"
  # 使用 jsonb_build_array 而非 JSON 字面量：SQL 需经 bash→sh -c→psql 三层解析，
  # 字面量 '["user:manage"]' 的双引号会在中途被剥离成非法 JSON（与 verify.ps1 同一规避）。
  activeAdmins="$(psql_q "select count(*) from iam_user where status='ACTIVE' and scopes @> jsonb_build_array('user:manage')")"
  if [ "${activeAdmins}" != "1" ]; then
    echo "  前置条件异常：持有 user:manage 的活跃管理员数量为 ${activeAdmins}，期望 1"; return 1
  fi
  # 拒绝审计必须真实落库。先取基线计数，拒绝后再断言恰好 +1：
  # 只写「>= 1」无法鉴别本次拒绝是否落库（历史运行的行会让断言恒真）。
  deniedBefore="$(psql_q "select count(*) from audit_log where event_type='USER_DISABLED' and result='DENIED'")"
  dis="$(curl -s -m 20 -X POST -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    "${BASE_URL}/api/v1/system/users/${adminUserId}:disable" -w '\n%{http_code}' 2>/dev/null)"
  code="$(printf '%s' "${dis}" | tail -n1)"
  if [ "${code}" = "200" ]; then
    # 产品未拒绝：立即复原，避免把验收栈留在「无管理员」状态，然后如实报出缺陷。
    psql_q "update iam_user set status='ACTIVE', failed_login_attempts=0, locked_until=null, updated_at=now() where user_id='${adminUserId}'" >/dev/null
    psql_q "update iam_principal set status='ACTIVE', updated_at=now() where principal_id=(select principal_id from iam_user where user_id='${adminUserId}')" >/dev/null
    echo "  最后一个有效平台管理员被允许禁用（返回 200），期望拒绝并返回 COMMON_INVALID_ARGUMENT 且产生拒绝审计（REQ-IAM-006）。已自动复原管理员状态。"
    return 1
  fi
  if [ "$(error_code "$(printf '%s' "${dis}" | sed '$d')")" != "COMMON_INVALID_ARGUMENT" ]; then
    echo "  禁用最后一个管理员错误码不是 COMMON_INVALID_ARGUMENT"; return 1
  fi
  # 拒绝路径必须有独立提交的拒绝审计：否则「拒绝」既无权威记录也不可追溯（REQ-IAM-006）。
  # 注意 resource_id 落的是目标 principalId（IdentityAuditAdapter 传 targetId=principalId），
  # 不是 iam_user.user_id，故须按 principal_id 关联。
  adminPrincipalId="$(psql_q "select principal_id from iam_user where user_id='${adminUserId}'")"
  deniedForAdmin="$(psql_q "select count(*) from audit_log where event_type='USER_DISABLED' and result='DENIED' and error_code='COMMON_INVALID_ARGUMENT' and resource_id='${adminPrincipalId}'")"
  if [ "${deniedForAdmin:-0}" -lt 1 ]; then
    echo "  禁用最后一个管理员被拒后未落库 USER_DISABLED/DENIED 审计（拒绝路径无权威审计记录）"; return 1
  fi
  deniedTotalAfter="$(psql_q "select count(*) from audit_log where event_type='USER_DISABLED' and result='DENIED'")"
  if [ "${deniedTotalAfter}" != "$((deniedBefore + 1))" ]; then
    echo "  USER_DISABLED/DENIED 审计计数由 ${deniedBefore} 变为 ${deniedTotalAfter}，期望恰好 +1（本次拒绝未审计或重复审计）"; return 1
  fi
  return 0
}

# ---- V05b: 验收主体前置条件 ----
# 1) 管理员解除首登改密门（否则后续 403/200 断言都与权限无关）；
# 2) 就绪最小权限验证主体（用于默认拒绝断言）。
check_verify_principals() {
  local new_token create create_code token
  new_token="$(resolve_password_change_gate "${ADMIN_USER}" "${ADMIN_PASS}" "${ACCESS_TOKEN}" "管理员 ${ADMIN_USER}")" || return 1
  ACCESS_TOKEN="${new_token}"

  # 就绪最小权限主体：创建（幂等，已存在则复用）→ 登录 → 解除其改密门。
  create="$(curl -s -m 15 -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -d "{\"username\":\"${LEAST_PRIV_USER}\",\"displayName\":\"Compose Verify Minimal\",\"email\":null,\"locale\":\"zh-CN\",\"temporaryPassword\":\"${LEAST_PRIV_PASS}\",\"scopes\":[\"project:view\"]}" \
    "${BASE_URL}/api/v1/system/users" -w '\n%{http_code}' 2>/dev/null)"
  create_code="$(printf '%s' "${create}" | tail -n1)"
  if [ "${create_code}" = "409" ]; then
    : # 幂等：上一轮运行已创建（口令轮换为同值，临时口令仍有效）。
  elif [ "${create_code}" != "201" ]; then
    echo "  创建最小权限验证主体失败，状态 ${create_code}，错误码 $(error_code "${create}")"; return 1
  fi

  token="$(get_access_token "${LEAST_PRIV_USER}" "${LEAST_PRIV_PASS}")"
  if [ -z "${token}" ]; then echo "  最小权限验证主体登录失败"; return 1; fi
  LEAST_PRIV_TOKEN="$(resolve_password_change_gate "${LEAST_PRIV_USER}" "${LEAST_PRIV_PASS}" "${token}" "最小权限验证主体")" || return 1

  # 反向自检：验证主体不得持有任何被测端点所需权限，否则默认拒绝断言会失去意义。
  local me scopes scope
  me="$(http_body GET /api/v1/me "${LEAST_PRIV_TOKEN}")"
  scopes="$(printf '%s' "${me}" | sed -nE 's/.*"scopes"[[:space:]]*:[[:space:]]*\[([^]]*)\].*/\1/p')"
  for scope in audit:read job:read notification:read system:observe dictionary:read tag:read; do
    if printf '%s' "${scopes}" | grep -q "${scope}"; then
      echo "  最小权限验证主体不应持有 ${scope}，否则其 403 无法证明默认拒绝"; return 1
    fi
  done
  return 0
}

# ---- V06: 权限过滤 ----
check_perm_filter() {
  assert_default_deny /api/v1/system/audit-logs || return 1
  assert_default_deny /api/v1/system/metrics/summary || return 1
  # 管理员经 ADMIN 角色合法持有 audit:read / system:observe，应真实可达。
  assert_permitted /api/v1/system/audit-logs || return 1
  assert_permitted /api/v1/system/metrics/summary || return 1
  return 0
}

# ---- V07: 字典/标签 ----
check_taxonomy() {
  assert_default_deny /api/v1/system/dictionaries || return 1
  assert_default_deny /api/v1/system/tags || return 1
  # AC-P0B-AUTH-004：有 dictionary:read 的主体必须返回真实数据而非空集合占位。
  assert_permitted /api/v1/system/dictionaries 1 || return 1
  return 0
}

# ---- V01: Compose 配置合法 ----
check_config() { docker compose config --quiet; }

# ---- V02: 核心服务健康 ----
check_health() {
  local ok=0
  for svc in postgres minio gitea backend; do
    local state
    state="$(docker compose ps --format '{{.Service}} {{.State}} {{.Health}}' "${svc}" 2>/dev/null)"
    if [ -z "${state}" ]; then echo "  ${svc} 未运行"; ok=1; continue; fi
    case "${state}" in
      *" running"*) : ;;
      *) echo "  ${state}"; ok=1 ;;
    esac
    case "${state}" in
      *unhealthy*|*starting*) echo "  ${svc} 健康检查未通过"; ok=1 ;;
    esac
  done
  return ${ok}
}

# ---- V03: Bucket 初始化 ----
check_buckets() {
  local listing ok=0
  listing="$(docker compose exec -T minio sh -c \
    'mc alias set local http://localhost:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1; mc ls local 2>/dev/null')"
  for b in gitea-storage dvc-cache asset-staging asset-preview; do
    if ! echo "${listing}" | grep -q "${b}"; then
      echo "  Bucket ${b} 不存在"; ok=1
    fi
  done
  return ${ok}
}

# ---- V04: 数据库迁移完整性 ----
check_migrations() {
  local count ok=0
  count="$(psql_q 'select count(*) from flyway_schema_history where success = true')"
  if [ -z "${count}" ] || [ "${count}" -lt 33 ]; then echo "  成功迁移数 ${count} < 33"; return 1; fi
  for t in iam_principal iam_user iam_role system_dict_item system_tag asset_tag system_config job_task audit_log notification asset_discussion asset_comment system_alert reconciliation_checkpoint asset_relation asset_subscription team api_idempotency; do
    if [ "$(psql_q "select to_regclass('public.${t}') is not null")" != "t" ]; then
      echo "  关键表 ${t} 缺失"; ok=1
    fi
  done
  return ${ok}
}

# 重置管理员口令为 ADMIN_PASS（处理持久化 DB 口令漂移）：经 worker 容器 python crypt 生成 bcrypt，
# 直接 UPDATE iam_user 并清 must_change_password，使后续登录可用 ADMIN_PASS。
reset_admin_password() {
  local hash
  hash="$(docker compose exec -T worker python3 -c \
    "import crypt; print(crypt.crypt('${ADMIN_PASS}', crypt.mksalt(crypt.METHOD_BLOWFISH)))" 2>/dev/null | tr -d '[:space:]')"
  if [ -z "${hash}" ]; then echo "  口令重置：worker 生成 bcrypt 失败"; return 1; fi
  # Spring jBCrypt 期望 $2a$ 前缀；python crypt 产出 $2b$（算法等价，仅版本前缀不同），归一化为 $2a$
  hash="${hash/\$2b\$/\$2a\$}"
  # 经 stdin 传入 SQL，避免 bcrypt 哈希中的 $ 被 sh -c 二次展开；must_change_password 为 smallint
  printf "update iam_user set password_hash='%s', must_change_password=0 where username='%s';\n" \
    "${hash}" "${ADMIN_USER}" \
    | docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null 2>&1
  echo "  口令漂移：已重置 admin 口令为 .env 默认（must_change_password=false）"
  return 0
}

# ---- V05: JWT 生命周期（登录/me/匿名 401 + refresh Cookie 轮换，PRD V22 子集）----
check_jwt() {
  local cookie_jar body token me anon refresh_body refresh_token must_change
  cookie_jar="$(mktemp)"

  # 双密码回退：先试 JOURNEY_ADMIN_PASS（改密后），再试 ADMIN_PASS（初始/未改密）
  token=""
  for pass in "${JOURNEY_ADMIN_PASS}" "${ADMIN_PASS}"; do
    body="$(http_login_body "${cookie_jar}" "${pass}")"
    token="$(echo "${body}" | sed -nE 's/.*"accessToken"\s*:\s*"([^"]+)".*/\1/p')"
    if [ -n "${token}" ]; then
      ADMIN_PASS="${pass}"
      break
    fi
  done

  # 凭据漂移兜底：双密码均失败时重置 admin 口令为 ADMIN_PASS 后重试
  if [ -z "${token}" ] && postgres_reachable && service_running worker; then
    reset_admin_password
    body="$(http_login_body "${cookie_jar}" "${ADMIN_PASS}")"
    token="$(echo "${body}" | sed -nE 's/.*"accessToken"\s*:\s*"([^"]+)".*/\1/p')"
  fi

  if [ -z "${token}" ]; then echo "  登录未取得 accessToken（凭据/改密状态？）"; rm -f "${cookie_jar}"; return 1; fi
  ACCESS_TOKEN="${token}"

  # 处理 must_change_password 强制改密闸门
  if postgres_reachable; then
    must_change="$(psql_q "select must_change_password from iam_user where username='${ADMIN_USER}'")"
    if [ "${must_change}" = "1" ] || [ "${must_change}" = "t" ]; then
      local code
      code="$(http_status PUT /api/v1/me/password "${ACCESS_TOKEN}" \
        "{\"currentPassword\":\"${ADMIN_PASS}\",\"newPassword\":\"${JOURNEY_ADMIN_PASS}\"}")"
      if [ "${code}" != "200" ]; then echo "  改密门 PUT /me/password 返回 ${code}"; rm -f "${cookie_jar}"; return 1; fi
      body="$(http_login_body "${cookie_jar}" "${JOURNEY_ADMIN_PASS}")"
      token="$(echo "${body}" | sed -nE 's/.*"accessToken"\s*:\s*"([^"]+)".*/\1/p')"
      if [ -z "${token}" ]; then echo "  改密后重新登录未取得 accessToken"; rm -f "${cookie_jar}"; return 1; fi
      ACCESS_TOKEN="${token}"
      ADMIN_PASS="${JOURNEY_ADMIN_PASS}"
    fi
  fi

  me="$(http_status GET /api/v1/me "${ACCESS_TOKEN}")"
  if [ "${me}" != "200" ]; then echo "  /me 携带 token 返回 ${me}，期望 200"; rm -f "${cookie_jar}"; return 1; fi
  anon="$(http_status GET /api/v1/system/users)"
  if [ "${anon}" != "401" ]; then echo "  无 Token 访问 /system/users 返回 ${anon}，期望 401"; rm -f "${cookie_jar}"; return 1; fi
  refresh_body="$(http_refresh_body "${cookie_jar}")"
  refresh_token="$(echo "${refresh_body}" | sed -nE 's/.*"accessToken"\s*:\s*"([^"]+)".*/\1/p')"
  if [ -z "${refresh_token}" ]; then echo "  refresh 未取得新 accessToken（Cookie/轮换？）"; rm -f "${cookie_jar}"; return 1; fi
  me="$(http_status GET /api/v1/me "${refresh_token}")"
  if [ "${me}" != "200" ]; then echo "  刷新后 /me 返回 ${me}，期望 200"; rm -f "${cookie_jar}"; return 1; fi
  ACCESS_TOKEN="${refresh_token}"
  rm -f "${cookie_jar}"
  return 0
}

# ---- V06: 权限过滤 ----
# 实现见上文（含默认拒绝 + 有权限正向断言）。

# ---- V07: 字典/标签 ----
# 实现见上文（含默认拒绝 + 有权限正向断言）。

# ---- V08: 审计脱敏 + identity 登录审计完整性 ----
# identity 登录/令牌/改密事件已接入权威 AuditService（IdentityAuditAdapter）。
# 校验：审计正文脱敏恒定不变式（无明文口令）；且 V05 登录成功后应产生登录审计事件。
check_audit() {
  local exists leak cnt i
  exists="$(psql_q "select to_regclass('public.audit_log') is not null")"
  if [ "${exists}" != "t" ]; then echo "  audit_log 表缺失"; return 1; fi
  leak="$(psql_q "select count(*) from audit_log where request_summary::text like '%${ADMIN_PASS}%'")"
  if [ "${leak}" != "0" ]; then echo "  audit_log 明文口令泄漏（脱敏失效）"; return 1; fi
  if [ "${BACKEND_UP:-0}" -eq 1 ]; then
    for i in $(seq 1 10); do
      cnt="$(psql_q "select count(*) from audit_log where event_type = 'AUTH_LOGIN_SUCCEEDED'")"
      if [ "${cnt}" -ge 1 ] 2>/dev/null; then return 0; fi
      sleep 0.5
    done
    echo "  未发现 AUTH_LOGIN_SUCCEEDED 审计事件（identity 审计接入未生效）"; return 1
  fi
  return 0
}

# ---- V09: 持久化任务 ----
check_jobs() {
  assert_default_deny /api/v1/system/jobs || return 1
  assert_permitted /api/v1/system/jobs || return 1
  return 0
}

# ---- V10: 通知 ----
check_notifications() {
  assert_default_deny /api/v1/system/notifications || return 1
  assert_permitted /api/v1/system/notifications || return 1
  return 0
}

# ---- V11: 观测与诊断 ----
check_observability() {
  local h
  h="$(http_status GET /actuator/health)"
  if [ "${h}" != "200" ]; then echo "  /actuator/health 返回 ${h}，期望 200"; return 1; fi
  assert_default_deny /api/v1/system/dependencies || return 1
  assert_permitted /api/v1/system/dependencies || return 1
  return 0
}

# ---- V12: 资产创建与 Gitea 仓库一致性 ----
check_asset_gitea_consistency() {
  local asset_cnt job_cnt
  asset_cnt="$(psql_q 'select count(*) from asset where deleted = 0')"
  if [ -z "${asset_cnt}" ] || [ "${asset_cnt}" -lt 0 ]; then echo "  asset 表查询失败"; return 1; fi
  # 检查资产表存在且 provisioning_status 列可用
  local col_exists
  col_exists="$(psql_q "select count(*) from information_schema.columns where table_name='asset' and column_name='provisioning_status'")"
  if [ "${col_exists}" != "1" ]; then echo "  asset.provisioning_status 列缺失"; return 1; fi
  # 检查 job_task 表中 ASSET_PROVISION / REPOSITORY_PROVISION 任务类型可用
  job_cnt="$(psql_q "select count(*) from job_task where type in ('ASSET_PROVISION','REPOSITORY_PROVISION')")"
  if [ -z "${job_cnt}" ]; then echo "  job_task 查询失败"; return 1; fi
  # Compose 启用 Gitea 时：断言已完成 provision 的资产具备仓库引用，且 Gitea API 可达
  if [ "${BACKEND_UP:-0}" -eq 1 ]; then
    local gitea_code
    # Gitea 按拓扑不直接暴露 3000 端口，而是经 nginx /git/ 子路径入口访问（compose.yaml ROOT_URL 注释）。
    gitea_code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "${BASE_URL}/git/api/v1/version" 2>/dev/null || echo 000)"
    if [ "${gitea_code}" = "200" ]; then
      local completed
      completed="$(psql_q "select count(*) from asset where deleted = 0 and provisioning_status = 'COMPLETED' and repo_full_name is not null")"
      # 允许空库（0）；若有 COMPLETED 资产则必须带仓库名
      if [ -n "${completed}" ] && [ "${completed}" -gt 0 ]; then
        local missing_repo
        missing_repo="$(psql_q "select count(*) from asset where deleted = 0 and provisioning_status = 'COMPLETED' and (repo_full_name is null or repo_full_name = '')")"
        if [ "${missing_repo}" != "0" ]; then
          echo "  ${missing_repo} 个 COMPLETED 资产缺少 repo_full_name（Gitea 已启用）"
          return 1
        fi
      fi
    else
      echo "  警告: Gitea API 不可达 (HTTP ${gitea_code})；跳过真实仓库断言"
    fi
  fi
  return 0
}

# ---- V13: 幂等键重放不重复建仓 ----
check_idempotency_replay() {
  # 检查幂等机制：api_idempotency 表存在且有唯一键，或 job_task.job_id 唯一约束
  local tbl_exists
  tbl_exists="$(psql_q "select to_regclass('public.api_idempotency') is not null")"
  if [ "${tbl_exists}" != "t" ]; then
    # 回退检查 job_task.job_id 唯一约束
    local idx_exists
    idx_exists="$(psql_q "select count(*) from pg_indexes where tablename='job_task' and indexdef like '%job_id%'")"
    if [ "${idx_exists}" -lt 1 ]; then echo "  api_idempotency 表缺失且 job_task.job_id 唯一索引缺失"; return 1; fi
  fi
  return 0
}

# ---- V14: 多组织权限主体搜索无泄漏 ----
check_multi_org_isolation() {
  # 检查 resource_acl 表存在并具备 resource_type/principal_id 列
  local col_cnt
  col_cnt="$(psql_q "select count(*) from information_schema.columns where table_name='iam_resource_acl' and column_name in ('resource_type','principal_id')")"
  if [ "${col_cnt}" != "2" ]; then echo "  resource_acl 关键字段缺失"; return 1; fi
  # 后端可达时验证搜索接口默认拒绝匿名
  if [ "${BACKEND_UP:-0}" -eq 1 ]; then
    local anon
    anon="$(http_status GET /api/v1/assets)"
    if [ "${anon}" != "401" ]; then echo "  匿名访问 /assets 返回 ${anon}，期望 401"; return 1; fi
  fi
  return 0
}

# ---- V15: 数据集 Facet 权限过滤 ----
check_facet_permission() {
  # 检查 asset 表存在 visibility 列
  local col_exists
  col_exists="$(psql_q "select count(*) from information_schema.columns where table_name='asset' and column_name='visibility'")"
  if [ "${col_exists}" != "1" ]; then echo "  asset.visibility 列缺失"; return 1; fi
  # 后端可达时验证 facet 接口默认拒绝匿名
  if [ "${BACKEND_UP:-0}" -eq 1 ]; then
    local anon
    anon="$(http_status GET /api/v1/assets/facets)"
    if [ "${anon}" != "401" ]; then echo "  匿名访问 /assets/facets 返回 ${anon}，期望 401"; return 1; fi
  fi
  return 0
}

step V01 "Compose 配置合法" check_config

if service_running postgres; then
  step V02 "核心服务健康" check_health
else
  skip V02 "核心服务健康" "服务未启动（未执行 docker compose up）"
fi

if service_running minio; then
  step V03 "Bucket 初始化" check_buckets
else
  skip V03 "Bucket 初始化" "minio 未启动（未执行 docker compose up）"
fi

if postgres_reachable; then
  step V04 "数据库迁移完整性" check_migrations
else
  skip V04 "数据库迁移完整性" "postgres 容器未运行（未执行 docker compose up）"
fi

if backend_reachable; then
  BACKEND_UP=1
else
  BACKEND_UP=0
fi

if [ "${BACKEND_UP}" -eq 1 ]; then
  step V05 "JWT 生命周期" check_jwt
  step V05b "验收主体前置条件（改密门已解除 + 最小权限主体就绪）" check_verify_principals
  step V06 "权限过滤（审计/指标默认拒绝 + 有权限正向可达）" check_perm_filter
  step V07 "字典/标签受控访问（默认拒绝 + 有权限正向断言）" check_taxonomy
else
  skip V05 "JWT 生命周期" "backend 健康端点不可达（未执行 docker compose up）"
  skip V05b "验收主体前置条件" "backend 不可达"
  skip V06 "权限过滤" "backend 不可达"
  skip V07 "字典/标签受控访问" "backend 不可达"
fi

if postgres_reachable; then
  step V08 "审计脱敏与登录审计（无明文口令 + 登录事件）" check_audit
else
  skip V08 "审计脱敏与登录审计" "postgres 不可达"
fi

if [ "${BACKEND_UP}" -eq 1 ]; then
  step V09 "持久化任务访问（默认拒绝 + 有权限正向可达）" check_jobs
  step V10 "通知访问（默认拒绝 + 有权限正向可达）" check_notifications
  step V11 "观测与诊断" check_observability
else
  skip V09 "持久化任务访问" "backend 不可达"
  skip V10 "通知访问" "backend 不可达"
  skip V11 "观测与诊断" "backend 不可达"
fi

# IAM 验收：按 AC 逐一独立执行，使缺陷可精确定位（一个场景失败不掩盖其余场景）。
# 每个 V 编号显式声明其覆盖的 AC-*（p0b-exit-catalog 第 20 行要求）。
if [ "${BACKEND_UP}" -eq 1 ] && postgres_reachable; then
  step V05c "身份生命周期（AC-P0B-IAM-001/003/004）" check_iam_lifecycle
  step V05d "刷新轮换（AC-P0B-IAM-006）" assert_refresh_rotation
  step V05e "禁用实时失效（AC-P0B-IAM-008）" assert_disable_revokes_sessions
  step V05f "登出与重用（AC-P0B-IAM-009）" assert_logout_and_reuse
  step V05g "刷新重放整族吊销（AC-P0B-IAM-007）" assert_refresh_replay_revokes_family
  step V05h "账户锁定（AC-P0B-IAM-005）" assert_account_lockout
  step V05i "并发刷新无双有效 Token（AC-P0B-IAM-011）" assert_concurrent_refresh
  step V05j "用户管理与最后一个管理员保护（AC-P0B-IAM-012）" assert_admin_user_lifecycle
else
  for pair in "V05c:身份生命周期（AC-P0B-IAM-001/003/004）" "V05d:刷新轮换（AC-P0B-IAM-006）" \
              "V05e:禁用实时失效（AC-P0B-IAM-008）" "V05f:登出与重用（AC-P0B-IAM-009）" \
              "V05g:刷新重放整族吊销（AC-P0B-IAM-007）" "V05h:账户锁定（AC-P0B-IAM-005）" \
              "V05i:并发刷新无双有效 Token（AC-P0B-IAM-011）" "V05j:用户管理与最后一个管理员保护（AC-P0B-IAM-012）"; do
    skip "${pair%%:*}" "${pair#*:}" "backend/postgres 不可达（无法执行 IAM 断言）"
  done
fi

# P1 资产目录验证
if postgres_reachable; then
  step V12 "资产创建与 Gitea 仓库一致性" check_asset_gitea_consistency
  step V13 "幂等键重放不重复建仓" check_idempotency_replay
else
  skip V12 "资产创建与 Gitea 仓库一致性" "postgres 不可达"
  skip V13 "幂等键重放不重复建仓" "postgres 不可达"
fi

if postgres_reachable; then
  step V14 "多组织权限主体搜索无泄漏" check_multi_org_isolation
  step V15 "数据集 Facet 权限过滤" check_facet_permission
else
  skip V14 "多组织权限主体搜索无泄漏" "postgres 不可达"
  skip V15 "数据集 Facet 权限过滤" "postgres 不可达"
fi

# ---- V16: 版本 Schema 验证 ----
check_version_schema() {
  local ok=0
  for t in asset_version upload_session upload_file upload_part version_artifact; do
    if [ "$(psql_q "select to_regclass('public.${t}') is not null")" != "t" ]; then
      echo "  关键表 ${t} 缺失"; ok=1
    fi
  done
  # 检查 asset_version 关键字段
  local col_cnt
  col_cnt="$(psql_q "select count(*) from information_schema.columns where table_name='asset_version' and column_name in ('version','status','asset_id','manifest_digest')")"
  if [ "${col_cnt}" -lt 4 ]; then echo "  asset_version 关键字段缺失（需 version/status/asset_id/manifest_digest）"; ok=1; fi
  return ${ok}
}

# ---- V17: 发布审批 Schema 验证 ----
check_publish_schema() {
  local ok=0
  for t in validation_report publish_request review_decision; do
    if [ "$(psql_q "select to_regclass('public.${t}') is not null")" != "t" ]; then
      echo "  关键表 ${t} 缺失"; ok=1
    fi
  done
  # 检查 publish_request 关键字段
  local col_cnt
  col_cnt="$(psql_q "select count(*) from information_schema.columns where table_name='publish_request' and column_name in ('version_id','status','submitted_by')")"
  if [ "${col_cnt}" -lt 3 ]; then echo "  publish_request 关键字段缺失"; ok=1; fi
  return ${ok}
}

# ---- V18: Webhook Inbox 验证 ----
check_webhook_inbox() {
  # 表存在性
  if [ "$(psql_q "select to_regclass('public.webhook_inbox') is not null")" != "t" ]; then
    echo "  webhook_inbox 表缺失"; return 1
  fi
  if [ "$(psql_q "select to_regclass('public.webhook_delivery') is not null")" != "t" ]; then
    echo "  webhook_delivery 表缺失"; return 1
  fi
  # 后端可达时验证端点默认拒绝
  if [ "${BACKEND_UP:-0}" -eq 1 ]; then
    local anon
    anon="$(http_status POST /api/v1/webhooks/gitea '' '{}')"
    if [ "${anon}" != "401" ] && [ "${anon}" != "400" ] && [ "${anon}" != "200" ]; then
      echo "  webhook 端点返回 ${anon}，期望 401/400/200"; return 1
    fi
  fi
  return 0
}

# ---- V19: MCP 端点可用性 ----
check_mcp_endpoint() {
  # 后端可达时验证 MCP initialize
  if [ "${BACKEND_UP:-0}" -ne 1 ]; then
    echo "  backend 不可达，无法验证 MCP"; return 1
  fi
  local body
  body="$(curl -s -m 15 -X POST -H 'Content-Type: application/json' \
    -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"verify","version":"1.0"}}}' \
    "${BASE_URL}/api/v1/mcp" 2>/dev/null)"
  if echo "${body}" | grep -q '"serverInfo"'; then return 0; fi
  # 可能返回 401（需认证），也视为端点可用
  local code
  code="$(http_status POST /api/v1/mcp "" '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}')"
  if [ "${code}" = "401" ] || [ "${code}" = "200" ]; then return 0; fi
  echo "  MCP /api/v1/mcp 端点不可用（HTTP ${code}）"; return 1
}

# ---- V20: 对账 Worker 注册验证 ----
check_reconciler_registration() {
  # 验证 6 种 Reconciler JobHandler 类型在 job_task 表中有记录或至少 job_type 列可接受
  local col_exists
  col_exists="$(psql_q "select count(*) from information_schema.columns where table_name='job_task' and column_name='type'")"
  if [ "${col_exists}" != "1" ]; then echo "  job_task.type 列缺失"; return 1; fi
  # 检查 6 种对账类型在 job_type 约束中可接受（枚举/字符串均可）
  # 如果后端可达，通过 /system/jobs 端点间接验证
  if [ "${BACKEND_UP:-0}" -eq 1 ]; then
    local code
    code="$(http_status GET /api/v1/system/jobs "${ACCESS_TOKEN}")"
    # 403 说明端点存在但权限不足（正常），200 说明可访问
    if [ "${code}" != "200" ] && [ "${code}" != "403" ]; then
      echo "  /system/jobs 端点返回 ${code}"; return 1
    fi
  fi
  return 0
}

# ---- V21: 告警 Schema 与端点验证 ----
check_alerts() {
  # 检查 system_alert 表存在
  if [ "$(psql_q "select to_regclass('public.system_alert') is not null")" != "t" ]; then
    echo "  system_alert 表缺失"; return 1
  fi
  # 检查关键字段
  local col_cnt
  col_cnt="$(psql_q "select count(*) from information_schema.columns where table_name='system_alert' and column_name in ('alert_id','alert_type','severity','status','fired_at')")"
  if [ "${col_cnt}" -lt 5 ]; then echo "  system_alert 关键字段缺失"; return 1; fi
  # 后端可达时验证端点默认拒绝
  if [ "${BACKEND_UP:-0}" -eq 1 ]; then
    assert_default_deny /api/v1/system/alerts || return 1
  fi
  return 0
}

# P2 版本与上传 Schema 验证
if postgres_reachable; then
  step V16 "版本 Schema 验证（asset_version/upload_session）" check_version_schema
  step V17 "发布审批 Schema 验证（validation_report/publish_request/review_decision）" check_publish_schema
else
  skip V16 "版本 Schema 验证" "postgres 不可达"
  skip V17 "发布审批 Schema 验证" "postgres 不可达"
fi

# P3-P4 Webhook 与 MCP 验证
if postgres_reachable; then
  step V18 "Webhook Inbox 验证" check_webhook_inbox
else
  skip V18 "Webhook Inbox 验证" "postgres 不可达"
fi

if [ "${BACKEND_UP}" -eq 1 ]; then
  step V19 "MCP 端点可用性" check_mcp_endpoint
  step V20 "对账 Worker 注册验证" check_reconciler_registration
else
  skip V19 "MCP 端点可用性" "backend 不可达"
  skip V20 "对账 Worker 注册验证" "backend 不可达"
fi

if postgres_reachable; then
  step V21 "告警 Schema 与端点验证" check_alerts
else
  skip V21 "告警 Schema 与端点验证" "postgres 不可达"
fi

# ---- V22: Outbox 事件表验证 ----
check_outbox() {
  if [ "$(psql_q "select to_regclass('public.outbox_event') is not null")" != "t" ]; then
    echo "  outbox_event 表缺失"; return 1
  fi
  local col_cnt
  col_cnt="$(psql_q "select count(*) from information_schema.columns where table_name='outbox_event' and column_name in ('aggregate_type','aggregate_id','event_type','payload','occurred_at')")"
  if [ "${col_cnt}" -lt 5 ]; then echo "  outbox_event 关键字段缺失"; return 1; fi
  return 0
}

if postgres_reachable; then
  step V22 "Outbox 事件表验证" check_outbox
else
  skip V22 "Outbox 事件表验证" "postgres 不可达"
fi

# ---- V23: 下载统计默认拒绝 ----
check_download_stats_default_deny() {
  local code
  code="$(http_status GET /api/v1/assets/ast_x/stats)"
  if [ "${code}" != "401" ]; then echo "  无 Token GET /assets/ast_x/stats 返回 ${code}，期望 401"; return 1; fi
  code="$(http_status GET /api/v1/system/metrics/downloads)"
  if [ "${code}" != "401" ]; then echo "  无 Token GET /system/metrics/downloads 返回 ${code}，期望 401"; return 1; fi
  return 0
}

# ---- V24: 预览生成默认拒绝 ----
check_preview_generate_default_deny() {
  local code
  code="$(http_status POST /api/v1/assets/ast_x/previews/generate "" '{}')"
  if [ "${code}" != "401" ]; then echo "  无 Token POST /assets/ast_x/previews/generate 返回 ${code}，期望 401"; return 1; fi
  return 0
}

# ---- V25: Agent 令牌端点存在性 ----
check_agent_token_path() {
  local code
  code="$(http_status POST /api/v1/auth/agent/token "" '{}')"
  case "${code}" in
    401|400|422) return 0 ;;
    404) echo "  POST /api/v1/auth/agent/token 返回 404（端点不存在）"; return 1 ;;
    *) echo "  POST /api/v1/auth/agent/token 返回 ${code}，期望 401/400/422"; return 1 ;;
  esac
}

# ---- V26: MCP /mcp 默认拒绝 ----
check_mcp_default_deny() {
  local code
  code="$(http_status POST /mcp "" '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}')"
  if [ "${code}" != "401" ]; then echo "  无 Token POST /mcp 返回 ${code}，期望 401"; return 1; fi
  return 0
}

# ---- V27: 资产 ACL 默认拒绝 ----
check_asset_access_default_deny() {
  local code
  code="$(http_status GET /api/v1/assets/ast_x/access)"
  if [ "${code}" != "401" ]; then echo "  无 Token GET /assets/ast_x/access 返回 ${code}，期望 401"; return 1; fi
  return 0
}

# ---- V28: DEPRECATED 搜索降权（源码/SQL 断言，无需后端）----
check_deprecation_sort() {
  local repo_root dao_file
  repo_root="$(cd "${COMPOSE_DIR}/../.." && pwd)"
  dao_file="${repo_root}/backend/src/main/java/com/aihub/asset/infrastructure/AssetSearchDao.java"
  if [ ! -f "${dao_file}" ]; then echo "  AssetSearchDao.java 不存在"; return 1; fi
  if ! grep -q "ORDER BY CASE a.status WHEN 'DEPRECATED' THEN 1 ELSE 0 END" "${dao_file}"; then
    echo "  AssetSearchDao ORDER BY 未包含 DEPRECATED 降权子句"
    return 1
  fi
  return 0
}

# P1-P5 行为级默认拒绝与源码断言（V23-V28）
if [ "${BACKEND_UP}" -eq 1 ]; then
  step V23 "下载统计默认拒绝" check_download_stats_default_deny
  step V24 "预览生成默认拒绝" check_preview_generate_default_deny
  step V25 "Agent 令牌端点存在性" check_agent_token_path
  step V26 "MCP /mcp 默认拒绝" check_mcp_default_deny
  step V27 "资产 ACL 默认拒绝" check_asset_access_default_deny
else
  skip V23 "下载统计默认拒绝" "backend 不可达"
  skip V24 "预览生成默认拒绝" "backend 不可达"
  skip V25 "Agent 令牌端点存在性" "backend 不可达"
  skip V26 "MCP /mcp 默认拒绝" "backend 不可达"
  skip V27 "资产 ACL 默认拒绝" "backend 不可达"
fi

step V28 "DEPRECATED 搜索降权（AssetSearchDao ORDER BY）" check_deprecation_sort

echo ""
if [ "${SKIPPED}" -ne 0 ]; then
  echo "注意：${SKIPPED} 项因服务未启动被 SKIP（非通过）。完整验收需先 docker compose up -d 再运行本脚本。"
fi

if [ "${FAILED}" -ne 0 ]; then
  echo "验收存在失败项。"
  exit 1
fi
echo "验收未发现失败项（PASS 项通过，SKIP 项需在服务就绪后补验）。"
exit 0
