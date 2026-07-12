# ============================================================================
# 功能: AIHub Compose 验收脚本 (PowerShell)。执行 V01-V11 级检查（与 verify.sh V01-V11 等价）。
#       verify.sh 另含 V12-V28（P1-P5 行为级）；本脚本未同步 V12+，请优先使用 verify.sh 做完整验收。
# 时间: 2026-07-01
# 作者: AxeXie
# ============================================================================
$ErrorActionPreference = "Stop"

$ComposeDir = Split-Path -Parent $PSScriptRoot
Set-Location $ComposeDir

$script:failed = $false
$script:skipped = 0

# 网关基址（Nginx 反代 /api 与 /actuator/health）。
$BaseUrl = "http://localhost:8080"

# ---- 读取 Bootstrap 管理员凭据（优先 .env，其次 .env.example，最后内置默认）----
function Get-EnvValue {
    param([string]$Key, [string]$Default)
    foreach ($file in @(".env", ".env.example")) {
        if (Test-Path $file) {
            $line = Select-String -Path $file -Pattern "^\s*$Key\s*=" -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($line) { return ($line.Line -replace "^\s*$Key\s*=\s*", "").Trim() }
        }
    }
    return $Default
}
$AdminUser = Get-EnvValue "AIHUB_BOOTSTRAP_ADMIN_USERNAME" "admin"
$AdminPass = Get-EnvValue "AIHUB_BOOTSTRAP_ADMIN_PASSWORD" "change-me-admin-01"
$PgUser = Get-EnvValue "POSTGRES_USER" "aihub"
$PgDb = Get-EnvValue "POSTGRES_DB" "aihub"

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

function Skip-Step {
    param([string]$Id, [string]$Name, [string]$Reason)
    Write-Host "[$Id] $Name ... SKIP: $Reason" -ForegroundColor Yellow
    $script:skipped++
}

# HTTP 请求：返回 @{ Status = <int>; Body = <string> }；网络错误返回 Status = -1。
function Invoke-Api {
    param([string]$Method, [string]$Path, [hashtable]$Headers, [string]$Body)
    $uri = "$BaseUrl$Path"
    try {
        $params = @{ Method = $Method; Uri = $uri; UseBasicParsing = $true; TimeoutSec = 15 }
        if ($Headers) { $params.Headers = $Headers }
        if ($Body) { $params.Body = $Body; $params.ContentType = "application/json" }
        $resp = Invoke-WebRequest @params
        return @{ Status = [int]$resp.StatusCode; Body = $resp.Content }
    } catch [System.Net.WebException] {
        $r = $_.Exception.Response
        if ($r -ne $null) {
            $code = [int]$r.StatusCode
            $reader = New-Object System.IO.StreamReader($r.GetResponseStream())
            $content = $reader.ReadToEnd()
            return @{ Status = $code; Body = $content }
        }
        return @{ Status = -1; Body = $_.Exception.Message }
    } catch {
        # PowerShell 5.1 下 HttpResponseException 也可能落到此分支。
        $r = $_.Exception.Response
        if ($r -ne $null) {
            try { $code = [int]$r.StatusCode.value__ } catch { $code = [int]$r.StatusCode }
            return @{ Status = $code; Body = "" }
        }
        return @{ Status = -1; Body = $_.Exception.Message }
    }
}

# 探测后端是否可达（用于决定 V05+ 是 SKIP 还是执行）。
function Test-BackendReachable {
    $r = Invoke-Api -Method GET -Path "/actuator/health"
    return ($r.Status -eq 200)
}

# 探测 postgres 容器是否在运行（用于 V04/V08 的 psql 检查）。
function Test-PostgresReachable {
    try {
        $state = (docker compose ps --format "{{.State}}" postgres | Select-Object -First 1)
        return ("$state".Trim() -eq "running")
    } catch { return $false }
}

function Invoke-Psql {
    param([string]$Sql)
    # 直接调用容器内 psql，避免 sh -c 的嵌套引号在 PowerShell 下被破坏。
    $out = docker compose exec -T postgres psql -U $PgUser -d $PgDb -tAc $Sql
    return ("" + $out).Trim()
}

# 探测指定服务容器是否在运行。
function Test-ServiceRunning {
    param([string]$Service)
    try {
        $state = (docker compose ps --format "{{.State}}" $Service | Select-Object -First 1)
        return ("$state".Trim() -eq "running")
    } catch { return $false }
}

# ---------------------------------------------------------------------------
# V01: Compose 配置合法
# ---------------------------------------------------------------------------
Test-Step "V01" "Compose 配置合法" {
    docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw "docker compose config 返回非零" }
}

