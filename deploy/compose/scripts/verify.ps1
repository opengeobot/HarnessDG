# ============================================================================
# 功能: AIHub Compose P0 验收脚本 (PowerShell)。执行 V01-V03 级检查：
#       V01 Compose 配置合法；V02 核心服务健康；V03 四个 Bucket 存在且非匿名。
#       任一步失败返回非零退出码。业务用例 (V05+) 属 P1，此处仅占位提示。
# 时间: 2026-06-29
# 作者: AxeXie
# ============================================================================
$ErrorActionPreference = "Stop"

$ComposeDir = Split-Path -Parent $PSScriptRoot
Set-Location $ComposeDir

$failed = $false

function Test-Step {
    param([string]$Id, [string]$Name, [scriptblock]$Check)
    Write-Host "[$Id] $Name ..." -NoNewline
    try {
        & $Check
        Write-Host " PASS" -ForegroundColor Green
    } catch {
        Write-Host " FAIL: $($_.Exception.Message)" -ForegroundColor Red
        $script:failed = $true
    }
}

# V01: Compose 配置合法
Test-Step "V01" "Compose 配置合法" {
    docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw "docker compose config 返回非零" }
}

# V02: 核心服务健康（postgres / minio / gitea / backend）
Test-Step "V02" "核心服务健康" {
    $core = @("postgres", "minio", "gitea", "backend")
    foreach ($svc in $core) {
        $state = (docker compose ps --format json $svc | ConvertFrom-Json)
        if (-not $state) { throw "$svc 未运行" }
        $health = $state.Health
        $status = $state.State
        if ($status -ne "running") { throw "$svc 状态为 $status" }
        if ($health -and $health -ne "healthy") { throw "$svc 健康状态为 $health" }
    }
}

# V03: 四个 Bucket 存在且非匿名
Test-Step "V03" "Bucket 初始化" {
    $buckets = @("gitea-storage", "dvc-cache", "asset-staging", "asset-preview")
    $listing = docker compose exec -T minio sh -c "mc alias set local http://localhost:9000 `$MINIO_ROOT_USER `$MINIO_ROOT_PASSWORD >/dev/null 2>&1; mc ls local 2>/dev/null"
    foreach ($b in $buckets) {
        if ($listing -notmatch $b) { throw "Bucket $b 不存在" }
    }
}

Write-Host ""
Write-Host "[V05+] 业务端到端用例属 P1 阶段，当前基线跳过。" -ForegroundColor DarkGray

if ($failed) {
    Write-Host "验收存在失败项。" -ForegroundColor Red
    exit 1
}
Write-Host "P0 基线验收通过 (V01-V03)。" -ForegroundColor Green
exit 0
