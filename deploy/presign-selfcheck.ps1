# deploy/presign-selfcheck.ps1 —— FILE-002 自检（Windows 宿主机，PowerShell 5.1+）
# 作者：AxeXie
# 日期：2026-08-24
#
# 目的：证明「客户端可达对象 public URL」—— 由 app 签发的 MinIO 预签名 part URL
#       对 Compose 网络之外的真实客户端（本脚本运行在宿主机上）可达且签名有效。
#
# 流程（全部走公网端点，不进入 Compose 内部网络）：
#   1. POST /api/v1/auth/login            用 bootstrap 平台管理员登录拿 Bearer token
#   2. POST /api/v1/repositories          建仓库并等待 provisioning active（Gitea 绑定就绪）
#   3. GET  /api/v1/repositories/{id}/files 取分支 head（resolvedCommitSha）
#   4. POST /api/v1/repositories/{id}/uploads  发起上传（等待 worker 初始化到 uploading）
#   5. POST /api/v1/uploads/{id}/part-urls    取 part 1 的预签名 PUT URL
#   6. 断言 URL 以配置的公网基址开头、且不含内部服务名（minio:9000 等）
#   7. 从宿主机 PUT 小对象到该 URL；HEAD 回探（收到 S3 应答即证明外部可达）
#   8. POST /api/v1/uploads/{id}:abort      清理上传会话（尽力而为）
#
# 说明：v1 默认 ContentScanner 为 fail-closed（未配置真实扫描器时拒绝发布），
#       因此自检刻意停在「预签名 PUT 直传成功」这一步 —— 这已完整覆盖 FILE-002
#       （URL 外部可达 + 签名有效），complete/publish 链路需部署真实扫描器后再验证。
#
# 用法（先启动整套栈，等待 app 健康后执行）：
#   powershell -ExecutionPolicy Bypass -File deploy/presign-selfcheck.ps1
#   # 自定义入口/凭据（与 deploy/.env 中的覆盖值保持一致）：
#   .\deploy\presign-selfcheck.ps1 -ApiBase http://localhost:8081 -MinioPublicBaseUrl http://localhost:9000 `
#       -Username platform-root -Password Boot-Strap-1x

param(
    [string]$ApiBase = "http://localhost:8081",          # Nginx 业务入口（公网）
    [string]$MinioPublicBaseUrl = "http://localhost:9000", # MinIO 公网端点（预签名 URL 期望基址）
    [string]$Username = "platform-root",                 # compose 默认 bootstrap 管理员
    [string]$Password = "Boot-Strap-1x",
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
$script:Failed = $false

function Fail([string]$Message) {
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    $script:Failed = $true
}

function Step([string]$Message) {
    Write-Host "== $Message" -ForegroundColor Cyan
}

# 调用 JSON API：非 2xx 直接抛错并携带响应体，便于定位
function Invoke-Json {
    param([string]$Method, [string]$Url, $Body = $null, [hashtable]$Headers = @{})
    $args = @{
        Method      = $Method
        Uri         = $Url
        Headers     = $Headers
        ErrorAction = "Stop"
    }
    if ($null -ne $Body) {
        $args.ContentType = "application/json"
        $args.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
    }
    try {
        return Invoke-RestMethod @args
    } catch {
        $detail = ""
        if ($_.Exception.Response) {
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                if ($stream) { $detail = (New-Object IO.StreamReader($stream)).ReadToEnd() }
            } catch { $detail = $_.ErrorDetails.Message }
        }
        if (-not $detail) { $detail = $_.ErrorDetails.Message }
        throw "HTTP $Method $Url 失败: $($_.Exception.Message) $detail"
    }
}

# 返回 HTTP 状态码；无任何 HTTP 应答（连接层失败）返回 -1
function Get-HttpStatus {
    param([string]$Method, [string]$Url, [byte[]]$Bytes = $null, [hashtable]$Headers = @{})
    try {
        $req = @{ Method = $Method; Uri = [Uri]$Url; Headers = $Headers; ErrorAction = "Stop" }
        if ($null -ne $Bytes) { $req.Body = $Bytes }
        $resp = Invoke-WebRequest @req
        return [int]$resp.StatusCode
    } catch {
        if ($_.Exception.Response) {
            try { return [int]$_.Exception.Response.StatusCode } catch { return -1 }
        }
        return -1
    }
}

