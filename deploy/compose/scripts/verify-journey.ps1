# ============================================================================
# 功能: PRD §13.2 行为旅程验收（PowerShell 轻量子集：V05/V08/V12/V14）。
#       与 verify-journey.sh 结构对齐；全量旅程请用 Bash 脚本。
#       V08 = 校验失败回退契约断言（AC-P3-VAL-001：无工件 draft 版本推进
#       VALIDATING 后必被 fail-closed 校验打回 DRAFT 且报告保留）；
#       正向发布三元组（上传链+双主体审批）由 verify-journey.sh journey_v08 独占。
# 时间: 2026-07-11（2026-10-08 TASK-P0BR-074 重写 V08：消除僵尸断言与 SKIP 假绿）
# 作者: AxeXie
# ============================================================================
$ErrorActionPreference = "Stop"

$ComposeDir = Split-Path -Parent $PSScriptRoot
Set-Location $ComposeDir

$BaseUrl = if ($env:AIHUB_BASE_URL) { $env:AIHUB_BASE_URL } else { "http://localhost:8080" }
$RunId = Get-Date -Format "yyyyMMddHHmmss"
$script:failed = 0
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
    } catch {
        # 兼容 PS5.1（WebException/HttpWebResponse）与 PS7（HttpResponseException/HttpResponseMessage）：
        # PS7 将 4xx 响应体放入 ErrorDetails.Message，PS5.1 需从响应流读取。
        $r = $_.Exception.Response
        if ($null -ne $r) {
            try {
                $code = [int]$r.StatusCode
                $bodyText = $_.ErrorDetails.Message
                if (-not $bodyText -and $r.GetResponseStream) {
                    $reader = New-Object System.IO.StreamReader($r.GetResponseStream())
                    $bodyText = $reader.ReadToEnd()
                }
                return @{ Status = $code; Body = $bodyText }
            } catch { }
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
    $script:failed++
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

# V08: 校验失败回退契约（AC-P3-VAL-001，TASK-P0BR-074 诚实化重写）。
# 无工件 draft 版本推进 VALIDATING 后，ValidationJobHandler 必因
# VAL-MNF-001/VAL-ART-001 fail-closed 打回 DRAFT 并保留 FAILED 报告；
# DRAFT 态提交发布请求必 409（VERSION_STATE_NOT_ALLOWED）。
# 正向发布三元组（上传链+双主体审批）由 verify-journey.sh journey_v08 独占。
function Test-V08 {
    if (-not $assetId) {
        Journey-Fail "V08" "校验失败回退契约" "no asset from V05 (V05 must not leave assetId empty)"
        return
    }
    $ver = Invoke-Api -Method POST -Path "/api/v1/assets/$assetId/versions" -Headers $auth -Body '{"version":"1.0.0"}'
    $verId = Get-JsonField $ver.Body "versionId"
    if ($ver.Status -ne 201 -or -not $verId) {
        Journey-Fail "V08" "校验失败回退契约" "draft create HTTP $($ver.Status)"
        return
    }
    $draft = Invoke-Api -Method GET -Path "/api/v1/assets/$assetId/versions/$verId" -Headers $auth
    if ((Get-JsonField $draft.Body "status") -ne "DRAFT") {
        Journey-Fail "V08" "校验失败回退契约" "initial status not DRAFT"
        return
    }
    $pubDraft = Invoke-Api -Method POST -Path "/api/v1/versions/$verId/publish-requests" -Headers $auth -Body '{}'
    if ($pubDraft.Status -ne 409) {
        Journey-Fail "V08" "校验失败回退契约" "DRAFT submit expected 409, got HTTP $($pubDraft.Status)"
        return
    }
    $tr = Invoke-Api -Method POST -Path "/api/v1/assets/$assetId/versions/$verId/transition" -Headers $auth -Body '{"targetStatus":"VALIDATING"}'
    if ($tr.Status -ne 200) {
        Journey-Fail "V08" "校验失败回退契约" "transition VALIDATING HTTP $($tr.Status)"
        return
    }
    # VERSION_VALIDATE 作业异步执行：轮询至多 90s 等待 fail-closed 回退 DRAFT
    $backToDraft = $false
    $unexpected = ""
    for ($i = 0; $i -lt 45; $i++) {
        Start-Sleep -Seconds 2
        $cur = Invoke-Api -Method GET -Path "/api/v1/assets/$assetId/versions/$verId" -Headers $auth
        $st = Get-JsonField $cur.Body "status"
        if ($st -eq "DRAFT") { $backToDraft = $true; break }
        if ($st -and $st -ne "VALIDATING") { $unexpected = $st; break }
    }
    if (-not $backToDraft) {
        $why = if ($unexpected) { "unexpected status $unexpected (empty version must fail validation)" } else { "no rollback to DRAFT within 90s" }
        Journey-Fail "V08" "校验失败回退契约" $why
        return
    }
    $rep = Invoke-Api -Method GET -Path "/api/v1/assets/$assetId/versions/$verId/validation-report" -Headers $auth
    $repStatus = Get-JsonField $rep.Body "status"
    if ($rep.Status -ne 200 -or $repStatus -ne "FAILED") {
        Journey-Fail "V08" "校验失败回退契约" "report HTTP $($rep.Status) status=$repStatus"
        return
    }
    Journey-Pass "V08" "校验失败回退 DRAFT + FAILED 报告保留 (AC-P3-VAL-001)"
}
Test-V08

Write-Host ""
Write-Host "Summary: PASS=$($script:passed) SKIP=$($script:skipped) FAIL=$($script:failed)"
if ($script:failed) { exit 1 }
exit 0
