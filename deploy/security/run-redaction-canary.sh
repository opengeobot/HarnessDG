#!/usr/bin/env bash
# 功能: 脱敏金丝雀测试——验证 JWT/密码/预签名 URL 不出现在掩码与审计路径。
# 时间: 2026-07-10
# 作者: AxeXie
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT/backend"

echo "==> Running SensitiveDataMasker canary tests..."
./mvnw -q -Dtest=SensitiveDataMaskerTest,SecurityVerificationTest,AuditServiceTest#shouldMaskSensitiveFieldsInSummary test

echo "==> Redaction canary PASSED"
