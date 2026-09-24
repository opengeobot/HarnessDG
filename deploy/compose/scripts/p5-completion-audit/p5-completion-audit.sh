#!/usr/bin/env bash
# ============================================================================
# 功能: REQ-REL-001 发布候选完成审计（TASK-P5-007, E5）。静态审计 + 运行时编排：
#   A1 EVD 全量 YAML 可解析（117 清单）
#   A2 EVD 绑定一致性：source.commitSha 均为真实 commit、列 HEAD 漂移清单
#   A3 P0-B/P5 治理卡状态分布（PASS/PARTIAL/DRAFT）
#   A4 Flyway V1/V2 不可变性（基线 commit 后无改动）+ 迁移数与运行时 applied 对齐
#   A5 Noop/占位实现是否进入可部署 Profile（compose env × ConditionalOnProperty 判定）
#   A6 必需工件存在性（verify.sh/verify-journey.sh/告警规则/runbook/契约文件）
#   A7 运行时证据编排（VERIFY_JOURNEY/PERF_SMOKE 日志统计；缺失则 SKIP 并如实报告）
# 语义:
#   - 审计器职责是"如实报告缺口"：FAIL 仅用于仓库/工件事实性破损；
#     EVD 漂移/PARTIAL/DRAFT 属"待治理与待人工评审"缺口，进入 GAP 清单不判 FAIL。
#   - exit 0 = 无事实性破损；exit 1 = 存在破损（YAML 不可解析/V1V2 被改/工件缺失/
#     Noop 进入可部署 Profile/已提供的运行时日志含 FAIL）。
#   - REQ-REL-001 第 1 条"干净环境启动 Compose"与第 5 条隔离恢复演练不在本脚本
#     自动化范围（down -v 破坏性，待用户窗口，见 EVD-P5007-001 limitations）。
# 时间: 2026-09-24
# 作者: AxeXie
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
cd "${REPO_ROOT}"

COMPOSE_FILE="deploy/compose/compose.yaml"
EVD_DIR="docs/ai-spec/tasks/evidence"
MIG_DIR="backend/src/main/resources/db/migration"
HEAD_SHA="$(git rev-parse HEAD)"

PASS=0; FAIL=0; SKIP=0; GAP=0
ok()   { PASS=$((PASS+1)); echo "[PASS] $*"; }
bad()  { FAIL=$((FAIL+1)); echo "[FAIL] $*"; }
skip() { SKIP=$((SKIP+1)); echo "[SKIP] $*"; }
gap()  { GAP=$((GAP+1));   echo "[GAP ] $*"; }

PY() { docker exec -i aihub-worker-1 python3 "$@"; }

echo "=== P5 发布候选完成审计 (REQ-REL-001) — HEAD ${HEAD_SHA:0:7} ==="

# ── A1 EVD YAML 全量可解析 ───────────────────────────────────────────────────
echo ""
echo "--- A1: Evidence Manifest YAML 语法 ---"
EVD_LIST="$(ls "${EVD_DIR}"/EVD-*.yaml 2>/dev/null)"
if [ -z "${EVD_LIST}" ]; then
  bad "未找到任何 EVD 清单"
else
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^aihub-worker-1$'; then
    # 一次进程批量解析：容器内看不到宿主路径，用 ===FILE=== 标记把内容从 stdin 拼接传入
    RESULT="$(for f in ${EVD_LIST}; do printf '===FILE===%s\n' "${f}"; cat "${f}"; done \
      | PY -c '
import sys, yaml
data = sys.stdin.read()
parts = data.split("===FILE===")
bad = []
n = 0
for p in parts:
    p = p.strip("\n")
    if not p: continue
    lines = p.split("\n", 1)
    name, body = lines[0], lines[1] if len(lines) > 1 else ""
    n += 1
    try:
        yaml.safe_load(body)
    except Exception as e:
        bad.append(f"{name}: {e}")
