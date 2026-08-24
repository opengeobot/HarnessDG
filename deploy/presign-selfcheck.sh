#!/usr/bin/env bash
# deploy/presign-selfcheck.sh —— FILE-002 自检（Linux/macOS/Git-Bash 宿主机）
# 作者：AxeXie
# 日期：2026-08-24
#
# 目的：证明「客户端可达对象 public URL」—— 由 app 签发的 MinIO 预签名 part URL
#       对 Compose 网络之外的真实客户端（本脚本运行在宿主机上）可达且签名有效。
# 流程与 presign-selfcheck.ps1 完全一致：
#   login → 建仓库等 active → files head → initiate upload 等 uploading →
#   part-urls → 断言公网基址/无内部服务名 → 宿主机 PUT + HEAD 回探 → abort 清理。
# 说明：v1 默认 ContentScanner fail-closed（未配置真实扫描器拒绝发布），自检刻意
#       停在「预签名 PUT 直传成功」—— 已完整覆盖 FILE-002。
#
# 依赖：curl；python3 或 jq（二选一，用于解析 JSON）；sha256sum 或 shasum。
# 用法：./deploy/presign-selfcheck.sh
#   环境变量覆盖（与 deploy/.env 保持一致）：
#     API_BASE（默认 http://localhost:8081）
#     MINIO_PUBLIC_BASE_URL（默认 http://localhost:9000）
#     MODEHUB_USERNAME / MODEHUB_PASSWORD（默认 platform-root / Boot-Strap-1x）

set -uo pipefail

API_BASE="${API_BASE:-http://localhost:8081}"
MINIO_PUBLIC_BASE_URL="${MINIO_PUBLIC_BASE_URL:-http://localhost:9000}"
USERNAME="${MODEHUB_USERNAME:-platform-root}"
PASSWORD="${MODEHUB_PASSWORD:-Boot-Strap-1x}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-180}"

FAILED=0
fail() { echo "[FAIL] $*" >&2; FAILED=1; }
step() { echo "== $*"; }

# ---------- JSON 解析：优先 python3，回落 jq ----------
# 用法: jget '<json>' '<python 表达式，变量 d>' '<等价 jq 路径>'
jget() {
    if command -v python3 >/dev/null 2>&1; then
        python3 -c 'import sys,json; d=json.loads(sys.argv[1]); print(eval(sys.argv[2]))' "$1" "$2"
    elif command -v jq >/dev/null 2>&1; then
        printf '%s' "$1" | jq -r "$3"
    else
        echo "需要 python3 或 jq 解析 JSON，请先安装其一" >&2
        exit 2
    fi
}

sha256hex() {
    printf '%s' "$1" \
        | { command -v sha256sum >/dev/null 2>&1 && sha256sum || shasum -a 256; } \
        | awk '{print $1}'
}

