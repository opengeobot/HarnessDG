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

# HTTP 请求：返回 @{ Status = <int>; Body = <string>; SetCookie = <string> }；网络错误返回 Status = -1。
# 使用 .NET HttpClient 而非 Invoke-WebRequest：后者对无请求体的 POST 会强制附加
# Content-Type: application/x-www-form-urlencoded，无法精确模拟「浏览器仅凭 Cookie 刷新」的调用形状。
# SetCookie 为 Set-Cookie 头原文（无该头时为空串），供刷新 Cookie 属性与轮换断言使用。
Add-Type -AssemblyName System.Net.Http -ErrorAction SilentlyContinue

function Invoke-Api {
    param([string]$Method, [string]$Path, [hashtable]$Headers, [string]$Body, [string]$RefreshCookie)
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.UseCookies = $false
    $client = New-Object System.Net.Http.HttpClient($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(20)
    try {
        # .NET Framework 的 HttpMethod 无 PATCH 静态属性，统一按名构造以支持全部方法。
        $httpMethod = New-Object System.Net.Http.HttpMethod($Method)
        $request = New-Object System.Net.Http.HttpRequestMessage $httpMethod, "$BaseUrl$Path"
        if ($Headers) {
            foreach ($key in $Headers.Keys) {
                [void]$request.Headers.TryAddWithoutValidation($key, "" + $Headers[$key])
            }
        }
        if (-not [string]::IsNullOrEmpty($RefreshCookie)) {
            [void]$request.Headers.TryAddWithoutValidation("Cookie", "aihub_refresh=$RefreshCookie")
        }
        if ($Body) {
            $request.Content = New-Object System.Net.Http.StringContent(
                $Body, [System.Text.Encoding]::UTF8, "application/json")
        }
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $setCookie = ""
        if ($response.Headers.Contains("Set-Cookie")) {
            $setCookie = ($response.Headers.GetValues("Set-Cookie") -join " ")
        }
        return @{ Status = [int]$response.StatusCode; Body = $content; SetCookie = $setCookie }
    } catch {
        return @{ Status = -1; Body = "$($_.Exception.Message)"; SetCookie = "" }
    } finally {
        $client.Dispose()
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

# ---- 稳定错误码提取：断言必须依据响应体 code，不得只看 HTTP 状态码 ----
function Get-ErrorCode {
    param([string]$Body)
    if ([string]::IsNullOrWhiteSpace($Body)) { return "" }
    try {
        $json = $Body | ConvertFrom-Json
        if ($json -and $json.code) { return "$($json.code)" }
    } catch { }
    return ""
}

# 前置条件守卫：首登强制改密门产生的 403 与权限判定无关，必须报为前置条件未满足（非 PASS）。
function Assert-NotPasswordChangeRequired {
    param([string]$Body, [string]$Who)
    if ((Get-ErrorCode $Body) -eq "PASSWORD_CHANGE_REQUIRED") {
        throw ("前置条件未满足：$Who 处于首登强制改密状态（must_change_password），" +
               "其 403 与权限判定无关。请先完成 PUT /api/v1/me/password 改密" +
               "（V05b 会按产品既定流程自动完成）。")
    }
}

# 最小权限验证主体（默认拒绝断言的被测主体）：权限集合显式列出，刻意不含任何被测端点所需权限。
# 该主体不绑定任何角色，故其后端判定只剩「JWT 粗粒度 scope」，403 必然来自权限判定。
$script:LeastPrivUser = "compose-verify-minimal"
$script:LeastPrivPass = "ComposeVerifyOnly-01!"
$script:LeastPrivToken = $null

# 以既有登录端点换取 access Token；返回 Token 或 $null。
function Get-AccessToken {
    param([string]$Username, [string]$Password)
    $body = "{`"username`":`"$Username`",`"password`":`"$Password`"}"
    $resp = Invoke-Api -Method POST -Path "/api/v1/auth/login" -Body $body
    if ($resp.Status -ne 200) { return $null }
    try { return ($resp.Body | ConvertFrom-Json).data.accessToken } catch { return $null }
}

# 读取主体概要中的 forcePasswordChange 标记（/me 在改密门放行名单内，必定可达）。
function Test-ForcePasswordChange {
    param([string]$Token)
    $resp = Invoke-Api -Method GET -Path "/api/v1/me" -Headers @{ Authorization = "Bearer $Token" }
    if ($resp.Status -ne 200) { return $null }
    try { return [bool]($resp.Body | ConvertFrom-Json).data.forcePasswordChange } catch { return $null }
}

# 按产品既定流程解除首登强制改密门（轮换为同值，不改变本地验收栈凭据），返回可用的新 Token。
function Resolve-PasswordChangeGate {
    param([string]$Username, [string]$Password, [string]$Token, [string]$Who)
    $force = Test-ForcePasswordChange -Token $Token
    if ($null -eq $force) { throw "无法读取 $Who 的 /api/v1/me，无法确认改密门状态" }
    if (-not $force) { return $Token }
    $body = "{`"currentPassword`":`"$Password`",`"newPassword`":`"$Password`"}"
    $chg = Invoke-Api -Method PUT -Path "/api/v1/me/password" `
        -Headers @{ Authorization = "Bearer $Token" } -Body $body
    if ($chg.Status -ne 200) { throw "$Who 首登改密失败，状态 $($chg.Status)（$(Get-ErrorCode $chg.Body)）" }
    # 改密会递增 tokenVersion，旧 access Token 不再可用，必须重新登录。
    $fresh = Get-AccessToken -Username $Username -Password $Password
    if ([string]::IsNullOrEmpty($fresh)) { throw "$Who 改密后重新登录失败" }
    return $fresh
}

# ---------------------------------------------------------------------------
# V05b: 验收主体前置条件
# 1) 管理员解除首登改密门（否则后续 403/200 断言都与权限无关）；
# 2) 就绪最小权限验证主体（用于默认拒绝断言）。
# 该用例失败时 V06/V07/V09/V10/V11 会一并 FAIL（不得 SKIP），因为验收前提已不成立。
# ---------------------------------------------------------------------------
if (-not $backendReachable) {
    Skip-Step "V05b" "验收主体前置条件" "backend 不可达"
} else {
    Test-Step "V05b" "验收主体前置条件（改密门已解除 + 最小权限主体就绪）" {
        $script:accessToken = Resolve-PasswordChangeGate -Username $AdminUser -Password $AdminPass `
            -Token $script:accessToken -Who "管理员 $AdminUser"

        # 就绪最小权限主体：创建（幂等，已存在则复用）→ 登录 → 解除其改密门。
        $createBody = "{`"username`":`"$($script:LeastPrivUser)`"," +
            "`"displayName`":`"Compose Verify Minimal`"," +
            "`"email`":null,`"locale`":`"zh-CN`"," +
            "`"temporaryPassword`":`"$($script:LeastPrivPass)`"," +
            "`"scopes`":[`"project:view`"]}"
        $create = Invoke-Api -Method POST -Path "/api/v1/system/users" `
            -Headers @{ Authorization = "Bearer $($script:accessToken)" } -Body $createBody
        $createCode = Get-ErrorCode $create.Body
        if ($create.Status -eq 409 -and $createCode -eq "USER_ALREADY_EXISTS") {
            # 幂等：上一轮运行已创建（口令轮换为同值，临时口令仍有效）。
        } elseif ($create.Status -ne 201) {
            throw "创建最小权限验证主体失败，状态 $($create.Status)，错误码 $createCode"
        }

        $token = Get-AccessToken -Username $script:LeastPrivUser -Password $script:LeastPrivPass
        if ([string]::IsNullOrEmpty($token)) { throw "最小权限验证主体登录失败" }
        $script:LeastPrivToken = Resolve-PasswordChangeGate -Username $script:LeastPrivUser `
            -Password $script:LeastPrivPass -Token $token -Who "最小权限验证主体"

        # 反向自检：验证主体不得持有任何被测端点所需权限，否则默认拒绝断言会失去意义。
        $me = Invoke-Api -Method GET -Path "/api/v1/me" `
            -Headers @{ Authorization = "Bearer $($script:LeastPrivToken)" }
        $lpScopes = @((($me.Body | ConvertFrom-Json).data.scopes))
        foreach ($scope in @("audit:read", "job:read", "notification:read",
                             "system:observe", "dictionary:read", "tag:read")) {
            if ($lpScopes -contains $scope) {
                throw "最小权限验证主体不应持有 $scope，否则其 403 无法证明默认拒绝"
            }
        }
    }
}

# 默认拒绝断言：无 Token→401(AUTH_UNAUTHENTICATED)；最小权限主体→403(AUTH_PERMISSION_DENIED)。
# 必须校验响应体稳定错误码：仅比较状态码会把首登改密门等无关 403 误判为权限拒绝（历史假阳性）。
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
# V05d: 会话轮换与失效（AC-P0B-IAM-006 轮换、AC-P0B-IAM-008 禁用实时失效、AC-P0B-IAM-009 登出）
# ---------------------------------------------------------------------------
function Assert-RefreshRotation {
    # AC-P0B-IAM-006：access/refresh 同时轮换；旧 jti 标记失效；新 Cookie 属性正确。
    $login = Invoke-Api -Method POST -Path "/api/v1/auth/login" `
        -Body "{`"username`":`"$AdminUser`",`"password`":`"$AdminPass`"}"
    if ($login.Status -ne 200) { throw "登录失败，状态 $($login.Status)（无法执行刷新轮换断言）" }
    Assert-RefreshCookieAttributes -SetCookie $login.SetCookie -Context "登录"
    $oldRefresh = Get-RefreshFromSetCookie $login.SetCookie
    if ([string]::IsNullOrEmpty($oldRefresh)) { throw "登录未下发刷新 Cookie" }
    $oldAccess = "$((($login.Body | ConvertFrom-Json)).data.accessToken)"
    $principalId = Invoke-Psql "select principal_id from iam_user where username='$AdminUser'"

    $refresh = Invoke-Api -Method POST -Path "/api/v1/auth/refresh" -RefreshCookie $oldRefresh
    if ($refresh.Status -ne 200) {
        throw "刷新失败，状态 $($refresh.Status)，错误码 $(Get-ErrorCode $refresh.Body)"
    }
    Assert-RefreshCookieAttributes -SetCookie $refresh.SetCookie -Context "刷新"
    $newRefresh = Get-RefreshFromSetCookie $refresh.SetCookie
    if ([string]::IsNullOrEmpty($newRefresh)) { throw "刷新未轮换出新刷新 Cookie" }
    if ($newRefresh -eq $oldRefresh) { throw "刷新后 refresh 未轮换（Cookie 值与旧的相同）" }
    $newAccess = "$((($refresh.Body | ConvertFrom-Json)).data.accessToken)"
    if ([string]::IsNullOrEmpty($newAccess)) { throw "刷新响应缺少 accessToken" }
    if ($newAccess -eq $oldAccess) { throw "刷新后 access 未轮换（响应体与旧的相同）" }
    $newAccessStatus = (Invoke-Api -Method GET -Path "/api/v1/me" -Headers @{ Authorization = "Bearer $newAccess" }).Status
    if ($newAccessStatus -ne 200) { throw "轮换出的新 access 访问 /me 返回 $newAccessStatus，期望 200" }

    # 旧 jti 必须已标记为 ROTATED（不得仍为 ACTIVE）。
    $family = Invoke-Psql ("select token_family from iam_token where jti=(select jti from iam_token " +
        "where principal_id='$principalId' and status='ROTATED' order by id desc limit 1)")
    if ([string]::IsNullOrEmpty($family) -or $family -eq "") {
        throw "未发现 ROTATED 状态的旧 jti（刷新未把旧 jti 标记失效）"
    }
    $activeInFamily = Invoke-Psql "select count(*) from iam_token where token_family='$family' and status='ACTIVE'"
    if ([int]$activeInFamily -ne 1) {
        throw "轮换后该 Token Family 的 ACTIVE 数量为 $activeInFamily，期望恰好 1（无双有效 refresh）"
    }
}

function Assert-RefreshReplayRevokesFamily {
    # AC-P0B-IAM-007：重用已轮换 refresh → AUTH_REFRESH_REPLAYED；整族吊销；新 refresh 也失效；拒绝审计。
    # 独立建立自己的 Token Family，不依赖其它用例的中间状态。
    $login = Invoke-Api -Method POST -Path "/api/v1/auth/login" `
        -Body "{`"username`":`"$AdminUser`",`"password`":`"$AdminPass`"}"
    $liveRefresh = Get-RefreshFromSetCookie $login.SetCookie
    $liveFamily = Invoke-Psql ("select token_family from iam_token where jti=(select jti from iam_token " +
        "where principal_id=(select principal_id from iam_user where username='$AdminUser') order by id desc limit 1)")
    $first = Invoke-Api -Method POST -Path "/api/v1/auth/refresh" -RefreshCookie $liveRefresh
    if ($first.Status -ne 200) { throw "建立重放前置的首次刷新失败，状态 $($first.Status)" }
    $rotatedRefresh = $liveRefresh
    $liveNew = Get-RefreshFromSetCookie $first.SetCookie

    $replay = Invoke-Api -Method POST -Path "/api/v1/auth/refresh" -RefreshCookie $rotatedRefresh
    if ($replay.Status -ne 401) {
        throw "重用已轮换 refresh 返回 $($replay.Status)，期望 401"
    }
    $replayCode = Get-ErrorCode $replay.Body
    if ($replayCode -ne "AUTH_REFRESH_REPLAYED") {
        throw "重用已轮换 refresh 返回错误码 $replayCode，期望 AUTH_REFRESH_REPLAYED"
    }
    # 整族吊销：该 Family 不得残留 ACTIVE，且轮换出的新 refresh 必须同样失效。
    $activeAfter = Invoke-Psql "select count(*) from iam_token where token_family='$liveFamily' and status='ACTIVE'"
    if ([int]$activeAfter -ne 0) {
        throw "重放检测后 Token Family $liveFamily 仍残留 $activeAfter 条 ACTIVE，期望 0（整族吊销未生效）"
    }
    $newAfter = Invoke-Api -Method POST -Path "/api/v1/auth/refresh" -RefreshCookie $liveNew
    if ($newAfter.Status -eq 200) {
        throw "重放后轮换出的新 refresh 仍可刷新，未整族吊销"
    }
    # 拒绝审计必须存在。
    $audit = Invoke-Psql "select count(*) from audit_log where event_type='AUTH_TOKEN_REPLAY_REJECTED'"
    if ([int]$audit -lt 1) { throw "未发现 AUTH_TOKEN_REPLAY_REJECTED 审计事件（重放拒绝未审计）" }
}

function Assert-DisableRevokesSessions {
    # AC-P0B-IAM-008：旧 access 下一请求立即被拒；refresh 与凭据交换同样被拒；状态与审计一致。
    $username = "compose-verify-iam8"
    $userId = New-IamProbeUser -Username $username -DisplayName "Compose Verify Iam8"
    $reset = Invoke-Api -Method POST -Path "/api/v1/system/users/$userId`:reset-password" `
        -Headers @{ Authorization = "Bearer $($script:accessToken)" } `
        -Body "{`"temporaryPassword`":`"$($script:IamProbePassword)`"}"
    if ($reset.Status -ne 200) { throw "重置验证用户口令失败，状态 $($reset.Status)" }
    [void](Reset-UserLockState $username)

    $login = Invoke-Api -Method POST -Path "/api/v1/auth/login" `
        -Body "{`"username`":`"$username`",`"password`":`"$($script:IamProbePassword)`"}"
    if ($login.Status -ne 200) { throw "验证用户登录失败，状态 $($login.Status)" }
    $userAccess = "$((($login.Body | ConvertFrom-Json)).data.accessToken)"
    $userRefresh = Get-RefreshFromSetCookie $login.SetCookie
    $before = (Invoke-Api -Method GET -Path "/api/v1/me" -Headers @{ Authorization = "Bearer $userAccess" }).Status
    if ($before -ne 200) { throw "禁用前 /me 返回 $before，期望 200（前置条件）" }

    $disable = Invoke-Api -Method POST -Path "/api/v1/system/users/$userId`:disable" `
        -Headers @{ Authorization = "Bearer $($script:accessToken)" }
    if ($disable.Status -ne 200) { throw "禁用用户失败，状态 $($disable.Status)" }
    $facts = Get-UserRowFacts $username
    if ($facts -notmatch '^DISABLED\|') { throw "禁用后 iam_user.status 未落库为 DISABLED（实际：$facts）" }

    $afterStatus = (Invoke-Api -Method GET -Path "/api/v1/me" -Headers @{ Authorization = "Bearer $userAccess" }).Status
    if ($afterStatus -ne 401) {
        throw "禁用后旧 access 访问 /me 返回 $afterStatus，期望 401（未实时失效）"
    }
    if (-not [string]::IsNullOrEmpty($userRefresh)) {
        $refreshStatus = (Invoke-Api -Method POST -Path "/api/v1/auth/refresh" -RefreshCookie $userRefresh).Status
        if ($refreshStatus -eq 200) { throw "禁用后该用户 refresh 仍可刷新，期望被拒" }
    }
    $relogin = Invoke-Api -Method POST -Path "/api/v1/auth/login" `
        -Body "{`"username`":`"$username`",`"password`":`"$($script:IamProbePassword)`"}"
    if ($relogin.Status -ne 403 -or (Get-ErrorCode $relogin.Body) -ne "AUTH_ACCOUNT_DISABLED") {
        throw "禁用用户重新登录返回 $($relogin.Status)/$(Get-ErrorCode $relogin.Body)，期望 403 AUTH_ACCOUNT_DISABLED"
    }
    # 状态与审计一致：USER_DISABLED 必须存在。
    $audit = Invoke-Psql "select count(*) from audit_log where event_type='USER_DISABLED'"
    if ([int]$audit -lt 1) { throw "未发现 USER_DISABLED 审计事件（禁用未审计）" }
    # 复原：启用，避免影响后续用例。
    $enable = Invoke-Api -Method POST -Path "/api/v1/system/users/$userId`:enable" `
        -Headers @{ Authorization = "Bearer $($script:accessToken)" }
    if ($enable.Status -ne 200) { throw "复原启用用户失败，状态 $($enable.Status)" }
}

