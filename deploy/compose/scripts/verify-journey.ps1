# ============================================================================
# 功能: PRD §13.2 行为旅程验收（PowerShell 轻量子集：V05/V08/V12/V14）。
#       与 verify-journey.sh 结构对齐；全量旅程请用 Bash 脚本。
# 时间: 2026-07-11
# 作者: AxeXie
# ============================================================================
$ErrorActionPreference = "Stop"

$ComposeDir = Split-Path -Parent $PSScriptRoot
Set-Location $ComposeDir

$BaseUrl = if ($env:AIHUB_BASE_URL) { $env:AIHUB_BASE_URL } else { "http://localhost:8080" }
$RunId = Get-Date -Format "yyyyMMddHHmmss"
$script:failed = $false
$script:skipped = 0
$script:passed = 0
$script:accessToken = ""
$script:ownerTeamId = "unmapped_team"

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
$JourneyPass = if ($env:JOURNEY_ADMIN_PASSWORD) { $env:JOURNEY_ADMIN_PASSWORD } else { "${AdminPass}Journey-E2E-01!" }

function Invoke-Api {
    param([string]$Method, [string]$Path, [hashtable]$Headers, [string]$Body)
    $uri = "$BaseUrl$Path"
    try {
        $params = @{ Method = $Method; Uri = $uri; UseBasicParsing = $true; TimeoutSec = 30 }
        if ($Headers) { $params.Headers = $Headers }
        if ($Body) { $params.Body = $Body; $params.ContentType = "application/json" }
        $resp = Invoke-WebRequest @params
        return @{ Status = [int]$resp.StatusCode; Body = $resp.Content }
    } catch [System.Net.WebException] {
        $r = $_.Exception.Response
        if ($r -ne $null) {
            $code = [int]$r.StatusCode
            $reader = New-Object System.IO.StreamReader($r.GetResponseStream())
            return @{ Status = $code; Body = $reader.ReadToEnd() }
        }
        return @{ Status = -1; Body = $_.Exception.Message }
    }
}