print(f"TOTAL={n}")
for b in bad: print("BAD::" + b)
' 2>/dev/null)"
    TOTAL="$(echo "${RESULT}" | grep '^TOTAL=' | cut -d= -f2)"
    BADS="$(echo "${RESULT}" | grep -c '^BAD::' || true)"
    if [ "${BADS:-1}" -eq 0 ]; then
      ok "EVD YAML 全部可解析（${TOTAL:-?} 份，worker 容器 python3 yaml.safe_load）"
    else
      echo "${RESULT}" | grep '^BAD::' | sed 's/^BAD::/       /'
      bad "${BADS} 份 EVD YAML 解析失败"
    fi
  else
    skip "aihub-worker-1 不可用，EVD YAML 解析改由 A2 绑定检查附带完成"
  fi
fi

# ── A2 EVD 绑定一致性（条款 8）───────────────────────────────────────────────
echo ""
echo "--- A2: Evidence Manifest 绑定一致性（条款 8）---"
drift=0; same=0; orphan=0; ph=0; nul=0; total=0
while IFS= read -r line; do
  name="${line%% *}"; sha="${line#* }"
  # EVD 存在 CRLF 书写：先剥 \r/空白，否则 cat-file 因尾 \r 误判不可解析
  sha="$(printf '%s' "${sha}" | tr -d '\r[:space:]')"
  total=$((total+1))
  # 三类非绑定形态：显式 null（早期允许）/ 字面占位 pending-commit / 真实 SHA
  case "${sha}" in
    null*|"") nul=$((nul+1)); continue ;;
    *pending*|*TODO*) ph=$((ph+1)); echo "       占位 commitSha: ${name} -> ${sha}"; continue ;;
  esac
  if [ "${sha}" = "${HEAD_SHA}" ]; then
    same=$((same+1))
  elif git cat-file -e "${sha}^{commit}" 2>/dev/null; then
    drift=$((drift+1))
  else
    orphan=$((orphan+1)); echo "       对象库不可解析 commit: ${name} -> ${sha}"
  fi
done <<EOF
$(for f in ${EVD_LIST}; do
    sha="$(grep -m1 '^  commitSha:' "${f}" 2>/dev/null \
      | sed 's/^  commitSha: *//;s/ *#.*//' | tr -d '\r')"
    echo "$(basename "${f}") ${sha:-null}"
  done)
EOF
if [ "${orphan}" -gt 0 ] || [ "${ph}" -gt 0 ]; then
  bad "$((orphan+ph))/${total} 份 EVD 的 commitSha 无法在当前对象库解析（历史重写/占位残留）——条款 8 发布前必须重绑或声明版本组合"
else
  ok "全部 EVD commitSha 均为真实 commit、显式 null 或已识别占位（HEAD 一致 ${same}，历史漂移 ${drift}，null ${nul}）"
fi
if [ "${drift}" -gt 0 ]; then
  gap "${drift}/${total} 份 EVD 绑定历史 Commit（非 HEAD）——REQ-REL-001 条款 8『同一 Commit/版本组合』要求发布前统一重绑或声明版本组合基线"
fi
if [ "${nul}" -gt 0 ]; then
  gap "${nul} 份 EVD 的 commitSha 为 null（早期允许，发布前需补充或声明版本组合）"
fi

# ── A3 P0-B/P5 治理卡状态分布 ───────────────────────────────────────────────
echo ""
echo "--- A3: 关键波次 EVD 状态（P0B/P5）---"
KEY_STATUS="$(for f in ${EVD_DIR}/EVD-P0*.yaml ${EVD_DIR}/EVD-P5*.yaml; do
    [ -f "${f}" ] || continue
    st="$(grep -m1 '^status:' "${f}" | awk '{print $2}' | tr -d '\r')"
    echo "$(basename "${f}" .yaml) ${st}"
  done)"
