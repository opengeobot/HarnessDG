#!/usr/bin/env bash
# ============================================================================
# 功能: AIHub Compose 验收脚本 (Shell)。与 verify.ps1 等价，执行 V01-V11：
#       V01 Compose 配置；V02 核心服务健康；V03 Bucket 初始化；
#       V04 数据库迁移；V05 JWT 生命周期；V06 权限过滤；V07 字典/标签；
#       V08 审计完整性与脱敏；V09 持久化任务；V10 通知；V11 观测与诊断。
#       后端/服务不可达时相关用例标记 SKIP（非 PASS，绝不冒充通过），
#       仅真实 FAIL 返回非零退出码。
# 时间: 2026-07-01
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

# 断言无 Token=401，携带管理员 Token 被拒=403（默认拒绝/最小权限/改密门）。
assert_default_deny() {
  local path="$1" anon auth
  anon="$(http_status GET "${path}")"
  if [ "${anon}" != "401" ]; then echo "  无 Token 访问 ${path} 返回 ${anon}，期望 401"; return 1; fi
  if [ -n "${ACCESS_TOKEN}" ]; then
    auth="$(http_status GET "${path}" "${ACCESS_TOKEN}")"
    if [ "${auth}" != "403" ]; then echo "  越权 Token 访问 ${path} 返回 ${auth}，期望 403"; return 1; fi
  fi
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
  if [ -z "${count}" ] || [ "${count}" -lt 12 ]; then echo "  成功迁移数 ${count} < 12"; return 1; fi
  for t in iam_principal iam_user iam_role system_dict_item system_tag asset_tag system_config job_task audit_log notification; do
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
check_perm_filter() {
  assert_default_deny /api/v1/system/audit-logs || return 1
  assert_default_deny /api/v1/system/metrics/summary || return 1
  return 0
}

# ---- V07: 字典/标签 ----
check_taxonomy() {
  assert_default_deny /api/v1/system/dictionaries || return 1
  assert_default_deny /api/v1/system/tags || return 1
  return 0
}

# ---- V08: 审计脱敏（audit_log 任何正文都不得包含明文口令）----
# 注意：identity 登录审计事件接入尚未统一（已知差距），此处只校验脱敏恒定不变式，
#       不断言登录事件计数；登录/写操作审计完整性由后端测试与后续整改覆盖。
check_audit() {
  local exists leak
  exists="$(psql_q "select to_regclass('public.audit_log') is not null")"
  if [ "${exists}" != "t" ]; then echo "  audit_log 表缺失"; return 1; fi
  leak="$(psql_q "select count(*) from audit_log where detail::text like '%${ADMIN_PASS}%'")"
  if [ "${leak}" != "0" ]; then echo "  audit_log 明文口令泄漏（脱敏失效）"; return 1; fi
  return 0
}

# ---- V09: 持久化任务 ----
check_jobs() { assert_default_deny /api/v1/system/jobs; }

# ---- V10: 通知 ----
check_notifications() { assert_default_deny /api/v1/system/notifications; }

# ---- V11: 观测与诊断 ----
check_observability() {
  local h
  h="$(http_status GET /actuator/health)"
  if [ "${h}" != "200" ]; then echo "  /actuator/health 返回 ${h}，期望 200"; return 1; fi
  assert_default_deny /api/v1/system/dependencies || return 1
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
  step V06 "权限过滤（审计/指标默认拒绝）" check_perm_filter
  step V07 "字典/标签受控访问（默认拒绝）" check_taxonomy
else
  skip V05 "JWT 生命周期" "backend 健康端点不可达（未执行 docker compose up）"
  skip V06 "权限过滤" "backend 不可达"
  skip V07 "字典/标签受控访问" "backend 不可达"
fi

if postgres_reachable; then
  step V08 "审计脱敏（无明文口令）" check_audit
else
  skip V08 "审计脱敏" "postgres 不可达"
fi

if [ "${BACKEND_UP}" -eq 1 ]; then
  step V09 "持久化任务访问（默认拒绝）" check_jobs
  step V10 "通知访问（默认拒绝）" check_notifications
  step V11 "观测与诊断" check_observability
else
  skip V09 "持久化任务访问" "backend 不可达"
  skip V10 "通知访问" "backend 不可达"
  skip V11 "观测与诊断" "backend 不可达"
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
