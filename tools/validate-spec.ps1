#!/usr/bin/env pwsh
# ============================================================================
# 功能: AI Spec 门禁脚本——校验设计文档与 OpenAPI 契约文件完整性。
#       CI 中作为强制门禁步骤运行，本地可由开发者手动执行。
# 时间: 2026-07-05
# 作者: AxeXie
# ============================================================================
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path "$PSScriptRoot/..").Path
$exitCode = 0

function Write-Ok($msg)   { Write-Host "[OK]   $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red; $script:exitCode = 1 }
function Write-Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }

# ---- 1. 需求文档完整性 ----
Write-Host "`n== Requirement Documents =="
$reqDir = Join-Path $root 'docs/ai-spec/01-requirements'
$requiredDocs = @(
    'p0b-platform-services.md',
    'p2-version-transfer.md',
    'p4-agent-integration.md',
    'p5-quality-operations.md',
    'dataset-experience.md',
    'personas-and-scope.md'
)
foreach ($doc in $requiredDocs) {
    $path = Join-Path $reqDir $doc
    if (Test-Path $path) {
        $size = (Get-Item $path).Length
        if ($size -gt 100) { Write-Ok "$doc ($size bytes)" }
        else { Write-Fail "$doc is too small ($size bytes)" }
    } else {
        Write-Fail "$doc not found"
    }
}

# ---- 2. 治理文档 ----
Write-Host "`n== Governance Documents =="
$govFiles = @(
    'docs/ai-spec/00-governance/decision-log.md',
    'docs/ai-spec/00-governance/open-questions.md',
    'docs/ai-spec/02-delivery/traceability-matrix.md'
)
foreach ($f in $govFiles) {
    $path = Join-Path $root $f
    if (Test-Path $path) { Write-Ok $f } else { Write-Fail "$f not found" }
}

# ---- 3. OpenAPI 契约文件 ----
Write-Host "`n== OpenAPI Contract =="
$contractPath = Join-Path $root 'contracts/openapi/aihub-v1.yaml'
if (Test-Path $contractPath) {
    $size = (Get-Item $contractPath).Length
    if ($size -gt 1000) { Write-Ok "aihub-v1.yaml ($size bytes)" }
    else { Write-Fail "aihub-v1.yaml is too small ($size bytes)" }
} else {
    Write-Fail "contracts/openapi/aihub-v1.yaml not found"
}

# ---- 4. 状态机文档 ----
Write-Host "`n== Domain Documents =="
$domainFiles = @(
    'docs/ai-spec/02-domain/state-machines.md'
)
foreach ($f in $domainFiles) {
    $path = Join-Path $root $f
    if (Test-Path $path) { Write-Ok $f } else { Write-Fail "$f not found" }
}

# ---- 汇总 ----
Write-Host ""
if ($exitCode -eq 0) {
    Write-Host "AI Spec Gate: PASSED" -ForegroundColor Green
} else {
    Write-Host "AI Spec Gate: FAILED" -ForegroundColor Red
}
exit $exitCode