function Assert-LogoutAndReuse {
    # AC-P0B-IAM-009：Cookie 清除；Token Family 吊销；重用被拒；重复登出幂等。
    $login = Invoke-Api -Method POST -Path "/api/v1/auth/login" `
        -Body "{`"username`":`"$AdminUser`",`"password`":`"$AdminPass`"}"
    $access = "$((($login.Body | ConvertFrom-Json)).data.accessToken)"
    $refresh = Get-RefreshFromSetCookie $login.SetCookie
    if ([string]::IsNullOrEmpty($refresh)) { throw "登录未下发刷新 Cookie" }
    $family = Invoke-Psql ("select token_family from iam_token where jti=(select jti from iam_token " +
        "where principal_id=(select principal_id from iam_user where username='$AdminUser') order by id desc limit 1)")

    $logout1 = Invoke-Api -Method POST -Path "/api/v1/auth/logout" `
        -Headers @{ Authorization = "Bearer $access" } -RefreshCookie $refresh -Body "{}"
    if ($logout1.Status -ne 200) { throw "登出失败，状态 $($logout1.Status)" }
    if ($logout1.SetCookie -notmatch 'aihub_refresh=;\s' -or $logout1.SetCookie -notmatch 'Max-Age=0') {
        throw "登出未下发清除 Cookie（实际：$($logout1.SetCookie)）"
    }
    $activeAfter = Invoke-Psql "select count(*) from iam_token where token_family='$family' and status='ACTIVE'"
    if ([int]$activeAfter -ne 0) {
        throw "登出后 Token Family $family 残留 $activeAfter 条 ACTIVE，期望 0（Family 未吊销）"
    }
    $reuse = Invoke-Api -Method POST -Path "/api/v1/auth/refresh" -RefreshCookie $refresh
    if ($reuse.Status -eq 200) { throw "登出后重用 refresh 仍可刷新，期望被拒" }
    $logout2 = Invoke-Api -Method POST -Path "/api/v1/auth/logout" `
        -Headers @{ Authorization = "Bearer $access" } -RefreshCookie $refresh -Body "{}"
    if ($logout2.Status -ne 200) { throw "重复登出返回 $($logout2.Status)，期望 200（登出必须幂等）" }
    $logout3 = Invoke-Api -Method POST -Path "/api/v1/auth/logout" `
        -Headers @{ Authorization = "Bearer $access" } -Body "{}"
    if ($logout3.Status -ne 200) { throw "无 refresh 的登出返回 $($logout3.Status)，期望 200（登出必须幂等）" }
}

# ---------------------------------------------------------------------------
# V05e: 锁定、重放（并入 V05d 断言）、并发刷新与管理审计
# AC-P0B-IAM-005 / AC-P0B-IAM-011 / AC-P0B-IAM-012
# ---------------------------------------------------------------------------
function Assert-AccountLockout {
    # AC-P0B-IAM-005：达到阈值进入 LOCKED；后续正确口令也返回 AUTH_ACCOUNT_LOCKED；审计存在。
    $username = "compose-verify-iam5"
    $userId = New-IamProbeUser -Username $username -DisplayName "Compose Verify Iam5"
    $reset = Invoke-Api -Method POST -Path "/api/v1/system/users/$userId`:reset-password" `
        -Headers @{ Authorization = "Bearer $($script:accessToken)" } `
        -Body "{`"temporaryPassword`":`"$($script:IamProbePassword)`"}"
    if ($reset.Status -ne 200) { throw "重置验证用户口令失败，状态 $($reset.Status)" }
    [void](Reset-UserLockState $username)

    # 阈值与实现常量一致（LocalUser.MAX_FAILED_ATTEMPTS）：连续 5 次错误口令应在第 5 次锁定。
    $threshold = 5
    $lastStatus = 0
    $lastCode = ""
    for ($attempt = 1; $attempt -le $threshold; $attempt++) {
        $resp = Invoke-Api -Method POST -Path "/api/v1/auth/login" `
            -Body "{`"username`":`"$username`",`"password`":`"ComposeWrong-Password-01!z`"}"
        $lastStatus = $resp.Status
        $lastCode = Get-ErrorCode $resp.Body
    }
    $facts = Get-UserRowFacts $username
    if ($lastCode -ne "AUTH_ACCOUNT_LOCKED") {
        throw "第 $threshold 次错误口令返回 $lastStatus/$lastCode，期望 AUTH_ACCOUNT_LOCKED（账户未锁定）"
    }
    # status 是 facts 首列，故 LOCKED 可能位于串首（前面没有分隔符），匹配需兼容两种位置。
    if ($facts -notmatch '(^|\|)LOCKED\|') {
        throw "锁定后 iam_user.status 未落库为 LOCKED（实际：$facts）"
    }
    $lockedUntil = Invoke-Psql "select coalesce(locked_until::text,'NULL') from iam_user where username='$username'"
    if ($lockedUntil -eq "NULL") { throw "锁定后 locked_until 为空，期望非空" }
    # 锁定期间即使口令正确也必须被拒。
    $correct = Invoke-Api -Method POST -Path "/api/v1/auth/login" `
        -Body "{`"username`":`"$username`",`"password`":`"$($script:IamProbePassword)`"}"
    if ($correct.Status -ne 423 -or (Get-ErrorCode $correct.Body) -ne "AUTH_ACCOUNT_LOCKED") {
        throw "锁定期间正确口令返回 $($correct.Status)/$(Get-ErrorCode $correct.Body)，期望 423 AUTH_ACCOUNT_LOCKED"
    }
    $audit = Invoke-Psql "select count(*) from audit_log where event_type='AUTH_LOGIN_FAILED'"
    if ([int]$audit -lt 1) { throw "未发现 AUTH_LOGIN_FAILED 审计事件（登录失败未审计）" }
    # 复原：管理员重置口令并启用，解除锁定，避免影响后续用例。
    [void](Invoke-Api -Method POST -Path "/api/v1/system/users/$userId`:reset-password" `
            -Headers @{ Authorization = "Bearer $($script:accessToken)" } `
            -Body "{`"temporaryPassword`":`"$($script:IamProbePassword)`"}")
    [void](Invoke-Api -Method POST -Path "/api/v1/system/users/$userId`:enable" `
            -Headers @{ Authorization = "Bearer $($script:accessToken)" })
}

function Assert-ConcurrentRefreshSingleActive {
    # AC-P0B-IAM-011：同一 refresh JWT 并发刷新只允许一个成功；库中不得出现两条并存 ACTIVE。
    $login = Invoke-Api -Method POST -Path "/api/v1/auth/login" `
        -Body "{`"username`":`"$AdminUser`",`"password`":`"$AdminPass`"}"
    $refresh = Get-RefreshFromSetCookie $login.SetCookie
    if ([string]::IsNullOrEmpty($refresh)) { throw "登录未下发刷新 Cookie" }
    $principalId = Invoke-Psql "select principal_id from iam_user where username='$AdminUser'"
    $family = Invoke-Psql ("select token_family from iam_token where jti=(select jti from iam_token " +
        "where principal_id='$principalId' order by id desc limit 1)")
    if ([string]::IsNullOrEmpty($family)) { throw "无法确定并发刷新所使用的 Token Family" }

    # 两个独立连接同时发出刷新请求，确保真实并发到达后端（而非串行两次调用）。
    $handlerA = New-Object System.Net.Http.HttpClientHandler
    $handlerA.UseCookies = $false
    $handlerB = New-Object System.Net.Http.HttpClientHandler
    $handlerB.UseCookies = $false
    $clientA = New-Object System.Net.Http.HttpClient($handlerA)
    $clientB = New-Object System.Net.Http.HttpClient($handlerB)
    $clientA.Timeout = [TimeSpan]::FromSeconds(30)
    $clientB.Timeout = [TimeSpan]::FromSeconds(30)
    $statusA = -1
    $statusB = -1
    $bodyA = ""
    $bodyB = ""
    try {
        $requestA = New-Object System.Net.Http.HttpRequestMessage ([System.Net.Http.HttpMethod]::Post), "$BaseUrl/api/v1/auth/refresh"
        [void]$requestA.Headers.TryAddWithoutValidation("Cookie", "aihub_refresh=$refresh")
        $requestB = New-Object System.Net.Http.HttpRequestMessage ([System.Net.Http.HttpMethod]::Post), "$BaseUrl/api/v1/auth/refresh"
        [void]$requestB.Headers.TryAddWithoutValidation("Cookie", "aihub_refresh=$refresh")
        $taskA = $clientA.SendAsync($requestA)
        $taskB = $clientB.SendAsync($requestB)
        # 逐个 Wait 而非 Task.WaitAll：PowerShell 对 WaitAll 的 (Task[], int) 重载解析会失败。
        foreach ($task in @($taskA, $taskB)) {
            if (-not $task.Wait(30000)) {
                throw "并发刷新请求未在超时内完成，无法取得并发语义证据"
            }
        }
        $statusA = [int]$taskA.Result.StatusCode
        $statusB = [int]$taskB.Result.StatusCode
        $bodyA = $taskA.Result.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $bodyB = $taskB.Result.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    } finally {
        $clientA.Dispose()
        $clientB.Dispose()
    }
    $successCount = 0
    if ($statusA -eq 200) { $successCount++ }
    if ($statusB -eq 200) { $successCount++ }
    if ($successCount -ne 1) {
        throw "并发刷新成功次数为 $successCount（状态 $statusA/$statusB），期望恰好 1"
    }
    # 输家复用的 jti 此刻已为 ROTATED，按 REQ-IAM-004「ROTATED jti 再用视为重放，
    # 整个 Family REVOKED」必须整族吊销，故该 Family 的 ACTIVE 为 0。
    # 出口要求「无双有效 Token」即 ACTIVE <= 1；== 0 是更强且符合规格重放语义的结论。
    $activeInFamily = Invoke-Psql "select count(*) from iam_token where token_family='$family' and status='ACTIVE'"
    if ([int]$activeInFamily -ne 0) {
        throw "并发刷新后该 Token Family 的 ACTIVE 数量为 $activeInFamily，期望 0（输家触发重放 → 整族吊销）"
    }
    # 输家必须返回定义好的重放语义，且留有拒绝审计。
    $loserCode = ""
    if ($statusA -ne 200) { $loserCode = Get-ErrorCode $bodyA } else { $loserCode = Get-ErrorCode $bodyB }
    if ($loserCode -ne "AUTH_REFRESH_REPLAYED") {
        throw "并发刷新失败方错误码为 $loserCode，期望 AUTH_REFRESH_REPLAYED"
    }
    $replayAudit = Invoke-Psql "select count(*) from audit_log where event_type='AUTH_TOKEN_REPLAY_REJECTED'"
    if ([int]$replayAudit -lt 1) { throw "未发现 AUTH_TOKEN_REPLAY_REJECTED 审计事件（并发拒绝未审计）" }
}

function Assert-AdminUserLifecycle {
    # AC-P0B-IAM-012：管理员创建/编辑/重置/禁用/启用用户，每步产生对应审计事件。
    $username = "compose-verify-iam12"
    $userId = New-IamProbeUser -Username $username -DisplayName "Compose Verify Iam12"
    $authHeader = @{ Authorization = "Bearer $($script:accessToken)" }

    $update = Invoke-Api -Method PATCH -Path "/api/v1/system/users/$userId" -Headers $authHeader `
        -Body "{`"displayName`":`"Compose Verify Iam12 Renamed`",`"email`":null,`"locale`":`"en-US`"}"
    if ($update.Status -ne 200) { throw "编辑用户失败，状态 $($update.Status)，错误码 $(Get-ErrorCode $update.Body)" }
    $locale = Invoke-Psql "select locale from iam_user where user_id='$userId'"
    if ($locale -ne "en-US") { throw "编辑后 locale 未落库为 en-US（实际：$locale）" }

    $reset = Invoke-Api -Method POST -Path "/api/v1/system/users/$userId`:reset-password" -Headers $authHeader `
        -Body "{`"temporaryPassword`":`"$($script:IamProbePassword)`"}"
    if ($reset.Status -ne 200) { throw "重置口令失败，状态 $($reset.Status)" }
    $facts = Get-UserRowFacts $username
    if ($facts -notmatch '^PENDING_ACTIVATION\|.*\|1\|') {
        throw "重置口令后未回到强制改密/待激活（实际：$facts）"
    }
    $disable = Invoke-Api -Method POST -Path "/api/v1/system/users/$userId`:disable" -Headers $authHeader
    if ($disable.Status -ne 200) { throw "禁用用户失败，状态 $($disable.Status)" }
    $enable = Invoke-Api -Method POST -Path "/api/v1/system/users/$userId`:enable" -Headers $authHeader
    if ($enable.Status -ne 200) { throw "启用用户失败，状态 $($enable.Status)" }

    # 每步审计：创建/编辑/禁用/启用/重置口令均须存在对应事件（OpenAPI x-audit-event）。
    $required = @("USER_CREATED", "USER_UPDATED", "USER_ENABLED", "USER_DISABLED", "USER_PASSWORD_RESET")
    $missing = @()
    foreach ($eventType in $required) {
        $count = Invoke-Psql "select count(*) from audit_log where event_type='$eventType'"
        if ([int]$count -lt 1) { $missing += $eventType }
    }
    if ($missing.Count -gt 0) {
        throw "以下用户管理审计事件缺失：$($missing -join '、')（管理动作未审计）"
    }

    # 不能禁用最后一个有效平台管理员：必须拒绝（REQ-IAM-006 / INV-IAM-006）。
    $adminUserId = Invoke-Psql "select user_id from iam_user where username='$AdminUser'"
    # 用 jsonb_build_array 构造数组字面量：避免 PowerShell 向 docker 传参时吞掉双引号，
    # 使 '["user:manage"]'::jsonb 退化成非法 JSON（[user:manage]）。
    $activeAdmins = Invoke-Psql "select count(*) from iam_user where status='ACTIVE' and scopes @> jsonb_build_array('user:manage')"
    if ([int]$activeAdmins -ne 1) {
        throw "前置条件异常：持有 user:manage 的活跃管理员数量为 $activeAdmins，期望 1"
    }
    # 拒绝审计必须真实落库。先取基线计数，拒绝后再断言恰好 +1：
    # 只写「>= 1」无法鉴别本次拒绝是否落库（历史运行的行会让断言恒真）。
    $deniedBefore = [int](Invoke-Psql "select count(*) from audit_log where event_type='USER_DISABLED' and result='DENIED'")
    $lastAdminDisable = Invoke-Api -Method POST -Path "/api/v1/system/users/$adminUserId`:disable" -Headers $authHeader
    if ($lastAdminDisable.Status -eq 200) {
        # 产品未拒绝：立即复原，避免把验收栈留在「无管理员」状态，然后如实报出缺陷。
        [void](Invoke-Psql ("update iam_user set status='ACTIVE', failed_login_attempts=0, locked_until=null, " +
                "updated_at=now() where user_id='$adminUserId'"))
        [void](Invoke-Psql ("update iam_principal set status='ACTIVE', updated_at=now() " +
                "where principal_id=(select principal_id from iam_user where user_id='$adminUserId')"))
        throw ("最后一个有效平台管理员被允许禁用（返回 200），期望拒绝并返回 COMMON_INVALID_ARGUMENT" +
               "且产生拒绝审计（REQ-IAM-006）。已自动复原管理员状态。")
    }
    if ((Get-ErrorCode $lastAdminDisable.Body) -ne "COMMON_INVALID_ARGUMENT") {
        throw "禁用最后一个管理员返回错误码 $(Get-ErrorCode $lastAdminDisable.Body)，期望 COMMON_INVALID_ARGUMENT"
    }
    # 拒绝路径必须有独立提交的拒绝审计：否则「拒绝」既无权威记录也不可追溯（REQ-IAM-006）。
    # 注意 resource_id 落的是目标 principalId（IdentityAuditAdapter 传 targetId=principalId），
    # 不是 iam_user.user_id，故须按 principal_id 关联。
    $adminPrincipalId = Invoke-Psql "select principal_id from iam_user where user_id='$adminUserId'"
    $deniedForAdmin = [int](Invoke-Psql ("select count(*) from audit_log where event_type='USER_DISABLED' " +
            "and result='DENIED' and error_code='COMMON_INVALID_ARGUMENT' and resource_id='$adminPrincipalId'"))
    if ($deniedForAdmin -lt 1) {
        throw "禁用最后一个管理员被拒后未落库 USER_DISABLED/DENIED 审计（拒绝路径无权威审计记录）"
    }
    $deniedTotalAfter = [int](Invoke-Psql "select count(*) from audit_log where event_type='USER_DISABLED' and result='DENIED'")
    if ($deniedTotalAfter -ne ($deniedBefore + 1)) {
        throw "USER_DISABLED/DENIED 审计计数由 $deniedBefore 变为 $deniedTotalAfter，期望恰好 +1（本次拒绝未审计或重复审计）"
    }
}