try {
    # ---------- 1. 登录 ----------
    Step "登录 $ApiBase/api/v1/auth/login（bootstrap 管理员）"
    $login = Invoke-Json "POST" "$ApiBase/api/v1/auth/login" @{ username = $Username; password = $Password }
    $Token = $login.data.accessToken
    $NamespaceId = $login.data.user.namespaceId
    if (-not $Token) { throw "登录响应缺少 data.accessToken" }
    if (-not $NamespaceId) { throw "登录响应缺少 data.user.namespaceId" }
    Write-Host "   token 已获取；namespaceId=$NamespaceId"
    $Auth = @{ Authorization = "Bearer $Token" }

    # ---------- 2. 建仓库并等待 active ----------
    $RepoName = "presign-check-" + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    Step "创建仓库 $RepoName 并等待 provisioning active"
    $repo = Invoke-Json "POST" "$ApiBase/api/v1/repositories" @{
        namespaceId          = $NamespaceId
        type                 = "model"
        metadataSchemaVersion = 1
        name                 = $RepoName
        visibility           = "public"
        metadata             = @{ license = "MIT" }
    } -Headers ($Auth + @{ "Idempotency-Key" = [Guid]::NewGuid().ToString() })
    $RepoId = $repo.data.id
    if (-not $RepoId) { throw "建仓响应缺少 data.id" }

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $Life = ""
    while ([DateTime]::UtcNow -lt $deadline) {
        $detail = Invoke-Json "GET" "$ApiBase/api/v1/repositories/$RepoId" -Headers $Auth
        $Life = $detail.data.lifecycleStatus
        if ($Life -eq "active") { break }
        Start-Sleep -Seconds 3
    }
    if ($Life -ne "active") { throw "等待仓库 active 超时（最后状态=$Life）" }
    Write-Host "   仓库 $RepoId 已 active"

    # ---------- 3. 分支 head ----------
    $files = Invoke-Json "GET" "$ApiBase/api/v1/repositories/$RepoId/files" -Headers $Auth
    $BaseSha = $files.data.resolvedCommitSha
    if (-not $BaseSha) { throw "files 响应缺少 data.resolvedCommitSha" }

    # ---------- 4. 发起上传并等待 uploading ----------
    $Content = [Text.Encoding]::UTF8.GetBytes("modelhub presign selfcheck @ $(Get-Date -Format o)`n")
    $Sha256 = [BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash($Content)) -replace "-", ""
    $Sha256 = $Sha256.ToLowerInvariant()
    $UploadPath = "presign-selfcheck.txt"
    Step "发起上传 $UploadPath（$($Content.Length) bytes，sha256=$($Sha256.Substring(0,12))...）"
    $up = Invoke-Json "POST" "$ApiBase/api/v1/repositories/$RepoId/uploads" @{
        branch        = "main"
        baseCommitSha = $BaseSha
        path          = $UploadPath
        sizeBytes     = $Content.Length
        sha256        = $Sha256
        contentType   = "text/plain"
    } -Headers ($Auth + @{ "Idempotency-Key" = [Guid]::NewGuid().ToString() })
    $UploadId = $up.data.id
    if (-not $UploadId) { throw "initiate 响应缺少 data.id" }
    Write-Host "   uploadId=$UploadId contentSource=$($up.data.contentSource)"

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $Status = ""
    while ([DateTime]::UtcNow -lt $deadline) {
        $st = Invoke-Json "GET" "$ApiBase/api/v1/uploads/$UploadId" -Headers $Auth
        $Status = $st.data.status
        if ($Status -eq "uploading") { break }
        if ($Status -in @("failed", "aborted", "expired")) { throw "上传提前进入终态 $Status" }
        Start-Sleep -Seconds 2
    }
    if ($Status -ne "uploading") { throw "等待上传状态 uploading 超时（最后状态=$Status）" }

    # ---------- 5. 取预签名 part URL ----------
    Step "签发 part 1 预签名 URL"
    $pu = Invoke-Json "POST" "$ApiBase/api/v1/uploads/$UploadId/part-urls" @{ partNumbers = @(1) } -Headers $Auth
    $Url = $pu.data.items[0].url
    if (-not $Url) { throw "part-urls 响应缺少 items[0].url" }
    Write-Host "   $Url"

    # ---------- 6. FILE-002 断言：公网基址 + 无内部服务名 ----------
    Step "FILE-002 断言：URL 指向公网端点且不含内部服务名"
    if ($Url.StartsWith("$MinioPublicBaseUrl/")) {
        Write-Host "   [OK] 以公网基址 $MinioPublicBaseUrl 开头"
    } else {
        Fail "预签名 URL 不以公网基址 $MinioPublicBaseUrl 开头（检查 MODEHUB_ARTIFACT_PUBLIC_BASE_URL）"
    }
    if ($Url -match "://minio:" -or $Url -match "://gitea:" -or $Url -match "://postgres:") {
        Fail "预签名 URL 含内部 Docker 服务名: $Url"
    } else {
        Write-Host "   [OK] 未发现内部服务名"
    }

    # ---------- 7. 宿主机直传 + 回探 ----------
    Step "从宿主机（Compose 网络之外）PUT 到预签名 URL"
    # x-amz-content-sha256 参与服务端签名（SignedHeaders），客户端必须回传同值头
    $putStatus = Get-HttpStatus "PUT" $Url $Content @{ "x-amz-content-sha256" = "UNSIGNED-PAYLOAD" }
    if ($putStatus -ge 200 -and $putStatus -lt 300) {
        Write-Host "   [OK] PUT $putStatus —— 外部可达且签名有效"
    } else {
        Fail "PUT 返回 $putStatus（403=签名/可达性问题，-1=连接失败）"
    }

    Step "HEAD 回探同一 URL（应答任何 HTTP 状态即证明外部可达；预签名按方法签名，HEAD 预期 403）"
    $headStatus = Get-HttpStatus "HEAD" $Url
    if ($headStatus -gt 0) {
        Write-Host "   [OK] HEAD 得到 HTTP $headStatus 应答（服务端可达）"
    } else {
        Fail "HEAD 无 HTTP 应答（连接失败）"
    }

    # ---------- 8. 清理（尽力而为）----------
    Step "清理：abort 上传会话"
    try {
        $null = Invoke-Json "POST" "$ApiBase/api/v1/uploads/${UploadId}:abort" -Headers $Auth
        Write-Host "   已 abort"
    } catch {
        Write-Host "   [WARN] abort 失败（不影响自检结论）: $($_.Exception.Message)"
    }
} catch {
    Fail $_.Exception.Message
}

Write-Host ""
if ($script:Failed) {
    Write-Host "RESULT: FAIL —— FILE-002 未满足（对象 public URL 对外部客户端不可达）" -ForegroundColor Red
    exit 1
}
Write-Host "RESULT: PASS —— FILE-002 满足：预签名对象 URL 对外部客户端可达" -ForegroundColor Green
exit 0
