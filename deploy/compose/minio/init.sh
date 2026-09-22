#!/bin/sh
# ============================================================================
# 功能: 初始化 MinIO——创建四个 Bucket（gitea-storage / dvc-cache /
#       asset-staging / asset-preview），并为 Gitea、后端、DVC 创建
#       相互隔离的最小权限服务账号。正式 Bucket 默认私有，禁止匿名访问。
# 时间: 2026-06-29
# 作者: AxeXie
# ============================================================================
set -eu

MC_ALIAS="local"
ENDPOINT="http://minio:9000"

echo "[minio-init] 等待 MinIO 就绪并配置别名..."
mc alias set "${MC_ALIAS}" "${ENDPOINT}" "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}"

echo "[minio-init] 创建 Bucket..."
for bucket in gitea-storage dvc-cache asset-staging asset-preview; do
  mc mb --ignore-existing "${MC_ALIAS}/${bucket}"
  # 确保私有：移除任何匿名访问策略
  mc anonymous set none "${MC_ALIAS}/${bucket}" || true
done

# 暂存桶设置生命周期（24~72 小时回收），此处取 2 天
echo "[minio-init] 为 asset-staging 设置生命周期回收策略..."
mc ilm rule add --expire-days 2 "${MC_ALIAS}/asset-staging" 2>/dev/null \
  || mc ilm add --expiry-days 2 "${MC_ALIAS}/asset-staging" 2>/dev/null \
  || echo "[minio-init] 跳过生命周期设置（当前 mc 版本语法不支持，可后续手动配置）"

create_user_with_policy() {
  user_key="$1"
  user_secret="$2"
  policy_name="$3"
  policy_file="$4"

  echo "[minio-init] 创建服务账号 ${user_key} 并绑定策略 ${policy_name}..."
  mc admin user add "${MC_ALIAS}" "${user_key}" "${user_secret}"
  printf '%s' "${policy_file}" > "/tmp/${policy_name}.json"
  mc admin policy create "${MC_ALIAS}" "${policy_name}" "/tmp/${policy_name}.json"
  mc admin policy attach "${MC_ALIAS}" "${policy_name}" --user "${user_key}"
}

# Gitea 账号：仅访问 gitea-storage
GITEA_POLICY='{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["s3:*"],"Resource":["arn:aws:s3:::gitea-storage","arn:aws:s3:::gitea-storage/*"]}]}'
create_user_with_policy "${GITEA_MINIO_ACCESS_KEY}" "${GITEA_MINIO_SECRET_KEY}" "gitea-storage-rw" "${GITEA_POLICY}"

# 后端账号：访问 asset-staging 与 asset-preview
AIHUB_POLICY='{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["s3:*"],"Resource":["arn:aws:s3:::asset-staging","arn:aws:s3:::asset-staging/*","arn:aws:s3:::asset-preview","arn:aws:s3:::asset-preview/*"]}]}'
create_user_with_policy "${AIHUB_MINIO_ACCESS_KEY}" "${AIHUB_MINIO_SECRET_KEY}" "aihub-asset-rw" "${AIHUB_POLICY}"

# DVC 账号（dvc-user）：dvc-cache 最小权限 + STS AssumeRole 签发基础
DVC_USER_KEY="${AIHUB_MINIO_DVC_ACCESS_KEY:-${DVC_ACCESS_KEY}}"
DVC_USER_SECRET="${AIHUB_MINIO_DVC_SECRET_KEY:-${DVC_SECRET_KEY}}"
DVC_SCOPED_POLICY='{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["s3:GetObject","s3:PutObject","s3:DeleteObject","s3:ListBucket"],"Resource":["arn:aws:s3:::dvc-cache","arn:aws:s3:::dvc-cache/*"]}]}'
create_user_with_policy "${DVC_USER_KEY}" "${DVC_USER_SECRET}" "dvc-scoped" "${DVC_SCOPED_POLICY}"

echo "[minio-init] 初始化完成。"
mc ls "${MC_ALIAS}"
