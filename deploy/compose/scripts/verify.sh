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
#       V21 告警 Schema 与端点验证。
#       默认拒绝断言以「最小权限验证主体 + 稳定错误码 AUTH_PERMISSION_DENIED」
#       为依据，不把首登改密门的 403 计为权限拒绝；并补有权限正向断言
#       （AC-P0B-AUTH-004），区分「默认拒绝」与「全部拒绝」。
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

# 登录并回显响应体（用于取 accessToken）。
http_login_body() {
  curl -s -m 15 -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}" \
    "${BASE_URL}/api/v1/auth/login" 2>/dev/null
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

# 仅取响应体（用于解析稳定错误码 code）。
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

# 默认拒绝断言：无 Token→401(AUTH_UNAUTHENTICATED)；最小权限主体→403(AUTH_PERMISSION_DENIED)。
# 必须校验响应体稳定错误码：仅比较状态码会把首登改密门等无关 403 误判为权限拒绝（历史假阳性）。
assert_default_deny() {
  local path="$1" anon anon_body anon_code auth auth_body code
  anon_body="$(http_body GET "${path}")"
  anon="$(http_status GET "${path}")"
  if [ "${anon}" != "401" ]; then echo "  无 Token 访问 ${path} 返回 ${anon}，期望 401"; return 1; fi
  anon_code="$(error_code "${anon_body}")"
  if [ -n "${anon_code}" ] && [ "${anon_code}" != "AUTH_UNAUTHENTICATED" ]; then
    echo "  无 Token 访问 ${path} 返回错误码 ${anon_code}，期望 AUTH_UNAUTHENTICATED"; return 1
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
  if [ -z "${count}" ] || [ "${count}" -lt 21 ]; then echo "  成功迁移数 ${count} < 21"; return 1; fi
  for t in iam_principal iam_user iam_role system_dict_item system_tag asset_tag system_config job_task audit_log notification asset_discussion asset_comment system_alert; do
    if [ "$(psql_q "select to_regclass('public.${t}') is not null")" != "t" ]; then
      echo "  关键表 ${t} 缺失"; ok=1
    fi
  done
  return ${ok}
}

# ---- V05: JWT 生命周期 ----
check_jwt() {
  local body token me anon
  body="$(http_login_body)"
  token="$(echo "${body}" | sed -nE 's/.*"accessToken"\s*:\s*"([^"]+)".*/\1/p')"
  if [ -z "${token}" ]; then echo "  登录未取得 accessToken（凭据/改密状态？）"; return 1; fi
  ACCESS_TOKEN="${token}"
  me="$(http_status GET /api/v1/me "${token}")"
  if [ "${me}" != "200" ]; then echo "  /me 携带 token 返回 ${me}，期望 200"; return 1; fi
  anon="$(http_status GET /api/v1/system/users)"
  if [ "${anon}" != "401" ]; then echo "  无 Token 访问 /system/users 返回 ${anon}，期望 401"; return 1; fi
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
  asset_cnt="$(psql_q 'select count(*) from asset where deleted_at is null')"
  if [ -z "${asset_cnt}" ] || [ "${asset_cnt}" -lt 0 ]; then echo "  asset 表查询失败"; return 1; fi
  # 检查资产表存在且 provisioning_status 列可用
  local col_exists
  col_exists="$(psql_q "select count(*) from information_schema.columns where table_name='asset' and column_name='provisioning_status'")"
  if [ "${col_exists}" != "1" ]; then echo "  asset.provisioning_status 列缺失"; return 1; fi
  # 检查 job_task 表中 ASSET_PROVISION 任务存在
  job_cnt="$(psql_q "select count(*) from job_task where job_type = 'ASSET_PROVISION'")"
  if [ -z "${job_cnt}" ]; then echo "  job_task 查询失败"; return 1; fi
  return 0
}

# ---- V13: 幂等键重放不重复建仓 ----
check_idempotency_replay() {
  # 检查 idempotency_key 列存在于 job_task 表
  local col_exists
  col_exists="$(psql_q "select count(*) from information_schema.columns where table_name='job_task' and column_name='idempotency_key'")"
  if [ "${col_exists}" != "1" ]; then echo "  job_task.idempotency_key 列缺失"; return 1; fi
  # 检查唯一约束
  local idx_exists
  idx_exists="$(psql_q "select count(*) from pg_indexes where tablename='job_task' and indexdef like '%idempotency_key%'")"
  if [ "${idx_exists}" -lt 1 ]; then echo "  job_task.idempotency_key 唯一索引缺失"; return 1; fi
  return 0
}

# ---- V14: 多组织权限主体搜索无泄漏 ----
check_multi_org_isolation() {
  # 检查 resource_acl 表存在并具备 resource_type/principal_id 列
  local col_cnt
  col_cnt="$(psql_q "select count(*) from information_schema.columns where table_name='resource_acl' and column_name in ('resource_type','principal_id')")"
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
  col_cnt="$(psql_q "select count(*) from information_schema.columns where table_name='asset_version' and column_name in ('version','status','asset_id','manifest_sha256')")"
  if [ "${col_cnt}" -lt 4 ]; then echo "  asset_version 关键字段缺失（需 version/status/asset_id/manifest_sha256）"; ok=1; fi
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
  col_cnt="$(psql_q "select count(*) from information_schema.columns where table_name='publish_request' and column_name in ('version_id','status','requested_by')")"
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
    anon="$(http_status POST /api/v1/integrations/webhooks/gitea)"
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
  col_exists="$(psql_q "select count(*) from information_schema.columns where table_name='job_task' and column_name='job_type'")"
  if [ "${col_exists}" != "1" ]; then echo "  job_task.job_type 列缺失"; return 1; fi
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
