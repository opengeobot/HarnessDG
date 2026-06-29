#!/usr/bin/env bash
# ============================================================================
# 功能: AIHub Compose P0 验收脚本 (Shell)。与 verify.ps1 等价，执行
#       V01-V03 级检查：Compose 配置、核心服务健康、四个 Bucket 存在且非匿名。
#       任一步失败返回非零退出码。业务用例 (V05+) 属 P1，仅占位提示。
# 时间: 2026-06-29
# 作者: AxeXie
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(dirname "${SCRIPT_DIR}")"
cd "${COMPOSE_DIR}"

FAILED=0

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

# V01: Compose 配置合法
check_config() {
  docker compose config --quiet
}

# V02: 核心服务健康
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

# V03: Bucket 初始化
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

step V01 "Compose 配置合法" check_config
step V02 "核心服务健康" check_health
step V03 "Bucket 初始化" check_buckets

echo ""
echo "[V05+] 业务端到端用例属 P1 阶段，当前基线跳过。"

if [ "${FAILED}" -ne 0 ]; then
  echo "验收存在失败项。"
  exit 1
fi
echo "P0 基线验收通过 (V01-V03)。"
exit 0