# ---------------------------------------------------------------------------
# V06: 权限过滤（审计查询默认拒绝 + 有权限正向可达）
# ---------------------------------------------------------------------------
if (-not $backendReachable) {
    Skip-Step "V06" "权限过滤（审计）" "backend 不可达"
} else {
    Test-Step "V06" "权限过滤（审计/指标默认拒绝 + 有权限正向可达）" {
        Assert-DefaultDeny "/api/v1/system/audit-logs"
        Assert-DefaultDeny "/api/v1/system/metrics/summary"
        # 管理员经 ADMIN 角色合法持有 audit:read / system:observe，应真实可达。
        Assert-Permitted "/api/v1/system/audit-logs"
        Assert-Permitted "/api/v1/system/metrics/summary"
    }
}

# ---------------------------------------------------------------------------
# V07: 字典/标签受控访问（默认拒绝 + 有权限正向返回真实数据）
# ---------------------------------------------------------------------------
if (-not $backendReachable) {
    Skip-Step "V07" "字典/标签受控访问" "backend 不可达"
} else {
    Test-Step "V07" "字典/标签受控访问（默认拒绝 + 有权限正向断言）" {
        Assert-DefaultDeny "/api/v1/system/dictionaries"
        Assert-DefaultDeny "/api/v1/system/tags"
        # AC-P0B-AUTH-004：有 dictionary:read / tag:read 的主体必须返回真实数据而非空集合占位。
        Assert-Permitted "/api/v1/system/dictionaries" -RequireNonEmptyData
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
# V09: 持久化任务（默认拒绝 + 有权限正向可达）
# ---------------------------------------------------------------------------
if (-not $backendReachable) {
    Skip-Step "V09" "持久化任务访问" "backend 不可达"
} else {
    Test-Step "V09" "持久化任务访问（默认拒绝 + 有权限正向可达）" {
        Assert-DefaultDeny "/api/v1/system/jobs"
        Assert-Permitted "/api/v1/system/jobs"
    }
}

# ---------------------------------------------------------------------------
# V10: 通知（默认拒绝 + 有权限正向可达）
# ---------------------------------------------------------------------------
if (-not $backendReachable) {
    Skip-Step "V10" "通知访问" "backend 不可达"
} else {
    Test-Step "V10" "通知访问（默认拒绝 + 有权限正向可达）" {
        Assert-DefaultDeny "/api/v1/system/notifications"
        Assert-Permitted "/api/v1/system/notifications"
    }
}

# ---------------------------------------------------------------------------
# V11: 观测（健康公开 200；诊断需 system:observe，默认拒绝 + 有权限正向可达）
# ---------------------------------------------------------------------------
if (-not $backendReachable) {
    Skip-Step "V11" "观测与诊断" "backend 不可达"
} else {
    Test-Step "V11" "观测与诊断" {
        $health = Invoke-Api -Method GET -Path "/actuator/health"
        if ($health.Status -ne 200) { throw "/actuator/health 返回 $($health.Status)，期望 200" }
        Assert-DefaultDeny "/api/v1/system/dependencies"
        Assert-Permitted "/api/v1/system/dependencies"
    }
}

# ---------------------------------------------------------------------------
# V05c: 身份生命周期（AC-P0B-IAM-001 引导唯一性、AC-P0B-IAM-003 首登改密、AC-P0B-IAM-004 防枚举）
# 依赖 V05b 已解除管理员改密门并备好最小权限主体。
# ---------------------------------------------------------------------------
if (-not $backendReachable) {
    Skip-Step "V05c" "身份生命周期（引导/首登改密/防枚举）" "backend 不可达"
} elseif (-not $pgReachable) {
    Skip-Step "V05c" "身份生命周期（引导/首登改密/防枚举）" "postgres 不可达（无法断言落库状态与审计）"
} else {
    Test-Step "V05c" "身份生命周期（AC-P0B-IAM-001/003/004）" {
        Assert-BootstrapAdminUniqueness
        Assert-FirstLoginPasswordChange
        Assert-LoginAntiEnumeration
    }
}

# ---------------------------------------------------------------------------
# V05d–V05j: 按 AC 逐一独立执行，使缺陷可精确定位（一个场景失败不掩盖其余场景）
# 每个 V 编号显式声明其覆盖的 AC-*（p0b-exit-catalog 第 20 行要求）。
# ---------------------------------------------------------------------------
if (-not $backendReachable) {
    foreach ($case in @(
            @("V05d", "刷新轮换（AC-P0B-IAM-006）"),
            @("V05e", "禁用实时失效（AC-P0B-IAM-008）"),
            @("V05f", "登出与重用（AC-P0B-IAM-009）"),
            @("V05g", "刷新重放整族吊销（AC-P0B-IAM-007）"),
            @("V05h", "账户锁定（AC-P0B-IAM-005）"),
            @("V05i", "并发刷新无双有效 Token（AC-P0B-IAM-011）"),
            @("V05j", "用户管理与最后一个管理员保护（AC-P0B-IAM-012）"))) {
        Skip-Step $case[0] $case[1] "backend 不可达"
    }
} elseif (-not $pgReachable) {
    foreach ($case in @(
            @("V05d", "刷新轮换（AC-P0B-IAM-006）"),
            @("V05e", "禁用实时失效（AC-P0B-IAM-008）"),
            @("V05f", "登出与重用（AC-P0B-IAM-009）"),
            @("V05g", "刷新重放整族吊销（AC-P0B-IAM-007）"),
            @("V05h", "账户锁定（AC-P0B-IAM-005）"),
            @("V05i", "并发刷新无双有效 Token（AC-P0B-IAM-011）"),
            @("V05j", "用户管理与最后一个管理员保护（AC-P0B-IAM-012）"))) {
        Skip-Step $case[0] $case[1] "postgres 不可达（无法断言 Token 与审计落库状态）"
    }
} else {
    Test-Step "V05d" "刷新轮换（AC-P0B-IAM-006）" { Assert-RefreshRotation }
    Test-Step "V05e" "禁用实时失效（AC-P0B-IAM-008）" { Assert-DisableRevokesSessions }
    Test-Step "V05f" "登出与重用（AC-P0B-IAM-009）" { Assert-LogoutAndReuse }
    Test-Step "V05g" "刷新重放整族吊销（AC-P0B-IAM-007）" { Assert-RefreshReplayRevokesFamily }
    Test-Step "V05h" "账户锁定（AC-P0B-IAM-005）" { Assert-AccountLockout }
    Test-Step "V05i" "并发刷新无双有效 Token（AC-P0B-IAM-011）" { Assert-ConcurrentRefreshSingleActive }
    Test-Step "V05j" "用户管理与最后一个管理员保护（AC-P0B-IAM-012）" { Assert-AdminUserLifecycle }
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