# http <method> <url> [json-body] [curl 额外参数...] → 打印 "status<TAB>body"
http() {
    local method="$1" url="$2" body="${3:-}"
    shift 2; [ $# -gt 0 ] && shift
    local tmp; tmp="$(mktemp)"
    local code
    if [ -n "$body" ]; then
        code=$(curl -sS -o "$tmp" -w '%{http_code}' -X "$method" -H 'Content-Type: application/json' \
            -d "$body" "$@" "$url") || code=000
    else
        code=$(curl -sS -o "$tmp" -w '%{http_code}' -X "$method" "$@" "$url") || code=000
    fi
    printf '%s\t%s\n' "$code" "$(cat "$tmp")"
    rm -f "$tmp"
}

die() { echo "[ERROR] $*" >&2; exit 1; }

# ---------- 1. 登录 ----------
step "登录 $API_BASE/api/v1/auth/login（bootstrap 管理员）"
RESP=$(http POST "$API_BASE/api/v1/auth/login" "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
CODE=${RESP%%	*}; BODY=${RESP#*	}
[ "$CODE" = "200" ] || die "登录失败 HTTP $CODE: $BODY"
TOKEN=$(jget "$BODY" 'd["data"]["accessToken"]' '.data.accessToken')
NAMESPACE_ID=$(jget "$BODY" 'd["data"]["user"]["namespaceId"]' '.data.user.namespaceId')
[ -n "$TOKEN" ] && [ "$TOKEN" != "None" ] || die "登录响应缺少 accessToken"
[ -n "$NAMESPACE_ID" ] && [ "$NAMESPACE_ID" != "None" ] || die "登录响应缺少 namespaceId"
echo "   token 已获取；namespaceId=$NAMESPACE_ID"
AUTH=(-H "Authorization: Bearer $TOKEN")

# ---------- 2. 建仓库并等待 active ----------
REPO_NAME="presign-check-$(date +%s)"
step "创建仓库 $REPO_NAME 并等待 provisioning active"
IDEM="Idempotency-Key: $(uuidgen 2>/dev/null || python3 -c 'import uuid;print(uuid.uuid4())' 2>/dev/null || cat /proc/sys/kernel/random/uuid)"
CREATE_BODY=$(printf '{"namespaceId":"%s","type":"model","metadataSchemaVersion":1,"name":"%s","visibility":"public","metadata":{"license":"MIT"}}' \
    "$NAMESPACE_ID" "$REPO_NAME")
RESP=$(http POST "$API_BASE/api/v1/repositories" "$CREATE_BODY" "${AUTH[@]}" -H "$IDEM")
CODE=${RESP%%	*}; BODY=${RESP#*	}
[ "$CODE" = "201" ] || die "建仓失败 HTTP $CODE: $BODY"
REPO_ID=$(jget "$BODY" 'd["data"]["id"]' '.data.id')
echo "   repoId=$REPO_ID"

DEADLINE=$(( $(date +%s) + TIMEOUT_SECONDS ))
LIFE=""
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
    RESP=$(http GET "$API_BASE/api/v1/repositories/$REPO_ID" "" "${AUTH[@]}")
    LIFE=$(jget "${RESP#*	}" 'd["data"]["lifecycleStatus"]' '.data.lifecycleStatus')
    [ "$LIFE" = "active" ] && break
    sleep 3
done
[ "$LIFE" = "active" ] || die "等待仓库 active 超时（最后状态=$LIFE）"
echo "   仓库已 active"

# ---------- 3. 分支 head ----------
RESP=$(http GET "$API_BASE/api/v1/repositories/$REPO_ID/files" "" "${AUTH[@]}")
BASE_SHA=$(jget "${RESP#*	}" 'd["data"]["resolvedCommitSha"]' '.data.resolvedCommitSha')
[ -n "$BASE_SHA" ] && [ "$BASE_SHA" != "None" ] || die "files 响应缺少 resolvedCommitSha"

# ---------- 4. 发起上传并等待 uploading ----------
CONTENT="modelhub presign selfcheck @ $(date -u +%FT%TZ)"
SIZE=${#CONTENT}
SHA256=$(sha256hex "$CONTENT")
step "发起上传 presign-selfcheck.txt（${SIZE} bytes，sha256=${SHA256:0:12}...）"
IDEM="Idempotency-Key: $(uuidgen 2>/dev/null || python3 -c 'import uuid;print(uuid.uuid4())' 2>/dev/null || cat /proc/sys/kernel/random/uuid)"
INIT_BODY=$(printf '{"branch":"main","baseCommitSha":"%s","path":"presign-selfcheck.txt","sizeBytes":%s,"sha256":"%s","contentType":"text/plain"}' \
    "$BASE_SHA" "$SIZE" "$SHA256")
RESP=$(http POST "$API_BASE/api/v1/repositories/$REPO_ID/uploads" "$INIT_BODY" "${AUTH[@]}" -H "$IDEM")
CODE=${RESP%%	*}; BODY=${RESP#*	}
[ "$CODE" = "201" ] || die "initiate 失败 HTTP $CODE: $BODY"
UPLOAD_ID=$(jget "$BODY" 'd["data"]["id"]' '.data.id')
echo "   uploadId=$UPLOAD_ID"

DEADLINE=$(( $(date +%s) + TIMEOUT_SECONDS ))
STATUS=""
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
    RESP=$(http GET "$API_BASE/api/v1/uploads/$UPLOAD_ID" "" "${AUTH[@]}")
    STATUS=$(jget "${RESP#*	}" 'd["data"]["status"]' '.data.status')
    [ "$STATUS" = "uploading" ] && break
    case "$STATUS" in failed|aborted|expired) die "上传提前进入终态 $STATUS" ;; esac
    sleep 2
done
[ "$STATUS" = "uploading" ] || die "等待上传状态 uploading 超时（最后状态=$STATUS）"

# ---------- 5. 取预签名 part URL ----------
step "签发 part 1 预签名 URL"
RESP=$(http POST "$API_BASE/api/v1/uploads/$UPLOAD_ID/part-urls" '{"partNumbers":[1]}' "${AUTH[@]}")
URL=$(jget "${RESP#*	}" 'd["data"]["items"][0]["url"]' '.data.items[0].url')
[ -n "$URL" ] && [ "$URL" != "None" ] || die "part-urls 响应缺少 items[0].url"
echo "   $URL"

# ---------- 6. FILE-002 断言 ----------
step "FILE-002 断言：URL 指向公网端点且不含内部服务名"
case "$URL" in
    "$MINIO_PUBLIC_BASE_URL"/*) echo "   [OK] 以公网基址 $MINIO_PUBLIC_BASE_URL 开头" ;;
    *) fail "预签名 URL 不以公网基址 $MINIO_PUBLIC_BASE_URL 开头（检查 MODEHUB_ARTIFACT_PUBLIC_BASE_URL）" ;;
esac
case "$URL" in
    *"://minio:"*|*"://gitea:"*|*"://postgres:"*) fail "预签名 URL 含内部 Docker 服务名: $URL" ;;
    *) echo "   [OK] 未发现内部服务名" ;;
esac

# ---------- 7. 宿主机直传 + 回探 ----------
step "从宿主机（Compose 网络之外）PUT 到预签名 URL"
# x-amz-content-sha256 参与服务端签名（SignedHeaders），客户端必须回传同值头
PUT_CODE=$(curl -sS -o /dev/null -w '%{http_code}' -X PUT \
    -H 'x-amz-content-sha256: UNSIGNED-PAYLOAD' --data-binary "$CONTENT" "$URL") || PUT_CODE=000
if [ "$PUT_CODE" -ge 200 ] 2>/dev/null && [ "$PUT_CODE" -lt 300 ] 2>/dev/null; then
    echo "   [OK] PUT $PUT_CODE —— 外部可达且签名有效"
else
    fail "PUT 返回 $PUT_CODE（403=签名/可达性问题，000=连接失败）"
fi

step "HEAD 回探同一 URL（任何 HTTP 应答即证明外部可达；预签名按方法签名，HEAD 预期 403）"
HEAD_CODE=$(curl -sS -o /dev/null -w '%{http_code}' -I "$URL") || HEAD_CODE=000
if [ "$HEAD_CODE" -gt 0 ] 2>/dev/null; then
    echo "   [OK] HEAD 得到 HTTP $HEAD_CODE 应答（服务端可达）"
else
    fail "HEAD 无 HTTP 应答（连接失败）"
fi

# ---------- 8. 清理（尽力而为）----------
step "清理：abort 上传会话"
http POST "$API_BASE/api/v1/uploads/${UPLOAD_ID}:abort" "" "${AUTH[@]}" >/dev/null \
    && echo "   已 abort" || echo "   [WARN] abort 失败（不影响自检结论）"

echo ""
if [ "$FAILED" -eq 0 ]; then
    echo "RESULT: PASS —— FILE-002 满足：预签名对象 URL 对外部客户端可达"
    exit 0
fi
echo "RESULT: FAIL —— FILE-002 未满足（对象 public URL 对外部客户端不可达）" >&2
exit 1
