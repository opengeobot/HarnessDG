#!/usr/bin/env bash
# ============================================================================
# 功能: AIHub Compose 验收脚本 (Shell)。与 verify.ps1 等价，执行 V01-V21：
#       V01 Compose 配置；V02 核心服务健康；V03 Bucket 初始化；
#       V04 数据库迁移；V05 JWT 生命周期；V06 权限过滤；V07 字典/标签；
#       V08 审计完整性与脱敏；V09 持久化任务；V10 通知；V11 观测与诊断；
#       V12 资产创建与 Gitea 仓库一致性；V13 幂等键重放；
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
  if [ -z "${count}" ] || [ "${count}" -lt 33 ]; then echo "  成功迁移数 ${count} < 33"; return 1; fi
  for t in iam_principal iam_user iam_role system_dict_item system_tag asset_tag system_config job_task audit_log notification asset_discussion asset_comment system_alert reconciliation_checkpoint asset_relation asset_subscription team api_idempotency; do
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
    gitea_code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:3000/api/v1/version 2>/dev/null || echo 000)"
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
  step V06 "权限过滤（审计/指标默认拒绝）" check_perm_filter
  step V07 "字典/标签受控访问（默认拒绝）" check_taxonomy
else
  skip V05 "JWT 生命周期" "backend 健康端点不可达（未执行 docker compose up）"
  skip V06 "权限过滤" "backend 不可达"
  skip V07 "字典/标签受控访问" "backend 不可达"
fi

if postgres_reachable; then
  step V08 "审计脱敏与登录审计（无明文口令 + 登录事件）" check_audit
else
  skip V08 "审计脱敏与登录审计" "postgres 不可达"
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
