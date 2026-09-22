#!/usr/bin/env bash
# ============================================================================
# 功能: AIHub 轻量 Schema/源码断言验收（CI verify-schema 作业）。
#       执行 verify.sh 中无需全栈 Compose 的子集：V04 迁移完整性、V12–V17/V21/V22
#       表结构断言，以及 V28 DEPRECATED 搜索降权源码断言。
#       支持两种 Postgres 连接模式：
#         1) Compose：postgres 容器运行时用 docker compose exec
#         2) CI 服务容器：POSTGRES_HOST/PORT/USER/PASSWORD/DB + psql 客户端
# 时间: 2026-07-11
# 作者: AxeXie
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(dirname "${SCRIPT_DIR}")"
REPO_ROOT="$(cd "${COMPOSE_DIR}/../.." && pwd)"

FAILED=0
MIN_MIGRATIONS="${MIN_MIGRATIONS:-33}"

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

postgres_reachable() {
  if [ -n "${POSTGRES_HOST:-}" ]; then
    PGPASSWORD="${POSTGRES_PASSWORD}" psql \
      -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT:-5432}" \
      -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -c 'SELECT 1' >/dev/null 2>&1
    return $?
  fi
  cd "${COMPOSE_DIR}"
  local state
  state="$(docker compose ps --format '{{.Service}} {{.State}}' postgres 2>/dev/null)"
  [[ "${state}" == *" running"* ]]
}

psql_q() {
  if [ -n "${POSTGRES_HOST:-}" ]; then
    PGPASSWORD="${POSTGRES_PASSWORD}" psql \
      -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT:-5432}" \
      -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
      -tAc "$1" 2>/dev/null | tr -d '[:space:]'
    return
  fi
  cd "${COMPOSE_DIR}"
  docker compose exec -T postgres sh -c "psql -U \$POSTGRES_USER -d \$POSTGRES_DB -tAc \"$1\"" 2>/dev/null | tr -d '[:space:]'
}

check_migrations() {
  local count ok=0
  count="$(psql_q 'select count(*) from flyway_schema_history where success = true')"
  if [ -z "${count}" ] || [ "${count}" -lt "${MIN_MIGRATIONS}" ]; then
    echo "  成功迁移数 ${count:-0} < ${MIN_MIGRATIONS}"
    return 1
  fi
  for t in iam_principal iam_user iam_role system_dict_item system_tag asset_tag system_config job_task audit_log notification asset_discussion asset_comment system_alert reconciliation_checkpoint asset_relation asset_subscription team api_idempotency; do
    if [ "$(psql_q "select to_regclass('public.${t}') is not null")" != "t" ]; then
      echo "  关键表 ${t} 缺失"
      ok=1
    fi
  done
  return ${ok}
}

check_version_schema() {
  local ok=0
  for t in asset_version upload_session upload_file upload_part version_artifact; do
    if [ "$(psql_q "select to_regclass('public.${t}') is not null")" != "t" ]; then
      echo "  关键表 ${t} 缺失"
      ok=1
    fi
  done
  local col_cnt
  col_cnt="$(psql_q "select count(*) from information_schema.columns where table_name='asset_version' and column_name in ('version','status','asset_id','manifest_digest')")"
  if [ "${col_cnt}" -lt 4 ]; then
    echo "  asset_version 关键字段缺失（需 version/status/asset_id/manifest_digest）"
    ok=1
  fi
  return ${ok}
}

check_publish_schema() {
  local ok=0
  for t in validation_report publish_request review_decision; do
    if [ "$(psql_q "select to_regclass('public.${t}') is not null")" != "t" ]; then
      echo "  关键表 ${t} 缺失"
      ok=1
    fi
  done
  local col_cnt
  col_cnt="$(psql_q "select count(*) from information_schema.columns where table_name='publish_request' and column_name in ('version_id','status','submitted_by')")"
  if [ "${col_cnt}" -lt 3 ]; then
    echo "  publish_request 关键字段缺失"
    ok=1
  fi
  return ${ok}
}

check_alerts_schema() {
  if [ "$(psql_q "select to_regclass('public.system_alert') is not null")" != "t" ]; then
    echo "  system_alert 表缺失"
    return 1
  fi
  local col_cnt
  col_cnt="$(psql_q "select count(*) from information_schema.columns where table_name='system_alert' and column_name in ('alert_id','alert_type','severity','status','fired_at')")"
  if [ "${col_cnt}" -lt 5 ]; then
    echo "  system_alert 关键字段缺失"
    return 1
  fi
  return 0
}

check_outbox_schema() {
  if [ "$(psql_q "select to_regclass('public.outbox_event') is not null")" != "t" ]; then
    echo "  outbox_event 表缺失"
    return 1
  fi
  local col_cnt
  col_cnt="$(psql_q "select count(*) from information_schema.columns where table_name='outbox_event' and column_name in ('aggregate_type','aggregate_id','event_type','payload','occurred_at')")"
  if [ "${col_cnt}" -lt 5 ]; then
    echo "  outbox_event 关键字段缺失"
    return 1
  fi
  return 0
}

check_deprecation_sort() {
  local dao_file="${REPO_ROOT}/backend/src/main/java/com/aihub/asset/infrastructure/AssetSearchDao.java"
  if [ ! -f "${dao_file}" ]; then
    echo "  AssetSearchDao.java 不存在"
    return 1
  fi
  if ! grep -q "ORDER BY CASE a.status WHEN 'DEPRECATED' THEN 1 ELSE 0 END" "${dao_file}"; then
    echo "  AssetSearchDao ORDER BY 未包含 DEPRECATED 降权子句"
    return 1
  fi
  return 0
}

# V28 始终执行（无需数据库）
step V28 "DEPRECATED 搜索降权（AssetSearchDao ORDER BY）" check_deprecation_sort

if postgres_reachable; then
  step V04 "数据库迁移完整性" check_migrations
  step V16 "版本 Schema 验证" check_version_schema
  step V17 "发布审批 Schema 验证" check_publish_schema
  step V21 "告警 Schema 验证" check_alerts_schema
  step V22 "Outbox 事件表验证" check_outbox_schema
else
  echo "[V04] 数据库迁移完整性 ... SKIP: postgres 不可达"
  echo "[V16] 版本 Schema 验证 ... SKIP: postgres 不可达"
  echo "[V17] 发布审批 Schema 验证 ... SKIP: postgres 不可达"
  echo "[V21] 告警 Schema 验证 ... SKIP: postgres 不可达"
  echo "[V22] Outbox 事件表验证 ... SKIP: postgres 不可达"
  FAILED=1
fi

if [ "${FAILED}" -ne 0 ]; then
  echo "Schema 验收存在失败项。"
  exit 1
fi
echo "Schema 验收未发现失败项。"
exit 0