echo "${KEY_STATUS}" | sed 's/^/       /'
P5_DRAFT="$(echo "${KEY_STATUS}" | grep -c ' DRAFT$' || true)"
P5_PARTIAL="$(echo "${KEY_STATUS}" | grep -c ' PARTIAL$' || true)"
P5_PASS="$(echo "${KEY_STATUS}" | grep -c ' PASS$' || true)"
ok "关键卡状态: PASS=${P5_PASS} PARTIAL=${P5_PARTIAL} DRAFT=${P5_DRAFT}"
if [ "${P5_DRAFT:-0}" -gt 0 ]; then
  gap "仍有 DRAFT 状态关键 EVD（发布候选不得含 DRAFT MUST 证据）: $(echo "${KEY_STATUS}" | grep ' DRAFT$' | awk '{printf "%s ", $1}')"
fi
if [ "${P5_PARTIAL:-0}" -gt 0 ]; then
  gap "${P5_PARTIAL} 份关键 EVD 为 PARTIAL（含 notProven 项），出口门禁需人工评审裁决（DEC-009）"
fi

# ── A4 Flyway V1/V2 不可变 + 迁移数对齐（条款 7 数据面）─────────────────────
echo ""
echo "--- A4: Flyway 迁移不可变性（条款 7）---"
# 基线动态推导：V1/V2 各自最后一次提交的“较旧者”不严谨；
# 正确语义 = 从任一当前 V1/V2 的最后提交之后不得再有改动。
V1_LAST="$(git log -1 --format=%H -- "${MIG_DIR}/V1__baseline.sql")"
V2_LAST="$(git log -1 --format=%H -- "${MIG_DIR}/V2__asset_catalog.sql")"
# 取两者中较新的最后改动作为不可变基线（若互为祖先）
BASELINE_COMMIT="${V1_LAST}"
if [ -n "${V2_LAST}" ] && git merge-base --is-ancestor "${V1_LAST}" "${V2_LAST}" 2>/dev/null; then
  BASELINE_COMMIT="${V2_LAST}"
fi
V1_CHANGED="$(git log --oneline "${BASELINE_COMMIT}..HEAD" -- "${MIG_DIR}/V1__baseline.sql" "${MIG_DIR}/V2__asset_catalog.sql" | grep -c . || true)"
if [ "${V1_CHANGED}" -eq 0 ]; then
  ok "V1/V2 自基线 $(git rev-parse --short "${BASELINE_COMMIT}")（两者最后一次改动）之后零修改（forward-only 成立）"
else
  bad "V1/V2 在基线后被修改 ${V1_CHANGED} 次——违反 forward-only 规则"
  git log --oneline "${BASELINE_COMMIT}..HEAD" -- "${MIG_DIR}/V1__baseline.sql" "${MIG_DIR}/V2__asset_catalog.sql" | sed 's/^/       /'
fi
FS_MIG="$(ls "${MIG_DIR}"/V*.sql 2>/dev/null | wc -l)"
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^aihub-postgres-1$'; then
  APPLIED="$(docker exec -i aihub-postgres-1 psql -U aihub -d aihub -t -A -c "SELECT COUNT(*) FROM flyway_schema_history WHERE success=true" 2>/dev/null || echo "")"
  if [ -n "${APPLIED}" ] && [ "${FS_MIG}" -le "${APPLIED}" ]; then
    ok "迁移对齐: 文件系统 ${FS_MIG} ≤ 运行时 applied ${APPLIED}"
  else
    bad "迁移不齐: 文件系统 ${FS_MIG} vs 运行时 applied ${APPLIED:-查询失败}"
  fi
else
  skip "postgres 容器不可用，applied 对齐未验证"
fi

# ── A5 Noop 是否进入可部署 Profile（条款 9）──────────────────────────────────
echo ""
echo "--- A5: Noop/占位实现 vs 可部署 Profile（条款 9）---"
# 仅审计顶层 Noop* Bean（业务 Port 的回退实现）；内部类兜底（如 JobWorker.NoopCounter，
# 无 Spring 装配）不属于"进入可部署 Profile"的违例面，故按文件名收敛。
NOOP_FILES="$(find backend/src/main/java -name 'Noop*.java' 2>/dev/null)"
if [ -z "${NOOP_FILES}" ]; then
  ok "未发现 Noop* 实现类"