# ---------------------------------------------------------------------------
# V02: 核心服务健康（postgres / minio / gitea / backend）
# ---------------------------------------------------------------------------
if (-not (Test-ServiceRunning "postgres")) {
    Skip-Step "V02" "核心服务健康" "服务未启动（未执行 docker compose up）"
} else {
    Test-Step "V02" "核心服务健康" {
        $core = @("postgres", "minio", "gitea", "backend")
        foreach ($svc in $core) {
            $status = ("" + (docker compose ps --format "{{.State}}" $svc | Select-Object -First 1)).Trim()
            $health = ("" + (docker compose ps --format "{{.Health}}" $svc | Select-Object -First 1)).Trim()
            if (-not $status) { throw "$svc 未运行" }
            if ($status -ne "running") { throw "$svc 状态为 $status" }
            if ($health -and $health -ne "healthy") { throw "$svc 健康状态为 $health" }
        }
    }
}

# ---------------------------------------------------------------------------
# V03: 四个 Bucket 存在且非匿名
# ---------------------------------------------------------------------------
if (-not (Test-ServiceRunning "minio")) {
    Skip-Step "V03" "Bucket 初始化" "minio 未启动（未执行 docker compose up）"
} else {
    Test-Step "V03" "Bucket 初始化" {
        $buckets = @("gitea-storage", "dvc-cache", "asset-staging", "asset-preview")
        # 单引号 PS 字符串：$VAR 原样传入容器 shell 展开，避免 PowerShell 破坏引号。
        $mcCmd = 'mc alias set local http://localhost:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD >/dev/null 2>&1; mc ls local 2>/dev/null'
        $listing = "" + (docker compose exec -T minio sh -c $mcCmd)
        foreach ($b in $buckets) {
            if ($listing -notmatch $b) { throw "Bucket $b 不存在" }
        }
    }
}

# ---- V04/V08 依赖 postgres 可达（V08 审计脱敏仅需 postgres）----
$pgReachable = Test-PostgresReachable

# ---------------------------------------------------------------------------
# V04: 数据库迁移 V1-V12 全部应用，关键表存在
# ---------------------------------------------------------------------------
if (-not $pgReachable) {
    Skip-Step "V04" "数据库迁移完整性" "postgres 容器未运行（未执行 docker compose up）"
} else {
    Test-Step "V04" "数据库迁移完整性" {
        $count = (Invoke-Psql "select count(*) from flyway_schema_history where success = true").Trim()
        if ([int]$count -lt 12) { throw "flyway_schema_history 成功迁移数 $count < 12" }
        $tables = @("iam_principal", "iam_user", "iam_role", "system_dict_item",
                    "system_tag", "asset_tag", "system_config", "job_task",
                    "audit_log", "notification")
        foreach ($t in $tables) {
            $exists = (Invoke-Psql "select to_regclass('public.$t') is not null").Trim()
            if ($exists -ne "t") { throw "关键表 $t 缺失" }
        }
    }
}

# ---- V05+ 依赖后端可达 ----
$backendReachable = Test-BackendReachable
$script:accessToken = $null

