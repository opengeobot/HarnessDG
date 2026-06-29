# ============================================================================
# 功能: AIHub Compose 引导脚本 (PowerShell)。准备 .env、构建镜像、启动核心
#       服务，并提示创建 Gitea 服务 Token。P0 阶段不创建业务数据。
# 时间: 2026-06-29
# 作者: AxeXie
# ============================================================================
$ErrorActionPreference = "Stop"

$ComposeDir = Split-Path -Parent $PSScriptRoot
Set-Location $ComposeDir

if (-not (Test-Path ".env")) {
    Write-Host "[bootstrap] 未发现 .env，从 .env.example 复制..."
    Copy-Item ".env.example" ".env"
    Write-Host "[bootstrap] 已创建 .env，请按需修改其中的占位密码后重新运行。" -ForegroundColor Yellow
}

Write-Host "[bootstrap] 校验 Compose 配置..."
docker compose config --quiet
if ($LASTEXITCODE -ne 0) { throw "Compose 配置校验失败" }

Write-Host "[bootstrap] 构建镜像（backend / worker / frontend）..."
docker compose build

Write-Host "[bootstrap] 启动服务..."
docker compose up -d

Write-Host "[bootstrap] 当前服务状态："
docker compose ps

Write-Host ""
Write-Host "[bootstrap] 后续步骤：" -ForegroundColor Cyan
Write-Host "  1. 在 Gitea (http://localhost:8080/git/) 创建管理员与服务账号 Token；"
Write-Host "  2. 将 Token 写入 .env 的 GITEA_SERVICE_TOKEN（或 .env.local，勿提交 Git）；"
Write-Host "  3. 运行 ./scripts/verify.ps1 执行 P0 验收检查。"