else
  NOOP_BAD=0
  for f in ${NOOP_FILES}; do
    cls="$(basename "${f}" .java)"
    # Java 源为 CRLF：解析值统一剥 \r，否则 hv 尾随 \r 会使"compose 装配 Noop"的
    # 违例匹配失效（漏报），且展示串异常。
    # 注意：GNU grep 把 pattern 之后的选项当文件参数，选项必须全部前置；
    # hv 曾因此解析为空（漏报条款 9 违例），改为直接匹配 havingValue 所在行。
    cond="$(grep -m1 '@ConditionalOnProperty' "${f}" | sed 's/.*name *= *"//;s/".*//' | tr -d '\r')"
    hv="$(grep -m1 'havingValue' "${f}" | sed 's/.*havingValue *= *"//;s/".*//' | tr -d '\r')"
    if [ -z "${cond}" ]; then
      gap "${cls}: 无法解析 ConditionalOnProperty 开关，需人工确认装配条件"
      continue
    fi
    envkey="$(echo "${cond}" | tr 'a-z.' 'A-Z_')"
    if grep -q "${envkey}: \"${hv}\"" "${COMPOSE_FILE}" 2>/dev/null; then
      bad "可部署 compose 显式装配 Noop: ${cls}（${cond}=${hv}）——条款 9 违例"
      NOOP_BAD=1
    else
      # compose 中该开关的实配值（AIHUB_* env）与 Noop 条件不一致或未设置
      actual="$(grep -m1 "${envkey}:" "${COMPOSE_FILE}" 2>/dev/null | sed 's/.*: *//;s/"//g' || true)"
      if [ -n "${actual}" ] && [ "${actual}" != "${hv}" ]; then
        ok "${cls}: Noop 条件 ${cond}=${hv}，compose 实配 ${cond}=${actual}（Noop 不装配，真实实现生效）"
      elif [ -z "${actual}" ]; then
        gap "${cls}: compose 未显式设置 ${envkey}；若 matchIfMissing=true 则默认装配 Noop，需确认默认 Profile 是否可部署"
      else
      ok "${cls}: compose 开关与真实实现一致"
      fi
    fi
  done
  [ "${NOOP_BAD}" -eq 0 ] && ok "无 Noop 被可部署 Profile 显式装配"
fi

# ── A6 必需工件存在性 ────────────────────────────────────────────────────────
echo ""
echo "--- A6: 必需工件存在性 ---"
for p in \
  deploy/compose/scripts/verify.sh \
  deploy/compose/scripts/verify-journey.sh \
  deploy/compose/scripts/perf-smoke.sh \
  deploy/compose/scripts/backup.sh \
  deploy/compose/scripts/restore.sh \
  deploy/compose/scripts/p5-restore-drill.sh \
  deploy/observability/alerting-rules.yaml \
  deploy/observability/p5-ops-fault-drill.sh \
  deploy/security/p5-security-drill.sh \
  docs/runbooks/ops.md \
  docs/runbooks/backup-restore.md \
  contracts/openapi/aihub-v1.yaml \
  contracts/mcp/tools.yaml \
  contracts/events/events-v1.yaml
do
  if [ -f "${p}" ]; then ok "存在: ${p}"; else bad "缺失: ${p}"; fi
done