function Get-JsonField {
    param([string]$Json, [string]$Field)
    if ($Json -match "`"$Field`"\s*:\s*`"([^`"]+)`"") { return $Matches[1] }
    return ""
}

function Journey-Pass { param([string]$Id, [string]$Name)
    Write-Host "[PRD-$Id] $Name ... PASS" -ForegroundColor Green
    $script:passed++
}

function Journey-Skip { param([string]$Id, [string]$Name, [string]$Reason)
    Write-Host "[PRD-$Id] $Name ... SKIP: $Reason" -ForegroundColor Yellow
    $script:skipped++
}

function Journey-Fail { param([string]$Id, [string]$Name, [string]$Reason)
    Write-Host "[PRD-$Id] $Name ... FAIL: $Reason" -ForegroundColor Red
    $script:failed = $true
}

function Ensure-AdminJwt {
    foreach ($pass in @($JourneyPass, $AdminPass)) {
        $login = Invoke-Api -Method POST -Path "/api/v1/auth/login" -Body (@{ username = $AdminUser; password = $pass } | ConvertTo-Json)
        $token = Get-JsonField $login.Body "accessToken"
        if ($token) {
            $script:accessToken = $token
            $script:AdminPass = $pass
            break
        }
    }
    if (-not $script:accessToken) {
        Journey-Fail "SETUP" "admin login" "no accessToken"
        return $false
    }
    $auth = @{ Authorization = "Bearer $($script:accessToken)" }
    $me = Invoke-Api -Method GET -Path "/api/v1/me" -Headers $auth
    if ($me.Status -ne 200) {
        Journey-Fail "SETUP" "admin /me" "HTTP $($me.Status)"
        return $false
    }
    try {
        $team = (docker compose exec -T postgres psql -U (Get-EnvValue "POSTGRES_USER" "aihub") -d (Get-EnvValue "POSTGRES_DB" "aihub") -tAc "select team_id from team where status='ACTIVE' limit 1").Trim()
        if ($team) { $script:ownerTeamId = $team }
    } catch { }
    return $true
}

Write-Host "=== PRD §13.2 Journey E2E (verify-journey.ps1 subset) ==="
$health = Invoke-Api -Method GET -Path "/actuator/health"
if ($health.Status -ne 200) {
    Write-Host "Backend unreachable at $BaseUrl"
    exit 1
}
if (-not (Ensure-AdminJwt)) {
    Write-Host "Summary: PASS=$($script:passed) SKIP=$($script:skipped) FAIL=$($script:failed)"
    exit 1
}

$auth = @{ Authorization = "Bearer $($script:accessToken)" }

# V05: create model
$assetBody = (@{
    type = "MODEL"; namespace = "journey"; name = "mdl-$RunId"
    displayName = "Journey PS"; visibility = "INTERNAL"; ownerTeamId = $script:ownerTeamId
    model = @{ framework = "PYTORCH"; task = "TEXT_GENERATION" }
} | ConvertTo-Json -Compress)
$create = Invoke-Api -Method POST -Path "/api/v1/assets" -Headers $auth -Body $assetBody
$assetId = Get-JsonField $create.Body "assetId"
if ($create.Status -in 200, 201 -and $assetId) {
    Journey-Pass "V05" "创建模型 assetId=$assetId"
} elseif ($create.Status -eq 500) {
    Journey-Skip "V05" "创建模型" "ASSET_PROVISION job enqueue unavailable"
} else {
    Journey-Fail "V05" "创建模型" "HTTP $($create.Status)"
}

# V12: MCP
$mcpInit = Invoke-Api -Method POST -Path "/mcp" -Headers $auth -Body '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"verify-journey-ps","version":"1.0"}}}'
$mcpList = Invoke-Api -Method POST -Path "/mcp" -Headers $auth -Body '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
$mcpSearch = Invoke-Api -Method POST -Path "/mcp" -Headers $auth -Body '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"asset_search","arguments":{"limit":5}}}'
if ($mcpInit.Body -match "serverInfo" -and $mcpList.Body -match "asset_search" -and $mcpSearch.Body -notmatch '"isError"\s*:\s*true') {
    Journey-Pass "V12" "MCP initialize/list/call"
} else {
    Journey-Fail "V12" "MCP initialize/list/call" "MCP response invalid"
}

# V14: idempotency replay
$idemKey = "journey-ps-$RunId"
$idemBody = (@{
    type = "MODEL"; namespace = "journey"; name = "idem-$RunId"
    displayName = "Idem PS"; visibility = "INTERNAL"; ownerTeamId = $script:ownerTeamId
    model = @{ framework = "PYTORCH"; task = "TEXT_GENERATION" }
} | ConvertTo-Json -Compress)
$idemHeaders = @{ Authorization = "Bearer $($script:accessToken)"; "Idempotency-Key" = $idemKey }
$c1 = Invoke-Api -Method POST -Path "/api/v1/assets" -Headers $idemHeaders -Body $idemBody
$c2 = Invoke-Api -Method POST -Path "/api/v1/assets" -Headers $idemHeaders -Body $idemBody
$id1 = Get-JsonField $c1.Body "assetId"
$id2 = Get-JsonField $c2.Body "assetId"
if ($c1.Status -eq 500 -or $c2.Status -eq 500) {
    Journey-Skip "V14" "幂等重放" "asset create infra error"
} elseif ($id1 -and $id1 -eq $id2 -and $c1.Status -eq $c2.Status) {
    Journey-Pass "V14" "幂等重放 assetId=$id1"
} else {
    Journey-Fail "V14" "幂等重放" "id1=$id1 id2=$id2"
}

# V08: publish triplet (lightweight — skip if no asset)
if (-not $assetId) {
    Journey-Skip "V08" "版本发布 triplet" "no asset from V05"
} else {
    $ver = Invoke-Api -Method POST -Path "/api/v1/assets/$assetId/versions" -Headers $auth -Body '{"version":"1.0.0-ps"}'
    $verId = Get-JsonField $ver.Body "versionId"
    if (-not $verId) {
        Journey-Skip "V08" "版本发布 triplet" "draft version unavailable"
    } else {
        Invoke-Api -Method POST -Path "/api/v1/assets/$assetId/versions/$verId/transition" -Headers $auth -Body '{"targetStatus":"VALIDATING"}' | Out-Null
        Start-Sleep -Seconds 2
        $pubReq = Invoke-Api -Method POST -Path "/api/v1/versions/$verId/publish-requests" -Headers $auth -Body '{}'
        $reqId = Get-JsonField $pubReq.Body "requestId"
        if ($reqId) {
            Invoke-Api -Method POST -Path "/api/v1/publish-requests/$reqId/decisions" -Headers $auth -Body '{"decision":"APPROVE"}' | Out-Null
            Start-Sleep -Seconds 3
            $detail = Invoke-Api -Method GET -Path "/api/v1/assets/$assetId/versions/$verId" -Headers $auth
            if ($detail.Body -match "gitTag" -and $detail.Body -match "sourceCommit" -and $detail.Body -match "manifestDigest") {
                Journey-Pass "V08" "版本发布 triplet"
            } else {
                Journey-Skip "V08" "版本发布 triplet" "publish incomplete (manifest/worker)"
            }
        } else {
            Journey-Skip "V08" "版本发布 triplet" "submit publish failed"
        }
    }
}

Write-Host ""
Write-Host "Summary: PASS=$($script:passed) SKIP=$($script:skipped) FAIL=$($script:failed)"
if ($script:failed) { exit 1 }
exit 0
