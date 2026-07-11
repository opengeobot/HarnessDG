#!/usr/bin/env bash
# ============================================================================
# 功能: MCP tools.yaml 结构校验——YAML 可解析、必填字段、工具名唯一、
#       write/enabled 布尔类型。供 CI mcp-contract 作业与本地门禁使用。
# 时间: 2026-07-11
# 作者: AxeXie
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_YAML="${SCRIPT_DIR}/tools.yaml"

if [ ! -f "${TOOLS_YAML}" ]; then
  echo "FAIL: ${TOOLS_YAML} 不存在"
  exit 1
fi

# YAML 语法
python3 - <<'PY' "${TOOLS_YAML}"
import sys
import yaml
path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    doc = yaml.safe_load(f)
if not isinstance(doc, dict):
    raise SystemExit("FAIL: 根节点必须为 mapping")
for key in ("schemaVersion", "contractStatus", "tools"):
    if key not in doc:
        raise SystemExit(f"FAIL: 缺少顶层字段 {key}")
tools = doc["tools"]
if not isinstance(tools, list) or not tools:
    raise SystemExit("FAIL: tools 必须为非空列表")
names = []
for i, tool in enumerate(tools):
    if not isinstance(tool, dict):
        raise SystemExit(f"FAIL: tools[{i}] 必须为 mapping")
    for req in ("name", "enabled", "write", "summary", "requiredScopes", "requiredPermissions"):
        if req not in tool:
            raise SystemExit(f"FAIL: tools[{i}] 缺少字段 {req}")
    if not isinstance(tool["name"], str) or not tool["name"]:
        raise SystemExit(f"FAIL: tools[{i}].name 无效")
    if not isinstance(tool["enabled"], bool) or not isinstance(tool["write"], bool):
        raise SystemExit(f"FAIL: tools[{i}] enabled/write 必须为布尔")
    names.append(tool["name"])
if len(names) != len(set(names)):
    raise SystemExit("FAIL: 工具名重复")
print(f"OK: tools.yaml 结构有效，共 {len(names)} 个工具")
PY

echo "OK: YAML 结构校验通过"