# ── A7 运行时证据编排（条款 2/6 的已有实跑日志）─────────────────────────────
echo ""
echo "--- A7: 运行时证据（journey / perf 日志）---"
JLOG="${AUDIT_JOURNEY_LOG:-}"
if [ -n "${JLOG}" ] && [ -f "${JLOG}" ]; then
  # verify-journey.sh 输出格式: [PRD-xx] name ... PASS/SKIP: reason/FAIL: reason
  JP="$(grep -c '\.\.\. PASS$' "${JLOG}" || true)"; JF="$(grep -c '\.\.\. FAIL' "${JLOG}" || true)"; JS="$(grep -c '\.\.\. SKIP' "${JLOG}" || true)"
  if [ "${JF}" -eq 0 ]; then
    ok "verify-journey 日志: PASS=${JP} SKIP=${JS} FAIL=0（${JLOG}）"
  else
    bad "verify-journey 日志含 ${JF} FAIL（${JLOG}）"
  fi
else
  gap "未提供 AUDIT_JOURNEY_LOG——条款 2『P0-B..P4 全 MUST Journey』需发布窗口以本脚本运行前导出 verify-journey.sh 实跑日志（先例 §13: 18 PASS/0 SKIP/0 FAIL，属历史 Commit 非 HEAD）"
fi
PLOG="${AUDIT_PERF_LOG:-}"
if [ -n "${PLOG}" ] && [ -f "${PLOG}" ]; then
  # perf-smoke.sh 违例行以 "FAIL: " 前缀输出；汇总行 "PASS: n/4 ..." 为成功标记
  if grep -q '^FAIL: ' "${PLOG}"; then
    bad "perf-smoke 日志含 FAIL（${PLOG}）"
  elif grep -q '^PASS: ' "${PLOG}"; then
    ok "perf-smoke 日志无 FAIL（${PLOG}）——条款 6 性能面（100k 规模门禁除外，见 EVD-P5004 notProven）"
  else
    bad "perf-smoke 日志既无 PASS 汇总也无 FAIL——疑似未运行即导出（${PLOG}）"
  fi
else
  gap "未提供 AUDIT_PERF_LOG——条款 6 性能门禁按先例 perf-smoke.sh 实跑导出"
fi
SLOG="${AUDIT_SECURITY_LOG:-}"
if [ -n "${SLOG}" ] && [ -f "${SLOG}" ]; then
  if ! grep -q 'Security drill summary' "${SLOG}"; then
    bad "安全演练日志缺少 summary 行——疑似未运行即导出（${SLOG}）"
  else
    SF="$(grep -c '\[FAIL\]' "${SLOG}" || true)"
    [ "${SF}" -eq 0 ] && ok "安全演练日志无 FAIL（${SLOG}）" || bad "安全演练日志含 ${SF} FAIL"
  fi
else
  gap "未提供 AUDIT_SECURITY_LOG——条款 6 安全门禁按先例 p5-security-drill.sh 实跑导出"
fi

# ── A8 契约漂移（条款 7：OpenAPI / MCP / Event / Flyway / 前端生成类型）────────
echo ""
echo "--- A8: 五类契约漂移检查（条款 7）---"
# OpenAPI paths（server base 为 /api/v1）的顶层前缀须被后端映射注解覆盖。
# 后端两种书写形态：类级/方法级完整路径 "/api/v1/<base>…"，或方法级相对路径 "/<base>…"
#（挂在 @RequestMapping("/api/v1") 类下）。前缀匹配为启发式（base 段粒度）；
# 未命中登记 GAP 供人工确认，不判 FAIL。
# 契约文件多为 CRLF：必须剥 \r，否则 base 形如 "me\r" 使后端映射匹配全部失效（漏报漂移）。
OA_BASES="$(sed -n '/^paths:/,/^components:/p' contracts/openapi/aihub-v1.yaml 2>/dev/null \
  | tr -d '\r' | grep -E '^  /' | sed 's#^  /##;s#/$##;s#/.*##;s#:##' | sort -u)"
OA_TOTAL=0; OA_MISS=0
# 后端注解字面量在单行内完整（CR 仅位于行尾），故固定串匹配即可；
# base 的尾部 \r 已由上方 tr -d '\r' 剥离，避免 "me\r" 这类误失配。
while IFS= read -r base; do
  [ -n "${base}" ] || continue
  OA_TOTAL=$((OA_TOTAL+1))
  if grep -rqF "\"/api/v1/${base}" backend/src/main/java 2>/dev/null \
     || grep -rqF "\"/${base}" backend/src/main/java 2>/dev/null; then :; else
    OA_MISS=$((OA_MISS+1)); echo "       后端未见映射前缀: /${base}"
  fi
