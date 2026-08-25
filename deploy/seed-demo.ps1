# seed-demo.ps1 —— HarnessDG 前端演示数据幂等播种（09 §13 验证前置）
# 用法：.\deploy\seed-demo.ps1 [-BaseUrl http://localhost:8081]
# 幂等：用户/组织按固定用户名探测（已存在则登录复用）；仓库按 namespace/name 探测。
# 产出：2 用户 + 1 组织 + 13 model（含 1 gated）+ 4 dataset（含 1 gated）+ 6 studio。
param(
    [string]$BaseUrl = "http://localhost:8081"
)

$ErrorActionPreference = "Stop"
$api = "$BaseUrl/api/v1"
$script:created = 0; $script:skipped = 0; $script:failed = 0

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null, [string]$Csrf = $null, [string]$IdemKey = $null, [int]$Retries = 2)
    $headers = @{ }
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    if ($Csrf)  { $headers["X-CSRF-Token"] = $Csrf }
    if ($IdemKey) { $headers["Idempotency-Key"] = $IdemKey }
    # PS5.1 默认以 ISO-8859-1 编码 -Body 字符串，中文会损坏 → 显式 UTF-8 字节
    $bytes = $null
    if ($null -ne $Body) { $bytes = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8 -Compress)) }
    for ($i = 0; $i -le $Retries; $i++) {
        try {
            $resp = Invoke-RestMethod -Uri "$api$Path" -Method $Method -Headers $headers -ContentType "application/json; charset=utf-8" -Body $bytes -TimeoutSec 60
            return $resp
        } catch {
            $status = Get-HttpStatus $_
            # 409 冲突（已存在）不重试，直接抛给调用方判断
            if ($status -eq 409) { throw }
            if ($i -lt $Retries -and $Method -ne "GET") { Start-Sleep -Seconds 2; continue }
            throw
        }
    }
}

# PS5.1 的 HttpWebResponse 状态码提取（$_.Exception.Response 运行时类型为 HttpWebResponse）
function Get-HttpStatus($ErrRecord) {
    try {
        $r = $ErrRecord.Exception.Response
        if ($r -and $r -is [System.Net.HttpWebResponse]) { return [int]$r.StatusCode }
    } catch {}
    return 0
}

function Get-ErrDetail($e) {
    try {
        $r = $e.Exception.Response
        if ($r) {
            $sr = New-Object System.IO.StreamReader($r.GetResponseStream())
            return $sr.ReadToEnd()
        }
    } catch {}
    return "$e"
}

# ---------- 用户：注册或登录（返回 token/csrf/namespaceId/userId） ----------
function Ensure-User([string]$Username, [string]$Password, [string]$Nickname) {
    try {
        $r = Invoke-Api -Method POST -Path "/auth/register" -Body @{
            username = $Username; password = $Password; nickname = $Nickname
        } -Retries 0
        Write-Host "  + 用户已注册: $Username"
        $script:created++
        return @{ token = $r.data.accessToken; csrf = $r.data.csrfToken
                  namespaceId = $r.data.user.namespaceId; userId = $r.data.user.id }
    } catch {
        $r = Invoke-Api -Method POST -Path "/auth/login" -Body @{
            username = $Username; password = $Password
        } -Retries 0
        Write-Host "  · 用户已存在，复用登录: $Username"
        return @{ token = $r.data.accessToken; csrf = $r.data.csrfToken
                  namespaceId = $r.data.user.namespaceId; userId = $r.data.user.id }
    }
}