# ---------------------------------------------------------------------------
# V05: JWT 生命周期（登录签发、/me、无 Token fail-closed）
# ---------------------------------------------------------------------------
if (-not $backendReachable) {
    Skip-Step "V05" "JWT 生命周期" "backend 健康端点不可达（未执行 docker compose up）"
} else {
    Test-Step "V05" "JWT 生命周期" {
        $loginBody = "{`"username`":`"$AdminUser`",`"password`":`"$AdminPass`"}"
        $login = Invoke-Api -Method POST -Path "/api/v1/auth/login" -Body $loginBody
        if ($login.Status -ne 200) { throw "登录失败，状态 $($login.Status)（bootstrap 管理员凭据是否正确/是否已改密）" }
        $json = $login.Body | ConvertFrom-Json
        $token = $json.data.accessToken
        if ([string]::IsNullOrEmpty($token)) { throw "登录响应缺少 accessToken" }
        $script:accessToken = $token
        # 令牌调用 /me 应 200（改密门放行）。
        $me = Invoke-Api -Method GET -Path "/api/v1/me" -Headers @{ Authorization = "Bearer $token" }
        if ($me.Status -ne 200) { throw "/me 携带 access token 返回 $($me.Status)，期望 200" }
        # 无 Token 访问受保护端点应 401（fail-closed）。
        $anon = Invoke-Api -Method GET -Path "/api/v1/system/users"
        if ($anon.Status -ne 401) { throw "无 Token 访问 /system/users 返回 $($anon.Status)，期望 401" }
    }
}

# 通用：断言无 Token=401 且携带管理员 Token 被拒（403，默认拒绝/最小权限/改密门）。
function Assert-DefaultDeny {
    param([string]$Path)
    $anon = Invoke-Api -Method GET -Path $Path
    if ($anon.Status -ne 401) { throw "无 Token 访问 $Path 返回 $($anon.Status)，期望 401" }
    if ($script:accessToken) {
        $auth = Invoke-Api -Method GET -Path $Path -Headers @{ Authorization = "Bearer $($script:accessToken)" }
        # admin 经 rol_admin 持 system:* 权限合法访问 system 端点（Wave A 后），期望 200
        if ($auth.Status -ne 200) { throw "admin Token 访问 $Path 返回 $($auth.Status)，期望 200（rol_admin 合法访问）" }
    }
}

# ---------------------------------------------------------------------------
# V06: 权限过滤（审计查询默认拒绝）
# ---------------------------------------------------------------------------
if (-not $backendReachable) {
    Skip-Step "V06" "权限过滤（审计）" "backend 不可达"
} else {
    Test-Step "V06" "权限过滤（审计/指标默认拒绝）" {
        Assert-DefaultDeny "/api/v1/system/audit-logs"
        Assert-DefaultDeny "/api/v1/system/metrics/summary"
    }
}

# ---------------------------------------------------------------------------
# V07: 字典/标签受控访问（默认拒绝）
# ---------------------------------------------------------------------------
if (-not $backendReachable) {
    Skip-Step "V07" "字典/标签受控访问" "backend 不可达"
} else {
    Test-Step "V07" "字典/标签受控访问（默认拒绝）" {
        Assert-DefaultDeny "/api/v1/system/dictionaries"
        Assert-DefaultDeny "/api/v1/system/tags"
    }
}

# ---------------------------------------------------------------------------
# V08: 审计脱敏 + identity 登录审计完整性
# identity 登录/令牌/改密事件已接入权威 AuditService（IdentityAuditAdapter）。
# 本用例校验：审计正文脱敏恒定不变式（无明文口令）；且 V05 登录成功后应产生登录审计事件。
# ---------------------------------------------------------------------------
if (-not $pgReachable) {
    Skip-Step "V08" "审计脱敏与登录审计" "postgres 不可达"
} else {
    Test-Step "V08" "审计脱敏与登录审计（无明文口令 + 登录事件）" {
        # audit_log 表存在且可查询。
        $exists = (Invoke-Psql "select to_regclass('public.audit_log') is not null").Trim()
        if ($exists -ne "t") { throw "audit_log 表缺失" }
        # 审计正文不得包含明文口令（脱敏恒定不变式，空表亦成立）。
        $leak = (Invoke-Psql "select count(*) from audit_log where request_summary::text like '%$AdminPass%'").Trim()
        if ([int]$leak -ne 0) { throw "audit_log 明文口令泄漏（脱敏失效）" }
        # V05 登录成功后应产生登录审计事件（审计追加写可能异步，最多重试若干次）。
        if ($backendReachable) {
            $found = $false
            for ($i = 0; $i -lt 10; $i++) {
                $cnt = (Invoke-Psql "select count(*) from audit_log where event_type = 'AUTH_LOGIN_SUCCEEDED'").Trim()
                if ([int]$cnt -ge 1) { $found = $true; break }
                Start-Sleep -Milliseconds 500
            }
            if (-not $found) { throw "未发现 AUTH_LOGIN_SUCCEEDED 审计事件（identity 审计接入未生效）" }
        }
    }
}

# ---------------------------------------------------------------------------
# V09: 持久化任务（默认拒绝）
# ---------------------------------------------------------------------------
if (-not $backendReachable) {
    Skip-Step "V09" "持久化任务访问" "backend 不可达"
} else {
    Test-Step "V09" "持久化任务访问（默认拒绝）" {
        Assert-DefaultDeny "/api/v1/system/jobs"
    }
}

# ---------------------------------------------------------------------------
# V10: 通知（默认拒绝）
# ---------------------------------------------------------------------------
if (-not $backendReachable) {
    Skip-Step "V10" "通知访问" "backend 不可达"
} else {
    Test-Step "V10" "通知访问（默认拒绝）" {
        Assert-DefaultDeny "/api/v1/system/notifications"
    }
}

# ---------------------------------------------------------------------------
# V11: 观测（健康公开 200；诊断需 system:observe，默认拒绝）
# ---------------------------------------------------------------------------
if (-not $backendReachable) {
    Skip-Step "V11" "观测与诊断" "backend 不可达"
} else {
    Test-Step "V11" "观测与诊断" {
        $health = Invoke-Api -Method GET -Path "/actuator/health"
        if ($health.Status -ne 200) { throw "/actuator/health 返回 $($health.Status)，期望 200" }
        Assert-DefaultDeny "/api/v1/system/dependencies"
    }
}

Write-Host ""
if ($script:skipped -gt 0) {
    Write-Host "注意：$($script:skipped) 项因服务未启动被 SKIP（非通过）。完整验收需先 docker compose up -d 再运行本脚本。" -ForegroundColor Yellow
}

if ($script:failed) {
    Write-Host "验收存在失败项。" -ForegroundColor Red
    exit 1
}
Write-Host "验收未发现失败项（PASS 项通过，SKIP 项需在服务就绪后补验）。" -ForegroundColor Green
exit 0