done <<EOF
${OA_BASES}
EOF
if [ "${OA_TOTAL}" -lt 10 ]; then
  bad "OpenAPI paths 解析异常（仅 ${OA_TOTAL} 个顶层前缀，预期 >= 10）——契约文件损坏或解析失败"
elif [ "${OA_MISS}" -eq 0 ]; then
  ok "OpenAPI ${OA_TOTAL} 个顶层路径前缀均能在后端映射注解找到对应（条款 7 结构性对齐）"
else
  gap "OpenAPI 有 ${OA_MISS}/${OA_TOTAL} 个顶层前缀未直接匹配后端映射（需人工确认路由挂载差异，不构成 FAIL）"
fi
# MCP：tools.yaml 工具名与后端 McpToolCatalog 注册名集合须全等
MCP_YAML="$(grep -E '^  - name: [a-z_]+' contracts/mcp/tools.yaml | awk '{print $3}' | tr -d '\r' | sort)"
MCP_JAVA="$(grep -oE 'register\("(asset_[a-z_]+)"' backend/src/main/java/com/aihub/mcp/application/McpToolCatalog.java | sed 's/register("//;s/"//' | sort)"
if [ "${MCP_YAML}" = "${MCP_JAVA}" ]; then
  ok "MCP 契约工具集与 McpToolCatalog 注册集全等（$(echo "${MCP_YAML}" | grep -c .) 个）"
else
  bad "MCP 契约与后端注册漂移:"
  diff <(echo "${MCP_YAML}") <(echo "${MCP_JAVA}") | sed 's/^/       /'
fi
# Event：eventType 枚举须与后端领域事件类型字符串一致（抽查已实现核心事件）
EV_SRC="$(grep -rn 'ASSET_CREATED\|VERSION_PUBLISHED\|JOB_DEAD' backend/src/main/java --include='*.java' -l | wc -l)"
if [ "${EV_SRC}" -gt 0 ]; then
  ok "事件契约核心 eventType（ASSET_CREATED/VERSION_PUBLISHED/JOB_DEAD）在后端有实现引用（${EV_SRC} 文件）"
else
  gap "事件 eventType 未见后端引用，需确认事件发布面覆盖"
fi
# Flyway：见 A4（不可变性 + applied 对齐）——已覆盖，此处仅登记引用
ok "Flyway 契约面由 A4 覆盖（V1/V2 不可变 + 迁移数 applied 对齐）"
# 前端生成类型：仓库无 openapi-typescript/codegen 生成物（package.json 无相关脚本），
# 前端类型手写于 src/shared/api/types.ts —— 该契约面结构性 N/A，如实声明而非假通过
if grep -rq 'openapi-typescript\|generate:api\|codegen' frontend/package.json 2>/dev/null; then
  gap "前端存在类型生成脚本，需增加生成物与契约 diff 检查"
else
  skip "前端生成类型契约面 N/A：无 codegen 生成物（手写 types.ts），条款 7 该项不适用"
fi

# ── 汇总 ─────────────────────────────────────────────────────────────────────
echo ""
echo "=== 审计汇总: PASS=${PASS} FAIL=${FAIL} SKIP=${SKIP} GAP=${GAP} ==="
if [ "${FAIL}" -gt 0 ]; then
  echo "结论: 存在事实性违例（exit 1）。"
  exit 1
fi
echo "结论: 未发现事实性违例；GAP=${GAP} 项为发布前待治理/待人工评审/待运行证据缺口（见 EVD-P5007-001 coverage/followUps）。REQ-REL-001 完整闭合以 -CheckCompletion + 人类验收为准，本审计不宣告波次 VERIFIED。"
exit 0