# ---------- 仓库：按 namespace/name 探测，不存在则创建并轮询 lifecycle ----------
function Ensure-Repo($Ctx, [string]$Namespace, [string]$NamespaceId, $Spec) {
    if (-not $Spec.type) { $Spec.type = "model" }  # models 条目缺省 model
    try {
        $null = Invoke-Api -Method GET -Path "/repositories/resolve/$($Spec.type)/$Namespace/$($Spec.name)" -Retries 0
        Write-Host "  · 已存在，跳过: $Namespace/$($Spec.name)"
        $script:skipped++
        return
    } catch {
        $status = Get-HttpStatus $_
        if ($status -ne 404) { Write-Warning "resolve 探测异常($status)，尝试直接创建"; }
    }
    $body = @{
        namespaceId = $NamespaceId; type = $Spec.type; name = $Spec.name
        displayName = $Spec.displayName; description = $Spec.description
        visibility = "public"; metadataSchemaVersion = 1; metadata = $Spec.meta
    }
    if ($Spec.gated) { $body.gated = $true }
    try {
        # 契约要求写操作带 Idempotency-Key；用稳定键（namespace/name）使 seed 重跑幂等重放
        $r = Invoke-Api -Method POST -Path "/repositories" -Body $body -Token $Ctx.token -Csrf $Ctx.csrf `
             -IdemKey "seed-demo-$Namespace-$($Spec.name)" -Retries 0
    } catch {
        if ((Get-HttpStatus $_) -eq 409) {
            Write-Host "  · 已存在(409)，跳过: $Namespace/$($Spec.name)"; $script:skipped++; return
        }
        Write-Host "  × 创建失败: $Namespace/$($Spec.name)  $(Get-ErrDetail $_)"
        Write-Host "    BODY: $(($body | ConvertTo-Json -Depth 8 -Compress))"
        $script:failed++; return
    }
    $repoId = $r.data.id
    # 轮询 lifecycle：provisioning → active（saga 异步 provisioning gitea/minio 资源）
    $deadline = (Get-Date).AddSeconds(120)
    $life = $r.data.lifecycleStatus
    while ($life -eq "provisioning" -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
        try {
            $cur = Invoke-Api -Method GET -Path "/repositories/$repoId" -Retries 0
            $life = $cur.data.lifecycleStatus
        } catch { break }
    }
    if ($life -eq "active") {
        Write-Host "  + 已创建(active): $Namespace/$($Spec.name)"; $script:created++
    } else {
        Write-Host "  ! 已受理但 lifecycle=$life : $Namespace/$($Spec.name)"; $script:created++
    }
}

Write-Host "=== seed-demo: $api ==="

# ---------- 1. 用户 ----------
Write-Host "[1/4] 用户"
$alice = Ensure-User "demo-alice" "Alice-Demo-1x!" "演示用户·Alice"
$bob   = Ensure-User "demo-bob"   "Bob-Demo-1x!!"  "演示用户·Bob"

# ---------- 2. 组织（alice 创建，bob 加入） ----------
Write-Host "[2/4] 组织"
$orgNs = $null
try {
    $orgs = Invoke-Api -Method GET -Path "/organizations?page=1&pageSize=100" -Retries 0
    $exist = $orgs.data.items | Where-Object { $_.slug -eq "demo-lab" } | Select-Object -First 1
    if ($exist) { $orgNs = $exist.namespaceId; Write-Host "  · 组织已存在: demo-lab" }
} catch {}
if (-not $orgNs) {
    try {
        $o = Invoke-Api -Method POST -Path "/organizations" -Token $alice.token -Csrf $alice.csrf -Retries 0 -Body @{
            slug = "demo-lab"; name = "Demo Lab"; description = "前端演示组织（模型/数据集/工作空间示例）"
        }
        $orgNs = $o.data.namespaceId
        Write-Host "  + 组织已创建: demo-lab"; $script:created++
        try {
            $null = Invoke-Api -Method POST -Path "/organizations/$($o.data.id)/members" `
                -Token $alice.token -Csrf $alice.csrf -Retries 0 `
                -Body @{ userId = $bob.userId; role = "write" }
            Write-Host "  + 成员已加入: demo-bob (write)"
        } catch { Write-Warning "组织成员添加失败（可能已存在）" }
    } catch {
        Write-Host "  × 组织创建失败: $(Get-ErrDetail $_)"; $script:failed++
    }
}

# ---------- 3. 仓库 ----------
Write-Host "[3/4] 模型（13，含 1 gated）"
$models = @(
    @{ name="qwen2.5-7b-instruct"; displayName="Qwen2.5-7B-Instruct"; task="text-generation";
       meta=@{ license="apache-2.0"; framework="transformers"; architecture="qwen2"; language="zh";
               task="text-generation"; tags=@("chat","code"); capabilities=@("api-inference","model-demo");
               apiStatus="online"; parameterCount=7; parameterUnit="B"; deployable=$true; mcpCompatible=$true } },
    @{ name="qwen2.5-72b-instruct"; displayName="Qwen2.5-72B-Instruct"; task="text-generation";
       meta=@{ license="apache-2.0"; framework="transformers"; architecture="qwen2"; language="zh";
               task="text-generation"; tags=@("chat"); capabilities=@("api-inference");
               parameterCount=72; parameterUnit="B"; deployable=$true } },
    @{ name="llama3.1-8b"; displayName="Llama-3.1-8B"; task="text-generation";
       meta=@{ license="mit"; framework="pytorch"; architecture="llama3"; language="en";
               task="text-generation"; tags=@("chat"); capabilities=@("sdk-train","swing-deploy");
               parameterCount=8; parameterUnit="B"; deployable=$true; mcpCompatible=$false } },
    @{ name="deepseek-v3"; displayName="DeepSeek-V3"; task="text-generation";
       meta=@{ license="mit"; framework="pytorch"; architecture="deepseek-v3"; language="zh";
               task="text-generation"; tags=@("chat","code"); capabilities=@("api-inference");
               apiStatus="online"; parameterCount=671; parameterUnit="B" } },
    @{ name="qwen2.5-vl-7b"; displayName="Qwen2.5-VL-7B（图文理解）"; task="image-text-understanding";
       meta=@{ license="apache-2.0"; framework="transformers"; architecture="qwen2-5-vl"; language="zh";
               task="image-text-understanding"; tags=@("chat"); capabilities=@("model-demo");
               parameterCount=7; parameterUnit="B"; deployable=$true } },
    @{ name="bert-base-chinese"; displayName="BERT-Base-Chinese"; task="text-classification";
       meta=@{ license="apache-2.0"; framework="tensorflow"; architecture="bert"; language="zh";
               task="text-classification"; tags=@("ner"); parameterCount=110; parameterUnit="M" } },
    @{ name="chatglm3-6b"; displayName="ChatGLM3-6B"; task="text-generation";
       meta=@{ license="apache-2.0"; framework="pytorch"; architecture="chatglm"; language="zh";
               task="text-generation"; tags=@("chat"); parameterCount=6; parameterUnit="B" } },
    @{ name="stable-diffusion-xl"; displayName="Stable-Diffusion-XL"; task="image-generation";
       meta=@{ license="creativeml-openrail-m"; framework="diffusers"; architecture="stable-diffusion"; language="en";
               task="image-generation"; tags=@("code"); capabilities=@("model-demo","pai-gallery-deploy");
               deployable=$true } },
    @{ name="whisper-large-v3"; displayName="Whisper-Large-V3（语音识别）"; task="speech";
       meta=@{ license="mit"; framework="pytorch"; language="en"; task="speech";
               tags=@("tts"); parameterCount=1550; parameterUnit="M" } },
    @{ name="bge-m3-embedding"; displayName="BGE-M3 多语嵌入"; task="multimodal-embedding";
       meta=@{ license="mit"; framework="sentence-transformers"; language="zh";
               task="multimodal-embedding"; tags=@("mteb"); apiStatus="online" } },
    @{ name="mistral-7b-v0.3"; displayName="Mistral-7B-v0.3"; task="text-generation";
       meta=@{ license="apache-2.0"; framework="safetensors"; architecture="mistral"; language="en";
               task="text-generation"; parameterCount=7; parameterUnit="B" } },
    @{ name="internlm2-20b"; displayName="InternLM2-20B"; task="text-generation";
       meta=@{ license="apache-2.0"; framework="pytorch"; architecture="internlm2"; language="zh";
               task="text-generation"; parameterCount=20; parameterUnit="B" } },
    @{ name="medical-llama3-gated"; displayName="医疗问答模型（申请制）"; task="qa"; gated=$true;
       meta=@{ license="mit"; framework="gguf"; architecture="llama3"; language="zh";
               task="text-generation"; tags=@("chat"); parameterCount=8; parameterUnit="B" } }
)
foreach ($m in $models) {
    Ensure-Repo $alice "demo-alice" $alice.namespaceId $m
}

Write-Host "[4/4] 数据集（4，含 1 gated）+ 工作空间（6）"
$datasets = @(
    @{ name="chinese-news-corpus"; displayName="中文新闻语料"; type="dataset";
       meta=@{ license="cc-by-4.0"; task="text-classification"; estimatedRows=1200000;
               dataFormats=@("json","csv"); tags=@("chat"); sensitivityLevel="public"; previewPolicy="enabled" } },
    @{ name="image-net-subset"; displayName="ImageNet 分类子集"; type="dataset";
       meta=@{ license="cc-by-4.0"; task="image-classification"; estimatedRows=140000;
               dataFormats=@("parquet"); tags=@("code") } },
    @{ name="multilingual-qa-pairs"; displayName="多语言问答对"; type="dataset";
       meta=@{ license="apache-2.0"; task="multilingual"; estimatedRows=560000;
               dataFormats=@("json"); tags=@("mteb") } },
    @{ name="financial-reports-gated"; displayName="财报数据集（申请制）"; type="dataset"; gated=$true;
       meta=@{ license="cc-by-nc-nd"; task="qa"; estimatedRows=32000;
               dataFormats=@("csv"); sensitivityLevel="restricted"; previewPolicy="disabled" } }
)
foreach ($d in $datasets) { Ensure-Repo $bob "demo-bob" $bob.namespaceId $d }

$studios = @(
    @{ name="ai-resume-helper"; displayName="AI 简历助手"; type="studio";
       meta=@{ scenes=@("workplace"); tags=@("chat"); runtimeType="gradio" } },
    @{ name="english-tutor-bot"; displayName="英语口语陪练"; type="studio";
       meta=@{ scenes=@("education"); tags=@("chat"); runtimeType="streamlit" } },
    @{ name="pet-avatar-gen"; displayName="宠物头像生成器"; type="studio";
       meta=@{ scenes=@("pet","fun"); runtimeType="gradio" } },
    @{ name="finance-news-brief"; displayName="财经快讯摘要"; type="studio";
       meta=@{ scenes=@("finance","data"); runtimeType="docker" } },
    @{ name="mcp-tool-playground"; displayName="MCP 工具实验场"; type="studio";
       meta=@{ scenes=@("mcp","coding"); tags=@("code"); runtimeType="docker" } },
    @{ name="travel-planner"; displayName="旅行规划师"; type="studio";
       meta=@{ scenes=@("travel","life"); runtimeType="gradio" } }
)
foreach ($s in $studios) { Ensure-Repo $alice "demo-alice" $alice.namespaceId $s }

# 组织下也放一个模型（验证组织命名空间仓库）
if ($orgNs) {
    Write-Host "[+] 组织仓库"
    Ensure-Repo $alice "demo-lab" $orgNs @{
        name="lab-vision-base"; displayName="Demo Lab 视觉基础模型"; type="model"
        meta=@{ license="apache-2.0"; framework="pytorch"; task="vision-foundation";
                parameterCount=2; parameterUnit="B"; deployable=$true }
    }
}

Write-Host ""
Write-Host ("=== seed 完成：created={0} skipped={1} failed={2} ===" -f $script:created, $script:skipped, $script:failed)
if ($script:failed -gt 0) { exit 1 }
